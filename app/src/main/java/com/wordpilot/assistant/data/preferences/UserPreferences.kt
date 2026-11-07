package com.wordpilot.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordpilot.assistant.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wordpilot_prefs")

class UserPreferences(
    private val context: Context,
    private val cryptoManager: CryptoManager = CryptoManager()
) {

    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val GRAMMAR_CHECK_ENABLED = booleanPreferencesKey("grammar_check_enabled")
        val SPELL_CHECK_ENABLED = booleanPreferencesKey("spell_check_enabled")
        val AUTO_CHECK_ENABLED = booleanPreferencesKey("auto_check_enabled")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val SELECTED_ENGINE = stringPreferencesKey("selected_engine")
        val AUTO_PAUSE_BANKING_ENABLED = booleanPreferencesKey("auto_pause_banking_enabled")
        val BLOCK_CLOUD_AI = booleanPreferencesKey("block_cloud_ai")

        val SAPLING_API_KEY = stringPreferencesKey("sapling_api_key_enc")
        val SAPLING_ENABLED = booleanPreferencesKey("sapling_enabled")
        val LANGUAGETOOL_ENABLED = booleanPreferencesKey("languagetool_enabled")
        val LANGUAGETOOL_BASE_URL = stringPreferencesKey("languagetool_base_url")
        val GRAMMARBOT_API_KEY = stringPreferencesKey("grammarbot_api_key_enc")
        val GRAMMARBOT_ENABLED = booleanPreferencesKey("grammarbot_enabled")
        val GRAMMAR_PROVIDER_PRIORITY = stringPreferencesKey("grammar_provider_priority")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }

    val blockCloudAi: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BLOCK_CLOUD_AI] ?: false
    }

    val saplingEnabled: Flow<Boolean> = context.dataStore.data.map { it[SAPLING_ENABLED] ?: true }
    val saplingApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        val raw = prefs[SAPLING_API_KEY] ?: return@map null
        if (raw.isBlank()) null else cryptoManager.decrypt(raw)
    }
    val languageToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[LANGUAGETOOL_ENABLED] ?: true }
    val languageToolBaseUrl: Flow<String> = context.dataStore.data.map { it[LANGUAGETOOL_BASE_URL] ?: "https://api.languagetool.org" }
    val grammarBotEnabled: Flow<Boolean> = context.dataStore.data.map { it[GRAMMARBOT_ENABLED] ?: false }
    val grammarBotApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        val raw = prefs[GRAMMARBOT_API_KEY] ?: return@map null
        if (raw.isBlank()) null else cryptoManager.decrypt(raw)
    }
    val grammarProviderPriority: Flow<String> = context.dataStore.data.map {
        it[GRAMMAR_PROVIDER_PRIORITY] ?: "sapling,languagetool"
    }

    val autoPauseBankingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_PAUSE_BANKING_ENABLED] ?: false
    }

    val geminiApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        val raw = prefs[GEMINI_API_KEY] ?: return@map null
        cryptoManager.decrypt(raw)
    }

    val selectedEngine: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_ENGINE] ?: "gemini"
    }

    val theme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system"
    }

    val grammarCheckEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[GRAMMAR_CHECK_ENABLED] ?: true
    }

    val spellCheckEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SPELL_CHECK_ENABLED] ?: true
    }

    val autoCheckEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CHECK_ENABLED] ?: true
    }

    val historyEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HISTORY_ENABLED] ?: false
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = theme
        }
    }

    suspend fun setGrammarCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GRAMMAR_CHECK_ENABLED] = enabled
        }
    }

    suspend fun setSpellCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SPELL_CHECK_ENABLED] = enabled
        }
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_CHECK_ENABLED] = enabled
        }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HISTORY_ENABLED] = enabled
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        val encrypted = if (apiKey.isNotBlank()) cryptoManager.encrypt(apiKey.trim()) else ""
        context.dataStore.edit { prefs ->
            prefs[GEMINI_API_KEY] = encrypted
        }
    }

    suspend fun setSelectedEngine(engine: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_ENGINE] = engine
        }
    }

    suspend fun setAutoPauseBankingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_PAUSE_BANKING_ENABLED] = enabled
        }
    }

    suspend fun setBlockCloudAi(blocked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BLOCK_CLOUD_AI] = blocked
            if (blocked) {
                prefs[SELECTED_ENGINE] = "rule_based"
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun clearAll() {
        cryptoManager.clearKey()
        context.dataStore.edit { it.clear() }
    }

    suspend fun setSaplingApiKey(key: String) {
        val encrypted = if (key.isNotBlank()) cryptoManager.encrypt(key.trim()) else ""
        context.dataStore.edit { it[SAPLING_API_KEY] = encrypted }
    }
    suspend fun setSaplingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SAPLING_ENABLED] = enabled }
    }
    suspend fun setLanguageToolEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LANGUAGETOOL_ENABLED] = enabled }
    }
    suspend fun setLanguageToolBaseUrl(url: String) {
        context.dataStore.edit { it[LANGUAGETOOL_BASE_URL] = url.trim() }
    }
    suspend fun setGrammarBotApiKey(key: String) {
        val encrypted = if (key.isNotBlank()) cryptoManager.encrypt(key.trim()) else ""
        context.dataStore.edit { it[GRAMMARBOT_API_KEY] = encrypted }
    }
    suspend fun setGrammarBotEnabled(enabled: Boolean) {
        context.dataStore.edit { it[GRAMMARBOT_ENABLED] = enabled }
    }
    suspend fun setGrammarProviderPriority(priority: String) {
        context.dataStore.edit { it[GRAMMAR_PROVIDER_PRIORITY] = priority }
    }
}
