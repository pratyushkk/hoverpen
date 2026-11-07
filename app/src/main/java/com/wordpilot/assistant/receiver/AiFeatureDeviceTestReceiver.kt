package com.wordpilot.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wordpilot.assistant.WordPilotApp
import com.wordpilot.assistant.domain.models.AIEngineType
import com.wordpilot.assistant.domain.models.RewriteStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

/**
 * Diagnostic broadcast receiver to verify live Gemini AI features (Rewrite & Tone Adjustment) on device.
 * Trigger via:
 * adb shell am broadcast -a com.wordpilot.assistant.TEST_AI_FEATURES -n com.wordpilot.assistant.v2/com.wordpilot.assistant.receiver.AiFeatureDeviceTestReceiver
 */
class AiFeatureDeviceTestReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AiFeatureTestReceiver"
        const val ACTION_TEST_AI_FEATURES = "com.wordpilot.assistant.TEST_AI_FEATURES"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TEST_AI_FEATURES) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = WordPilotApp.instance
                val reportBuilder = StringBuilder()

                reportBuilder.append("=====================================================\n")
                reportBuilder.append("       HOVERPEN V2 ON-DEVICE AI TEST REPORT         \n")
                reportBuilder.append("=====================================================\n")
                reportBuilder.append("Timestamp: ${Instant.now()}\n")

                val apiKey = app.cachedGeminiApiKey 
                    ?: try { app.userPreferences.geminiApiKey.first() } catch (_: Exception) { null }

                if (apiKey.isNullOrBlank()) {
                    reportBuilder.append("STATUS: FAILED - Gemini API key is missing or blank.\n")
                    Log.e(TAG, reportBuilder.toString())
                    writeReport(context, reportBuilder.toString())
                    return@launch
                }

                reportBuilder.append("API Key Present: YES (${apiKey.length} chars)\n")
                reportBuilder.append("Active Engine: Gemini Model (Core V2 AI)\n\n")

                // Step 1: Endpoint Discovery Test
                reportBuilder.append("--- [TEST 1/8] Dynamic Model Endpoint Discovery ---\n")
                val discStart = System.currentTimeMillis()
                val discoveredEndpoint = com.wordpilot.assistant.engine.impl.GeminiAIEngine.discoverActiveModelEndpoint(apiKey)
                val discDuration = System.currentTimeMillis() - discStart
                reportBuilder.append("Discovered Endpoint: ${discoveredEndpoint ?: com.wordpilot.assistant.engine.impl.GeminiAIEngine.cachedWorkingEndpoint}\n")
                reportBuilder.append("Discovery Latency: ${discDuration}ms\n\n")

                // Step 2-4: Rewrite Features
                val rewriteTestCases = listOf(
                    Triple(
                        "Fix Grammar",
                        RewriteStyle.FIX_GRAMMAR,
                        "they is going to the store yesterday and they doesnt care about the rain"
                    ),
                    Triple(
                        "Rewrite & Polish",
                        RewriteStyle.REWRITE,
                        "the meet was cancel due to bad wheather conditions and we need re-schedule for tomorrow"
                    ),
                    Triple(
                        "Improve Clarity",
                        RewriteStyle.IMPROVE_CLARITY,
                        "due to the fact that there was an absence of sufficient clarity in the explanation, we were unable to proceed"
                    )
                )

                // Step 5-8: Tone Adjustments
                val toneTestCases = listOf(
                    Triple(
                        "Professional Tone",
                        RewriteStyle.MAKE_PROFESSIONAL,
                        "hey man, send me that report right now before the manager gets super mad at us"
                    ),
                    Triple(
                        "Casual Tone",
                        RewriteStyle.MAKE_CASUAL,
                        "I am writing this communication to formally inform you that I shall not be present at the upcoming gathering."
                    ),
                    Triple(
                        "Concise Tone",
                        RewriteStyle.MAKE_CONCISE,
                        "In order to make sure that each and every one of us is completely on the same page regarding the things we talked about, I am writing this message."
                    ),
                    Triple(
                        "Polite Tone",
                        RewriteStyle.MAKE_POLITE,
                        "Fix this bug immediately, it broke my feature."
                    )
                )

                var testIndex = 2

                // Run Rewrite tests
                for ((name, style, input) in rewriteTestCases) {
                    reportBuilder.append("--- [TEST $testIndex/8] Rewrite: $name ---\n")
                    reportBuilder.append("Input: \"$input\"\n")
                    val start = System.currentTimeMillis()
                    val result = app.rewriteCoordinator.rewrite(
                        text = input,
                        style = style,
                        forcedEngine = AIEngineType.GEMINI_FLASH
                    )
                    val duration = System.currentTimeMillis() - start
                    reportBuilder.append("Latency: ${duration}ms\n")
                    reportBuilder.append("Success: ${result.isSuccess}\n")
                    if (result.isSuccess) {
                        reportBuilder.append("Output: \"${result.rewrittenText}\"\n")
                    } else {
                        reportBuilder.append("Error: ${result.errorMessage}\n")
                    }
                    reportBuilder.append("\n")
                    testIndex++
                }

                // Run Tone tests
                for ((name, style, input) in toneTestCases) {
                    reportBuilder.append("--- [TEST $testIndex/8] Tone Adjustment: $name ---\n")
                    reportBuilder.append("Input: \"$input\"\n")
                    val start = System.currentTimeMillis()
                    val result = app.rewriteCoordinator.rewrite(
                        text = input,
                        style = style,
                        forcedEngine = AIEngineType.GEMINI_FLASH
                    )
                    val duration = System.currentTimeMillis() - start
                    reportBuilder.append("Latency: ${duration}ms\n")
                    reportBuilder.append("Success: ${result.isSuccess}\n")
                    if (result.isSuccess) {
                        reportBuilder.append("Output: \"${result.rewrittenText}\"\n")
                    } else {
                        reportBuilder.append("Error: ${result.errorMessage}\n")
                    }
                    reportBuilder.append("\n")
                    testIndex++
                }

                reportBuilder.append("=====================================================\n")
                reportBuilder.append("             ON-DEVICE AI TESTING COMPLETE           \n")
                reportBuilder.append("=====================================================\n")

                val finalOutput = reportBuilder.toString()
                Log.i(TAG, finalOutput)
                writeReport(context, finalOutput)

            } catch (e: Exception) {
                Log.e(TAG, "Unhandled error during AI testing on device", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun writeReport(context: Context, report: String) {
        try {
            val reportFile = File(context.filesDir, "ai_feature_test_results.txt")
            reportFile.writeText(report)
            Log.i(TAG, "Report saved to: ${reportFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write report file", e)
        }
    }
}
