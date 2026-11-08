package com.wordpilot.assistant.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wordpilot.assistant.MainActivity
import com.wordpilot.assistant.WordPilotApp
import com.wordpilot.assistant.domain.correction.SafeReplacementHelper
import com.wordpilot.assistant.domain.models.AIEngineType
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.CorrectionCategory
import com.wordpilot.assistant.domain.models.EditorSnapshot
import com.wordpilot.assistant.domain.models.RewriteStyle
import com.wordpilot.assistant.domain.models.TextOperationResult
import com.wordpilot.assistant.model.*
import com.wordpilot.assistant.overlay.OverlayGeometry
import com.wordpilot.assistant.ui.floating.FloatingAssistantView
import com.wordpilot.assistant.ui.floating.FloatingUiState
import com.wordpilot.assistant.ui.navigation.Routes
import com.wordpilot.assistant.ui.settings.AppearanceSettings
import com.wordpilot.assistant.ui.theme.WordPilotTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.min

class FloatingAssistantController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner
) {
    companion object {
        const val TAG = "FloatingController"
        const val PREFS_NAME = "wordpilot_overlay_prefs"
        const val KEY_X_PX = "overlay_x_px"
        const val KEY_Y_PX = "overlay_y_px"
        const val KEY_MINIMIZED = "overlay_minimized"
        const val KEY_CUSTOM_WIDTH_DP = "overlay_width_dp"
        const val KEY_CUSTOM_HEIGHT_DP = "overlay_height_dp"
        const val KEY_CUSTOM_TEXT_SIZE_SP = "overlay_text_size_sp"
        const val KEY_CUSTOM_CORNER_RADIUS_DP = "overlay_corner_radius_dp"
        const val KEY_CUSTOM_BUBBLE_SIZE_DP = "overlay_bubble_size_dp"

        const val DEFAULT_WIDTH_DP = 300f
        const val DEFAULT_HEIGHT_DP = 56f
        const val DEFAULT_TEXT_SIZE_SP = 16.5f
        const val DEFAULT_CORNER_RADIUS_DP = 34f
        const val DEFAULT_BUBBLE_SIZE_DP = 48f
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val safeReplacementHelper = SafeReplacementHelper()
    private val controllerExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> Unit
            is IOException -> Log.w(TAG, "Network I/O error in overlay", throwable)
            else -> Log.e(TAG, "Unexpected failure in overlay controller", throwable)
        }
    }
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + controllerExceptionHandler)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var isViewAttached = false
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    // Appearance settings loaded persistently
    private val _appearanceSettings = MutableStateFlow(AppearanceSettings.load(context))
    val appearanceSettings: StateFlow<AppearanceSettings> = _appearanceSettings.asStateFlow()

    // Floating UI state machine
    private val _uiState = MutableStateFlow(FloatingUiState.COLLAPSED)
    val uiState: StateFlow<FloatingUiState> = _uiState.asStateFlow()

    private val _isNearRightEdge = MutableStateFlow(false)
    val isNearRightEdge: StateFlow<Boolean> = _isNearRightEdge.asStateFlow()

    // Context & Text detection state
    private val _currentFullText = MutableStateFlow("")
    private val _currentSentence = MutableStateFlow("")
    private val _activeCorrectionsList = MutableStateFlow<List<Correction>>(emptyList())
    val activeCorrectionsList: StateFlow<List<Correction>> = _activeCorrectionsList.asStateFlow()

    private val _activeCorrection = MutableStateFlow<Correction?>(null)
    val activeCorrection: StateFlow<Correction?> = _activeCorrection.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private var snapAnimator: android.animation.ValueAnimator? = null

    // Suggestion Preview State
    private val _previewOriginalText = MutableStateFlow("")
    private val _previewImprovedText = MutableStateFlow("")
    private val _isPreviewLoading = MutableStateFlow(false)
    private val _previewErrorMessage = MutableStateFlow<String?>(null)
    private val _canUndo = MutableStateFlow(false)
        private val _currentTone = MutableStateFlow("Default")

    // Callbacks provided by WordPilotAccessibilityService
    private var onApplySentenceCallback: ((String) -> TextOperationResult)? = null
    private var onApplyFullTextCallback: ((String) -> TextOperationResult)? = null
    private var onInsertCallback: ((String) -> TextOperationResult)? = null
    private var onUndoCallback: (() -> TextOperationResult)? = null
    private var onAcceptSingleCorrectionCallback: ((Correction) -> Unit)? = null
    private var onAddToDictionaryCallback: ((Correction) -> Unit)? = null
    private var onIgnoreRuleCallback: ((Correction) -> Unit)? = null

    private var currentRequestId: Long = 0L
    private var bubbleRestingX: Int = 0
    private var bubbleRestingY: Int = 0

    private val appearancePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _appearanceSettings.value = AppearanceSettings.load(context)
        updateWindowLayoutForState(_uiState.value)
    }

    init {
        context.getSharedPreferences(AppearanceSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(appearancePrefsListener)
    }

    fun setUiState(targetState: FloatingUiState) {
        if (targetState == FloatingUiState.COLLAPSED) {
            _uiState.value = FloatingUiState.COLLAPSED
            // The native window stays at expanded bounds while Compose animates.
            // onCollapseTransitionCompleted() resizes the WindowManager bounds once Compose finishes!
        } else {
            updateWindowLayoutForState(targetState)
            _uiState.value = targetState
        }
    }

    fun onCollapseTransitionCompleted() {
        if (_uiState.value == FloatingUiState.COLLAPSED) {
            updateWindowLayoutForState(FloatingUiState.COLLAPSED)
        }
    }

    fun isInteracting(): Boolean {
        return _uiState.value != FloatingUiState.COLLAPSED
    }

    fun isExpanded(): Boolean = isInteracting()

    fun computeSafeBounds(context: Context): Rect {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or
                WindowInsets.Type.displayCutout() or
                WindowInsets.Type.navigationBars()
            )
            val b = metrics.bounds
            Rect(
                b.left + insets.left,
                b.top + insets.top,
                b.right - insets.right,
                b.bottom - insets.bottom
            )
        } else {
            val dm = context.resources.displayMetrics
            val navId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navH = if (navId > 0) context.resources.getDimensionPixelSize(navId) else 0
            val statId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statH = if (statId > 0) context.resources.getDimensionPixelSize(statId) else 0
            Rect(0, statH, dm.widthPixels, dm.heightPixels - navH)
        }
    }

    fun clampToSafeBounds(x: Int, y: Int, w: Int, h: Int, safe: Rect): PointF {
        val clampedX = x.coerceIn(safe.left, (safe.right - w).coerceAtLeast(safe.left))
        val clampedY = y.coerceIn(safe.top, (safe.bottom - h).coerceAtLeast(safe.top))
        return PointF(clampedX.toFloat(), clampedY.toFloat())
    }

    fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } catch (e: IllegalStateException) {
                val dm = context.resources.displayMetrics
                Pair(dm.widthPixels, dm.heightPixels)
            }
        } else {
            val dm = context.resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun updateWindowLayoutForState(state: FloatingUiState) {
        val view = overlayView ?: return
        val lp = windowLayoutParams ?: return
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val (screenWidthPx, screenHeightPx) = getScreenDimensions()

        val s = appearanceSettings.value
        val collapsedSizePx = OverlayGeometry.getCollapsedSizePx(density, s.bubbleSizeDp)

        when (state) {
            FloatingUiState.COLLAPSED -> {
                val targetX = if (s.edgeTuckEnabled) {
                    OverlayGeometry.getTuckedX(
                        isNearRightEdge = _isNearRightEdge.value,
                        bubbleSizeDp = s.bubbleSizeDp,
                        screenWidthPx = screenWidthPx,
                        density = density,
                        tuckPercent = s.edgeTuckPercent
                    )
                } else {
                    bubbleRestingX
                }
                bubbleRestingX = targetX
                lp.width = collapsedSizePx
                lp.height = collapsedSizePx
                lp.x = targetX
                lp.y = bubbleRestingY
            }
            FloatingUiState.EXPANDED -> {
                val expandedWidthPx = OverlayGeometry.getExpandedWidthPx(density, screenWidthPx, s.toolbarMaxWidthDp)
                val expandedHeightPx = OverlayGeometry.getExpandedHeightPx(density, s.toolbarHeightDp, s.bubbleSizeDp)

                lp.width = expandedWidthPx
                lp.height = expandedHeightPx
                lp.x = OverlayGeometry.calculateEdgeAnchoredX(
                    isNearRightEdge = _isNearRightEdge.value,
                    bubbleRestingX = bubbleRestingX,
                    collapsedWidthPx = collapsedSizePx,
                    targetWidthPx = expandedWidthPx,
                    screenWidthPx = screenWidthPx,
                    density = density
                )
                lp.y = bubbleRestingY
            }
            FloatingUiState.PREVIEW -> {
                val previewWidthPx = OverlayGeometry.getPreviewWidthPx(density, screenWidthPx)
                val previewHeightPx = OverlayGeometry.getPreviewHeightPx(density)

                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                lp.width = previewWidthPx
                lp.height = previewHeightPx
                lp.x = OverlayGeometry.calculateEdgeAnchoredX(
                    isNearRightEdge = _isNearRightEdge.value,
                    bubbleRestingX = bubbleRestingX,
                    collapsedWidthPx = collapsedSizePx,
                    targetWidthPx = previewWidthPx,
                    screenWidthPx = screenWidthPx,
                    density = density
                )
                lp.y = OverlayGeometry.clampY(bubbleRestingY, previewHeightPx, screenHeightPx, density)
            }
            FloatingUiState.TONE_SELECTOR, FloatingUiState.GEMINI_KEY_SETUP -> {
                // Accessibility overlays must always keep FLAG_NOT_FOCUSABLE to maintain stability,
                // prevent touch routing detachment, and keep the user's software keyboard open.
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                val panelWidthPx = OverlayGeometry.getMoreOptionsWidthPx(density, screenWidthPx)
                val panelHeightPx = OverlayGeometry.getMoreOptionsHeightPx(density)

                lp.width = panelWidthPx
                lp.height = panelHeightPx
                lp.x = OverlayGeometry.calculateEdgeAnchoredX(
                    isNearRightEdge = _isNearRightEdge.value,
                    bubbleRestingX = bubbleRestingX,
                    collapsedWidthPx = collapsedSizePx,
                    targetWidthPx = panelWidthPx,
                    screenWidthPx = screenWidthPx,
                    density = density
                )
                lp.y = OverlayGeometry.clampY(bubbleRestingY, panelHeightPx, screenHeightPx, density)
            }
        }
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "View not attached during layout update for state $state", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "View detached during layout update for state $state", e)
        }
    }

    fun canDrawOverlays(): Boolean {
        if (context is AccessibilityService) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun showAssistant() {
        if (!canDrawOverlays()) return
        if (!isViewAttached) {
            attachOverlayView()
        }
    }

    fun hideAssistant() {
        _activeCorrection.value = null
        _errorCount.value = 0
        _uiState.value = FloatingUiState.COLLAPSED
        detachOverlayView()
    }

    fun setChecking(checking: Boolean) {
        _isChecking.value = checking
    }

    private var lastValidTargetText: String = ""

    fun updateTextContext(
        snapshot: EditorSnapshot,
        onApplySentence: (String) -> TextOperationResult,
        onUndo: (() -> TextOperationResult)? = null,
        onInsert: ((String) -> TextOperationResult)? = null,
        onApplyFullText: ((String) -> TextOperationResult)? = null
    ) {
        _currentFullText.value = snapshot.fullText
        _currentSentence.value = snapshot.sentenceText
        val candidate = snapshot.fullText.ifBlank { snapshot.sentenceText }
        if (candidate.isNotBlank()) {
            lastValidTargetText = candidate
        }
        this.onApplySentenceCallback = onApplySentence
        this.onUndoCallback = onUndo
        this.onInsertCallback = onInsert
        this.onApplyFullTextCallback = onApplyFullText
    }

    fun showCorrections(
        corrections: List<Correction>,
        onAccept: (Correction) -> Unit,
        onAddToDictionary: ((Correction) -> Unit)? = null,
        onIgnoreRule: ((Correction) -> Unit)? = null
    ) {
        if (!canDrawOverlays()) return

        _activeCorrectionsList.value = corrections
        _activeCorrection.value = corrections.firstOrNull()
        _errorCount.value = corrections.size
        this.onAcceptSingleCorrectionCallback = onAccept
        this.onAddToDictionaryCallback = onAddToDictionary
        this.onIgnoreRuleCallback = onIgnoreRule

        if (!isViewAttached) {
            attachOverlayView()
        }
    }

    fun showCorrection(
        correction: Correction,
        totalErrorCount: Int = 1,
        onAccept: (Correction) -> Unit,
        onAddToDictionary: ((Correction) -> Unit)? = null,
        onIgnoreRule: ((Correction) -> Unit)? = null
    ) {
        showCorrections(
            corrections = listOf(correction),
            onAccept = onAccept,
            onAddToDictionary = onAddToDictionary,
            onIgnoreRule = onIgnoreRule
        )
    }

    fun acceptCorrection(correction: Correction) {
        val callback = onAcceptSingleCorrectionCallback ?: return

        overlayView?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        callback(correction)

        val currentList = _activeCorrectionsList.value
        val remaining = safeReplacementHelper.adjustOffsetsAfterReplacement(currentList, correction)
        _activeCorrectionsList.value = remaining
        _errorCount.value = remaining.size

        if (remaining.isNotEmpty()) {
            _activeCorrection.value = remaining.first()
        } else {
            _activeCorrection.value = null
            controllerScope.launch {
                delay(300L)
                if (_activeCorrection.value == null) {
                    setUiState(FloatingUiState.COLLAPSED)
                }
            }
        }
    }

    fun acceptCurrentCorrection() {
        val current = _activeCorrection.value ?: return
        acceptCorrection(current)
    }

    fun checkTextNow() {
        val targetText = getTargetText()
        if (targetText.isBlank()) return
        _isChecking.value = true
        controllerScope.launch {
            try {
                val app = WordPilotApp.instance
                val corrections = withContext(Dispatchers.Default) {
                    app.correctionCoordinator.checkText(targetText)
                }
                val valid = corrections.filter { correction ->
                    val isIgnored = app.dictionaryRepository.isRuleIgnored(correction.ruleId, correction.originalText)
                    val inDict = if (correction.category == CorrectionCategory.SPELLING) {
                        app.dictionaryRepository.wordExists(correction.originalText.lowercase())
                    } else false
                    !isIgnored && !inDict
                }
                _activeCorrectionsList.value = valid
                _activeCorrection.value = valid.firstOrNull()
                _errorCount.value = valid.size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error during grammar check", e)
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun dismissCurrentCorrection() {
        val current = _activeCorrection.value ?: return
        overlayView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        val currentList = _activeCorrectionsList.value
        val remaining = currentList.filter { it.id != current.id }
        _activeCorrectionsList.value = remaining
        _errorCount.value = remaining.size
        _activeCorrection.value = remaining.firstOrNull()

        if (remaining.isEmpty()) {
            setUiState(FloatingUiState.COLLAPSED)
        }
    }

    fun acceptAllCorrections() {
        val list = _activeCorrectionsList.value
        if (list.isEmpty()) return

        val targetText = getTargetText()
        val fixed = safeReplacementHelper.applyMultipleCorrections(targetText, list)
        if (fixed.isNotBlank()) {
            val callback = onApplyFullTextCallback ?: onApplySentenceCallback
            if (callback != null) {
                val result = callback(fixed)
                if (result is TextOperationResult.Success) {
                    _canUndo.value = true
                    Toast.makeText(context, "All errors corrected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        overlayView?.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        _activeCorrectionsList.value = emptyList()
        _activeCorrection.value = null
        _errorCount.value = 0

        controllerScope.launch {
            delay(500L)
            setUiState(FloatingUiState.COLLAPSED)
        }
    }

    fun clearCorrection() {
        _activeCorrection.value = null
        _activeCorrectionsList.value = emptyList()
        _errorCount.value = 0
        cachedTextCorrections = null
    }

    fun clearTextContext() {
        _currentFullText.value = ""
        _currentSentence.value = ""
        lastValidTargetText = ""
        clearCorrection()
    }

    fun dismiss() {
        clearCorrection()
        setUiState(FloatingUiState.COLLAPSED)
    }

    // Cached analysis results for instant preview
    private var cachedTextCorrections: Pair<String, List<Correction>>? = null

    fun setCachedCorrections(text: String, corrections: List<Correction>) {
        cachedTextCorrections = Pair(text, corrections)
    }

    // =========================================================================
    // ACTION DISPATCHERS & GEMINI HELPERS
    // =========================================================================

    fun isGeminiKeyMissing(): Boolean {
        return WordPilotApp.instance.cachedGeminiApiKey.isNullOrBlank()
    }

    fun saveGeminiApiKeyFromOverlay(key: String) {
        val cleanKey = com.wordpilot.assistant.engine.impl.GeminiAIEngine.sanitizeApiKey(key)
        WordPilotApp.instance.updateGeminiApiKey(cleanKey)
        Toast.makeText(context, "Gemini API key saved! Ready to rewrite.", Toast.LENGTH_SHORT).show()
        setUiState(FloatingUiState.TONE_SELECTOR)
    }

    fun onActionSelected(actionId: String) {
        when (actionId) {
            "gemini_setup" -> {
                setUiState(FloatingUiState.GEMINI_KEY_SETUP)
            }
            "rewrite_tone", "tones", "more" -> {
                if (isGeminiKeyMissing()) {
                setUiState(FloatingUiState.GEMINI_KEY_SETUP)
                } else {
                    setUiState(FloatingUiState.TONE_SELECTOR)
                }
            }
            "rewrite_professional" -> {
                executeRewrite(RewriteStyle.MAKE_PROFESSIONAL, "Professional")
            }
            "rewrite_casual" -> {
                executeRewrite(RewriteStyle.MAKE_CASUAL, "Casual & Friendly")
            }
            "rewrite_concise" -> {
                executeRewrite(RewriteStyle.MAKE_CONCISE, "Concise")
            }
            "rewrite_clarity" -> {
                executeRewrite(RewriteStyle.IMPROVE_CLARITY, "Clarity")
            }
            "rewrite_friendly", "rewrite_polite" -> {
                executeRewrite(RewriteStyle.MAKE_POLITE, "Polite")
            }
            "rewrite_polish", "fix_grammar", "punctuation" -> {
                executeRewrite(RewriteStyle.FIX_GRAMMAR, "Fix & Polish")
            }
            "settings" -> {
                openAppearanceSettings(Routes.BUBBLE_CUSTOMIZATION)
            }
            "back_to_expanded" -> {
                setUiState(FloatingUiState.EXPANDED)
            }
            else -> {
                executeRewrite(RewriteStyle.REWRITE, "Rewrite")
            }
        }
    }

    fun executeFixGrammar() {
        val targetText = getTargetText()
        if (targetText.isBlank()) {
            Toast.makeText(context, "No text detected to fix", Toast.LENGTH_SHORT).show()
            return
        }

        // Fast-path: If accessibility service already analyzed this text, show result instantly
        val cached = cachedTextCorrections
        if (cached != null && cached.first == targetText && cached.second.isNotEmpty()) {
            val fixed = safeReplacementHelper.applyMultipleCorrections(targetText, cached.second)
            if (fixed.isNotBlank()) {
                _previewOriginalText.value = targetText
                _previewImprovedText.value = fixed
                _previewErrorMessage.value = null
                _isPreviewLoading.value = false
                setUiState(FloatingUiState.PREVIEW)
                return
            }
        }

        val requestId = ++currentRequestId
        _previewOriginalText.value = targetText
        _previewImprovedText.value = ""
        _previewErrorMessage.value = null
        _isPreviewLoading.value = true
        setUiState(FloatingUiState.PREVIEW)

        controllerScope.launch {
            try {
                val app = WordPilotApp.instance
                val corrections = withContext(Dispatchers.Default) {
                    app.correctionCoordinator.checkText(targetText)
                }

                if (requestId != currentRequestId) return@launch

                var fixed = targetText
                if (corrections.isNotEmpty()) {
                    val replaced = safeReplacementHelper.applyMultipleCorrections(targetText, corrections)
                    if (replaced.isNotBlank()) {
                        fixed = replaced
                    }
                }

                if (requestId != currentRequestId) return@launch
                _previewImprovedText.value = fixed
                _isPreviewLoading.value = false
            } catch (e: CancellationException) {
                _isPreviewLoading.value = false
                throw e
            } catch (e: IOException) {
                if (requestId != currentRequestId) return@launch
                Log.w(TAG, "Network error in executeFixGrammar", e)
                _previewErrorMessage.value = "Network error: check connection"
                _isPreviewLoading.value = false
            } catch (e: SecurityException) {
                if (requestId != currentRequestId) return@launch
                Log.e(TAG, "Security error in executeFixGrammar", e)
                _previewErrorMessage.value = "Permission error"
                _isPreviewLoading.value = false
            } catch (e: IllegalStateException) {
                if (requestId != currentRequestId) return@launch
                Log.w(TAG, "Engine not ready in executeFixGrammar", e)
                _previewErrorMessage.value = "Engine not ready"
                _isPreviewLoading.value = false
            }
        }
    }

        fun executeRewrite(style: RewriteStyle, toneName: String = style.displayName, customPrompt: String? = null) {
        val targetText = getTargetText()
        if (targetText.isBlank()) {
            Toast.makeText(context, "No text detected to rewrite", Toast.LENGTH_SHORT).show()
            return
        }

        val requestId = ++currentRequestId
        _previewOriginalText.value = targetText
        _previewImprovedText.value = ""
        _previewErrorMessage.value = null
        _isPreviewLoading.value = true
        _currentTone.value = toneName
        setUiState(FloatingUiState.PREVIEW)

        controllerScope.launch {
            try {
                val app = WordPilotApp.instance
                val result = withContext(Dispatchers.Default) {
                    app.rewriteCoordinator.rewrite(
                        text = targetText,
                        style = style,
                        customInstruction = customPrompt
                    )
                }

                if (requestId != currentRequestId) return@launch
                if (result.isSuccess) {
                    _previewImprovedText.value = result.rewrittenText
                    _previewErrorMessage.value = null
                } else {
                    _previewErrorMessage.value = result.errorMessage ?: "Rewrite failed"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (requestId != currentRequestId) return@launch
                Log.w(TAG, "Network error in executeRewrite", e)
                _previewErrorMessage.value = "Network error: check connection"
            } catch (e: SecurityException) {
                if (requestId != currentRequestId) return@launch
                Log.e(TAG, "Security error in executeRewrite", e)
                _previewErrorMessage.value = "Permission error"
            } catch (e: IllegalStateException) {
                if (requestId != currentRequestId) return@launch
                Log.w(TAG, "Engine not ready in executeRewrite", e)
                _previewErrorMessage.value = "Engine not ready"
            } finally {
                if (requestId == currentRequestId) {
                    _isPreviewLoading.value = false
                }
            }
        }
    }

    fun onCustomPromptSubmit(prompt: String) {
        if (prompt.isBlank()) return
        executeRewrite(
            style = RewriteStyle.CUSTOM,
            toneName = "Custom Prompt",
            customPrompt = prompt
        )
    }

    fun onToneSelected(tone: String) {
        val style = when (tone.lowercase()) {
            "professional" -> RewriteStyle.MAKE_PROFESSIONAL
            "casual" -> RewriteStyle.MAKE_CASUAL
            "concise" -> RewriteStyle.MAKE_CONCISE
            "polite", "friendly" -> RewriteStyle.MAKE_POLITE
            "natural" -> RewriteStyle.IMPROVE_NATURAL
            "clarity" -> RewriteStyle.IMPROVE_CLARITY
            else -> RewriteStyle.REWRITE
        }
        executeRewrite(style = style, toneName = tone)
    }

    fun onSelectEngine(engine: AIEngineType) {
        val app = WordPilotApp.instance
        app.rewriteCoordinator.setEngineType(engine)
        controllerScope.launch {
            app.userPreferences.setSelectedEngine(engine.id)
        }
    }

    fun applyPreviewReplacement() {
        val improved = _previewImprovedText.value
        if (improved.isBlank()) return

        val callback = onApplyFullTextCallback ?: onApplySentenceCallback
        if (callback != null) {
            val result = callback(improved)
            when (result) {
                is TextOperationResult.Success -> {
                    _canUndo.value = true
                    setUiState(FloatingUiState.COLLAPSED)
                    clearCorrection()
                    Toast.makeText(context, "Text updated", Toast.LENGTH_SHORT).show()
                }
                is TextOperationResult.StaleSnapshot -> {
                    Toast.makeText(context, "Editor text was modified. Please review.", Toast.LENGTH_LONG).show()
                }
                is TextOperationResult.EditorModified -> {
                    Toast.makeText(context, "Editor was modified", Toast.LENGTH_SHORT).show()
                }
                is TextOperationResult.UnsupportedEditor -> {
                    copyPreviewText()
                    Toast.makeText(context, "Direct edit unsupported. Text copied to clipboard.", Toast.LENGTH_LONG).show()
                }
                is TextOperationResult.Failure -> {
                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            copyPreviewText()
        }
    }

        fun applyTextDirectly(text: String) {
        if (text.isBlank()) return
        val callback = onApplyFullTextCallback ?: onApplySentenceCallback
        if (callback != null) {
            val result = callback(text)
            when (result) {
                is TextOperationResult.Success -> {
                    _canUndo.value = true
                    setUiState(FloatingUiState.COLLAPSED)
                    clearCorrection()
                    Toast.makeText(context, "Applied", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Hoverpen", text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Hoverpen", text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun insertPreviewText() {
        val textToInsert = _previewImprovedText.value
        if (textToInsert.isBlank()) return

        val callback = onInsertCallback
        if (callback != null) {
            val result = callback(textToInsert)
            when (result) {
                is TextOperationResult.Success -> {
                    _canUndo.value = true
                    setUiState(FloatingUiState.COLLAPSED)
                    Toast.makeText(context, "Text inserted", Toast.LENGTH_SHORT).show()
                }
                is TextOperationResult.Failure -> {
                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                }
                else -> {
                    copyPreviewText()
                    Toast.makeText(context, "Direct insert unsupported. Copied to clipboard.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            copyPreviewText()
        }
    }

    fun copyPreviewText() {
        val textToCopy = _previewImprovedText.value.ifBlank { _previewOriginalText.value }
        if (textToCopy.isNotBlank()) {
            val clip = ClipData.newPlainText("Hoverpen Text", textToCopy)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun undoLastChange() {
        val callback = onUndoCallback
        if (callback != null) {
            val result = callback()
            when (result) {
                is TextOperationResult.Success -> {
                    _canUndo.value = false
                    Toast.makeText(context, "Change undone", Toast.LENGTH_SHORT).show()
                }
                is TextOperationResult.EditorModified -> {
                    _canUndo.value = false
                    Toast.makeText(context, "Cannot undo: editor text has changed", Toast.LENGTH_LONG).show()
                }
                is TextOperationResult.Failure -> {
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    _canUndo.value = false
                }
            }
        }
    }

    fun openAppearanceSettings(destination: String = Routes.AI_WINDOW_STUDIO) {
        setUiState(FloatingUiState.COLLAPSED)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", destination)
        }
        context.startActivity(intent)
    }

    fun getTargetText(): String {
        val direct = _currentFullText.value.ifBlank { _currentSentence.value }
        if (direct.isNotBlank()) {
            lastValidTargetText = direct
            return direct
        }
        // If attached to an active input field and the field is empty, do not fallback to stale text or clipboard
        if (onApplySentenceCallback != null || onApplyFullTextCallback != null) {
            return ""
        }
        if (lastValidTargetText.isNotBlank()) {
            return lastValidTargetText
        }
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString() ?: ""
                if (clipText.isNotBlank()) {
                    return clipText
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access clipboard without focus", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Clipboard in invalid state", e)
        }
        return ""
    }

    // =========================================================================
    // WINDOW & OVERLAY MANAGEMENT
    // =========================================================================

    fun onDrag(deltaX: Float, deltaY: Float) {
        val view = overlayView ?: return
        val lp = windowLayoutParams ?: return
        val (screenWidth, screenHeight) = getScreenDimensions()
        val safeBounds = computeSafeBounds(context)
        val density = context.resources.displayMetrics.density
        val s = appearanceSettings.value

        val minX = if (s.edgeTuckEnabled) {
            OverlayGeometry.getTuckedX(isNearRightEdge = false, s.bubbleSizeDp, screenWidth, density, s.edgeTuckPercent)
        } else {
            safeBounds.left
        }
        val maxX = if (s.edgeTuckEnabled) {
            OverlayGeometry.getTuckedX(isNearRightEdge = true, s.bubbleSizeDp, screenWidth, density, s.edgeTuckPercent)
        } else {
            (safeBounds.right - lp.width).coerceAtLeast(safeBounds.left)
        }
        val minY = safeBounds.top
        val maxY = (screenHeight - lp.height).coerceAtLeast(minY)

        val newX = (lp.x + deltaX.toInt()).coerceIn(minX, maxX)
        val newY = (lp.y - deltaY.toInt()).coerceIn(minY, maxY)

        lp.x = newX
        lp.y = newY
        bubbleRestingX = newX
        bubbleRestingY = newY
        _isNearRightEdge.value = newX > screenWidth / 2

        prefs.edit().putInt(KEY_X_PX, newX).putInt(KEY_Y_PX, newY).apply()

        try {
            windowManager.updateViewLayout(view, lp)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "View not attached during drag update", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "View in invalid state during drag update", e)
        }
    }

    fun onDragEnd() {
        val view = overlayView ?: return
        val lp = windowLayoutParams ?: return
        val (screenWidth, _) = getScreenDimensions()
        val density = context.resources.displayMetrics.density
        val s = appearanceSettings.value

        val isLeft = (lp.x + (lp.width / 2)) < screenWidth / 2
        val nearRight = !isLeft
        _isNearRightEdge.value = nearRight

        val targetX = if (s.edgeTuckEnabled) {
            OverlayGeometry.getTuckedX(
                isNearRightEdge = nearRight,
                bubbleSizeDp = s.bubbleSizeDp,
                screenWidthPx = screenWidth,
                density = density,
                tuckPercent = s.edgeTuckPercent
            )
        } else {
            val edgeMarginPx = (12 * density).toInt()
            if (isLeft) {
                edgeMarginPx
            } else {
                (screenWidth - lp.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            }
        }

        snapAnimator?.cancel()
        snapAnimator = android.animation.ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 240L
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val animatedX = animator.animatedValue as Int
                lp.x = animatedX
                bubbleRestingX = animatedX
                try {
                    windowManager.updateViewLayout(view, lp)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "View not attached during snap animation", e)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "View in invalid state during snap animation", e)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    lp.x = targetX
                    bubbleRestingX = targetX
                    _isNearRightEdge.value = targetX > screenWidth / 2
                    prefs.edit().putInt(KEY_X_PX, targetX).putInt(KEY_Y_PX, lp.y).apply()
                }
            })
            start()
        }
    }

    private fun attachOverlayView() {
        if (isViewAttached) return

        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val defaultMarginX = (16 * density).toInt()
        val defaultY = (340 * density).toInt()

        val savedX = prefs.getInt(KEY_X_PX, defaultMarginX)
        val savedY = prefs.getInt(KEY_Y_PX, defaultY)
        bubbleRestingX = savedX
        bubbleRestingY = savedY

        val (screenWidthPx, _) = getScreenDimensions()
        _isNearRightEdge.value = savedX > screenWidthPx / 2

        val s = appearanceSettings.value
        val collapsedSizePx = OverlayGeometry.getCollapsedSizePx(density, s.bubbleSizeDp)

        val overlayType = if (context is AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            collapsedSizePx,
            collapsedSizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val initialX = if (s.edgeTuckEnabled) {
                OverlayGeometry.getTuckedX(_isNearRightEdge.value, s.bubbleSizeDp, displayMetrics.widthPixels, density, s.edgeTuckPercent)
            } else {
                savedX
            }
            x = initialX
            y = savedY
            bubbleRestingX = initialX
        }
        windowLayoutParams = params

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(this@FloatingAssistantController.lifecycleOwner)
            setViewTreeViewModelStoreOwner(this@FloatingAssistantController.viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(this@FloatingAssistantController.savedStateRegistryOwner)

            setContent {
                val settings by appearanceSettings.collectAsState()
                val currentUiState by uiState.collectAsState()
                val nearRight by isNearRightEdge.collectAsState()
                val errCount by errorCount.collectAsState()
                val checking by isChecking.collectAsState()
                val activeCorr by activeCorrection.collectAsState()
                val allCorrections by activeCorrectionsList.collectAsState()

                val origText by _previewOriginalText.collectAsState()
                val imprText by _previewImprovedText.collectAsState()
                val prevLoading by _isPreviewLoading.collectAsState()
                val prevError by _previewErrorMessage.collectAsState()
                val undoAvailable by _canUndo.collectAsState()
                val tone by _currentTone.collectAsState()
                val activeEngine by WordPilotApp.instance.rewriteCoordinator.selectedEngine.collectAsState()

                val cardMode = when (currentUiState) {
                    FloatingUiState.PREVIEW -> CardMode.PREVIEW
                    FloatingUiState.TONE_SELECTOR -> CardMode.TONE
                    FloatingUiState.GEMINI_KEY_SETUP -> CardMode.GEMINI_KEY
                    else -> CardMode.CORRECTIONS
                }

                val uiModel = remember(
                    settings, currentUiState, nearRight, errCount, checking,
                    activeCorr, allCorrections, origText, imprText, prevLoading,
                    prevError, undoAvailable, tone, activeEngine
                ) {
                    FloatingUiModel(
                        settings = settings,
                        uiState = currentUiState,
                        bubble = BubbleState(
                            isNearRightEdge = nearRight,
                            errorCount = errCount,
                            isChecking = checking
                        ),
                        card = CardState(
                            isExpanded = currentUiState != FloatingUiState.COLLAPSED,
                            mode = cardMode,
                            preview = PreviewState(
                                originalText = origText,
                                improvedText = imprText,
                                currentTone = tone,
                                isLoading = prevLoading,
                                errorMessage = prevError
                            )
                        ),
                        engine = EngineInfo(
                            id = activeEngine.name,
                            displayName = activeEngine.displayName
                        ),
                        corrections = CorrectionList(
                            items = allCorrections,
                            activeCorrection = activeCorr,
                            activeCorrections = allCorrections.map { it.id }.toSet()
                        ),
                        undo = UndoState(canUndo = undoAvailable),
                        error = prevError?.let { UserFacingError.Generic(it) }
                    )
                }

                WordPilotTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = OverlayGeometry.CONTENT_PADDING_DP.dp, vertical = OverlayGeometry.CONTENT_PADDING_DP.dp),
                        contentAlignment = if (nearRight) Alignment.BottomEnd else Alignment.BottomStart
                    ) {
                        FloatingAssistantView(
                            model = uiModel,
                            onEvent = { event -> handleFloatingEvent(event) }
                        )
                    }
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(composeView) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val safe = computeSafeBounds(context)
            val availableHeight = safe.height() - ime
            if (_uiState.value != FloatingUiState.COLLAPSED) {
                windowLayoutParams?.let { lp ->
                    if (lp.height > availableHeight && availableHeight > 0) {
                        lp.height = availableHeight
                        try {
                            windowManager.updateViewLayout(v, lp)
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "View not attached on IME resize", e)
                        } catch (e: IllegalStateException) {
                            Log.w(TAG, "View detached on IME resize", e)
                        }
                    }
                }
            }
            insets
        }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            isViewAttached = true
            updateWindowLayoutForState(_uiState.value)
            Log.d(TAG, "Overlay attached on screen at x=$savedX, y=$savedY")
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "Bad token while adding overlay — service may be stopping", e)
            isViewAttached = false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Overlay view already attached", e)
            isViewAttached = true
        }
    }

    private fun detachOverlayView() {
        if (!isViewAttached || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view not attached to window manager", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Overlay view already detached", e)
        } finally {
            overlayView = null
            isViewAttached = false
        }
    }

    fun handleFloatingEvent(event: FloatingEvent) {
        when (event) {
            is FloatingEvent.OnBubbleClick -> {
                if (_uiState.value == FloatingUiState.COLLAPSED) {
                    setUiState(FloatingUiState.EXPANDED)
                    if (_activeCorrectionsList.value.isEmpty() && getTargetText().isNotBlank()) {
                        checkTextNow()
                    }
                } else {
                    setUiState(FloatingUiState.COLLAPSED)
                }
            }
            is FloatingEvent.OnBubbleLongClick -> {
                setUiState(FloatingUiState.COLLAPSED)
            }
            is FloatingEvent.OnDrag -> onDrag(event.dx, event.dy)
            is FloatingEvent.OnDragEnd -> onDragEnd()
            is FloatingEvent.OnAccept -> {
                val corr = _activeCorrectionsList.value.firstOrNull { it.id == event.correctionId }
                    ?: _activeCorrection.value
                if (corr != null) acceptCorrection(corr)
            }
            is FloatingEvent.OnDismiss -> dismissCurrentCorrection()
            is FloatingEvent.OnAcceptAll -> acceptAllCorrections()
            is FloatingEvent.OnReviewAll -> executeFixGrammar()
            is FloatingEvent.OnUndo -> undoLastChange()
            is FloatingEvent.OnReplace -> applyPreviewReplacement()
            is FloatingEvent.OnInsert -> insertPreviewText()
            is FloatingEvent.OnCopy -> copyPreviewText()
            is FloatingEvent.OnCancelPreview -> setUiState(FloatingUiState.COLLAPSED)
            is FloatingEvent.OnOpenSettings -> openAppearanceSettings()
            is FloatingEvent.OnCollapse -> setUiState(FloatingUiState.COLLAPSED)
            is FloatingEvent.OnCollapseTransitionCompleted -> onCollapseTransitionCompleted()
            is FloatingEvent.OnActionClick -> onActionSelected(event.actionId)
            is FloatingEvent.OnCustomPromptSubmit -> onCustomPromptSubmit(event.prompt)
            is FloatingEvent.OnSelectTone -> onToneSelected(event.tone)
            is FloatingEvent.OnSelectToneOption -> executeRewrite(event.style, event.title)
            is FloatingEvent.OnSelectEngine -> onSelectEngine(event.engineType)
            is FloatingEvent.OnGeminiKeySaved -> saveGeminiApiKeyFromOverlay(event.key)
            is FloatingEvent.OnHide1h -> hideForDuration(3600_000L)
            is FloatingEvent.OnHideUntilTomorrow -> hideForDuration(86400_000L)
            is FloatingEvent.OnTurnOff -> hideAssistant()
        }
    }

    fun hideForDuration(durationMs: Long) {
        hideAssistant()
        mainHandler.postDelayed({
            showAssistant()
        }, durationMs)
    }

    fun destroy() {
        hideAssistant()
        controllerScope.cancel()
        context.getSharedPreferences(AppearanceSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(appearancePrefsListener)
    }
}
