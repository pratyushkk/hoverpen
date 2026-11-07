package com.wordpilot.assistant.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onNavigateToBubbleCustomization: () -> Unit = {},
    onNavigateToAiWindowStudio: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("Delete all local data including dictionary, history, and preferences? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security Information") },
            text = {
                Text(
                    "Hoverpen is engineered with strict user privacy in mind:\n\n" +
                    "• Grammar checking is handled exclusively by your configured APIs (Sapling AI, LanguageTool)\n" +
                    "• Over 50+ banking, wallet, and UPI applications are strictly shielded and never inspected\n" +
                    "• Sensitive fields such as passwords and PINs are completely ignored\n" +
                    "• No background telemetry, analytics, or keystroke tracking\n" +
                    "• Document history is stored purely locally on your device and can be cleared anytime"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Got It") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Group 1: Assistant
            SettingsSection(title = "Assistant") {
                SettingsSwitch(
                    title = "Auto Check While Typing",
                    subtitle = "Continuously analyze text as you type",
                    checked = uiState.autoCheckEnabled,
                    onCheckedChange = { viewModel.setAutoCheckEnabled(it) }
                )
                SettingsSwitch(
                    title = "Grammar Checking",
                    subtitle = "Detect and fix grammatical issues",
                    checked = uiState.grammarCheckEnabled,
                    onCheckedChange = { viewModel.setGrammarCheckEnabled(it) }
                )
                SettingsSwitch(
                    title = "Spell Checking",
                    subtitle = "Detect and suggest correctly spelled words",
                    checked = uiState.spellCheckEnabled,
                    onCheckedChange = { viewModel.setSpellCheckEnabled(it) }
                )
                SettingsClickable(
                    title = "Floating Tone & Rewrite Options",
                    subtitle = "Select which quick tones appear in your floating panel (Fix & Polish, Short, Rewrite, Polite, etc.)",
                    onClick = onNavigateToAiWindowStudio
                )
                SettingsClickable(
                    title = "Personal Dictionary",
                    subtitle = "Manage your custom words and ignored rules",
                    onClick = onNavigateToDictionary
                )
            }

            // Group 2: Appearance
            SettingsSection(title = "Appearance") {
                SettingsThemeSelector(
                    currentTheme = uiState.theme,
                    onThemeSelected = { viewModel.setTheme(it) }
                )
                SettingsClickable(
                    title = "Appearance & Layout Studio",
                    subtitle = "Customize bubble size, curated gradient presets, and edge tuck",
                    onClick = onNavigateToBubbleCustomization
                )
                SettingsClickable(
                    title = "AI Window & Tone Studio",
                    subtitle = "Customize floating AI chat dimensions, typewriter speed, glass theme & prompts",
                    onClick = onNavigateToAiWindowStudio
                )
            }

            // Group 3: Engines & Keys
            SettingsSection(title = "Engines & Keys") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = onNavigateToApiSettings
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    "API Providers & Failover",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (!uiState.saplingApiKey.isNullOrBlank()) {
                                "Sapling AI (Active) ➔ LanguageTool (Automatic Fallback)"
                            } else {
                                "Sapling API key required for primary neural checking"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Group 4: Privacy
            SettingsSection(title = "Privacy") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "50+ Financial Apps Automatically Shielded",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Google Pay, PhonePe, Paytm, YONO SBI, HDFC, ICICI, Axis, Chase, and password fields are strictly blocked from being monitored or inspected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsSwitch(
                    title = "Auto-Pause in Banking Apps",
                    subtitle = "Automatically pauses the assistant whenever a financial app opens",
                    checked = uiState.autoPauseBankingEnabled,
                    onCheckedChange = { viewModel.setAutoPauseBankingEnabled(it) }
                )

                SettingsClickable(
                    title = "Accessibility Settings",
                    subtitle = "Manage or toggle the Floating Assistant service",
                    onClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) {} catch (_: SecurityException) {}
                    }
                )

                SettingsSwitch(
                    title = "Document History",
                    subtitle = "Save drafts locally in the app",
                    checked = uiState.historyEnabled,
                    onCheckedChange = { viewModel.setHistoryEnabled(it) }
                )

                SettingsClickable(
                    title = "Clear All Local Data",
                    subtitle = "Delete dictionary, history, and preferences",
                    onClick = { showClearDialog = true },
                    isDestructive = true
                )
            }

            // Group 5: About
            SettingsSection(title = "About") {
                SettingsClickable(
                    title = "Privacy Policy & Details",
                    subtitle = "How Hoverpen protects your privacy and text",
                    onClick = { showPrivacyDialog = true }
                )
                SettingsInfo(
                    title = "Version",
                    value = "2.0.0 (API Architecture)"
                )
                SettingsInfo(
                    title = "Hoverpen",
                    value = "Production Grammar & Writing Assistant"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickable(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsThemeSelector(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                FilterChip(
                    selected = currentTheme == value,
                    onClick = { onThemeSelected(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}


@Composable
private fun SettingsInfo(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
