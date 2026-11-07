package com.wordpilot.assistant.model

import androidx.compose.runtime.Immutable
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.RewriteStyle
import com.wordpilot.assistant.ui.floating.FloatingUiState
import com.wordpilot.assistant.ui.settings.AppearanceSettings

@Immutable
data class FloatingUiModel(
    val settings: AppearanceSettings,
    val uiState: FloatingUiState,
    val bubble: BubbleState,
    val card: CardState,
    val engine: EngineInfo,
    val corrections: CorrectionList,
    val undo: UndoState,
    val error: UserFacingError? = null
)

@Immutable
data class BubbleState(
    val isNearRightEdge: Boolean,
    val errorCount: Int,
    val isChecking: Boolean
)

enum class CardMode {
    PREVIEW,
    CORRECTIONS,
    TONE,
    GEMINI_KEY
}

@Immutable
data class CardState(
    val isExpanded: Boolean,
    val mode: CardMode,
    val preview: PreviewState? = null
)

@Immutable
data class PreviewState(
    val originalText: String,
    val improvedText: String,
    val currentTone: String,
    val isLoading: Boolean,
    val errorMessage: String? = null
)

@Immutable
data class CorrectionList(
    val items: List<Correction>,
    val activeCorrection: Correction? = null,
    val activeCorrections: Set<String> = emptySet()
)

@Immutable
data class UndoState(
    val canUndo: Boolean
)

@Immutable
data class EngineInfo(
    val id: String,
    val displayName: String
)

sealed interface UserFacingError {
    data class Network(val message: String) : UserFacingError
    data class Permission(val message: String) : UserFacingError
    data class EngineNotReady(val message: String) : UserFacingError
    data class Generic(val message: String) : UserFacingError
}

sealed interface FloatingEvent {
    data object OnBubbleClick : FloatingEvent
    data object OnBubbleLongClick : FloatingEvent
    data class OnDrag(val dx: Float, val dy: Float) : FloatingEvent
    data object OnDragEnd : FloatingEvent
    data class OnAccept(val correctionId: String) : FloatingEvent
    data class OnDismiss(val correctionId: String) : FloatingEvent
    data object OnAcceptAll : FloatingEvent
    data object OnReviewAll : FloatingEvent
    data object OnUndo : FloatingEvent
    data object OnReplace : FloatingEvent
    data object OnInsert : FloatingEvent
    data object OnCopy : FloatingEvent
    data object OnCancelPreview : FloatingEvent
    data object OnOpenSettings : FloatingEvent
    data object OnCollapse : FloatingEvent
    data object OnCollapseTransitionCompleted : FloatingEvent
    data class OnActionClick(val actionId: String) : FloatingEvent
    data class OnCustomPromptSubmit(val prompt: String) : FloatingEvent
    data class OnSelectTone(val tone: String) : FloatingEvent
    data class OnSelectToneOption(val style: RewriteStyle, val title: String) : FloatingEvent
    data class OnSelectEngine(val engineType: com.wordpilot.assistant.domain.models.AIEngineType) : FloatingEvent
    data class OnGeminiKeySaved(val key: String) : FloatingEvent
    data object OnHide1h : FloatingEvent
    data object OnHideUntilTomorrow : FloatingEvent
    data object OnTurnOff : FloatingEvent
}
