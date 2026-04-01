package dev.hyprconnect.app.ui.filetransfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcMessage
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val client: HyprConnectClient
) : ViewModel() {
    private val chunkSize = 128 * 1024
    private var requestIdCounter: Int = 5000

    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers: StateFlow<List<FileTransfer>> = _transfers.asStateFlow()

    private enum class FinalizeOutcome {
        SUCCESS,
        TIMEOUT,
        ERROR
    }

    fun sendSharedUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val localId = java.util.UUID.randomUUID().toString()
            val contentResolver = context.contentResolver
            val fileName = queryDisplayName(contentResolver, uri) ?: "shared-file"
            val fileSize = querySize(contentResolver, uri)

            val newTransfer = FileTransfer(
                id = localId,
                name = fileName,
                size = fileSize,
                isIncoming = false,
                progress = 0f
            )
            _transfers.value += newTransfer

            if (!client.isConnected.value) {
                updateStatus(localId, "Not connected")
                return@launch
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var bytesSent = 0L

            try {
                val transferId = offerTransfer(fileName, fileSize)
                if (transferId == null) {
                    updateStatus(localId, "Offer rejected or timed out")
                    return@launch
                }

                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        updateStatus(localId, "Cannot open file")
                        return@launch
                    }

                    val buffer = ByteArray(chunkSize)
                    var read: Int
                    var offset = 0L
                    while (true) {
                        read = withContext(Dispatchers.IO) { input.read(buffer) }
                        if (read <= 0) break

                        digest.update(buffer, 0, read)
                        val chunkBytes = if (read == buffer.size) buffer else buffer.copyOf(read)
                        val encoded = Base64.getEncoder().withoutPadding().encodeToString(chunkBytes)

                        val ok = sendChunk(transferId, offset, encoded)
                        if (!ok) {
                            updateStatus(localId, "Chunk upload failed")
                            return@launch
                        }

                        offset += read
                        bytesSent += read
                        if (fileSize > 0) {
                            updateProgress(localId, (bytesSent.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }

                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                val finalizeResult = completeTransfer(transferId, fileName, hashHex)
                val completed = when (finalizeResult) {
                    FinalizeOutcome.SUCCESS -> true
                    FinalizeOutcome.ERROR -> false
                    FinalizeOutcome.TIMEOUT -> {
                        // Retry once in case the first response was delayed/missed.
                        when (completeTransfer(transferId, fileName, hashHex)) {
                            FinalizeOutcome.SUCCESS -> true
                            FinalizeOutcome.ERROR -> false
                            FinalizeOutcome.TIMEOUT -> {
                                updateProgress(localId, 1f)
                                updateStatus(localId, "Completed (confirmation timeout)")
                                true
                            }
                        }
                    }
                }

                if (!completed) {
                    updateStatus(localId, "Finalize failed")
                    return@launch
                }

                updateProgress(localId, 1f)
                updateStatus(localId, "Completed")
            } catch (e: Exception) {
                updateStatus(localId, "Failed: ${e.message}")
            }
        }
    }

    private suspend fun offerTransfer(fileName: String, fileSize: Long): String? {
        val requestId = nextRequestId()
        val offer = JsonRpcRequest(
            method = "file.offer",
            params = buildJsonObject {
                put("filename", fileName)
                put("size", fileSize)
                put("mime_type", "application/octet-stream")
            },
            id = requestId
        )

        val responseDeferred = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
            awaitResponse(requestId)
        }

        if (!client.sendRequest(offer)) {
            responseDeferred.cancel()
            return null
        }

        val response = responseDeferred.await() ?: return null
        if (response.error != null) return null
        val result = response.result as? JsonObject ?: return null
        val accepted = result["accepted"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!accepted) return null
        return result["transfer_id"]?.jsonPrimitive?.contentOrNull
    }

    private suspend fun sendChunk(transferId: String, offset: Long, base64Data: String): Boolean {
        val requestId = nextRequestId()
        val req = JsonRpcRequest(
            method = "file.chunk",
            params = buildJsonObject {
                put("transfer_id", transferId)
                put("offset", offset)
                put("data", base64Data)
            },
            id = requestId
        )

        val responseDeferred = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
            awaitResponse(requestId)
        }

        if (!client.sendRequest(req)) {
            responseDeferred.cancel()
            return false
        }

        val response = responseDeferred.await() ?: return false
        return response.error == null
    }

    private suspend fun completeTransfer(transferId: String, fileName: String, sha256: String): FinalizeOutcome {
        val requestId = nextRequestId()
        val req = JsonRpcRequest(
            method = "file.complete",
            params = buildJsonObject {
                put("transfer_id", transferId)
                put("filename", fileName)
                put("sha256", sha256)
            },
            id = requestId
        )

        val responseDeferred = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
            awaitResponse(requestId)
        }

        if (!client.sendRequest(req)) {
            responseDeferred.cancel()
            return FinalizeOutcome.ERROR
        }

        val response = responseDeferred.await() ?: return FinalizeOutcome.TIMEOUT
        if (response.error != null) {
            return FinalizeOutcome.ERROR
        }

        val result = response.result as? JsonObject ?: return FinalizeOutcome.SUCCESS
        val success = result["success"]?.jsonPrimitive?.booleanOrNull
        return if (success == false) FinalizeOutcome.ERROR else FinalizeOutcome.SUCCESS
    }

    private suspend fun awaitResponse(requestId: Int): JsonRpcMessage? {
        return withTimeoutOrNull(20000) {
            client.incomingMessages.first { msg ->
                msg.id == requestId && msg.isResponse()
            }
        }
    }

    private fun nextRequestId(): Int {
        requestIdCounter += 1
        return requestIdCounter
    }

    private fun updateStatus(id: String, status: String) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    private fun updateProgress(id: String, progress: Float) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) it.copy(progress = progress) else it
        }
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun querySize(contentResolver: ContentResolver, uri: Uri): Long {
        val projection = arrayOf(android.provider.OpenableColumns.SIZE)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(index)
            }
        }
        return 0L
    }
}

data class FileTransfer(
    val id: String,
    val name: String,
    val size: Long,
    val progress: Float,
    val isIncoming: Boolean,
    val status: String = "Transferring"
)
