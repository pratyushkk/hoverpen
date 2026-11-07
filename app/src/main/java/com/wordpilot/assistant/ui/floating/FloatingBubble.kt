package com.wordpilot.assistant.ui.floating

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.scale
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.ui.settings.AppearanceSettings
import com.wordpilot.assistant.ui.settings.BadgeLetterPosition
import com.wordpilot.assistant.ui.theme.AssistantColors
import kotlinx.coroutines.delay

@Composable
fun FloatingBubble(
    settings: AppearanceSettings,
    onClick: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    errorCount: Int = 0,
    isChecking: Boolean = false,
    hasError: Boolean = false,
    badgeColor: Color = AssistantColors.BadgeGrammar
) {
    // Auto-dimming state tracking
    var isDimmed by remember { mutableStateOf(false) }
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(settings.autoDimEnabled, settings.autoDimDelaySeconds, lastInteractionTimestamp, isChecking) {
        if (isChecking) {
            isDimmed = false
        } else if (settings.autoDimEnabled && settings.autoDimDelaySeconds > 0) {
            isDimmed = false
            delay(settings.autoDimDelaySeconds * 1000L)
            if (!isChecking) {
                isDimmed = true
            }
        } else {
            isDimmed = false
        }
    }

    val targetDimMultiplier = if (isDimmed && !isExpanded) settings.autoDimOpacity.coerceIn(0.1f, 1f) else 1.0f
    val animatedMultiplier by animateFloatAsState(
        targetValue = targetDimMultiplier,
        animationSpec = tween(durationMillis = 350),
        label = "autoDimAnimation"
    )

    val targetSizeDp = if (isExpanded) {
        (settings.toolbarHeightDp - 12f).coerceAtLeast(36f).dp
    } else {
        settings.bubbleSizeDp.dp
    }

    val animatedSizeDp by animateDpAsState(
        targetValue = targetSizeDp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bubbleAnchorSize"
    )

    val targetIconSizeDp = if (isExpanded) 18.dp else settings.iconSizeDp.dp
    val animatedIconSizeDp by animateDpAsState(
        targetValue = targetIconSizeDp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubbleIconSize"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val shape = settings.bubbleShape.toShape(
        sizeDp = animatedSizeDp.value,
        customRadiusDp = if (isExpanded) animatedSizeDp.value / 2 else settings.bubbleCornerRadiusDp
    )

    // Independent layer alphas modulated by auto-dim
    val effectiveBgAlpha = (settings.backgroundOpacity * animatedMultiplier).coerceIn(0.05f, 1f)
    val effectiveIconAlpha = (settings.iconOpacity * animatedMultiplier).coerceIn(0.05f, 1f)
    val effectiveBorderAlpha = (settings.borderOpacity * animatedMultiplier).coerceIn(0f, 1f)
    val effectiveShadowElevation = if (isExpanded) 0.dp else (settings.shadowIntensity * 12f * animatedMultiplier).dp

    val gradient = settings.getEffectiveGradient()

    val view = androidx.compose.ui.platform.LocalView.current

    val gestureModifier = if (!isExpanded) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    lastInteractionTimestamp = System.currentTimeMillis()
                    isDimmed = false
                },
                onDragEnd = {
                    lastInteractionTimestamp = System.currentTimeMillis()
                    try { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) } catch (_: SecurityException) {} catch (_: IllegalStateException) {}
                    onDragEnd()
                },
                onDragCancel = {
                    lastInteractionTimestamp = System.currentTimeMillis()
                    onDragEnd()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    lastInteractionTimestamp = System.currentTimeMillis()
                    isDimmed = false
                    onDrag(dragAmount.x, dragAmount.y)
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(animatedSizeDp)
            .scale(if (isChecking) pulseScale else 1f)
            .then(gestureModifier)
            .pointerInput(isExpanded) {
                detectTapGestures(
                    onTap = {
                        lastInteractionTimestamp = System.currentTimeMillis()
                        isDimmed = false
                        try { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: SecurityException) {} catch (_: IllegalStateException) {}
                        onClick()
                    },
                    onLongPress = {
                        lastInteractionTimestamp = System.currentTimeMillis()
                        isDimmed = false
                        try { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: SecurityException) {} catch (_: IllegalStateException) {}
                        onLongClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val activeGradient = if (errorCount > 0 && !isExpanded && !isChecking) {
            val baseColor = when (badgeColor) {
                AssistantColors.BadgeSpelling -> Color(0xFFFF2D55)
                AssistantColors.BadgePunctuation -> Color(0xFF8B5CF6)
                AssistantColors.BadgeCapitalization -> Color(0xFFFF9500)
                else -> Color(0xFFFF3B30) // Vivid Coral Red
            }
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(baseColor, baseColor.copy(alpha = 0.85f))
            )
        } else {
            gradient.createBrush()
        }

        val centerState = when {
            hasError -> -2
            isChecking -> 0
            !isExpanded && errorCount > 0 -> errorCount
            else -> -1
        }

        // Main bubble surface with shadow and gradient
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = effectiveShadowElevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.4f * settings.shadowIntensity),
                    spotColor = Color.Black.copy(alpha = 0.6f * settings.shadowIntensity)
                ),
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(
                settings.borderWidthDp.dp,
                Color.White.copy(alpha = effectiveBorderAlpha)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(activeGradient, alpha = effectiveBgAlpha),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = centerState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "bubbleCenterContentTransition"
                ) { state ->
                    when {
                        state == -2 -> {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Engine Offline",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(animatedIconSizeDp)
                            )
                        }
                        state == 0 -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(animatedIconSizeDp),
                                color = Color.White.copy(alpha = effectiveIconAlpha),
                                strokeWidth = 2.dp
                            )
                        }
                        state > 0 -> {
                            val countStr = if (state > 99) "99+" else state.toString()
                            val letter = settings.badgeLetter.trim()
                            val errorText = when (settings.badgeLetterPosition) {
                                BadgeLetterPosition.PREFIX -> "$letter$countStr"
                                BadgeLetterPosition.SUFFIX -> "$countStr$letter"
                                BadgeLetterPosition.NONE -> countStr
                            }
                            val baseSize = settings.badgeTextSizeSp
                            val calculatedSize = when {
                                errorText.length >= 4 -> (baseSize * 0.68f).coerceAtLeast(10f)
                                errorText.length == 3 -> (baseSize * 0.82f).coerceAtLeast(11f)
                                else -> baseSize
                            }.sp
                            Text(
                                text = errorText,
                                color = Color.White.copy(alpha = effectiveIconAlpha),
                                fontSize = calculatedSize,
                                fontWeight = if (settings.badgeFontWeightBold) FontWeight.Black else FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = settings.getAiLogo().icon,
                                contentDescription = if (isExpanded) "Hoverpen AI Anchor (Collapse)" else "Hoverpen Assistant",
                                tint = Color.White.copy(alpha = effectiveIconAlpha),
                                modifier = Modifier.size(animatedIconSizeDp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleDismissSheet(
    onDismissRequest: () -> Unit,
    onHide1h: () -> Unit,
    onHideUntilTomorrow: () -> Unit,
    onTurnOff: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Hoverpen Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ListItem(
                headlineContent = { Text("Hide for 1 hour") },
                leadingContent = { Icon(Icons.Filled.Timer, contentDescription = null) },
                modifier = Modifier.clickable { onHide1h() }
            )
            ListItem(
                headlineContent = { Text("Hide until tomorrow") },
                leadingContent = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
                modifier = Modifier.clickable { onHideUntilTomorrow() }
            )
            ListItem(
                headlineContent = { Text("Turn off", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onTurnOff() }
            )
        }
    }
}
