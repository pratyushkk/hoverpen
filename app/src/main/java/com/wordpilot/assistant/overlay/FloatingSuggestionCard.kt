package com.wordpilot.assistant.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.R
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.CorrectionCategory

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingSuggestionCard(
    correction: Correction?,
    isExpanded: Boolean,
    isMinimized: Boolean = false,
    customWidthDp: Float = 300f,
    customHeightDp: Float = 56f,
    customTextSizeSp: Float = 16.5f,
    customCornerRadiusDp: Float = 34f,
    customBubbleSizeDp: Float = 48f,
    isCustomizing: Boolean = false,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onCloseAssistant: () -> Unit = onDismiss,
    onToggleMinimize: () -> Unit = {},
    onToggleExpand: () -> Unit = {},
    onToggleCustomize: () -> Unit = {},
    onUpdateDimensions: (width: Float, height: Float, textSize: Float, cornerRadius: Float, bubbleSize: Float) -> Unit = { _, _, _, _, _ -> },
    onAddToDictionary: () -> Unit = {},
    onIgnoreRule: () -> Unit = {},
    isChecking: Boolean = false,
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragY: (Float) -> Unit = {},
    onResizeWidth: (Float) -> Unit = {},
    onSetWidthPreset: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val pillWidth = minOf(customWidthDp.dp, screenWidth - 12.dp)

    val isDark = isSystemInDarkTheme()
    val pillBackgroundColor = if (isDark) Color(0xFF222427) else Color(0xFFFFFFFF)
    val pillBorderColor = if (isDark) Color(0xFF383B40) else Color(0xFFE2E4E8)
    val mutedTextColor = if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val suggestionBlue = if (isDark) Color(0xFF4285F4) else Color(0xFF1A73E8)

    // Using explicit RoundedCornerShape with user's chosen corner radius
    // When >= 32dp it forms a 100% stadium pill with authentic hardware-accelerated curved drop shadow!
    val pillShape = RoundedCornerShape(customCornerRadiusDp.dp)

    if (isMinimized) {
        // =========================================================================
        // MINIMIZED STATE: Edge-docked bubble SHOWING THE WORDPILOT LOGO
        // Resizable & can go all the way to the extreme corner/edge of the screen
        // =========================================================================
        Box(
            modifier = modifier
                .size(customBubbleSizeDp.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .combinedClickable(
                    onClick = onToggleMinimize,
                    onLongClick = onToggleCustomize
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size((customBubbleSizeDp - 4).dp),
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
                        painter = painterResource(id = R.drawable.ic_wordpilot_logo),
                        contentDescription = "Hoverpen",
                        modifier = Modifier
                            .size((customBubbleSizeDp * 0.7f).dp)
                            .clip(CircleShape)
                    )
                }
            }

            // Small circular error badge on top-right of the logo
            if (correction != null) {
                val badgeColor = when (correction.category) {
                    CorrectionCategory.SPELLING -> Color(0xFFB71C1C)
                    CorrectionCategory.GRAMMAR -> Color(0xFF1565C0)
                    else -> Color(0xFF6A1B9A)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size((customBubbleSizeDp * 0.36f).coerceIn(14f, 20f).dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "1",
                        color = Color.White,
                        fontSize = (customBubbleSizeDp * 0.22f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // =========================================================================
        // OPEN POPUP: Pure Grammarly Pill
        // - NO logo or app name in open popup!
        // - Translucent edge on left: tap to minimize, long-press to resize!
        // - NO X or - buttons (matches reference screenshot with only trash icon)
        // - Fully customizable width, height, shape, text size with live preview
        // =========================================================================
        Surface(
            modifier = modifier
                .width(pillWidth)
                .wrapContentHeight(),
            shape = pillShape,
            color = pillBackgroundColor,
            shadowElevation = 4.dp,
            border = BorderStroke(0.8.dp, pillBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                // Main Stadium Pill Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (customHeightDp - 4).dp, max = (customHeightDp + 6).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Translucent edge on left:
                    // - Tap: minimizes instantly to extreme corner
                    // - Long press: opens in-place resize sliders!
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleMinimize,
                                onLongClick = onToggleCustomize
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height((customHeightDp * 0.42f).coerceIn(16f, 28f).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.28f)
                                    else Color.Black.copy(alpha = 0.18f)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    if (correction != null) {
                        val badgeColor = when (correction.category) {
                            CorrectionCategory.SPELLING -> Color(0xFFB71C1C) // Deep red from reference screenshot
                            CorrectionCategory.GRAMMAR -> Color(0xFF1565C0) // Deep blue for grammar
                            CorrectionCategory.PUNCTUATION -> Color(0xFF6A1B9A)
                            CorrectionCategory.STYLE -> Color(0xFF00695C)
                            CorrectionCategory.CAPITALIZATION -> Color(0xFFE65100)
                        }

                        // 1. Left Circular Red Badge (Draggable to move anywhere)
                        val badgeSize = (customHeightDp * 0.68f).coerceIn(32f, 44f)
                        Box(
                            modifier = Modifier
                                .size(badgeSize.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.x, dragAmount.y)
                                    }
                                },
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

                        // 2. Center Text Column:
                        // Top line: "Correct your spelling" (muted gray, draggable)
                        // Bottom line: Replacement word in blue (tapping directly applies the correction!)
                        // Font size scales with user's slider setting!
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (correction.category) {
                                    CorrectionCategory.SPELLING -> "Correct your spelling"
                                    CorrectionCategory.GRAMMAR -> "Grammar suggestion"
                                    CorrectionCategory.PUNCTUATION -> "Punctuation"
                                    CorrectionCategory.STYLE -> "Style suggestion"
                                    CorrectionCategory.CAPITALIZATION -> "Capitalization"
                                },
                                color = mutedTextColor,
                                fontSize = (customTextSizeSp * 0.76f).sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onToggleExpand() }
                            )

                            Text(
                                text = correction.suggestedReplacement ?: correction.originalText,
                                color = suggestionBlue,
                                fontSize = customTextSizeSp.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onAccept() }
                            )
                        }

                        // 3. Right Action Icon: ONLY Outlined Trash (Dismiss suggestion)
                        // Long-press also triggers resize/reshape sliders!
                        Box(
                            modifier = Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss,
                                    onLongClick = onToggleCustomize
                                )
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Dismiss suggestion",
                                modifier = Modifier.size((customHeightDp * 0.36f).coerceIn(18f, 22f).dp),
                                tint = mutedTextColor
                            )
                        }
                    } else {
                        // 0 Errors / Monitoring state:
                        val checkBadgeSize = (customHeightDp * 0.65f).coerceIn(30f, 40f)
                        Box(
                            modifier = Modifier
                                .size(checkBadgeSize.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E3A24) else Color(0xFFE8F5E9))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.x, dragAmount.y)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Clear",
                                modifier = Modifier.size((checkBadgeSize * 0.55f).dp),
                                tint = Color(0xFF2E7D32)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onToggleExpand() },
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isChecking) "Checking sentence..." else "All clear",
                                color = if (isChecking) suggestionBlue else Color(0xFF2E7D32),
                                fontSize = (customTextSizeSp * 0.85f).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Right: Subtle dismiss icon
                        Box(
                            modifier = Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCloseAssistant,
                                    onLongClick = onToggleCustomize
                                )
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size((customHeightDp * 0.34f).coerceIn(16f, 20f).dp),
                                tint = mutedTextColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // =========================================================================
                // IN-PLACE RESIZE & RESHAPE SLIDERS PANEL
                // Live preview and real-time adjustment right on the overlay!
                // =========================================================================
                AnimatedVisibility(
                    visible = isCustomizing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = pillBorderColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Resize & Reshape",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = suggestionBlue
                            )
                            TextButton(
                                onClick = onToggleCustomize,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Done", fontSize = 11.sp, color = suggestionBlue)
                            }
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SuggestionChip(
                                onClick = { onUpdateDimensions(300f, 56f, 16.5f, 34f, 48f) },
                                label = { Text("Pill", fontSize = 10.sp) }
                            )
                            SuggestionChip(
                                onClick = { onUpdateDimensions(260f, 48f, 14f, 24f, 42f) },
                                label = { Text("Compact", fontSize = 10.sp) }
                            )
                            SuggestionChip(
                                onClick = { onUpdateDimensions(340f, 66f, 18f, 34f, 54f) },
                                label = { Text("Large", fontSize = 10.sp) }
                            )
                            SuggestionChip(
                                onClick = { onUpdateDimensions(310f, 58f, 16f, 16f, 48f) },
                                label = { Text("Card", fontSize = 10.sp) }
                            )
                        }

                        // Width Slider
                        Text("Width: ${customWidthDp.toInt()} dp", fontSize = 10.5.sp, color = mutedTextColor)
                        Slider(
                            value = customWidthDp,
                            onValueChange = { onUpdateDimensions(it, customHeightDp, customTextSizeSp, customCornerRadiusDp, customBubbleSizeDp) },
                            valueRange = 240f..360f,
                            modifier = Modifier.height(22.dp)
                        )

                        // Height Slider
                        Text("Height: ${customHeightDp.toInt()} dp", fontSize = 10.5.sp, color = mutedTextColor)
                        Slider(
                            value = customHeightDp,
                            onValueChange = { onUpdateDimensions(customWidthDp, it, customTextSizeSp, customCornerRadiusDp, customBubbleSizeDp) },
                            valueRange = 46f..76f,
                            modifier = Modifier.height(22.dp)
                        )

                        // Text Size Slider
                        Text("Text Size: ${String.format("%.1f", customTextSizeSp)} sp", fontSize = 10.5.sp, color = mutedTextColor)
                        Slider(
                            value = customTextSizeSp,
                            onValueChange = { onUpdateDimensions(customWidthDp, customHeightDp, it, customCornerRadiusDp, customBubbleSizeDp) },
                            valueRange = 13f..22f,
                            modifier = Modifier.height(22.dp)
                        )

                        // Shape / Corner Roundness Slider
                        Text(
                            "Shape: ${if (customCornerRadiusDp >= 32f) "Stadium Pill" else "${customCornerRadiusDp.toInt()} dp"}",
                            fontSize = 10.5.sp,
                            color = mutedTextColor
                        )
                        Slider(
                            value = customCornerRadiusDp,
                            onValueChange = { onUpdateDimensions(customWidthDp, customHeightDp, customTextSizeSp, it, customBubbleSizeDp) },
                            valueRange = 12f..36f,
                            modifier = Modifier.height(22.dp)
                        )

                        // Bubble Size Slider
                        Text("Bubble Size: ${customBubbleSizeDp.toInt()} dp", fontSize = 10.5.sp, color = mutedTextColor)
                        Slider(
                            value = customBubbleSizeDp,
                            onValueChange = { onUpdateDimensions(customWidthDp, customHeightDp, customTextSizeSp, customCornerRadiusDp, it) },
                            valueRange = 40f..64f,
                            modifier = Modifier.height(22.dp)
                        )
                    }
                }

                // Expanded Section (Smooth 150ms animation)
                AnimatedVisibility(
                    visible = isExpanded && correction != null && !isCustomizing,
                    enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(120))
                ) {
                    correction?.let { corr ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 10.dp, end = 10.dp, bottom = 4.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = pillBorderColor
                            )
                            Text(
                                text = corr.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = mutedTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (corr.category == CorrectionCategory.SPELLING) {
                                    TextButton(
                                        onClick = onAddToDictionary,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Add to dictionary", fontSize = 11.sp, color = suggestionBlue)
                                    }
                                }
                                TextButton(
                                    onClick = onToggleCustomize,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Resize & Reshape", fontSize = 11.sp, color = suggestionBlue)
                                }
                                TextButton(
                                    onClick = onIgnoreRule,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Ignore rule", fontSize = 11.sp, color = mutedTextColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
