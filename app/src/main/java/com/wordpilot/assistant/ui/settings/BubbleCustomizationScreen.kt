package com.wordpilot.assistant.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.R
import com.wordpilot.assistant.overlay.FloatingAssistantController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleCustomizationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(FloatingAssistantController.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var widthDp by remember {
        mutableFloatStateOf(prefs.getFloat(FloatingAssistantController.KEY_CUSTOM_WIDTH_DP, FloatingAssistantController.DEFAULT_WIDTH_DP))
    }
    var heightDp by remember {
        mutableFloatStateOf(prefs.getFloat(FloatingAssistantController.KEY_CUSTOM_HEIGHT_DP, FloatingAssistantController.DEFAULT_HEIGHT_DP))
    }
    var textSizeSp by remember {
        mutableFloatStateOf(prefs.getFloat(FloatingAssistantController.KEY_CUSTOM_TEXT_SIZE_SP, FloatingAssistantController.DEFAULT_TEXT_SIZE_SP))
    }
    var cornerRadiusDp by remember {
        mutableFloatStateOf(prefs.getFloat(FloatingAssistantController.KEY_CUSTOM_CORNER_RADIUS_DP, FloatingAssistantController.DEFAULT_CORNER_RADIUS_DP))
    }
    var bubbleSizeDp by remember {
        mutableFloatStateOf(prefs.getFloat(FloatingAssistantController.KEY_CUSTOM_BUBBLE_SIZE_DP, FloatingAssistantController.DEFAULT_BUBBLE_SIZE_DP))
    }

    // Save changes to SharedPreferences whenever a slider moves
    fun savePreferences(
        w: Float = widthDp,
        h: Float = heightDp,
        ts: Float = textSizeSp,
        cr: Float = cornerRadiusDp,
        bs: Float = bubbleSizeDp
    ) {
        prefs.edit()
            .putFloat(FloatingAssistantController.KEY_CUSTOM_WIDTH_DP, w)
            .putFloat(FloatingAssistantController.KEY_CUSTOM_HEIGHT_DP, h)
            .putFloat(FloatingAssistantController.KEY_CUSTOM_TEXT_SIZE_SP, ts)
            .putFloat(FloatingAssistantController.KEY_CUSTOM_CORNER_RADIUS_DP, cr)
            .putFloat(FloatingAssistantController.KEY_CUSTOM_BUBBLE_SIZE_DP, bs)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resize & Reshape Popup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            widthDp = FloatingAssistantController.DEFAULT_WIDTH_DP
                            heightDp = FloatingAssistantController.DEFAULT_HEIGHT_DP
                            textSizeSp = FloatingAssistantController.DEFAULT_TEXT_SIZE_SP
                            cornerRadiusDp = FloatingAssistantController.DEFAULT_CORNER_RADIUS_DP
                            bubbleSizeDp = FloatingAssistantController.DEFAULT_BUBBLE_SIZE_DP
                            savePreferences(widthDp, heightDp, textSizeSp, cornerRadiusDp, bubbleSizeDp)
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset to Default")
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =====================================================================
            // 1. LIVE INTERACTIVE PREVIEW
            // =====================================================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "LIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Pill Preview
                    BubblePreviewCard(
                        widthDp = widthDp,
                        heightDp = heightDp,
                        textSizeSp = textSizeSp,
                        cornerRadiusDp = cornerRadiusDp
                    )

                    // Minimized Bubble Preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Minimized Bubble:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MinimizedBubblePreview(sizeDp = bubbleSizeDp)
                    }
                }
            }

            // =====================================================================
            // 2. QUICK PRESETS
            // =====================================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = widthDp == 300f && heightDp == 56f && cornerRadiusDp >= 32f,
                        onClick = {
                            widthDp = 300f
                            heightDp = 56f
                            textSizeSp = 16.5f
                            cornerRadiusDp = 34f
                            bubbleSizeDp = 48f
                            savePreferences(300f, 56f, 16.5f, 34f, 48f)
                        },
                        label = { Text("Default Pill") }
                    )

                    FilterChip(
                        selected = widthDp == 260f && heightDp == 48f,
                        onClick = {
                            widthDp = 260f
                            heightDp = 48f
                            textSizeSp = 14f
                            cornerRadiusDp = 24f
                            bubbleSizeDp = 42f
                            savePreferences(260f, 48f, 14f, 24f, 42f)
                        },
                        label = { Text("Compact") }
                    )

                    FilterChip(
                        selected = widthDp == 340f && heightDp == 66f,
                        onClick = {
                            widthDp = 340f
                            heightDp = 66f
                            textSizeSp = 18f
                            cornerRadiusDp = 34f
                            bubbleSizeDp = 54f
                            savePreferences(340f, 66f, 18f, 34f, 54f)
                        },
                        label = { Text("Spacious") }
                    )

                    FilterChip(
                        selected = cornerRadiusDp == 16f,
                        onClick = {
                            widthDp = 310f
                            heightDp = 58f
                            textSizeSp = 16f
                            cornerRadiusDp = 16f
                            bubbleSizeDp = 48f
                            savePreferences(310f, 58f, 16f, 16f, 48f)
                        },
                        label = { Text("Card") }
                    )
                }
            }

            HorizontalDivider()

            // =====================================================================
            // 3. SLIDERS
            // =====================================================================

            // Width Slider
            CustomSliderItem(
                label = "Width",
                valueDisplay = "${widthDp.toInt()} dp",
                value = widthDp,
                valueRange = 240f..360f,
                steps = 23,
                onValueChange = {
                    widthDp = it
                    savePreferences(w = it)
                }
            )

            // Height Slider
            CustomSliderItem(
                label = "Height",
                valueDisplay = "${heightDp.toInt()} dp",
                value = heightDp,
                valueRange = 46f..76f,
                steps = 14,
                onValueChange = {
                    heightDp = it
                    savePreferences(h = it)
                }
            )

            // Text Size Slider
            CustomSliderItem(
                label = "Text Size",
                valueDisplay = "${String.format("%.1f", textSizeSp)} sp",
                value = textSizeSp,
                valueRange = 13f..22f,
                steps = 17,
                onValueChange = {
                    textSizeSp = it
                    savePreferences(ts = it)
                }
            )

            // Corner Shape / Roundness Slider
            CustomSliderItem(
                label = "Corner Shape (Reshape)",
                valueDisplay = if (cornerRadiusDp >= 32f) "Stadium Pill (100%)" else "${cornerRadiusDp.toInt()} dp",
                value = cornerRadiusDp,
                valueRange = 12f..36f,
                steps = 11,
                onValueChange = {
                    cornerRadiusDp = it
                    savePreferences(cr = it)
                }
            )

            // Minimized Bubble Size Slider
            CustomSliderItem(
                label = "Minimized Bubble Size",
                valueDisplay = "${bubbleSizeDp.toInt()} dp",
                value = bubbleSizeDp,
                valueRange = 40f..64f,
                steps = 11,
                onValueChange = {
                    bubbleSizeDp = it
                    savePreferences(bs = it)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CustomSliderItem(
    label: String,
    valueDisplay: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BubblePreviewCard(
    widthDp: Float,
    heightDp: Float,
    textSizeSp: Float,
    cornerRadiusDp: Float
) {
    val isDark = isSystemInDarkTheme()
    val pillBackgroundColor = if (isDark) Color(0xFF222427) else Color(0xFFFFFFFF)
    val pillBorderColor = if (isDark) Color(0xFF383B40) else Color(0xFFE2E4E8)
    val mutedTextColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val suggestionBlue = if (isDark) Color(0xFF4285F4) else Color(0xFF1A73E8)
    val actualShape = RoundedCornerShape(cornerRadiusDp.dp)

    Surface(
        modifier = Modifier
            .width(widthDp.dp)
            .heightIn(min = (heightDp - 4).dp, max = (heightDp + 4).dp),
        shape = actualShape,
        color = pillBackgroundColor,
        shadowElevation = 4.dp,
        border = BorderStroke(0.8.dp, pillBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Translucent edge on left: touch to minimize
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height((heightDp * 0.42f).coerceIn(16f, 28f).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.28f)
                            else Color.Black.copy(alpha = 0.18f)
                        )
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Red circular badge
            val badgeSize = (heightDp * 0.65f).coerceIn(32f, 42f)
            Box(
                modifier = Modifier
                    .size(badgeSize.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB71C1C)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1",
                    color = Color.White,
                    fontSize = (badgeSize * 0.42f).sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Correct your spelling",
                    color = mutedTextColor,
                    fontSize = (textSizeSp * 0.76f).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "which",
                    color = suggestionBlue,
                    fontSize = textSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Action: Only Outlined Trash
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                modifier = Modifier.size((heightDp * 0.36f).coerceIn(18f, 22f).dp),
                tint = mutedTextColor
            )
        }
    }
}

@Composable
fun MinimizedBubblePreview(sizeDp: Float) {
    val isDark = isSystemInDarkTheme()
    val pillBackgroundColor = if (isDark) Color(0xFF222427) else Color(0xFFFFFFFF)
    val pillBorderColor = if (isDark) Color(0xFF383B40) else Color(0xFFE2E4E8)

    Box(
        modifier = Modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size((sizeDp - 4).dp),
            shape = CircleShape,
            color = pillBackgroundColor,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, pillBorderColor)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_hoverpen_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size((sizeDp * 0.7f).dp)
                        .clip(CircleShape)
                )
            }
        }

        // Small red badge on top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size((sizeDp * 0.36f).coerceIn(14f, 20f).dp)
                .clip(CircleShape)
                .background(Color(0xFFB71C1C)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "1",
                color = Color.White,
                fontSize = (sizeDp * 0.22f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
