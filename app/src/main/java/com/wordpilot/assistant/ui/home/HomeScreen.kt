package com.wordpilot.assistant.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wordpilot.assistant.R
import com.wordpilot.assistant.overlay.WordPilotAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEditor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onNavigateToBubbleCustomization: () -> Unit = {},
    onNavigateToAiWindowStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeSignal by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeSignal++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isServiceActive = remember(resumeSignal) {
        WordPilotAccessibilityService.instance != null || isAccessibilityServiceEnabled(context)
    }
    val isBatteryOptimized = remember(resumeSignal) {
        isBatteryOptimizationIgnored(context)
    }
    val serviceInstance = WordPilotAccessibilityService.instance
    var isPaused by remember { mutableStateOf(serviceInstance?.isAssistantPaused ?: false) }

    var showAccessibilityGuide by remember { mutableStateOf(false) }
    val app = com.wordpilot.assistant.WordPilotApp.instance
    val geminiApiKey by app.userPreferences.geminiApiKey.collectAsState(initial = null)
    val saplingApiKey by app.userPreferences.saplingApiKey.collectAsState(initial = null)

    var demoFixed by remember { mutableStateOf(false) }

    if (showAccessibilityGuide) {
        AccessibilityGuideDialog(
            isServiceActive = isServiceActive,
            onDismiss = { showAccessibilityGuide = false },
            onOpenAccessibilitySettings = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try { context.startActivity(intent) } catch (e: Exception) {}
            },
            onOpenAppSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try { context.startActivity(intent) } catch (e: Exception) {}
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_wordpilot_logo),
                            contentDescription = "Hoverpen Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                "Hoverpen",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Control Center • v2.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // =========================================================================
            // 1. HERO STATUS CARD ("State of the Assistant")
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (!isServiceActive) {
                        Color(0xFF1E1F24)
                    } else if (isPaused) {
                        Color(0xFF241D14)
                    } else {
                        Color(0xFF0D251C)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (!isServiceActive) {
                        Color(0xFF374151)
                    } else if (isPaused) {
                        Color(0xFFF59E0B).copy(alpha = 0.5f)
                    } else {
                        Color(0xFF10B981).copy(alpha = 0.6f)
                    }
                ),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (!isServiceActive) Color(0xFFEF4444)
                                        else if (isPaused) Color(0xFFF59E0B)
                                        else Color(0xFF10B981)
                                    )
                            )
                            Text(
                                text = if (!isServiceActive) "ASSISTANT INACTIVE"
                                else if (isPaused) "ASSISTANT PAUSED"
                                else "ASSISTANT ACTIVE & LIVE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = if (!isServiceActive) Color(0xFFF87171)
                                else if (isPaused) Color(0xFFFBBF24)
                                else Color(0xFF34D399)
                            )
                        }

                        // Hero Action Button
                        if (isServiceActive) {
                            FilledTonalButton(
                                onClick = {
                                    val newPaused = !isPaused
                                    isPaused = newPaused
                                    serviceInstance?.setAssistantPaused(newPaused)
                                    Toast.makeText(
                                        context,
                                        if (newPaused) "Hoverpen paused across apps" else "Hoverpen resumed & active",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isPaused) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                    contentColor = if (isPaused) Color(0xFF34D399) else Color(0xFFFBBF24)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPaused) "Resume" else "Pause", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { showAccessibilityGuide = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Setup →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (!isServiceActive) {
                            "Enable Hoverpen in Accessibility Settings to monitor your writing and floating suggestions across all compatible apps."
                        } else if (isPaused) {
                            "Hoverpen is temporarily paused. Tap 'Resume' above or via the Quick Settings tile to restore floating suggestions."
                        } else {
                            "Floating assistant is actively monitoring text inputs in WhatsApp, Notes, Instagram, Gmail, and Chrome with automatic banking shield."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Connected Engine Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EngineBadge(
                            name = "Gemini Model",
                            role = "V2 Core AI",
                            color = if (!geminiApiKey.isNullOrBlank()) Color(0xFF8B5CF6) else Color(0xFF6B7280)
                        )
                        EngineBadge(
                            name = "Sapling AI",
                            role = "Primary",
                            color = if (!saplingApiKey.isNullOrBlank()) Color(0xFF10B981) else Color(0xFF6B7280)
                        )
                        EngineBadge(
                            name = "LanguageTool",
                            role = "Auto Fallback",
                            color = Color(0xFF06B6D4)
                        )
                    }
                }
            }

            // =========================================================================
            // 2. INTERACTIVE SETUP CHECKLIST (Auto-refreshes on ON_RESUME)
            // =========================================================================
            val totalSteps = 3
            val completedSteps = (if (isServiceActive) 1 else 0) +
                (if (!geminiApiKey.isNullOrBlank()) 1 else 0) +
                (if (isBatteryOptimized) 1 else 0)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assistant Setup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (completedSteps == totalSteps) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$completedSteps of $totalSteps Ready",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (completedSteps == totalSteps) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 1: Accessibility Service
                    SetupChecklistRow(
                        title = "Accessibility Service",
                        description = if (isServiceActive) "Active & monitoring text inputs" else "Required for floating bubble & overlay",
                        isDone = isServiceActive,
                        actionLabel = if (isServiceActive) "Settings" else "Enable",
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    // Row 2: Gemini API Key
                    SetupChecklistRow(
                        title = "Gemini Model (V2 AI)",
                        description = if (!geminiApiKey.isNullOrBlank()) "Connected for Rewrite & 4 Tone styles" else "Enter free API key to activate tone rewrites",
                        isDone = !geminiApiKey.isNullOrBlank(),
                        actionLabel = if (!geminiApiKey.isNullOrBlank()) "Manage" else "Add Key",
                        onClick = onNavigateToApiSettings
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    // Row 3: Battery Optimization
                    SetupChecklistRow(
                        title = "Battery Background Guard",
                        description = if (isBatteryOptimized) "Unrestricted: Android won't sleep assistant" else "Prevent Android OS from killing assistant",
                        isDone = isBatteryOptimized,
                        actionLabel = if (isBatteryOptimized) "Protected" else "Fix",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    )
                }
            }

            // =========================================================================
            // 3. "TRY IT NOW" INTERACTIVE PLAYGROUND
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Try It Now",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Interactive Demo",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Playground Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!demoFixed) {
                                Text(
                                    text = "Their going to the store tomorrow and its amazing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SuggestionChip(
                                        onClick = { demoFixed = true },
                                        label = {
                                            Text("Fix: Their ➔ They're", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                                            labelColor = Color(0xFFEF4444)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                                    )
                                    SuggestionChip(
                                        onClick = { demoFixed = true },
                                        label = {
                                            Text("Fix: its ➔ it's", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = Color(0xFF10B981).copy(alpha = 0.12f),
                                            labelColor = Color(0xFF10B981)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "They're going to the store tomorrow and it's amazing.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF10B981)
                                    )
                                    IconButton(
                                        onClick = { demoFixed = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToEditor,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Editor", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (isServiceActive) {
                                    val controller = serviceInstance?.floatingController
                                    if (controller != null) {
                                        controller.showAssistant()
                                        Toast.makeText(context, "Floating bubble activated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Tap anywhere inside a text field to use bubble", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    showAccessibilityGuide = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6)
                            ),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Filled.BubbleChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Bubble", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // =========================================================================
            // 4. QUICK TOOLS & STUDIOS HUB
            // =========================================================================
            Text(
                text = "Quick Tools & Studios",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            HomeActionCard(
                icon = Icons.Outlined.Palette,
                title = "Appearance & Layout Studio",
                description = "Customize bubble size, AI logo icon, error badge & 30% edge tuck",
                onClick = onNavigateToBubbleCustomization
            )

            HomeActionCard(
                icon = Icons.Outlined.AutoAwesome,
                title = "AI Window & Tone Studio",
                description = "Select floating quick tones, window size, glass theme & custom prompts",
                onClick = onNavigateToAiWindowStudio
            )

            HomeActionCard(
                icon = Icons.Outlined.CloudQueue,
                title = "API Providers & Keys",
                description = "Configure Gemini Model, Sapling (Primary) & LanguageTool failover",
                onClick = onNavigateToApiSettings
            )

            HomeActionCard(
                icon = Icons.Outlined.Book,
                title = "Personal Dictionary",
                description = "Manage custom words and ignored suggestions",
                onClick = onNavigateToDictionary
            )

            HomeActionCard(
                icon = Icons.Outlined.History,
                title = "Document History",
                description = "View and manage saved documents",
                onClick = onNavigateToHistory
            )

            HomeActionCard(
                icon = Icons.Outlined.Tune,
                title = "Settings",
                description = "Themes, auto-check preferences, and banking shield",
                onClick = onNavigateToSettings
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Privacy info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Hoverpen uses lightweight external APIs with automatic failover. 50+ banking and payment apps are automatically shielded for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupChecklistRow(
    title: String,
    description: String,
    isDone: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isDone) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Filled.Check else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (isDone) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.size(13.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = actionLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EngineBadge(
    name: String,
    role: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$name ($role)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun HomeActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, WordPilotAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName) {
            return true
        }
    }
    return false
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true
}

@Composable
fun AccessibilityGuideDialog(
    isServiceActive: Boolean,
    onDismiss: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Assistant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Floating Assistant Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Setup, Auto-Pause & Resume",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isServiceActive) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isServiceActive) Color(0xFF10B981) else Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (isServiceActive) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (isServiceActive) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (isServiceActive)
                                    "Status: Active & Running. Floating bubble appears automatically whenever you type."
                                else
                                    "Status: Inactive. Turn on the Accessibility Service to enable cross-app floating assistant.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Section 1: How to Turn On
                    GuideSectionCard(
                        stepNumber = "1",
                        title = "How to Turn On Accessibility Service",
                        badge = "Required"
                    ) {
                        Text(
                            "Follow these 3 quick steps in your phone's settings:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        GuideStepItem(number = "1", text = "Tap the 'Open Accessibility Settings' button below.")
                        GuideStepItem(number = "2", text = "Scroll to 'Downloaded apps' (or 'Installed services').")
                        GuideStepItem(number = "3", text = "Select 'Hoverpen Assistant', turn the toggle ON, and tap 'Allow'.")

                        Spacer(modifier = Modifier.height(8.dp))

                        // MIUI / ColorOS / HyperOS Tip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "If Android shows 'Restricted setting': Go to Phone Settings ➔ Apps ➔ Hoverpen ➔ 3 dots (top right) ➔ tap 'Allow restricted settings', then turn on accessibility.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Accessibility Settings", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Section 2: Auto-Pause & How to Resume
                    GuideSectionCard(
                        stepNumber = "2",
                        title = "How Auto-Pause & Resume Works",
                        badge = "Privacy Shield"
                    ) {
                        Text(
                            "Hoverpen is built with bank-grade privacy protection:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        GuideStepItem(
                            number = "🔒",
                            text = "Auto-Pause in 50+ Banking Apps: The bubble completely hides and pauses in apps like Google Pay, PhonePe, Paytm, YONO SBI, HDFC, CRED, and password fields."
                        )
                        GuideStepItem(
                            number = "⚡",
                            text = "Auto-Resume: When you exit the banking app and return to any regular app (WhatsApp, Chrome, Notes, Gmail) or tap an editable text field, Hoverpen detects the safe context and resumes automatically!"
                        )
                        GuideStepItem(
                            number = "🎛️",
                            text = "Quick Settings Tile: Swipe down your Android notification panel and tap the 'Hoverpen' tile anytime to toggle or resume instantly."
                        )
                        GuideStepItem(
                            number = "📱",
                            text = "Home Screen Widget: Add Hoverpen's 1-tap widget to your home screen for quick single-touch resumption."
                        )
                    }

                    // Section 3: Battery Optimization
                    GuideSectionCard(
                        stepNumber = "3",
                        title = "Prevent Android Background Killing",
                        badge = "Recommended"
                    ) {
                        Text(
                            "Some Android phone brands (Samsung, Xiaomi, OnePlus, Motorola) aggressively kill background services to save battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "To ensure Hoverpen is always available when typing, set its Battery Usage to 'Unrestricted' in App Info.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onOpenAppSettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open App Battery Settings")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Got It, Close Guide", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GuideSectionCard(
    stepNumber: String,
    title: String,
    badge: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stepNumber, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            content()
        }
    }
}

@Composable
private fun GuideStepItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            number.let { if (it.length == 1 && it[0].isDigit()) "$it." else it },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}
