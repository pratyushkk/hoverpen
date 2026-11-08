package com.wordpilot.assistant.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Debouncer(
    private val scope: CoroutineScope,
    private val defaultDelayMs: Long = 300L
) {
    private var job: Job? = null

    fun debounce(customDelayMs: Long? = null, action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(customDelayMs ?: defaultDelayMs)
            action()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
