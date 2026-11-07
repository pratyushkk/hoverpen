package com.wordpilot.assistant.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordpilot.assistant.WordPilotApp
import com.wordpilot.assistant.engine.providers.ProviderConfig
import com.wordpilot.assistant.engine.providers.ProviderResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ApiSettingsUiState(
    val geminiApiKey: String = "",
    val isTestingGemini: Boolean = false,
    val geminiTestResult: String? = null,
    val geminiTestSuccess: Boolean? = null,
    val saplingApiKey: String = "",
    val saplingEnabled: Boolean = true,
    val languageToolBaseUrl: String = "https://api.languagetool.org",
    val languageToolEnabled: Boolean = true,
    val isTestingSapling: Boolean = false,
    val saplingTestResult: String? = null,
    val saplingTestSuccess: Boolean? = null,
    val isTestingLt: Boolean = false,
    val ltTestResult: String? = null,
    val ltTestSuccess: Boolean? = null
)

class ApiSettingsViewModel : ViewModel() {
    private val app = WordPilotApp.instance
    private val prefs = app.userPreferences

    private val _uiState = MutableStateFlow(ApiSettingsUiState())
    val uiState: StateFlow<ApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.geminiApiKey,
                prefs.saplingApiKey,
                prefs.saplingEnabled,
                prefs.languageToolBaseUrl,
                prefs.languageToolEnabled
            ) { gemKey, sapKey, sapEnabled, ltUrl, ltEnabled ->
                ApiSettingsUiState(
                    geminiApiKey = gemKey ?: "",
                    saplingApiKey = sapKey ?: "",
                    saplingEnabled = sapEnabled,
                    languageToolBaseUrl = ltUrl,
                    languageToolEnabled = ltEnabled
                )
            }.collect { newState ->
                _uiState.update { current ->
                    current.copy(
                        geminiApiKey = newState.geminiApiKey,
                        saplingApiKey = newState.saplingApiKey,
                        saplingEnabled = newState.saplingEnabled,
                        languageToolBaseUrl = newState.languageToolBaseUrl,
                        languageToolEnabled = newState.languageToolEnabled
                    )
                }
            }
        }
    }

    fun setGeminiApiKey(key: String) {
        val sanitized = com.wordpilot.assistant.engine.impl.GeminiAIEngine.sanitizeApiKey(key)
        _uiState.update { it.copy(geminiApiKey = sanitized) }
        viewModelScope.launch {
            prefs.setGeminiApiKey(sanitized)
            app.updateGeminiApiKey(sanitized)
        }
    }

    fun testGeminiConnection() {
        val cleanKey = com.wordpilot.assistant.engine.impl.GeminiAIEngine.sanitizeApiKey(_uiState.value.geminiApiKey)
        if (cleanKey.isBlank()) {
            _uiState.update { it.copy(geminiTestResult = "Please enter your Gemini API key first", geminiTestSuccess = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingGemini = true, geminiTestResult = null, geminiTestSuccess = null) }
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Discover active endpoints or fall back across candidate endpoints
                    val discoveredList = com.wordpilot.assistant.engine.impl.GeminiAIEngine.discoverActiveModelEndpoints(cleanKey)
                    val endpointsToTry = mutableListOf<String>()
                    discoveredList.forEach { if (!endpointsToTry.contains(it)) endpointsToTry.add(it) }
                    com.wordpilot.assistant.engine.impl.GeminiAIEngine.cachedWorkingEndpoint?.let {
                        if (!endpointsToTry.contains(it)) endpointsToTry.add(it)
                    }
                    com.wordpilot.assistant.engine.impl.GeminiAIEngine.FALLBACK_ENDPOINTS.forEach {
                        if (!endpointsToTry.contains(it)) endpointsToTry.add(it)
                    }

                    var lastErr = "Unable to connect with this API key. Please verify that your Gemini key is active in Google AI Studio."
                    for (endpoint in endpointsToTry) {
                        try {
                            val url = java.net.URL("$endpoint?key=$cleanKey")
                            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                                requestMethod = "POST"
                                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                                setRequestProperty("Accept", "application/json")
                                setRequestProperty("x-goog-api-key", cleanKey)
                                connectTimeout = 8000
                                readTimeout = 10000
                                doOutput = true
                            }
                            val json = org.json.JSONObject().apply {
                                val contents = org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply {
                                        put("role", "user")
                                        val parts = org.json.JSONArray().apply {
                                            put(org.json.JSONObject().apply { put("text", "ping") })
                                        }
                                        put("parts", parts)
                                    })
                                }
                                put("contents", contents)
                            }
                            conn.outputStream.use { os ->
                                java.io.OutputStreamWriter(os, "UTF-8").use { it.write(json.toString()) }
                            }
                            val code = conn.responseCode
                            if (code in 200..299) {
                                com.wordpilot.assistant.engine.impl.GeminiAIEngine.cachedWorkingEndpoint = endpoint
                                return@withContext "SUCCESS"
                            } else {
                                val stream = conn.errorStream ?: conn.inputStream
                                val err = java.io.BufferedReader(java.io.InputStreamReader(stream, "UTF-8")).use { it.readText() }
                                val parsed = com.wordpilot.assistant.engine.impl.GeminiAIEngine { cleanKey }.parseErrorMessage(err, code)
                                lastErr = parsed
                                if (code == 400 && (err.contains("API_KEY_INVALID", ignoreCase = true) || err.contains("API key not valid", ignoreCase = true))) {
                                    return@withContext "Invalid Gemini API key. Please check your key from Google AI Studio (aistudio.google.com/app/apikey)."
                                }
                                if (code == 429) {
                                    return@withContext "Gemini API quota exceeded (HTTP 429). Please wait a moment."
                                }
                            }
                        } catch (e: Exception) {
                            lastErr = e.localizedMessage ?: "Connection error"
                        }
                    }
                    lastErr
                }

                if (result == "SUCCESS") {
                    _uiState.update {
                        it.copy(
                            isTestingGemini = false,
                            geminiTestResult = "✓ Connected to Gemini Model successfully!",
                            geminiTestSuccess = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isTestingGemini = false,
                            geminiTestResult = "✗ $result",
                            geminiTestSuccess = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingGemini = false,
                        geminiTestResult = "✗ Network error: ${e.localizedMessage ?: "Unknown error"}",
                        geminiTestSuccess = false
                    )
                }
            }
        }
    }

    fun setSaplingApiKey(key: String) {
        val trimmed = key.trim()
        viewModelScope.launch {
            prefs.setSaplingApiKey(trimmed)
            app.providerOrchestrator.configureProvider(
                "sapling",
                ProviderConfig(
                    id = "sapling",
                    enabled = _uiState.value.saplingEnabled,
                    apiKey = trimmed.ifBlank { null },
                    baseUrl = null,
                    priority = 0
                )
            )
        }
    }

    fun setSaplingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSaplingEnabled(enabled)
            app.providerOrchestrator.configureProvider(
                "sapling",
                ProviderConfig(
                    id = "sapling",
                    enabled = enabled,
                    apiKey = _uiState.value.saplingApiKey.ifBlank { null },
                    baseUrl = null,
                    priority = 0
                )
            )
        }
    }

    fun testSapling() {
        val key = _uiState.value.saplingApiKey.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(saplingTestResult = "Please enter an API key first", saplingTestSuccess = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingSapling = true, saplingTestResult = null, saplingTestSuccess = null) }
            try {
                val provider = app.providerOrchestrator.getProvider("sapling")
                if (provider == null) {
                    _uiState.update { it.copy(isTestingSapling = false, saplingTestResult = "Sapling provider not registered", saplingTestSuccess = false) }
                    return@launch
                }
                provider.configure(
                    ProviderConfig(
                        id = "sapling",
                        enabled = true,
                        apiKey = key,
                        baseUrl = null,
                        priority = 0
                    )
                )
                val result = provider.check("She go to school and they is happy.", "en")
                when (result) {
                    is ProviderResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isTestingSapling = false,
                                saplingTestResult = "✓ Connected! Sapling returned ${result.corrections.size} corrections (200 OK)",
                                saplingTestSuccess = true
                            )
                        }
                    }
                    is ProviderResult.Failure.AuthError -> {
                        _uiState.update {
                            it.copy(
                                isTestingSapling = false,
                                saplingTestResult = "✗ Invalid API key (HTTP ${result.statusCode})",
                                saplingTestSuccess = false
                            )
                        }
                    }
                    is ProviderResult.Failure.RateLimited -> {
                        _uiState.update {
                            it.copy(
                                isTestingSapling = false,
                                saplingTestResult = "✗ Rate limit exceeded (retry in ${result.retryAfterSeconds ?: 60}s)",
                                saplingTestSuccess = false
                            )
                        }
                    }
                    is ProviderResult.Failure.QuotaExhausted -> {
                        _uiState.update {
                            it.copy(
                                isTestingSapling = false,
                                saplingTestResult = "✗ Monthly quota exhausted: ${result.message}",
                                saplingTestSuccess = false
                            )
                        }
                    }
                    is ProviderResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isTestingSapling = false,
                                saplingTestResult = "✗ Connection failed: ${result.message}",
                                saplingTestSuccess = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingSapling = false,
                        saplingTestResult = "✗ Error: ${e.localizedMessage ?: "Unknown error"}",
                        saplingTestSuccess = false
                    )
                }
            }
        }
    }

    fun setLanguageToolBaseUrl(url: String) {
        val trimmed = url.trim()
        viewModelScope.launch {
            prefs.setLanguageToolBaseUrl(trimmed)
            app.providerOrchestrator.configureProvider(
                "languagetool",
                ProviderConfig(
                    id = "languagetool",
                    enabled = _uiState.value.languageToolEnabled,
                    apiKey = null,
                    baseUrl = trimmed.ifBlank { "https://api.languagetool.org" },
                    priority = 1
                )
            )
        }
    }

    fun setLanguageToolEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setLanguageToolEnabled(enabled)
            app.providerOrchestrator.configureProvider(
                "languagetool",
                ProviderConfig(
                    id = "languagetool",
                    enabled = enabled,
                    apiKey = null,
                    baseUrl = _uiState.value.languageToolBaseUrl.ifBlank { "https://api.languagetool.org" },
                    priority = 1
                )
            )
        }
    }

    fun testLanguageTool() {
        val url = _uiState.value.languageToolBaseUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(ltTestResult = "Please enter server URL first", ltTestSuccess = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingLt = true, ltTestResult = null, ltTestSuccess = null) }
            try {
                val provider = app.providerOrchestrator.getProvider("languagetool")
                if (provider == null) {
                    _uiState.update { it.copy(isTestingLt = false, ltTestResult = "LanguageTool provider not registered", ltTestSuccess = false) }
                    return@launch
                }
                provider.configure(
                    ProviderConfig(
                        id = "languagetool",
                        enabled = true,
                        apiKey = null,
                        baseUrl = url,
                        priority = 1
                    )
                )
                val result = provider.check("She go to school.", "en")
                when (result) {
                    is ProviderResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isTestingLt = false,
                                ltTestResult = "✓ Connected! LanguageTool verified (${result.corrections.size} matches)",
                                ltTestSuccess = true
                            )
                        }
                    }
                    is ProviderResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isTestingLt = false,
                                ltTestResult = "✗ Failed: ${result.message}",
                                ltTestSuccess = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingLt = false,
                        ltTestResult = "✗ Error: ${e.localizedMessage ?: "Unknown error"}",
                        ltTestSuccess = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ApiSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Providers & Keys", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 1. SIMPLE SEQUENTIAL FAILOVER BANNER
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Automatic Failover Architecture",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Hoverpen checks grammar using your primary provider first. If it reaches quota or is unreachable, it automatically falls back seamlessly with zero interruption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Priority Chain Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "1. Sapling (Primary)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (uiState.saplingApiKey.isNotBlank() && uiState.saplingEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                        )
                        Text("➔", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "2. LanguageTool (Automatic Fallback)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (uiState.languageToolEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // =========================================================================
            // 1.5 GOOGLE GEMINI 1.5 FLASH (V2 CORE LLM)
            // =========================================================================
            ProviderCard(
                title = "Gemini Model",
                roleBadge = "V2 Core AI",
                badgeColor = Color(0xFF8B5CF6),
                description = "Powers generative rewriting and tone adjustments across all apps.",
                isEnabled = true,
                onToggleEnabled = { /* Always active for V2 */ }
            ) {
                var isPasswordVisible by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = uiState.geminiApiKey,
                    onValueChange = { viewModel.setGeminiApiKey(it) },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle key visibility",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (uiState.geminiApiKey.isNotBlank()) {
                                IconButton(onClick = { viewModel.setGeminiApiKey("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text ?: ""
                            if (clipText.isNotBlank()) {
                                viewModel.setGeminiApiKey(clipText)
                                focusManager.clearFocus()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste Key", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.testGeminiConnection()
                        },
                        enabled = !uiState.isTestingGemini && uiState.geminiApiKey.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6)
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        if (uiState.isTestingGemini) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Connection", fontSize = 12.sp)
                        }
                    }
                }

                if (uiState.geminiTestResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TestResultBox(
                        message = uiState.geminiTestResult ?: "",
                        isSuccess = uiState.geminiTestSuccess == true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                uriHandler.openUri("https://aistudio.google.com/app/apikey")
                            } catch (e: Exception) {}
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Get free Gemini API key at Google AI Studio (30 secs)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =========================================================================
            // 2. SAPLING (PRIMARY) CARD
            // =========================================================================
            ProviderCard(
                title = "Sapling AI",
                roleBadge = "Primary Provider",
                badgeColor = Color(0xFF10B981),
                description = "Recommended. Fast neural grammar, punctuation, and contextual spelling corrections.",
                isEnabled = uiState.saplingEnabled,
                onToggleEnabled = { viewModel.setSaplingEnabled(it) }
            ) {
                var isSecretVisible by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = uiState.saplingApiKey,
                    onValueChange = { viewModel.setSaplingApiKey(it) },
                    label = { Text("Sapling API Key") },
                    placeholder = { Text("Paste key here...") },
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                Icon(
                                    if (isSecretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle visibility",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (uiState.saplingApiKey.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSaplingApiKey("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://sapling.ai") },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get Free API Key on sapling.ai", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (!clipText.isNullOrBlank()) {
                                viewModel.setSaplingApiKey(clipText)
                                focusManager.clearFocus()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste Key", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.testSapling()
                        },
                        enabled = !uiState.isTestingSapling && uiState.saplingApiKey.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        if (uiState.isTestingSapling) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Connection", fontSize = 12.sp)
                        }
                    }
                }

                // Test Result Status Box
                if (uiState.saplingTestResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TestResultBox(
                        message = uiState.saplingTestResult ?: "",
                        isSuccess = uiState.saplingTestSuccess == true
                    )
                }
            }

            // =========================================================================
            // 3. LANGUAGETOOL (FIRST FALLBACK) CARD
            // =========================================================================
            ProviderCard(
                title = "LanguageTool",
                roleBadge = "First Fallback",
                badgeColor = MaterialTheme.colorScheme.primary,
                description = "Self-hosted server or authorized commercial endpoint. Seamlessly activates if Sapling is unreachable.",
                isEnabled = uiState.languageToolEnabled,
                onToggleEnabled = { viewModel.setLanguageToolEnabled(it) }
            ) {
                OutlinedTextField(
                    value = uiState.languageToolBaseUrl,
                    onValueChange = { viewModel.setLanguageToolBaseUrl(it) },
                    label = { Text("Server Base URL") },
                    placeholder = { Text("http://192.168.1.100:8081") },
                    singleLine = true,
                    trailingIcon = {
                        if (uiState.languageToolBaseUrl.isNotBlank()) {
                            IconButton(onClick = { viewModel.setLanguageToolBaseUrl("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://languagetool.org") },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LanguageTool Website (languagetool.org)", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (!clipText.isNullOrBlank()) {
                                viewModel.setLanguageToolBaseUrl(clipText)
                                focusManager.clearFocus()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste URL", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.testLanguageTool()
                        },
                        enabled = !uiState.isTestingLt && uiState.languageToolBaseUrl.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        if (uiState.isTestingLt) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Server", fontSize = 12.sp)
                        }
                    }
                }

                if (uiState.ltTestResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TestResultBox(
                        message = uiState.ltTestResult ?: "",
                        isSuccess = uiState.ltTestSuccess == true
                    )
                }
            }

            // =========================================================================
            // 4. STEP-BY-STEP API SETUP INSTRUCTIONS GUIDE
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "How to Get & Set Up Free APIs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sapling Instructions
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("1. Sapling AI (Primary)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text("Free Monthly Tier", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• Step 1: Open sapling.ai and click 'Sign Up' or 'Get Started'.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Step 2: In your dashboard, open the 'API' or 'API Keys' tab.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Step 3: Copy your secret API key.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Step 4: Tap 'Paste Key' above and click 'Test Connection'.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { uriHandler.openUri("https://sapling.ai") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open sapling.ai Website", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // LanguageTool Instructions
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("2. LanguageTool (Automatic Fallback)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text("Zero-Setup Free", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• Option A (Default): Public server 'https://api.languagetool.org' is pre-configured and 100% free with no key required!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Option B (100% Private Offline): Run Docker locally on your PC or network:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text("docker run -d -p 8010:8010 erikvl87/languagetool", fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                            }
                            Text("Then enter 'http://<your-ip>:8010' above. Unlimited speed, offline & completely private.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { uriHandler.openUri("https://languagetool.org") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open languagetool.org Website", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    title: String,
    roleBadge: String,
    badgeColor: Color,
    description: String,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = roleBadge,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
private fun TestResultBox(message: String, isSuccess: Boolean) {
    val bg = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
    val textColor = if (isSuccess) Color(0xFF059669) else MaterialTheme.colorScheme.onErrorContainer
    val borderColor = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
