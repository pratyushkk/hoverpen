package com.wordpilot.assistant.ui.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.CorrectionCategory
import com.wordpilot.assistant.ui.settings.AppearanceSettings
import com.wordpilot.assistant.ui.theme.AssistantColors
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolbarActionItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

object DefaultActionItems {
    val FixGrammar = ToolbarActionItem("fix_grammar", "Check Writing", Icons.Filled.AutoAwesome)
    val Settings = ToolbarActionItem("settings", "Settings", Icons.Filled.Settings)

    val AllItems = listOf(
        FixGrammar,
        Settings
    )

    fun findById(id: String): ToolbarActionItem? = AllItems.find { it.id == id }
}

@Composable
fun ToolbarActionsContent(
    settings: AppearanceSettings,
    onCollapse: () -> Unit,
    isExpandedToLeft: Boolean,
    activeCorrection: Correction? = null,
    activeCorrections: List<Correction> = emptyList(),
    errorCount: Int = 0,
    isChecking: Boolean = false,
    onAcceptCorrection: (Correction) -> Unit = {},
    onDismissCorrection: () -> Unit = {},
    onActionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val correctionsToShow = if (activeCorrections.isNotEmpty()) {
        activeCorrections
    } else if (activeCorrection != null) {
        listOf(activeCorrection)
    } else {
        emptyList()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExpandedToLeft) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Collapse",
                    tint = AssistantColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (correctionsToShow.isNotEmpty()) {
            // Direct Corrected Word pills with Material 3 Expressive Staggered Cascade
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                correctionsToShow.forEachIndexed { index, correction ->
                    StaggeredCorrectionChip(
                        correction = correction,
                        index = index,
                        onAccept = { onAcceptCorrection(correction) }
                    )
                }

                IconButton(
                    onClick = onDismissCorrection,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss error",
                        tint = AssistantColors.TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        } else if (isChecking) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AssistantColors.AccentCyan
                )
                Text(
                    text = "Checking...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCollapse() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "All clear",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Dedicated AI Button in Expanded Pill
        Surface(
            onClick = { onActionClick("rewrite_tone") },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF6366F1).copy(alpha = 0.32f),
            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.75f)),
            modifier = Modifier
                .height(30.dp)
                .padding(horizontal = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "AI",
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "AI",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!isExpandedToLeft) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Collapse",
                    tint = AssistantColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ExpandedToolbar(
    settings: AppearanceSettings,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    isExpandedToLeft: Boolean = false,
    activeCorrection: Correction? = null,
    activeCorrections: List<Correction> = emptyList(),
    errorCount: Int = 0,
    isChecking: Boolean = false,
    onAcceptCorrection: (Correction) -> Unit = {},
    onDismissCorrection: () -> Unit = {},
    onActionClick: (String) -> Unit = {}
) {
    GlassBackground(
        modifier = modifier
            .height(settings.toolbarHeightDp.dp)
            .widthIn(max = settings.toolbarMaxWidthDp.dp),
        opacity = settings.glassOpacity,
        baseColor = Color(settings.glassTintArgb),
        cornerRadius = settings.glassCornerRadiusDp.dp,
        borderWidth = settings.glassBorderWidthDp.dp,
        borderOpacity = settings.glassBorderOpacity,
        highlightOpacity = settings.glassHighlightOpacity,
        shadowElevation = settings.glassShadowElevationDp.dp
    ) {
        ToolbarActionsContent(
            settings = settings,
            onCollapse = onCollapse,
            isExpandedToLeft = isExpandedToLeft,
            activeCorrection = activeCorrection,
            activeCorrections = activeCorrections,
            errorCount = errorCount,
            isChecking = isChecking,
            onAcceptCorrection = onAcceptCorrection,
            onDismissCorrection = onDismissCorrection,
            onActionClick = onActionClick
        )
    }
}

@Composable
fun StaggeredCorrectionChip(
    correction: Correction,
    index: Int,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(correction.id) {
        delay(index * 35L)
        isVisible = true
    }

    val categoryColor = when (correction.category) {
        CorrectionCategory.SPELLING -> AssistantColors.BadgeSpelling
        CorrectionCategory.GRAMMAR -> AssistantColors.BadgeGrammar
        CorrectionCategory.PUNCTUATION -> AssistantColors.BadgePunctuation
        CorrectionCategory.STYLE -> AssistantColors.BadgeStyle
        CorrectionCategory.CAPITALIZATION -> AssistantColors.BadgeCapitalization
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(180, easing = LinearOutSlowInEasing)
        ) + slideInHorizontally(
            animationSpec = tween(
                durationMillis = 240,
                easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            ),
            initialOffsetX = { it / 3 }
        ),
        exit = fadeOut(tween(100)) + shrinkHorizontally(tween(100)),
        modifier = modifier
    ) {
        Surface(
            onClick = onAccept,
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF10B981).copy(alpha = 0.22f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.75f)),
            modifier = Modifier.height(34.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )

                if (correction.originalText.isNotBlank()) {
                    Text(
                        text = correction.originalText,
                        color = Color(0xFFFF9999),
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "→",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = correction.suggestedReplacement ?: "",
                    color = Color(0xFF34D399),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
