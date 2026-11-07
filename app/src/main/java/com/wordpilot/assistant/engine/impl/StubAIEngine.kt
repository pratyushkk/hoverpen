package com.wordpilot.assistant.engine.impl

import com.wordpilot.assistant.domain.models.Correction
import com.wordpilot.assistant.domain.models.ModelInfo
import com.wordpilot.assistant.domain.models.ModelState
import com.wordpilot.assistant.domain.models.ResourceUsage
import com.wordpilot.assistant.domain.models.RewriteResult
import com.wordpilot.assistant.domain.models.RewriteStyle
import com.wordpilot.assistant.engine.LocalAIEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubAIEngine : LocalAIEngine {
    
    private val _modelState = MutableStateFlow(ModelState.NOT_INSTALLED)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    override val isModelInstalled: Boolean = false

    override val modelInfo: ModelInfo = ModelInfo(
        name = "Hoverpen AI (Not configured)",
        source = "No model configured",
        license = "N/A",
        downloadSizeMb = 0,
        installedSizeMb = 0,
        isInstalled = false
    )

    override suspend fun loadModel() {
        // Stub: no-op
    }

    override suspend fun unloadModel() {
        // Stub: no-op
    }

    override suspend fun rewrite(text: String, style: RewriteStyle, customInstruction: String?): RewriteResult {
        return RewriteResult(
            originalText = text,
            rewrittenText = text,
            style = style,
            isSuccess = false,
            errorMessage = "AI model is not installed. This is a stub implementation. " +
                "Install an AI model from Settings → AI Model to enable rewriting features."
        )
    }

    override suspend fun explainErrors(text: String, corrections: List<Correction>): String {
        return "AI model is not installed. Grammar explanations require an installed AI model."
    }

    override fun cancelInference() {
        // Stub: no-op
    }

    override fun getResourceUsage(): ResourceUsage {
        return ResourceUsage()
    }
}
