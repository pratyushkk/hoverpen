package com.wordpilot.assistant.ui.floating

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wordpilot.assistant.domain.models.AIEngineType
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.CorrectionCategory
import com.wordpilot.assistant.domain.models.RewriteStyle
import com.wordpilot.assistant.model.*
import com.wordpilot.assistant.ui.settings.AppearanceSettings
import com.wordpilot.assistant.ui.theme.AssistantColors

enum class FloatingUiState {
    COLLAPSED,
    EXPANDED,
    TONE_SELECTOR,
    PREVIEW,
    GEMINI_KEY_SETUP
}

@Composable
fun FloatingAssistantView(
    model: FloatingUiModel,
    onEvent: (FloatingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = model.settings
    val uiState = model.uiState
    val isNearRightEdge = model.bubble.isNearRightEdge
    val errorCount = model.bubble.errorCount
    val isChecking = model.bubble.isChecking
    val activeCorrection = model.corrections.activeCorrection
    val activeCorrections = model.corrections.items
    val previewState = model.card.preview
    val previewOriginalText = previewState?.originalText ?: ""
    val previewImprovedText = previewState?.improvedText ?: ""
    val isPreviewLoading = previewState?.isLoading ?: false
    val previewErrorMessage = previewState?.errorMessage
    val canUndo = model.undo.canUndo
    val currentTone = previewState?.currentTone ?: "Default"
    val selectedEngine = try {
        AIEngineType.valueOf(model.engine.id)
    } catch (_: IllegalArgumentException) {
        AIEngineType.GEMINI_FLASH
    }

    FloatingAssistantView(
        settings = settings,
        uiState = uiState,
        isNearRightEdge = isNearRightEdge,
        errorCount = errorCount,
        isChecking = isChecking,
        activeCorrection = activeCorrection,
        activeCorrections = activeCorrections,
        previewOriginalText = previewOriginalText,
        previewImprovedText = previewImprovedText,
        isPreviewLoading = isPreviewLoading,
        previewErrorMessage = previewErrorMessage,
        canUndo = canUndo,
        currentTone = currentTone,
        onBubbleClick = { onEvent(FloatingEvent.OnBubbleClick) },
        onDragStart = { onEvent(FloatingEvent.OnDragStart) },
        onDrag = { dx, dy -> onEvent(FloatingEvent.OnDrag(dx, dy)) },
        onDragEnd = { onEvent(FloatingEvent.OnDragEnd) },
        onActionClick = { onEvent(FloatingEvent.OnActionClick(it)) },
        onCustomPromptSubmit = { onEvent(FloatingEvent.OnCustomPromptSubmit(it)) },
        onSelectTone = { onEvent(FloatingEvent.OnSelectTone(it)) },
        onToneOptionSelect = { style, title -> onEvent(FloatingEvent.OnSelectToneOption(style, title)) },
        onReplace = { onEvent(FloatingEvent.OnReplace) },
        onInsert = { onEvent(FloatingEvent.OnInsert) },
        onCopy = { onEvent(FloatingEvent.OnCopy) },
        onCancelPreview = { onEvent(FloatingEvent.OnCancelPreview) },
        onUndo = { onEvent(FloatingEvent.OnUndo) },
        onOpenSettings = { onEvent(FloatingEvent.OnOpenSettings) },
        onCollapse = { onEvent(FloatingEvent.OnCollapse) },
        onAcceptCorrection = { onEvent(FloatingEvent.OnAccept(it.id)) },
        onDismissCorrection = { onEvent(FloatingEvent.OnDismiss(activeCorrection?.id ?: "")) },
        onAcceptAll = { onEvent(FloatingEvent.OnAcceptAll) },
        onReviewAll = { onEvent(FloatingEvent.OnReviewAll) },
        onCollapseTransitionCompleted = { onEvent(FloatingEvent.OnCollapseTransitionCompleted) },
        selectedEngine = selectedEngine,
        onSelectEngine = { onEvent(FloatingEvent.OnSelectEngine(it)) },
        onGeminiKeySaved = { onEvent(FloatingEvent.OnGeminiKeySaved(it)) },
        modifier = modifier
    )
}

@Composable
fun FloatingAssistantView(
    settings: AppearanceSettings,
    uiState: FloatingUiState,
    isNearRightEdge: Boolean,
    errorCount: Int,
    isChecking: Boolean,
    activeCorrection: Correction?,
    previewOriginalText: String,
    previewImprovedText: String,
    isPreviewLoading: Boolean,
    previewErrorMessage: String?,
    canUndo: Boolean,
    currentTone: String,
    onBubbleClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onActionClick: (String) -> Unit,
    onCustomPromptSubmit: (String) -> Unit,
    onSelectTone: (String) -> Unit,
    onToneOptionSelect: (RewriteStyle, String) -> Unit = { _, _ -> },
    onReplace: () -> Unit,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onCancelPreview: () -> Unit,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    onCollapse: () -> Unit,
    activeCorrections: List<Correction> = emptyList(),
    onAcceptCorrection: (Correction) -> Unit = {},
    onDismissCorrection: () -> Unit = {},
    onAcceptAll: () -> Unit = {},
    onReviewAll: () -> Unit = {},
    onCollapseTransitionCompleted: () -> Unit = {},
    selectedEngine: AIEngineType = AIEngineType.GEMINI_FLASH,
    onSelectEngine: (AIEngineType) -> Unit = {},
    onGeminiKeySaved: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val badgeColor = when (activeCorrection?.category) {
        CorrectionCategory.SPELLING -> AssistantColors.BadgeSpelling
        CorrectionCategory.GRAMMAR -> AssistantColors.BadgeGrammar
        CorrectionCategory.PUNCTUATION -> AssistantColors.BadgePunctuation
        CorrectionCategory.STYLE -> AssistantColors.BadgeStyle
        CorrectionCategory.CAPITALIZATION -> AssistantColors.BadgeCapitalization
        null -> AssistantColors.BadgeGrammar
    }

    val isExpanded = uiState == FloatingUiState.EXPANDED
    val bubbleSize = settings.bubbleSizeDp.dp
    val toolbarHeight = settings.toolbarHeightDp.dp
    val toolbarMaxWidth = settings.toolbarMaxWidthDp.dp

    // Morphing Pill Dimensions with Material 3 Expressive Emphasized Decelerate motion
    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) toolbarMaxWidth else bubbleSize,
        animationSpec = if (isExpanded) {
            tween(durationMillis = 260, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
        } else {
            tween(durationMillis = 200, easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f))
        },
        finishedListener = { finalWidth ->
            if (uiState == FloatingUiState.COLLAPSED && finalWidth <= bubbleSize + 1.dp) {
                onCollapseTransitionCompleted()
            }
        },
        label = "pillWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) toolbarHeight else bubbleSize,
        animationSpec = if (isExpanded) {
            tween(durationMillis = 260, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
        } else {
            tween(durationMillis = 200, easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f))
        },
        label = "pillHeight"
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) settings.glassCornerRadiusDp.dp else (bubbleSize / 2),
        animationSpec = if (isExpanded) {
            tween(durationMillis = 260, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
        } else {
            tween(durationMillis = 200, easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f))
        },
        label = "pillCornerRadius"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = if (isExpanded) {
            tween(durationMillis = 180, delayMillis = 70, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = 100, easing = FastOutLinearInEasing)
        },
        label = "contentAlpha"
    )

    val pillGlassOpacity by animateFloatAsState(
        targetValue = if (isExpanded) settings.glassOpacity else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "pillGlassOpacity"
    )

    val pillBorderOpacity by animateFloatAsState(
        targetValue = if (isExpanded) settings.glassBorderOpacity else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "pillBorderOpacity"
    )

    val pillElevation by animateDpAsState(
        targetValue = if (isExpanded) settings.glassShadowElevationDp.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "pillElevation"
    )

    // Panels enter / exit transitions
    val panelEnterAnim = fadeIn(
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)
    ) + expandVertically(
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        expandFrom = Alignment.Bottom
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        transformOrigin = if (isNearRightEdge) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
    )

    val panelExitAnim = fadeOut(
        animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
    ) + shrinkVertically(
        animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
        shrinkTowards = Alignment.Bottom
    ) + scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
        transformOrigin = if (isNearRightEdge) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
    )

    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = if (isNearRightEdge) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Panel 1: Gemini API Key Setup
        AnimatedVisibility(
            visible = uiState == FloatingUiState.GEMINI_KEY_SETUP,
            enter = panelEnterAnim,
            exit = panelExitAnim
        ) {
            Box(modifier = Modifier.padding(bottom = 6.dp)) {
                FloatingGeminiKeySetupPanel(
                    settings = settings,
                    onKeySaved = onGeminiKeySaved,
                    onClose = onCollapse
                )
            }
        }

        // Panel 2: Tone Selector Panel (Rewrite & Tone)
        AnimatedVisibility(
            visible = uiState == FloatingUiState.TONE_SELECTOR,
            enter = panelEnterAnim,
            exit = panelExitAnim
        ) {
            Box(modifier = Modifier.padding(bottom = 6.dp)) {
                ToneSelectorPanel(
                    settings = settings,
                    selectedEngine = selectedEngine,
                    onToneSelect = onToneOptionSelect,
                    onCustomPromptSubmit = onCustomPromptSubmit,
                    onOpenGeminiSetup = { onActionClick("gemini_setup") },
                    onOpenSettings = onOpenSettings,
                    onClose = onCollapse
                )
            }
        }

        // Panel 3: Suggestion Preview Panel (Diff & Action buttons)
        AnimatedVisibility(
            visible = uiState == FloatingUiState.PREVIEW,
            enter = panelEnterAnim,
            exit = panelExitAnim
        ) {
            Box(modifier = Modifier.padding(bottom = 6.dp)) {
                SuggestionPreview(
                    originalText = previewOriginalText,
                    improvedText = previewImprovedText,
                    isLoading = isPreviewLoading,
                    errorMessage = previewErrorMessage,
                    canUndo = canUndo,
                    currentTone = currentTone,
                    settings = settings,
                    selectedEngine = selectedEngine,
                    onSelectEngine = onSelectEngine,
                    onReplace = onReplace,
                    onInsert = onInsert,
                    onCopy = onCopy,
                    onCancel = onCancelPreview,
                    onUndo = onUndo,
                    onSelectTone = onSelectTone
                )
            }
        }

        // The Unified Morphing Pill Container
        GlassBackground(
            modifier = Modifier
                .size(width = animatedWidth, height = animatedHeight),
            opacity = pillGlassOpacity,
            baseColor = Color(settings.glassTintArgb),
            cornerRadius = animatedCornerRadius,
            borderWidth = settings.glassBorderWidthDp.dp,
            borderOpacity = pillBorderOpacity,
            highlightOpacity = if (isExpanded) settings.glassHighlightOpacity else 0f,
            shadowElevation = pillElevation
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isNearRightEdge) Arrangement.End else Arrangement.Start
            ) {
                if (isNearRightEdge) {
                    // Expanding to the left:
                    if (animatedWidth > bubbleSize + 12.dp) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = contentAlpha }
                                .padding(start = 6.dp, end = 2.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            ToolbarActionsContent(
                                settings = settings,
                                onCollapse = onCollapse,
                                isExpandedToLeft = true,
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

                    // Bubble Anchor at the right edge
                    Box(
                        modifier = Modifier.padding(end = if (isExpanded) 5.dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingBubble(
                            settings = settings,
                            isExpanded = isExpanded,
                            errorCount = errorCount,
                            isChecking = isChecking,
                            badgeColor = badgeColor,
                            onClick = onBubbleClick,
                            onDragStart = onDragStart,
                            onDrag = onDrag,
                            onDragEnd = onDragEnd
                        )
                    }
                } else {
                    // Expanding to the right:
                    // Bubble Anchor at the left edge
                    Box(
                        modifier = Modifier.padding(start = if (isExpanded) 5.dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingBubble(
                            settings = settings,
                            isExpanded = isExpanded,
                            errorCount = errorCount,
                            isChecking = isChecking,
                            badgeColor = badgeColor,
                            onClick = onBubbleClick,
                            onDragStart = onDragStart,
                            onDrag = onDrag,
                            onDragEnd = onDragEnd
                        )
                    }

                    // Actions on right
                    if (animatedWidth > bubbleSize + 12.dp) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = contentAlpha }
                                .padding(start = 2.dp, end = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            ToolbarActionsContent(
                                settings = settings,
                                onCollapse = onCollapse,
                                isExpandedToLeft = false,
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
                }
            }
        }
    }
}
