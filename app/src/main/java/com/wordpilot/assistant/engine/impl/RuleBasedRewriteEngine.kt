package com.wordpilot.assistant.engine.impl

import com.wordpilot.assistant.domain.correction.SafeReplacementHelper
import com.wordpilot.assistant.domain.models.*
import com.wordpilot.assistant.engine.CorrectionCoordinator
import com.wordpilot.assistant.engine.LocalAIEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RuleBasedRewriteEngine(
    private val correctionCoordinator: CorrectionCoordinator,
    private val safeReplacementHelper: SafeReplacementHelper
) : LocalAIEngine {

    private val _modelState = MutableStateFlow(ModelState.READY)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    override val isModelInstalled: Boolean = true

    override val modelInfo: ModelInfo = ModelInfo(
        name = "Hoverpen Offline Engine",
        source = "Built-in Rule Engine",
        license = "Hoverpen",
        downloadSizeMb = 0,
        installedSizeMb = 0,
        isInstalled = true
    )

    override suspend fun loadModel() {
        _modelState.value = ModelState.READY
    }

    override suspend fun unloadModel() {
        // no-op
    }

    override suspend fun rewrite(
        text: String,
        style: RewriteStyle,
        customInstruction: String?
    ): RewriteResult {
        if (text.isBlank()) {
            return RewriteResult(
                originalText = text,
                rewrittenText = text,
                style = style,
                isSuccess = false,
                errorMessage = "Text is empty."
            )
        }

        var resultText = text.trim()

        // 1. Run spelling and grammar correction first
        val corrections = correctionCoordinator.checkSentence(resultText, 0, resultText.length)
        if (corrections.isNotEmpty()) {
            val corrected = safeReplacementHelper.applyMultipleCorrections(resultText, corrections)
            if (corrected.isNotBlank()) {
                resultText = corrected
            }
        }

        // 2. Style-specific rule transforms
        resultText = when (style) {
            RewriteStyle.FIX_GRAMMAR -> {
                ensureSentencePunctuationAndCapitalization(resultText)
            }
            RewriteStyle.MAKE_CONCISE -> {
                makeConcise(resultText)
            }
            RewriteStyle.MAKE_PROFESSIONAL -> {
                makeProfessional(resultText)
            }
            RewriteStyle.MAKE_POLITE -> {
                makePolite(resultText)
            }
            RewriteStyle.MAKE_CASUAL -> {
                makeCasual(resultText)
            }
            else -> {
                ensureSentencePunctuationAndCapitalization(resultText)
            }
        }

        return RewriteResult(
            originalText = text,
            rewrittenText = resultText,
            style = style,
            isSuccess = true
        )
    }

    override suspend fun explainErrors(text: String, corrections: List<Correction>): String {
        if (corrections.isEmpty()) return "No grammatical or spelling errors detected."
        return corrections.joinToString("\n") { "• ${it.category.name}: Replace '${it.originalText}' with '${it.suggestedReplacement ?: ""}' (${it.explanation})" }
    }

    override fun cancelInference() {
        // Instant
    }

    override fun getResourceUsage(): ResourceUsage {
        return ResourceUsage(
            memoryUsageMb = 2,
            isModelLoaded = true,
            loadTimeMs = 1,
            lastInferenceTimeMs = 3
        )
    }

    private fun ensureSentencePunctuationAndCapitalization(text: String): String {
        if (text.isBlank()) return text
        var s = text.trim()
        if (s.first().isLowerCase()) {
            s = s.replaceFirstChar { it.uppercase() }
        }
        if (!s.endsWith('.') && !s.endsWith('!') && !s.endsWith('?')) {
            s += "."
        }
        return s
    }

    private fun makeConcise(text: String): String {
        var s = text
        val fillers = mapOf(
            "in order to" to "to",
            "due to the fact that" to "because",
            "at this point in time" to "now",
            "at the present time" to "currently",
            "for the purpose of" to "for",
            "in the event that" to "if",
            "with regard to" to "regarding",
            "in spite of the fact that" to "although",
            "as a matter of fact" to "actually",
            "needless to say" to "",
            "it is important to note that" to "",
            "basically" to "",
            "literally" to ""
        )
        for ((filler, replacement) in fillers) {
            s = s.replace(Regex("(?i)\\b${Regex.escape(filler)}\\b"), replacement)
        }
        // Clean up multi spaces
        s = s.replace(Regex("\\s{2,}"), " ").trim()
        return ensureSentencePunctuationAndCapitalization(s)
    }

    private fun makeProfessional(text: String): String {
        var s = text
        val contractions = mapOf(
            "don't" to "do not",
            "doesn't" to "does not",
            "didn't" to "did not",
            "can't" to "cannot",
            "won't" to "will not",
            "wouldn't" to "would not",
            "shouldn't" to "should not",
            "couldn't" to "could not",
            "isn't" to "is not",
            "aren't" to "are not",
            "wasn't" to "was not",
            "weren't" to "were not",
            "haven't" to "have not",
            "hasn't" to "has not",
            "gonna" to "going to",
            "wanna" to "want to",
            "gotta" to "need to",
            "kinda" to "somewhat",
            "sorta" to "somewhat",
            "btw" to "incidentally",
            "asap" to "as soon as possible",
            "thx" to "thank you",
            "thanks" to "thank you",
            "pls" to "please",
            "plz" to "please",
            "u" to "you",
            "r" to "are"
        )
        for ((word, expansion) in contractions) {
            s = s.replace(Regex("(?i)\\b${Regex.escape(word)}\\b"), expansion)
        }
        s = s.replace(Regex("\\s{2,}"), " ").trim()
        return ensureSentencePunctuationAndCapitalization(s)
    }

    private fun makePolite(text: String): String {
        var s = makeProfessional(text)
        if (!s.contains(Regex("(?i)\\b(please|thank you|kindly)\\b"))) {
            s = "Please $s"
        }
        return ensureSentencePunctuationAndCapitalization(s)
    }

    private fun makeCasual(text: String): String {
        var s = text.trim()
        val casualMap = mapOf(
            "do not hesitate to" to "feel free to",
            "inquire" to "ask",
            "assist" to "help",
            "utilize" to "use",
            "subsequently" to "later",
            "furthermore" to "also"
        )
        for ((formal, casual) in casualMap) {
            s = s.replace(Regex("(?i)\\b${Regex.escape(formal)}\\b"), casual)
        }
        return ensureSentencePunctuationAndCapitalization(s)
    }
}
