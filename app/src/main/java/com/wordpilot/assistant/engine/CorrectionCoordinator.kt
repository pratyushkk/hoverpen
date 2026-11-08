package com.wordpilot.assistant.engine

import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.engine.impl.RuleBasedGrammarEngine
import com.wordpilot.assistant.engine.impl.TrieSpellEngine
import com.wordpilot.assistant.engine.offline.model.AnalysisOptions
import com.wordpilot.assistant.engine.orchestration.ProviderOrchestrator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Coordinates grammar and spelling corrections through the multi-provider orchestrator
 * with automatic failover to on-device offline spelling/grammar engine if cloud APIs fail.
 */
class CorrectionCoordinator(
    val providerOrchestrator: ProviderOrchestrator? = null,
    private val spellEngine: SpellEngine? = null,
    private val grammarEngine: GrammarEngine? = null,
    private val sentenceAnalyzer: SentenceAnalyzer = SentenceAnalyzer()
) {
    // Secondary constructor for unit test suites passing spellEngine & grammarEngine
    constructor(
        spellEngine: SpellEngine?,
        grammarEngine: GrammarEngine?
    ) : this(
        providerOrchestrator = null,
        spellEngine = spellEngine,
        grammarEngine = grammarEngine,
        sentenceAnalyzer = SentenceAnalyzer()
    )

    /**
     * Checks full text through the multi-provider orchestrator with automatic failover.
     * If cloud providers fail or time out, seamlessly falls back to on-device offline analysis.
     */
    suspend fun checkText(text: String): List<Correction> = coroutineScope {
        if (text.isBlank()) return@coroutineScope emptyList()

        if (providerOrchestrator != null) {
            val result = providerOrchestrator.check(text)
            if (result.success && result.corrections.isNotEmpty()) {
                return@coroutineScope result.corrections
            }
            if (result.success && result.complete) {
                return@coroutineScope emptyList()
            }
            // Cloud provider failed or was unavailable; fallback to offline engine
            val fallback = runOfflineCheck(text)
            if (fallback.isNotEmpty()) {
                return@coroutineScope fallback
            }
            return@coroutineScope emptyList()
        }

        runOfflineCheck(text)
    }

    private suspend fun runOfflineCheck(text: String): List<Correction> = coroutineScope {
        val baseCorrections = if (spellEngine is TrieSpellEngine) {
            spellEngine.facade.analyze(
                rawText = text,
                options = AnalysisOptions(
                    includeSpelling = spellEngine.isAvailable(),
                    includeGrammar = grammarEngine?.isAvailable() ?: true
                )
            )
        } else if (grammarEngine is RuleBasedGrammarEngine) {
            grammarEngine.facade.analyze(
                rawText = text,
                options = AnalysisOptions(
                    includeSpelling = spellEngine?.isAvailable() ?: false,
                    includeGrammar = grammarEngine.isAvailable()
                )
            )
        } else {
            val spellJob = async {
                try { spellEngine?.check(text) ?: emptyList() } catch (e: Exception) { emptyList() }
            }
            val grammarJob = async {
                try { grammarEngine?.check(text) ?: emptyList() } catch (e: Exception) { emptyList() }
            }
            spellJob.await() + grammarJob.await()
        }
        resolveOverlaps(baseCorrections)
    }

    /**
     * Checks an individual sentence span through the multi-provider orchestrator,
     * mapping character offsets back to the document coordinate space.
     */
    suspend fun checkSentence(text: String, sentenceStart: Int, sentenceEnd: Int): List<Correction> {
        if (text.isBlank() || sentenceStart >= sentenceEnd) return emptyList()

        val sentence = text.substring(sentenceStart, sentenceEnd.coerceAtMost(text.length))
        if (providerOrchestrator != null) {
            val result = providerOrchestrator.check(sentence)
            if (result.success) {
                return result.corrections.map {
                    it.copy(
                        startOffset = (it.startOffset + sentenceStart).coerceAtMost(sentenceEnd),
                        endOffset = (it.endOffset + sentenceStart).coerceAtMost(sentenceEnd)
                    )
                }
            }
            val fallback = runOfflineCheck(sentence)
            if (fallback.isNotEmpty()) {
                return fallback.map {
                    it.copy(
                        startOffset = (it.startOffset + sentenceStart).coerceAtMost(sentenceEnd),
                        endOffset = (it.endOffset + sentenceStart).coerceAtMost(sentenceEnd)
                    )
                }
            }
            return emptyList()
        }

        val baseCorrections = if (spellEngine is TrieSpellEngine) {
            spellEngine.facade.checkSentenceSpan(
                rawText = text,
                sentenceStart = sentenceStart,
                sentenceEnd = sentenceEnd,
                options = AnalysisOptions(
                    includeSpelling = spellEngine.isAvailable(),
                    includeGrammar = grammarEngine?.isAvailable() ?: true
                )
            )
        } else {
            val corrections = checkText(sentence)
            corrections.map {
                it.copy(
                    startOffset = (it.startOffset + sentenceStart).coerceAtMost(sentenceEnd),
                    endOffset = (it.endOffset + sentenceStart).coerceAtMost(sentenceEnd)
                )
            }
        }

        return resolveOverlaps(baseCorrections)
    }

    private fun resolveOverlaps(corrections: List<Correction>): List<Correction> {
        if (corrections.size <= 1) return corrections

        val sorted = corrections.sortedWith(
            compareBy<Correction> { it.startOffset }
                .thenByDescending { it.severity.ordinal }
        )

        val result = mutableListOf<Correction>()
        var lastEnd = -1

        for (correction in sorted) {
            if (correction.startOffset >= lastEnd) {
                result.add(correction)
                lastEnd = correction.endOffset
            }
        }
        return result
    }
}
