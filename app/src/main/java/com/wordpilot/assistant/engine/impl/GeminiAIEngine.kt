package com.wordpilot.assistant.engine.impl

import android.util.Log
import com.wordpilot.assistant.domain.models.*
import com.wordpilot.assistant.engine.LocalAIEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiAIEngine(
    private val apiKeyProvider: () -> String?
) : LocalAIEngine {

    companion object {
        private const val TAG = "GeminiAIEngine"

        // Prioritized list of active Google Generative AI endpoints
        val FALLBACK_ENDPOINTS = listOf(
            // 1. Gemini 2.0 Flash on v1beta (Official standard for Google AI Studio)
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
            // 2. Gemini 2.0 Flash Lite on v1beta
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent",
            // 3. Gemini 2.5 Flash on v1beta
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            // 4. Gemini 2.0 Flash on v1
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent",
            // 5. Gemini 2.5 Flash on v1
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent"
        )

        @Volatile
        var cachedWorkingEndpoint: String? = null

        /**
         * Sanitizes user-pasted API keys to eliminate whitespace, accidental wrapping quotes,
         * carriage returns, or prefix keywords (like 'Bearer ' or 'key=').
         */
        fun sanitizeApiKey(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.trim()
                .removePrefix("\"").removeSuffix("\"")
                .removePrefix("'").removeSuffix("'")
                .removePrefix("Bearer ").removePrefix("bearer ")
                .removePrefix("key=").removePrefix("KEY=")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .trim()
        }

        /**
         * Dynamically queries Google's ModelService across both v1 and v1beta to discover
         * all models that are active and support generateContent for the provided API key.
         */
        fun discoverActiveModelEndpoints(apiKey: String): List<String> {
            val cleanKey = sanitizeApiKey(apiKey)
            if (cleanKey.isBlank()) return emptyList()

            val discovered = mutableListOf<String>()
            val listUrls = listOf(
                "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey",
                "https://generativelanguage.googleapis.com/v1/models?key=$cleanKey"
            )

            for (listUrl in listUrls) {
                try {
                    val url = URL(listUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("x-goog-api-key", cleanKey)
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = 6000
                        readTimeout = 8000
                    }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                        val root = JSONObject(body)
                        val models = root.optJSONArray("models") ?: continue
                        val baseApi = if (listUrl.contains("/v1/")) "https://generativelanguage.googleapis.com/v1" else "https://generativelanguage.googleapis.com/v1beta"

                        val candidateList = mutableListOf<String>()
                        for (i in 0 until models.length()) {
                            val m = models.optJSONObject(i) ?: continue
                            val name = m.optString("name", "") // e.g. "models/gemini-2.0-flash"
                            
                            // Exclude audio/tts, image, and embedding models
                            if (name.contains("tts", ignoreCase = true) ||
                                name.contains("image", ignoreCase = true) ||
                                name.contains("audio", ignoreCase = true) ||
                                name.contains("embedding", ignoreCase = true) ||
                                name.contains("aqa", ignoreCase = true)) {
                                continue
                            }

                            // CRITICAL: Exclude legacy 1.5 models that fail on v1beta generateContent
                            if (name.contains("1.5-flash", ignoreCase = true) ||
                                name.contains("1.5-pro", ignoreCase = true)) {
                                continue
                            }

                            val methods = m.optJSONArray("supportedGenerationMethods")
                            var canGenerate = false
                            if (methods != null) {
                                for (j in 0 until methods.length()) {
                                    if (methods.optString(j) == "generateContent") {
                                        canGenerate = true
                                        break
                                    }
                                }
                            }
                            if (canGenerate && name.isNotBlank()) {
                                candidateList.add("$baseApi/$name:generateContent")
                            }
                        }

                        // Sort candidates: gemini-2.0-flash / 2.5-flash first, then lite models, then other flash
                        candidateList.sortedWith(compareByDescending<String> { endpoint ->
                            when {
                                endpoint.contains("gemini-2.0-flash", ignoreCase = true) && !endpoint.contains("lite", ignoreCase = true) -> 100
                                endpoint.contains("gemini-2.5-flash", ignoreCase = true) && !endpoint.contains("lite", ignoreCase = true) -> 95
                                endpoint.contains("gemini-2.0-flash-lite", ignoreCase = true) -> 90
                                endpoint.contains("gemini-2.5-flash-lite", ignoreCase = true) -> 85
                                endpoint.contains("flash", ignoreCase = true) -> 70
                                endpoint.contains("gemini", ignoreCase = true) -> 50
                                else -> 10
                            }
                        }).forEach { ep ->
                            if (!discovered.contains(ep)) {
                                discovered.add(ep)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Model discovery attempt failed on $listUrl: ${e.message}")
                }
            }

            Log.i(TAG, "Discovered active text endpoints (${discovered.size}): $discovered")
            if (discovered.isNotEmpty() && cachedWorkingEndpoint == null) {
                cachedWorkingEndpoint = discovered.first()
                Log.i(TAG, "Discovered and cached primary Gemini endpoint: ${discovered.first()}")
            }
            return discovered
        }

        fun discoverActiveModelEndpoint(apiKey: String): String? {
            val list = discoverActiveModelEndpoints(apiKey)
            return list.firstOrNull() ?: cachedWorkingEndpoint
        }
    }

    private val _modelState = MutableStateFlow(ModelState.READY)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    override val isModelInstalled: Boolean
        get() = sanitizeApiKey(apiKeyProvider()).isNotBlank()

    override val modelInfo: ModelInfo = ModelInfo(
        name = "Gemini Model",
        source = "Google AI Studio",
        license = "Google Cloud",
        downloadSizeMb = 0,
        installedSizeMb = 0,
        isInstalled = true
    )

    private var activeConnection: HttpURLConnection? = null

    override suspend fun loadModel() {
        _modelState.value = ModelState.READY
    }

    override suspend fun unloadModel() {
        cancelInference()
        _modelState.value = ModelState.READY
    }

    override suspend fun rewrite(
        text: String,
        style: RewriteStyle,
        customInstruction: String?
    ): RewriteResult = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKeyProvider())
        if (cleanKey.isBlank()) {
            return@withContext RewriteResult(
                originalText = text,
                rewrittenText = text,
                style = style,
                isSuccess = false,
                errorMessage = "Gemini API key is not configured. Tap 'AI' to add your API key."
            )
        }

        if (text.isBlank()) {
            return@withContext RewriteResult(
                originalText = text,
                rewrittenText = text,
                style = style,
                isSuccess = false,
                errorMessage = "Text is empty."
            )
        }

        _modelState.value = ModelState.LOADING
        val prompt = buildPrompt(text, style, customInstruction)

        // Build list of endpoints to attempt, starting with the cached working endpoint
        val endpointsToTry = mutableListOf<String>()
        cachedWorkingEndpoint?.let { endpointsToTry.add(it) }

        // Discover active model endpoints dynamically from Google's ModelService
        val discovered = discoverActiveModelEndpoints(cleanKey)
        discovered.forEach { if (!endpointsToTry.contains(it)) endpointsToTry.add(it) }

        FALLBACK_ENDPOINTS.forEach { if (!endpointsToTry.contains(it)) endpointsToTry.add(it) }

        var lastErrorMsg = "Failed to connect to Gemini."

        for (endpointBase in endpointsToTry) {
            try {
                val url = URL("$endpointBase?key=$cleanKey")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 6000
                    readTimeout = 8000
                    doOutput = true
                    doInput = true
                }
                activeConnection = connection

                val requestJson = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                val partObj = JSONObject().apply {
                                    put("text", prompt)
                                }
                                put(partObj)
                            }
                            put("parts", parts)
                        }
                        put(contentObj)
                    }
                    put("contents", contents)

                    val generationConfig = JSONObject().apply {
                        put("temperature", 0.3)
                        put("maxOutputTokens", 1024)
                    }
                    put("generationConfig", generationConfig)
                }

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(requestJson.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }

                val responseBody = BufferedReader(InputStreamReader(responseStream, "UTF-8")).use { reader ->
                    reader.readText()
                }

                if (responseCode in 200..299) {
                    // Success! Cache this working endpoint
                    cachedWorkingEndpoint = endpointBase
                    val parsedOutput = parseGeminiOutput(responseBody)
                    val cleanedOutput = cleanRewrittenText(parsedOutput, text)

                    _modelState.value = ModelState.READY
                    return@withContext RewriteResult(
                        originalText = text,
                        rewrittenText = cleanedOutput,
                        style = style,
                        isSuccess = true
                    )
                } else {
                    lastErrorMsg = parseErrorMessage(responseBody, responseCode)
                    Log.w(TAG, "Endpoint $endpointBase failed with HTTP $responseCode: $lastErrorMsg")
                    // If it's a 404 (model not found on this version), continue to next endpoint
                    if (responseCode == 404 || responseBody.contains("not found for API version", ignoreCase = true)) {
                        continue
                    } else if (responseCode == 400 && responseBody.contains("API_KEY_INVALID", ignoreCase = true)) {
                        // Key itself is invalid, no need to retry other endpoints
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $endpointBase threw exception", e)
                lastErrorMsg = e.localizedMessage ?: "Network error"
            } finally {
                activeConnection?.disconnect()
                activeConnection = null
            }
        }

        _modelState.value = ModelState.READY
        return@withContext RewriteResult(
            originalText = text,
            rewrittenText = text,
            style = style,
            isSuccess = false,
            errorMessage = lastErrorMsg
        )
    }

    override suspend fun explainErrors(
        text: String,
        corrections: List<com.wordpilot.assistant.domain.models.Correction>
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKey(apiKeyProvider())
        if (cleanKey.isBlank()) {
            return@withContext "Gemini API key is not configured."
        }
        val prompt = "Briefly explain the grammar improvements for this text:\n$text"
        val result = rewrite(prompt, RewriteStyle.REWRITE)
        if (result.isSuccess) result.rewrittenText else "No explanation available."
    }

    override fun cancelInference() {
        try {
            activeConnection?.disconnect()
            activeConnection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling Gemini inference", e)
        }
        _modelState.value = ModelState.READY
    }

    override fun getResourceUsage(): ResourceUsage {
        return ResourceUsage(
            memoryUsageMb = 0,
            isModelLoaded = true,
            loadTimeMs = 0,
            lastInferenceTimeMs = 0
        )
    }

    fun buildPrompt(text: String, style: RewriteStyle, customInstruction: String?): String {
        val instruction = when (style) {
            RewriteStyle.FIX_GRAMMAR ->
                "Fix all grammatical, spelling, capitalization, and punctuation mistakes. Preserve the original meaning and tone strictly."
            RewriteStyle.REWRITE ->
                "Rewrite the text to be fluent, natural, and expressive while keeping the core meaning."
            RewriteStyle.IMPROVE_CLARITY ->
                "Rewrite the text to be crystal clear, crisp, and unambiguous."
            RewriteStyle.MAKE_PROFESSIONAL ->
                "Rewrite the text in a polished, respectful, executive business tone suitable for work or email."
            RewriteStyle.MAKE_CONCISE ->
                "Rewrite the text to be concise and direct. Cut filler and wordiness while keeping every essential point."
            RewriteStyle.MAKE_POLITE ->
                "Rewrite the text to be courteous, friendly, warm, and polite."
            RewriteStyle.MAKE_CASUAL ->
                "Rewrite the text in a relaxed, modern, casual conversational tone."
            RewriteStyle.IMPROVE_NATURAL ->
                "Rewrite the text to sound like a native English speaker with natural phrasing."
            RewriteStyle.EXPLAIN_MISTAKES ->
                "Explain the mistakes found in this text."
            RewriteStyle.CUSTOM ->
                customInstruction?.takeIf { it.isNotBlank() } ?: "Improve this text."
        }

        return """
            You are Hoverpen, a premier mobile writing assistant.
            Instruction: $instruction
            
            Original Text:
            "$text"
            
            CRITICAL RULES:
            1. Output ONLY the rewritten text.
            2. Do NOT add any preamble, conversational commentary, or explanations.
            3. Do NOT surround the output in quotation marks or markdown backticks.
        """.trimIndent()
    }

    fun parseGeminiOutput(responseJson: String): String {
        try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            return parts.getJSONObject(0).optString("text", "")
        } catch (_: Exception) {
            return ""
        }
    }

    fun cleanRewrittenText(raw: String, originalText: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        if (cleaned.startsWith("“") && cleaned.endsWith("”") && cleaned.length >= 2) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        if (cleaned.startsWith("```") && cleaned.endsWith("```") && cleaned.length >= 6) {
            cleaned = cleaned.substring(3, cleaned.length - 3).trim()
        }
        return if (cleaned.isNotBlank()) cleaned else originalText
    }

    fun parseErrorMessage(responseBody: String, code: Int): String {
        try {
            val root = JSONObject(responseBody)
            val error = root.optJSONObject("error")
            if (error != null) {
                val message = error.optString("message", "")
                val status = error.optString("status", "")

                val details = error.optJSONArray("details")
                var isKeyInvalid = false
                if (details != null) {
                    for (i in 0 until details.length()) {
                        val detail = details.optJSONObject(i)
                        if (detail?.optString("reason") == "API_KEY_INVALID") {
                            isKeyInvalid = true
                            break
                        }
                    }
                }

                if (isKeyInvalid || message.contains("API key not valid", ignoreCase = true) || message.contains("API_KEY_INVALID", ignoreCase = true)) {
                    return "Invalid Gemini API key. Please check your key from Google AI Studio (aistudio.google.com/app/apikey)."
                }
                if (status == "PERMISSION_DENIED" || code == 403) {
                    return "Permission denied (HTTP 403). Make sure Generative Language API is enabled for this API key in Google Cloud/AI Studio."
                }
                if (status == "RESOURCE_EXHAUSTED" || code == 429) {
                    return "Gemini API quota exceeded (HTTP 429). Please wait a moment."
                }
                if (code == 404 || message.contains("not found for API version", ignoreCase = true) || message.contains("is not found", ignoreCase = true)) {
                    return "Model endpoint not found for this API version. Trying alternative Gemini models..."
                }
                if (message.isNotBlank()) {
                    return "Gemini error: $message"
                }
            }
        } catch (_: Exception) {}
        return "Gemini request failed (HTTP $code). Verify your API key or check your internet connection."
    }
}
