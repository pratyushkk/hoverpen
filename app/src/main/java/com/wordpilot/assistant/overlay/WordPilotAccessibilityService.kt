package com.wordpilot.assistant.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.wordpilot.assistant.WordPilotApp
import com.wordpilot.assistant.domain.correction.SafeReplacementHelper
import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.CorrectionCategory
import com.wordpilot.assistant.domain.models.EditorSnapshot
import com.wordpilot.assistant.domain.models.TextOperationResult
import com.wordpilot.assistant.engine.SentenceSpan
import com.wordpilot.assistant.ui.floating.FloatingUiState
import com.wordpilot.assistant.utils.Debouncer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class WordPilotAccessibilityService : AccessibilityService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        private const val TAG = "WordPilotA11y"
        var instance: WordPilotAccessibilityService? = null
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        when (throwable) {
            is CancellationException -> Unit
            is IOException -> Log.w(TAG, "Network hiccup in service scope", throwable)
            else -> Log.e(TAG, "Unexpected failure in service scope", throwable)
        }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + serviceExceptionHandler)
    private val debouncer = Debouncer(serviceScope, 220L)
    private val safeReplacementHelper = SafeReplacementHelper()

    data class UndoRecord(
        val fullTextBefore: String,
        val fullTextAfter: String,
        val selectionBefore: Int
    )

    var floatingController: FloatingAssistantController? = null
        private set
    val isControllerReady: Boolean get() = floatingController != null

    var isAssistantPaused: Boolean = false
        private set

    fun setAssistantPaused(paused: Boolean) {
        isAssistantPaused = paused
        if (paused) {
            floatingController?.hideAssistant()
            floatingController?.clearCorrection()
        } else {
            floatingController?.showAssistant()
        }
    }

    private var lastAnalyzedText: String = ""
    private var hideJob: Job? = null
    private var lastUndoRecord: UndoRecord? = null
    private var lastActiveSentenceSpan: SentenceSpan? = null
    private var currentSnapshot: EditorSnapshot? = null
    private var isAutoPauseBankingEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "onCreate: WordPilotAccessibilityService started")
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        serviceScope.launch {
            try {
                WordPilotApp.instance.userPreferences.autoPauseBankingEnabled.collect {
                    isAutoPauseBankingEnabled = it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error collecting autoPauseBankingEnabled", e)
            }
        }

        floatingController = FloatingAssistantController(
            context = this,
            lifecycleOwner = this,
            viewModelStoreOwner = this,
            savedStateRegistryOwner = this
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "onServiceConnected: Configuring AccessibilityServiceInfo")
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_FOCUSED or
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.notificationTimeout = 50
        serviceInfo = info

        val controller = floatingController
        val autoHide = controller?.appearanceSettings?.value?.autoHideWhenKeyboardClosed ?: true
        if (!isAssistantPaused && (!autoHide || isKeyboardVisible())) {
            serviceScope.launch(Dispatchers.Main) {
                controller?.showAssistant()
            }
        }
        com.wordpilot.assistant.widget.WordPilotWidgetProvider.updateAllWidgets(this, true)
    }

    /**
     * Intercepts system back key presses. When the floating window is expanded
     * (tone selector, preview diff, or expanded pill), collapses it back to the small pill.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            val controller = floatingController
            if (controller != null && controller.isExpanded()) {
                if (event.action == KeyEvent.ACTION_UP) {
                    controller.setUiState(FloatingUiState.COLLAPSED)
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun isExcludedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()

        // Allow WordPilot itself and System UI / IME
        if (lower.startsWith("com.wordpilot.") || lower == "android" || lower.startsWith("com.android.systemui")) {
            return false
        }

        // 1. Known Banking, Payment & UPI Apps
        if (lower.startsWith("com.google.android.apps.nbu.paisa.user") || // GPay
            lower.startsWith("com.phonepe.") || // PhonePe
            lower.startsWith("net.one97.paytm") || // Paytm
            lower.startsWith("in.org.npci.upiapp") || // BHIM
            lower.startsWith("com.dreamplug.androidapp") || // CRED
            lower.startsWith("com.sbi.") || // SBI / YONO
            lower.startsWith("com.snapwork.sbi") ||
            lower.startsWith("com.hdfcbank.") || // HDFC
            lower.startsWith("com.hdfc.payzapp") ||
            lower.startsWith("com.csam.icici.bank.imobile") || // ICICI
            lower.startsWith("com.axis.mobile") || // Axis
            lower.startsWith("com.kotak.") || // Kotak
            lower.startsWith("com.bankofbaroda.") || // BoB
            lower.startsWith("com.pnb.pnbone") || // PNB
            lower.startsWith("com.canarabank.") || // Canara
            lower.startsWith("com.unionbank.") || // Union Bank
            lower.startsWith("com.idfcfirstbank.") || // IDFC First
            lower.startsWith("com.indusind.") || // IndusInd
            lower.startsWith("com.rblbank.") || // RBL
            lower.startsWith("com.federalbank.") || // Federal
            lower.startsWith("com.yesbank") || // Yes Bank
            lower.startsWith("com.freecharge.") || // Freecharge
            lower.startsWith("com.mobikwik_new") || // Mobikwik
            lower.startsWith("com.fi.money") || // Fi Money
            lower.startsWith("money.jupiter") || // Jupiter
            lower.startsWith("com.zerodha.kite3") || // Zerodha
            lower.startsWith("com.nextbillion.groww") || // Groww
            lower.startsWith("com.upstox.pro") || // Upstox
            lower.startsWith("com.angelbroking.") || // Angel One
            lower.startsWith("com.chase.sig.android") || // Chase
            lower.startsWith("com.infonow.bofa") || // Bank of America
            lower.startsWith("com.wf.wellsfargomobile") || // Wells Fargo
            lower.startsWith("com.citi.citimobile") || // Citi
            lower.startsWith("com.barclays.") || // Barclays
            lower.startsWith("uk.co.hsbc.") || // HSBC
            lower.startsWith("com.paypal.") || // PayPal
            lower.startsWith("com.revolut.") || // Revolut
            lower.startsWith("com.squareup.cash") // Cash App
        ) {
            return true
        }

        // 2. Password Managers, 2FA Authenticators & Wallets
        if (lower.startsWith("com.google.android.apps.authenticator2") ||
            lower.startsWith("com.azure.authenticator") ||
            lower.startsWith("com.x8bit.bitwarden") ||
            lower.startsWith("com.onepassword.") ||
            lower.startsWith("com.lastpass.") ||
            lower.startsWith("com.dashlane") ||
            lower.startsWith("com.authy.authy")
        ) {
            return true
        }

        // 3. Keyword heuristics for unlisted banking and payment apps
        if (lower.contains("banking") ||
            lower.contains(".bank") ||
            lower.contains("yono") ||
            lower.contains("paytm") ||
            lower.contains("phonepe") ||
            lower.contains("authenticator") ||
            lower.contains("password")
        ) {
            return true
        }

        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (isAssistantPaused) {
            hideJob?.cancel()
            floatingController?.hideAssistant()
            floatingController?.clearCorrection()
            return
        }

        val packageName = event.packageName?.toString()
        if (isExcludedPackage(packageName)) {
            hideJob?.cancel()
            floatingController?.hideAssistant()
            floatingController?.clearCorrection()

            if (isAutoPauseBankingEnabled) {
                Toast.makeText(this, "Hoverpen paused for banking app. Tap Quick Tile to re-enable.", Toast.LENGTH_LONG).show()
                disableSelf()
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextInputEvent(event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val changeTypes = event.contentChangeTypes
                val isTextRelated = (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0 ||
                    (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) != 0 ||
                    changeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
                if (isTextRelated) {
                    handleTextInputEvent(event)
                }
            }
        }
    }

    private fun handleTextInputEvent(event: AccessibilityEvent) {
        val controller = floatingController ?: return

        val packageName = event.packageName?.toString()
        if (isExcludedPackage(packageName)) {
            hideJob?.cancel()
            controller.hideAssistant()
            controller.clearCorrection()
            return
        }

        val isInteracting = controller.isInteracting()
        val autoHide = controller.appearanceSettings.value.autoHideWhenKeyboardClosed
        val isTyping = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        if (!isInteracting && autoHide && !isKeyboardVisible() && !isTyping) {
            controller.hideAssistant()
            return
        }

        val node = findActiveInputNode(event)

        if (node != null && !node.isPassword) {
            // Cancel any pending dismissal
            hideJob?.cancel()

            // Keep assistant visible on screen
            controller.showAssistant()

            val text = extractNodeText(node)
            if (text.isBlank()) {
                controller.clearTextContext()
                lastAnalyzedText = ""
                return
            }

            val selStart = node.textSelectionStart
            val selEnd = node.textSelectionEnd
            val cursor = if (selStart in 0..text.length) selStart else text.length

            val app = WordPilotApp.instance
            val sentence = app.sentenceAnalyzer.findCurrentSentence(text, cursor)
                ?: SentenceSpan(0, text.length, text)
            lastActiveSentenceSpan = sentence

            val snapshot = EditorSnapshot(
                packageName = node.packageName?.toString(),
                fieldIdentifier = node.viewIdResourceName,
                fullText = text,
                selectionStart = selStart,
                selectionEnd = selEnd,
                sentenceStart = sentence.start,
                sentenceEnd = sentence.end,
                sentenceText = sentence.text,
                capturedAt = System.currentTimeMillis()
            )
            currentSnapshot = snapshot

            controller.updateTextContext(
                snapshot = snapshot,
                onApplySentence = { newSentence ->
                    applySentenceReplacementToNode(node, snapshot, newSentence)
                },
                onUndo = {
                    undoLastReplacement(node)
                },
                onInsert = { textToInsert ->
                    insertTextToNode(node, snapshot, textToInsert)
                },
                onApplyFullText = { fullText ->
                    applyFullTextReplacementToNode(node, fullText)
                }
            )

            if (text != lastAnalyzedText) {
                Log.d(TAG, "Text changed in ${event.packageName} (len=${text.length})")
                val isWordEnding = text.endsWith(" ") || text.endsWith(".") || text.endsWith(",") || text.endsWith("?") || text.endsWith("!")
                debouncer.debounce(if (isWordEnding) 80L else 220L) {
                    analyzeText(text, node)
                }
            }
        } else {
            if (!isInteracting && autoHide) {
                controller.hideAssistant()
            } else if (!isInteracting) {
                controller.clearCorrection()
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val controller = floatingController ?: return

        val packageName = event.packageName?.toString()
        if (isExcludedPackage(packageName)) {
            hideJob?.cancel()
            controller.hideAssistant()
            controller.clearCorrection()

            if (isAutoPauseBankingEnabled) {
                Toast.makeText(this, "Hoverpen paused for banking app. Tap Quick Tile to re-enable.", Toast.LENGTH_LONG).show()
                disableSelf()
            }
            return
        }

        val isInteracting = controller.isInteracting()
        val autoHide = controller.appearanceSettings.value.autoHideWhenKeyboardClosed
        if (autoHide && !isInteracting) {
            if (!isKeyboardVisible()) {
                // Keyboard is not visible: hide immediately with brief debounce
                hideJob?.cancel()
                hideJob = serviceScope.launch {
                    delay(120L)
                    if (!isKeyboardVisible() && !controller.isInteracting()) {
                        controller.hideAssistant()
                    }
                }
            } else {
                val node = findActiveInputNodeAcrossAllWindows()
                if (node != null && !node.isPassword) {
                    hideJob?.cancel()
                    controller.showAssistant()
                } else if (!controller.isInteracting()) {
                    controller.hideAssistant()
                }
            }
        } else {
            hideJob?.cancel()
            controller.showAssistant()
        }
    }

    private fun isKeyboardVisible(): Boolean {
        return try {
            val winList = windows
            if (winList.isNullOrEmpty()) return false
            winList.any { win ->
                if (win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val bounds = android.graphics.Rect()
                    win.getBoundsInScreen(bounds)
                    bounds.height() > 150 && bounds.width() > 200
                } else {
                    false
                }
            }
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun findActiveInputNode(event: AccessibilityEvent): AccessibilityNodeInfo? {
        // Priority 1: Direct event source if editable
        val source = event.source
        if (source != null && isEditableNode(source)) {
            return source
        }

        // Priority 2: Check active window / interactive windows
        return findActiveInputNodeAcrossAllWindows()
    }

    private fun findActiveInputNodeAcrossAllWindows(): AccessibilityNodeInfo? {
        // 1. Search rootInActiveWindow
        try {
            rootInActiveWindow?.let { root ->
                if (isExcludedPackage(root.packageName?.toString())) return null
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (isEditableNode(it)) return it }
                findFocusedEditable(root)?.let { return it }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Window detached while checking rootInActiveWindow", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error checking rootInActiveWindow", e)
        }

        // 2. Search all interactive non-IME windows
        try {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                val root = window.root ?: continue
                if (isExcludedPackage(root.packageName?.toString())) continue
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (isEditableNode(it)) return it }
                findFocusedEditable(root)?.let { return it }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Window detached while searching windows", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error searching windows", e)
        }

        return null
    }

    private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return false
        return node.isEditable ||
            node.className?.contains("EditText", ignoreCase = true) == true ||
            node.className?.contains("AutoCompleteTextView", ignoreCase = true) == true ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && isEditableNode(node)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun isCommonPlaceholder(text: String): Boolean {
        val trimmed = text.trim().lowercase()
        return trimmed in listOf(
            "message", "message...", "type a message", "type a message...",
            "search", "search...", "send a message", "send a message...",
            "add a comment", "add a comment...", "comment", "comment...",
            "write a reply", "write a reply...", "reply", "reply...",
            "write a message", "write a message..."
        ) || trimmed.startsWith("message ") || trimmed.startsWith("type a message")
    }

    private fun extractNodeText(node: AccessibilityNodeInfo): String {
        val hintText = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()
        } else null) ?: AccessibilityNodeInfoCompat.wrap(node).hintText?.toString()

        val rawText = node.text?.toString()

        // 1. If rawText is present and non-empty
        if (!rawText.isNullOrEmpty()) {
            val matchesHint = !hintText.isNullOrEmpty() && rawText.trim().equals(hintText.trim(), ignoreCase = true)
            val isHintFlagged = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText) ||
                AccessibilityNodeInfoCompat.wrap(node).isShowingHintText

            // Only reject if rawText explicitly matches the hint text or is flagged as placeholder
            if (matchesHint || (isHintFlagged && isCommonPlaceholder(rawText))) {
                return ""
            }
            return rawText
        }

        // 2. For composite containers or custom views (e.g. Instagram, WhatsApp), inspect children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val childHint = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    child.hintText?.toString()
                } else null) ?: AccessibilityNodeInfoCompat.wrap(child).hintText?.toString()

                val childText = child.text?.toString()
                if (!childText.isNullOrEmpty()) {
                    val matchesChildHint = !childHint.isNullOrEmpty() && childText.trim().equals(childHint.trim(), ignoreCase = true)
                    val matchesParentHint = !hintText.isNullOrEmpty() && childText.trim().equals(hintText.trim(), ignoreCase = true)
                    if (matchesChildHint || matchesParentHint || isCommonPlaceholder(childText)) {
                        continue
                    }
                    return childText
                }
            } catch (_: IllegalStateException) {} catch (_: SecurityException) {}
        }

        // 3. Fallback to contentDescription if rawText is empty, but protect against placeholders
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrEmpty()) {
            val matchesHint = !hintText.isNullOrEmpty() && contentDesc.trim().equals(hintText.trim(), ignoreCase = true)
            if (!matchesHint && !isCommonPlaceholder(contentDesc)) {
                return contentDesc
            }
        }

        return ""
    }

    private suspend fun analyzeText(text: String, targetNode: AccessibilityNodeInfo) = withContext(Dispatchers.Default) {
        val app = WordPilotApp.instance

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                floatingController?.setChecking(true)
            }

            val corrections = app.correctionCoordinator.checkText(text)

            val validCorrections = corrections.filter { correction ->
                val isIgnored = app.dictionaryRepository.isRuleIgnored(correction.ruleId, correction.originalText)
                val inDict = if (correction.category == CorrectionCategory.SPELLING) {
                    app.dictionaryRepository.wordExists(correction.originalText.lowercase())
                } else false
                !isIgnored && !inDict
            }

            val cursor = try {
                targetNode.textSelectionStart.takeIf { it in 0..text.length } ?: text.length
            } catch (e: IllegalStateException) {
                text.length
            } catch (e: SecurityException) {
                text.length
            }
            val currentSentenceSpan = lastActiveSentenceSpan ?: app.sentenceAnalyzer.findCurrentSentence(text, cursor)
            val sortedCorrections = validCorrections.sortedWith(
                compareBy<Correction> { correction ->
                    if (currentSentenceSpan != null && correction.startOffset >= currentSentenceSpan.start && correction.endOffset <= currentSentenceSpan.end) 0 else 1
                }.thenBy { correction ->
                    kotlin.math.abs(correction.startOffset - cursor)
                }
            )

            withContext(Dispatchers.Main) {
                lastAnalyzedText = text
                val controller = floatingController
                controller?.setChecking(false)
                controller?.setCachedCorrections(text, sortedCorrections)
                val autoHide = controller?.appearanceSettings?.value?.autoHideWhenKeyboardClosed ?: true
                if (autoHide && !isKeyboardVisible()) {
                    controller?.hideAssistant()
                } else if (sortedCorrections.isNotEmpty()) {
                    Log.d(TAG, "Showing corrections (total: ${sortedCorrections.size}, top rule: ${sortedCorrections.first().ruleId})")
                    controller?.showCorrections(
                        corrections = sortedCorrections,
                        onAccept = { correction ->
                            applyCorrectionToNode(targetNode, text, correction)
                        },
                        onAddToDictionary = { correction ->
                            serviceScope.launch {
                                app.dictionaryRepository.addWord(correction.originalText.lowercase())
                                app.spellEngine.addWord(correction.originalText.lowercase())
                            }
                        },
                        onIgnoreRule = { correction ->
                            serviceScope.launch {
                                app.dictionaryRepository.ignoreRule(correction.ruleId, correction.originalText)
                            }
                        }
                    )
                } else {
                    controller?.clearCorrection()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Network error analyzing text", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception analyzing text", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Node or state error analyzing text", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error analyzing text", e)
        } finally {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                floatingController?.setChecking(false)
            }
        }
    }

    private fun applyCorrectionToNode(
        targetNode: AccessibilityNodeInfo,
        originalFullText: String,
        correction: Correction
    ) {
        try {
            // Find the most fresh node instance across windows
            val node = findActiveInputNodeAcrossAllWindows() ?: targetNode
            val nodePkg = node.packageName?.toString()
            if (isExcludedPackage(nodePkg)) {
                Log.w(TAG, "Aborting correction: target package is excluded: $nodePkg")
                floatingController?.clearCorrection()
                return
            }

            val currentText = extractNodeText(node).ifBlank { originalFullText }
            val applicableCorrection = safeReplacementHelper.findApplicableCorrection(currentText, correction) ?: run {
                Log.w(TAG, "Aborting correction: unable to find applicable span in current text")
                return
            }

            val updatedText = safeReplacementHelper.applyCorrection(currentText, applicableCorrection) ?: run {
                Log.w(TAG, "Aborting correction: unable to apply replacement safely to current text")
                return
            }

            Log.d(TAG, "Applying correction at [${applicableCorrection.startOffset}, ${applicableCorrection.endOffset}) -> updatedText: '$updatedText'")
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "ACTION_SET_TEXT result: $success")

            if (success) {
                lastAnalyzedText = updatedText
                // Keep cursor at the end of the replaced word on a fresh node after UI processes text update
                val replacementLength = applicableCorrection.suggestedReplacement?.length ?: 0
                val newCursor = (applicableCorrection.startOffset + replacementLength).coerceIn(0, updatedText.length)
                serviceScope.launch(Dispatchers.Main) {
                    delay(80L)
                    val freshNode = findActiveInputNodeAcrossAllWindows()
                    if (freshNode != null) {
                        val cursorArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                        }
                        freshNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Node detached before replace — dropping suggestion", e)
            floatingController?.setChecking(false)
        } catch (e: SecurityException) {
            Log.e(TAG, "Accessibility revoked mid-operation", e)
            stopSelf()
        }
    }

    private fun applyFullTextReplacementToNode(
        targetNode: AccessibilityNodeInfo,
        fullText: String
    ): TextOperationResult {
        return try {
            val node = findActiveInputNodeAcrossAllWindows() ?: targetNode
            val nodePkg = node.packageName?.toString()
            if (isExcludedPackage(nodePkg)) {
                Log.w(TAG, "Aborting full text replacement: target package is excluded: $nodePkg")
                return TextOperationResult.UnsupportedEditor
            }

            val currentText = extractNodeText(node)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fullText)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                lastAnalyzedText = fullText
                serviceScope.launch(Dispatchers.Main) {
                    delay(80L)
                    val freshNode = findActiveInputNodeAcrossAllWindows()
                    if (freshNode != null) {
                        val cursorArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, fullText.length)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, fullText.length)
                        }
                        freshNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                    }
                }
                lastUndoRecord = UndoRecord(
                    fullTextBefore = currentText,
                    fullTextAfter = fullText,
                    selectionBefore = currentText.length
                )
                TextOperationResult.Success
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Hoverpen Text", fullText))
                TextOperationResult.Failure("Field does not support direct replacement. Text copied to clipboard.")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Node detached before replace — dropping suggestion", e)
            floatingController?.setChecking(false)
            TextOperationResult.Failure("Field detached before text replacement")
        } catch (e: SecurityException) {
            Log.e(TAG, "Accessibility revoked mid-operation", e)
            stopSelf()
            TextOperationResult.Failure("Accessibility permission revoked")
        }
    }

    private fun applySentenceReplacementToNode(
        targetNode: AccessibilityNodeInfo,
        snapshot: EditorSnapshot,
        newSentence: String
    ): TextOperationResult {
        return try {
            val node = findActiveInputNodeAcrossAllWindows() ?: targetNode
            val nodePkg = node.packageName?.toString()
            if (isExcludedPackage(nodePkg)) {
                Log.w(TAG, "Aborting sentence replacement: target package is excluded: $nodePkg")
                return TextOperationResult.UnsupportedEditor
            }

            val currentText = extractNodeText(node).ifBlank { snapshot.fullText }
            val isFullFieldTarget = snapshot.sentenceText == snapshot.fullText || 
                (snapshot.sentenceStart == 0 && snapshot.sentenceEnd >= currentText.length)

            val updatedText = if (isFullFieldTarget) {
                newSentence
            } else {
                safeReplacementHelper.replaceSentenceWithSnapshot(currentText, snapshot, newSentence) ?: newSentence
            }

            Log.d(TAG, "Applying sentence replacement at [${snapshot.sentenceStart}, ${snapshot.sentenceEnd})")
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "ACTION_SET_TEXT result: $success")

            if (success) {
                lastAnalyzedText = updatedText
                val newCursor = (snapshot.sentenceStart + newSentence.length).coerceIn(0, updatedText.length)
                serviceScope.launch(Dispatchers.Main) {
                    delay(80L)
                    val freshNode = findActiveInputNodeAcrossAllWindows()
                    if (freshNode != null) {
                        val cursorArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                        }
                        freshNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                    }
                }
                lastUndoRecord = UndoRecord(
                    fullTextBefore = currentText,
                    fullTextAfter = updatedText,
                    selectionBefore = snapshot.cursorPosition
                )
                TextOperationResult.Success
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Hoverpen Text", newSentence))
                TextOperationResult.Failure("Field does not support direct replacement. Text copied to clipboard.")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Node detached before replace — dropping suggestion", e)
            floatingController?.setChecking(false)
            TextOperationResult.Failure("Field detached before text replacement")
        } catch (e: SecurityException) {
            Log.e(TAG, "Accessibility revoked mid-operation", e)
            stopSelf()
            TextOperationResult.Failure("Accessibility permission revoked")
        }
    }

    private fun undoLastReplacement(targetNode: AccessibilityNodeInfo): TextOperationResult {
        return try {
            val record = lastUndoRecord ?: return TextOperationResult.Failure("No replacement to undo")
            val node = findActiveInputNodeAcrossAllWindows() ?: targetNode
            val currentText = extractNodeText(node)
            if (currentText.isNotEmpty() && currentText != record.fullTextAfter) {
                Log.w(TAG, "Cannot undo: text has been modified since replacement")
                return TextOperationResult.EditorModified
            }

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, record.fullTextBefore)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                val cursorArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, record.selectionBefore)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, record.selectionBefore)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                lastAnalyzedText = record.fullTextBefore
                lastUndoRecord = null
                TextOperationResult.Success
            } else {
                TextOperationResult.Failure("Failed to restore text")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Node detached before undo", e)
            TextOperationResult.Failure("Field detached before undo")
        } catch (e: SecurityException) {
            Log.e(TAG, "Accessibility revoked mid-operation", e)
            stopSelf()
            TextOperationResult.Failure("Accessibility permission revoked")
        }
    }

    private fun insertTextToNode(
        targetNode: AccessibilityNodeInfo,
        snapshot: EditorSnapshot,
        textToInsert: String
    ): TextOperationResult {
        return try {
            val node = findActiveInputNodeAcrossAllWindows() ?: targetNode
            val nodePkg = node.packageName?.toString()
            if (isExcludedPackage(nodePkg)) {
                Log.w(TAG, "Aborting insertText: target package is excluded: $nodePkg")
                return TextOperationResult.UnsupportedEditor
            }
            val currentText = extractNodeText(node).ifBlank { snapshot.fullText }
            val selStart = if (node.textSelectionStart >= 0) node.textSelectionStart else snapshot.selectionStart
            val selEnd = if (node.textSelectionEnd >= 0) node.textSelectionEnd else snapshot.selectionEnd
            val (updatedText, newCursor) = safeReplacementHelper.insertAtCursor(currentText, textToInsert, selStart, selEnd)

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (success) {
                val cursorArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                lastAnalyzedText = updatedText
                lastUndoRecord = UndoRecord(
                    fullTextBefore = currentText,
                    fullTextAfter = updatedText,
                    selectionBefore = selStart.coerceIn(0, currentText.length)
                )
                TextOperationResult.Success
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Hoverpen Text", textToInsert))
                TextOperationResult.Failure("Field does not support direct insertion. Text copied to clipboard.")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Node detached before insert", e)
            TextOperationResult.Failure("Field detached before insert")
        } catch (e: SecurityException) {
            Log.e(TAG, "Accessibility revoked mid-operation", e)
            stopSelf()
            TextOperationResult.Failure("Accessibility permission revoked")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: service interrupted")
        floatingController?.hideAssistant()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        com.wordpilot.assistant.widget.WordPilotWidgetProvider.updateAllWidgets(this, false)
        Log.d(TAG, "onDestroy: service destroyed")
        floatingController?.destroy()
        floatingController = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
    }
}
