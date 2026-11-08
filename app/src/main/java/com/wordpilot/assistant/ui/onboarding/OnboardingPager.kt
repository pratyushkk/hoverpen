package com.wordpilot.assistant.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wordpilot.assistant.R
import com.wordpilot.assistant.WordPilotApp
import com.wordpilot.assistant.overlay.WordPilotAccessibilityService
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingPagerScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = WordPilotApp.instance
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

    val isAccessibilityActive = remember(resumeSignal) {
        WordPilotAccessibilityService.instance != null || isAccessibilityEnabled(context)
    }

    val isBatteryOptimized = remember(resumeSignal) {
        isBatteryOptIgnored(context)
    }

    val geminiApiKey by app.userPreferences.geminiApiKey.collectAsState(initial = null)
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeySaved by remember { mutableStateOf(false) }

    LaunchedEffect(geminiApiKey) {
        if (!geminiApiKey.isNullOrBlank()) {
            apiKeyInput = geminiApiKey ?: ""
            apiKeySaved = true
        }
    }

    val pagerState = rememberPagerState(pageCount = { 3 })

    fun completeOnboarding() {
        scope.launch {
            app.userPreferences.setOnboardingCompleted(true)
            onFinished()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Brand & Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_hoverpen_logo),
                        contentDescription = "Hoverpen Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Text(
                        text = "Hoverpen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pagerState.currentPage < 2) {
                    TextButton(onClick = { completeOnboarding() }) {
                        Text(
                            text = "Skip to App",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingAccessibilityPage(
                        isActive = isAccessibilityActive,
                        onEnableClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: android.content.ActivityNotFoundException) {} catch (_: SecurityException) {}
                        }
                    )
                    1 -> OnboardingApiKeyPage(
                        apiKey = apiKeyInput,
                        isSaved = apiKeySaved,
                        onApiKeyChange = { apiKeyInput = it },
                        onSaveApiKey = {
                            scope.launch {
                                app.userPreferences.setGeminiApiKey(apiKeyInput)
                                apiKeySaved = true
                            }
                        },
                        onOpenAiStudio = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: android.content.ActivityNotFoundException) {} catch (_: SecurityException) {}
                        }
                    )
                    2 -> OnboardingBatteryPage(
                        isOptimized = isBatteryOptimized,
                        onRequestIgnore = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                var opened = false
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    opened = true
                                } catch (e: Exception) {
                                    android.util.Log.w("Hoverpen", "Direct battery prompt failed in onboarding", e)
                                }

                                if (!opened) {
                                    try {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(fallback)
                                        opened = true
                                    } catch (e: Exception) {
                                        android.util.Log.w("Hoverpen", "Battery settings fallback failed in onboarding", e)
                                    }
                                }

                                if (!opened) {
                                    try {
                                        val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(appSettings)
                                    } catch (e: Exception) {
                                        android.util.Log.e("Hoverpen", "App details fallback failed in onboarding", e)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isCurrent = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            completeOnboarding()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.5f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pagerState.currentPage == 2) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Get Started" else "Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        if (pagerState.currentPage == 2) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingAccessibilityPage(
    isActive: Boolean,
    onEnableClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AccessibilityNew,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Step 1 of 3",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enable Accessibility",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Hoverpen sits unobtrusively above any app (WhatsApp, Gmail, Notes) to inspect grammar errors and provide 1-tap replacements.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isActive) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, if (isActive) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (isActive) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (isActive) "Service is Active" else "Service is Disabled",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isActive) "Ready to monitor text inputs" else "Requires permission in Settings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onEnableClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (isActive) "Active" else "Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OnboardingApiKeyPage(
    apiKey: String,
    isSaved: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onOpenAiStudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Step 2 of 3 (Optional)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Connect Gemini AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Unlock Fix & Polish, Short, Rewrite, and Polite style transformations with a free Gemini API key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Gemini API Key") },
            placeholder = { Text("AIzaSy...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenAiStudio) {
                Text("Get free API key ↗", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }

            if (apiKey.isNotBlank()) {
                FilledTonalButton(
                    onClick = onSaveApiKey,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(if (isSaved) "Saved ✓" else "Save Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OnboardingBatteryPage(
    isOptimized: Boolean,
    onRequestIgnore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.BatteryChargingFull,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Step 3 of 3",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Battery Background Guard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Prevent Android OS from putting Hoverpen to sleep in the background so suggestions appear instantly whenever you type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isOptimized) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, if (isOptimized) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (isOptimized) Icons.Filled.CheckCircle else Icons.Outlined.BatteryAlert,
                        contentDescription = null,
                        tint = if (isOptimized) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                    Column {
                        Text(
                            text = if (isOptimized) "Protected from Sleep" else "Standard Battery Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isOptimized) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOptimized) "Unrestricted background operation" else "Android may sleep the assistant",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onRequestIgnore,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOptimized) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (isOptimized) "Protected" else "Protect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}

private fun isBatteryOptIgnored(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }
}
