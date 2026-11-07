package com.wordpilot.assistant.data.repository

import android.util.Log
import com.wordpilot.assistant.domain.models.ModelInfo
import com.wordpilot.assistant.domain.models.ModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelRepositoryImpl(private val modelDir: File) {

    companion object {
        private const val TAG = "ModelRepository"
        const val MODEL_FILENAME = "llama-3.2-1b-instruct-q4_k_m.gguf"
        const val MODEL_NAME = "Meta Llama 3.2 (1B Instruct)"
        const val MODEL_SOURCE = "Hugging Face (bartowski/Llama-3.2-1B-Instruct-GGUF)"
        const val MODEL_LICENSE = "Llama 3.2 Community License"
        const val DOWNLOAD_URL = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        const val EXPECTED_SIZE_MB = 770L
    }

    private val _modelState = MutableStateFlow(ModelState.NOT_INSTALLED)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _downloadErrorMessage = MutableStateFlow<String?>(null)
    val downloadErrorMessage: StateFlow<String?> = _downloadErrorMessage.asStateFlow()

    private var downloadJob: Job? = null

    init {
        if (!modelDir.exists()) modelDir.mkdirs()
        checkInstalledModel()
    }

    fun getModelFile(): File = File(modelDir, MODEL_FILENAME)

    fun isModelInstalled(): Boolean {
        val file = getModelFile()
        // File must exist and be at least 100MB to be considered a complete model
        return file.exists() && file.length() > 100 * 1024 * 1024
    }

    fun calculateSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists() || expectedHash.isBlank()) return false
        val computed = calculateSha256(file)
        return computed.equals(expectedHash.trim(), ignoreCase = true)
    }

    fun getModelInfo(): ModelInfo {
        val modelFile = getModelFile()
        val installed = isModelInstalled()
        val sizeMb = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0L
        return ModelInfo(
            name = MODEL_NAME,
            source = MODEL_SOURCE,
            license = MODEL_LICENSE,
            downloadSizeMb = EXPECTED_SIZE_MB,
            installedSizeMb = sizeMb,
            isInstalled = installed,
            filePath = if (installed) modelFile.absolutePath else null
        )
    }

    suspend fun downloadModel(urlStr: String = DOWNLOAD_URL) = withContext(Dispatchers.IO) {
        if (isModelInstalled()) {
            _modelState.value = ModelState.INSTALLED
            return@withContext
        }

        val targetFile = getModelFile()
        val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")

        try {
            downloadJob = coroutineContext[Job]
            _modelState.value = ModelState.DOWNLOADING
            _downloadProgress.value = 0f
            _downloadErrorMessage.value = null

            var currentUrl = urlStr
            var connection: HttpURLConnection
            var redirectCount = 0

            while (true) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", "Hoverpen-Android")

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == 307 || status == 308
                ) {
                    currentUrl = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    redirectCount++
                    if (redirectCount > 5) break
                    continue
                }
                break
            }

            val total = connection.contentLengthLong.let { if (it > 0) it else EXPECTED_SIZE_MB * 1024 * 1024 }
            _totalBytes.value = total

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead = 0
            var totalRead = 0L

            while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                _downloadedBytes.value = totalRead
                if (total > 0) {
                    _downloadProgress.value = totalRead.toFloat() / total.toFloat()
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            if (!isActive) {
                if (tempFile.exists()) tempFile.delete()
                _modelState.value = if (isModelInstalled()) ModelState.INSTALLED else ModelState.NOT_INSTALLED
                _downloadProgress.value = 0f
                return@withContext
            }

            // Atomically rename to final model file
            if (tempFile.exists() && tempFile.length() > 100 * 1024 * 1024) {
                if (targetFile.exists()) targetFile.delete()
                val success = tempFile.renameTo(targetFile)
                if (success) {
                    _modelState.value = ModelState.INSTALLED
                    _downloadProgress.value = 1f
                    Log.d(TAG, "Llama 3.2 1B installed successfully: ${targetFile.absolutePath}")
                } else {
                    _modelState.value = ModelState.ERROR
                    _downloadErrorMessage.value = "Failed to finalize model file."
                }
            } else {
                _modelState.value = ModelState.ERROR
                _downloadErrorMessage.value = "Downloaded file is incomplete."
                tempFile.delete()
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Model download cancelled by user")
            if (tempFile.exists()) tempFile.delete()
            _modelState.value = if (isModelInstalled()) ModelState.INSTALLED else ModelState.NOT_INSTALLED
            _downloadProgress.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Llama 3.2 1B model", e)
            _modelState.value = ModelState.ERROR
            _downloadErrorMessage.value = e.localizedMessage ?: "Download failed"
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")
        if (tempFile.exists()) tempFile.delete()
        _modelState.value = if (isModelInstalled()) ModelState.INSTALLED else ModelState.NOT_INSTALLED
        _downloadProgress.value = 0f
    }

    fun deleteModel(): Boolean {
        val file = getModelFile()
        val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")
        if (tempFile.exists()) tempFile.delete()
        val result = if (file.exists()) file.delete() else true
        if (result) {
            _modelState.value = ModelState.NOT_INSTALLED
            _downloadProgress.value = 0f
            _downloadedBytes.value = 0L
        }
        return result
    }

    fun setModelState(state: ModelState) {
        _modelState.value = state
    }

    fun setDownloadProgress(progress: Float) {
        _downloadProgress.value = progress
    }

    fun checkInstalledModel() {
        _modelState.value = if (isModelInstalled()) ModelState.INSTALLED else ModelState.NOT_INSTALLED
    }

    fun getStorageUsedMb(): Long {
        return modelDir.listFiles()?.sumOf { it.length() }?.div(1024 * 1024) ?: 0
    }
}
