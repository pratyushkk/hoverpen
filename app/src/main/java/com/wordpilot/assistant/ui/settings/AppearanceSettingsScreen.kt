package com.wordpilot.assistant.ui.settings

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.CorrectionCategory
import com.wordpilot.assistant.domain.models.CorrectionSeverity
import com.wordpilot.assistant.ui.floating.FloatingAssistantView
import com.wordpilot.assistant.ui.floating.FloatingUiState
import com.wordpilot.assistant.ui.theme.AssistantColors
import com.wordpilot.assistant.ui.theme.GradientConfig
import com.wordpilot.assistant.ui.theme.GradientPresets

/**
 * World-Class Appearance & Layout Studio for WordPilot V2.
 * Redesigned for effortless customization, high craft, and instant visual feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppearanceSettings.load(context)) }
    var previewUiState by remember { mutableStateOf(FloatingUiState.EXPANDED) }

    val defaultDemoCorrections = remember {
        listOf(
            Correction(
                id = "demo-1",
                startOffset = 4,
                endOffset = 13,
                originalText = "have went",
                suggestedReplacement = "has gone",
                category = CorrectionCategory.GRAMMAR,
                severity = CorrectionSeverity.ERROR,
                ruleId = "SAPLING_GRAMMAR"
            ),
            Correction(
                id = "demo-2",
                startOffset = 19,
                endOffset = 26,
                originalText = "a apple",
                suggestedReplacement = "an apple",
                category = CorrectionCategory.GRAMMAR,
                severity = CorrectionSeverity.ERROR,
                ruleId = "SAPLING_ARTICLE"
            )
        )
    }
    var previewCorrections by remember { mutableStateOf(defaultDemoCorrections) }

    fun updateAndSave(newSettings: AppearanceSettings) {
        settings = newSettings
        AppearanceSettings.save(context, newSettings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Appearance Studio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Floating Bubble & Layout Design",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val defaultSettings = AppearanceSettings()
                            updateAndSave(defaultSettings)
                            previewCorrections = defaultDemoCorrections
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // =========================================================================
            // 1. STICKY HERO INTERACTIVE STAGE (Always Visible on Top)
            // =========================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF14171F),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Preview Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = "LIVE CANVAS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "AAA 7.4:1",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                            if (previewCorrections.isEmpty()) {
                                TextButton(
                                    onClick = { previewCorrections = defaultDemoCorrections },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Text("Reset Demo", fontSize = 10.sp, color = Color(0xFF60A5FA))
                                }
                            }
                        }

                        // Segmented Preview Mode Pills
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1F2430))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            PreviewModeTab(
                                label = "Bubble",
                                icon = Icons.Filled.BubbleChart,
                                isSelected = previewUiState == FloatingUiState.COLLAPSED
                            ) {
                                previewUiState = FloatingUiState.COLLAPSED
                            }
                            PreviewModeTab(
                                label = "Correction Pill",
                                icon = Icons.Filled.AutoFixHigh,
                                isSelected = previewUiState == FloatingUiState.EXPANDED
                            ) {
                                previewUiState = FloatingUiState.EXPANDED
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Simulated Device Wallpaper Stage
                    val tuckOffset = if (previewUiState == FloatingUiState.COLLAPSED && settings.edgeTuckEnabled) {
                        -(settings.bubbleSizeDp * settings.edgeTuckPercent + 10f).dp
                    } else {
                        0.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF090A0F), Color(0xFF11141C))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        // Ambient Wallpaper Text Hint
                        Text(
                            text = "Tap bubble to test animation ➔",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.align(Alignment.TopEnd)
                        )

                        Box(modifier = Modifier.offset(x = tuckOffset)) {
                            FloatingAssistantView(
                                settings = settings,
                                uiState = previewUiState,
                                isNearRightEdge = false,
                                errorCount = if (previewUiState == FloatingUiState.COLLAPSED) 0 else previewCorrections.size,
                                isChecking = false,
                                activeCorrection = previewCorrections.firstOrNull(),
                                activeCorrections = previewCorrections,
                                previewOriginalText = "She have went to a apple store.",
                                previewImprovedText = "She has gone to an apple store.",
                                isPreviewLoading = false,
                                previewErrorMessage = null,
                                canUndo = true,
                                currentTone = "Default",
                                onBubbleClick = {
                                    previewUiState = if (previewUiState == FloatingUiState.COLLAPSED) {
                                        FloatingUiState.EXPANDED
                                    } else {
                                        FloatingUiState.COLLAPSED
                                    }
                                },
                                onDrag = { _, _ -> },
                                onActionClick = {},
                                onCustomPromptSubmit = {},
                                onSelectTone = {},
                                onReplace = {},
                                onInsert = {},
                                onCopy = {},
                                onCancelPreview = { previewUiState = FloatingUiState.COLLAPSED },
                                onUndo = {},
                                onOpenSettings = {},
                                onCollapse = { previewUiState = FloatingUiState.COLLAPSED },
                                onAcceptCorrection = { accepted ->
                                    previewCorrections = previewCorrections.filter { it.id != accepted.id }
                                    if (previewCorrections.isEmpty()) {
                                        previewUiState = FloatingUiState.COLLAPSED
                                    }
                                },
                                onDismissCorrection = {
                                    if (previewCorrections.isNotEmpty()) {
                                        previewCorrections = previewCorrections.drop(1)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. SCROLLABLE DESIGN CONTROLS (Curated & High-Craft)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---------------------------------------------------------------------
                // A. MASTER STYLE PRESETS (1-Tap Curated Aesthetics)
                // ---------------------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "CURATED STYLE PRESETS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            MasterPreset(
                                name = "Compact",
                                subtitle = "36dp · Discreet",
                                bubbleSize = 36f,
                                iconSize = 18f,
                                shape = BubbleShape.CIRCLE,
                                gradientId = GradientPresets.Ocean.id,
                                logoId = "sparkle",
                                edgeTuckPercent = 0.35f,
                                glassOpacity = 0.85f,
                                previewColors = GradientPresets.Ocean.colors
                            ),
                            MasterPreset(
                                name = "Default",
                                subtitle = "48dp · Standard",
                                bubbleSize = 48f,
                                iconSize = 24f,
                                shape = BubbleShape.SQUIRCLE,
                                gradientId = GradientPresets.Aurora.id,
                                logoId = "sparkle",
                                edgeTuckPercent = 0.30f,
                                glassOpacity = 0.90f,
                                previewColors = GradientPresets.Aurora.colors
                            ),
                            MasterPreset(
                                name = "Large",
                                subtitle = "56dp · Prominent",
                                bubbleSize = 56f,
                                iconSize = 28f,
                                shape = BubbleShape.ROUNDED_SQUARE,
                                gradientId = GradientPresets.Cosmic.id,
                                logoId = "sparkle",
                                edgeTuckPercent = 0.25f,
                                glassOpacity = 0.95f,
                                previewColors = GradientPresets.Cosmic.colors
                            ),
                            MasterPreset(
                                name = "High-contrast",
                                subtitle = "Bold · Accessible",
                                bubbleSize = 48f,
                                iconSize = 24f,
                                shape = BubbleShape.SQUIRCLE,
                                gradientId = GradientPresets.Prism.id,
                                logoId = "sparkle",
                                edgeTuckPercent = 0.30f,
                                glassOpacity = 1.0f,
                                previewColors = listOf(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF2563EB))
                            ),
                            MasterPreset(
                                name = "Minimal",
                                subtitle = "32dp · Subtle",
                                bubbleSize = 32f,
                                iconSize = 16f,
                                shape = BubbleShape.CIRCLE,
                                gradientId = GradientPresets.Aurora.id,
                                logoId = "sparkle",
                                edgeTuckPercent = 0.40f,
                                glassOpacity = 0.70f,
                                previewColors = listOf(Color(0xFF475569), Color(0xFF94A3B8))
                            )
                        )

                        presets.forEach { preset ->
                            val isSelected = settings.bubbleSizeDp == preset.bubbleSize &&
                                    settings.bubbleShape == preset.shape &&
                                    settings.gradientId == preset.gradientId

                            Surface(
                                onClick = {
                                    val updated = settings.copy(
                                        bubbleSizeDp = preset.bubbleSize,
                                        iconSizeDp = preset.iconSize,
                                        bubbleShape = preset.shape,
                                        gradientId = preset.gradientId,
                                        aiLogoIconId = preset.logoId,
                                        edgeTuckPercent = preset.edgeTuckPercent,
                                        glassOpacity = preset.glassOpacity
                                    )
                                    updateAndSave(updated)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF14171F),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier.width(132.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Brush.horizontalGradient(preset.previewColors))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = preset.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------------
                // B. BUBBLE GEOMETRY & SIZING (Tactile 3-Size Cards)
                // ---------------------------------------------------------------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171F)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bubble Size & Reach",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${settings.bubbleSizeDp.toInt()} dp",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3 Segmented Tactile Size Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Compact", 36f, "Discreet"),
                                Triple("Standard", 48f, "Balanced"),
                                Triple("Comfort", 58f, "Large Target")
                            ).forEach { (label, sizeDp, desc) ->
                                val isSelected = kotlin.math.abs(settings.bubbleSizeDp - sizeDp) < 3f
                                Surface(
                                    onClick = {
                                        val iconRatio = when (sizeDp) {
                                            36f -> 18f
                                            48f -> 24f
                                            else -> 28f
                                        }
                                        updateAndSave(settings.copy(bubbleSizeDp = sizeDp, iconSizeDp = iconRatio))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF1A1F2B),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                        Text(
                                            text = "${sizeDp.toInt()} dp",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Shape Selector
                        Text(
                            text = "Bubble Shape",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                BubbleShape.CIRCLE to "Circle",
                                BubbleShape.SQUIRCLE to "Squircle",
                                BubbleShape.ROUNDED_SQUARE to "Rounded"
                            ).forEach { (shape, name) ->
                                val isSelected = settings.bubbleShape == shape
                                Surface(
                                    onClick = { updateAndSave(settings.copy(bubbleShape = shape)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1A1F2B),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------------
                // C. EDGE DOCKING & 30% TUCK (WordPilot's Signature Innovation)
                // ---------------------------------------------------------------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171F)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Edge Docking & 30% Tuck",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "SIGNATURE",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Snaps to screen edge and hides 30% inside when idle to never obstruct typing.",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = settings.edgeTuckEnabled,
                                onCheckedChange = { updateAndSave(settings.copy(edgeTuckEnabled = it)) }
                            )
                        }

                        if (settings.edgeTuckEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Subtle (20%)" to 0.20f,
                                    "Standard (30%)" to 0.30f,
                                    "Deep (40%)" to 0.40f
                                ).forEach { (label, percent) ->
                                    val isSelected = kotlin.math.abs(settings.edgeTuckPercent - percent) < 0.05f
                                    Surface(
                                        onClick = { updateAndSave(settings.copy(edgeTuckPercent = percent)) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF1A1F2B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------------
                // D. AI EMBLEM PERSONALITY (6 Curated World-Class Icons)
                // ---------------------------------------------------------------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171F)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "AI Emblem & Identity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "The glowing symbol representing Hoverpen AI in the bubble and editing bar.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 6 Curated Icons in a clean 3x2 Grid
                        val curatedLogos = listOf(
                            CuratedLogo("sparkle", "Sparkle", "Gemini AI", Icons.Filled.AutoAwesome),
                            CuratedLogo("wand", "Magic Wand", "Grammar Polish", Icons.Filled.AutoFixHigh),
                            CuratedLogo("bolt", "Lightning", "Zero Latency", Icons.Filled.Bolt),
                            CuratedLogo("brain", "Neural", "Intelligence", Icons.Filled.Psychology),
                            CuratedLogo("pen", "Editorial", "Craftsmanship", Icons.Filled.Edit),
                            CuratedLogo("robot", "Companion", "Smart Assistant", Icons.Filled.SmartToy)
                        )

                        curatedLogos.chunked(3).forEach { rowLogos ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowLogos.forEach { item ->
                                    val isSelected = settings.aiLogoIconId == item.id
                                    Surface(
                                        onClick = { updateAndSave(settings.copy(aiLogoIconId = item.id)) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color(0xFF222838) else Color(0xFF1A1F2B),
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                        else Color.White.copy(alpha = 0.06f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.title,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            Text(
                                                text = item.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // ---------------------------------------------------------------------
                // E. GRADIENT COLORWAY & GLASS TRANSLUCENCY
                // ---------------------------------------------------------------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171F)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Colorway & Glass Translucency",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Signature radiant gradient and smoked frosted glass styling.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Gradient Swatches Grid (2 columns)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradientPresets.AllPresets.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { preset ->
                                        val isSelected = settings.gradientId == preset.id
                                        Surface(
                                            onClick = { updateAndSave(settings.copy(gradientId = preset.id)) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF1E2638) else Color(0xFF1A1F2B),
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(36.dp)
                                                        .height(20.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(preset.createHorizontalBrush())
                                                        .border(
                                                            BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                )
                                                Text(
                                                    text = preset.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Frosted Glass Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Glass Opacity",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            Text(
                                text = "${(settings.glassOpacity * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.glassOpacity * 100f,
                            onValueChange = { updateAndSave(settings.copy(glassOpacity = it / 100f)) },
                            valueRange = 40f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // Expander Window Blur Design Option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Expander Window Frosted Blur",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Renders a frosted blur backdrop on the expanded rewrite & tone window.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Switch(
                                checked = settings.expanderBlurEnabled,
                                onCheckedChange = { updateAndSave(settings.copy(expanderBlurEnabled = it)) }
                            )
                        }

                        if (settings.expanderBlurEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Subtle (12dp)" to 12f,
                                    "Medium (20dp)" to 20f,
                                    "Deep (32dp)" to 32f
                                ).forEach { (label, radius) ->
                                    val isSelected = kotlin.math.abs(settings.expanderBlurRadiusDp - radius) < 2f
                                    Surface(
                                        onClick = { updateAndSave(settings.copy(expanderBlurRadiusDp = radius)) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF1A1F2B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------------
                // F. ERROR BADGE & SMART VISIBILITY (Grouped Settings)
                // ---------------------------------------------------------------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171F)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Error Badge & Smart Behavior",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Error Badge Format Selector
                        Text(
                            text = "Error Badge Format",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                BadgeLetterPosition.NONE to "Number Only (2)",
                                BadgeLetterPosition.PREFIX to "Tagged (E2)"
                            ).forEach { (pos, label) ->
                                val isSelected = settings.badgeLetterPosition == pos
                                Surface(
                                    onClick = { updateAndSave(settings.copy(badgeLetterPosition = pos)) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF1A1F2B),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // Smart Toggle 1: Hide When Keyboard Closed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Hide when Keyboard Closed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Only display the floating bubble while typing in an active text box.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Switch(
                                checked = settings.autoHideWhenKeyboardClosed,
                                onCheckedChange = { updateAndSave(settings.copy(autoHideWhenKeyboardClosed = it)) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // Smart Toggle 2: Auto Dim Idle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Auto-Dim when Idle",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Gently fades bubble opacity after 5 seconds of inactivity.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Switch(
                                checked = settings.autoDimEnabled,
                                onCheckedChange = { updateAndSave(settings.copy(autoDimEnabled = it)) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// =============================================================================
// HELPER MODELS & COMPOSABLES
// =============================================================================

private data class MasterPreset(
    val name: String,
    val subtitle: String,
    val bubbleSize: Float,
    val iconSize: Float,
    val shape: BubbleShape,
    val gradientId: String,
    val logoId: String,
    val edgeTuckPercent: Float,
    val glassOpacity: Float,
    val previewColors: List<Color>
)

private data class CuratedLogo(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun PreviewModeTab(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF94A3B8),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF94A3B8)
            )
        }
    }
}
