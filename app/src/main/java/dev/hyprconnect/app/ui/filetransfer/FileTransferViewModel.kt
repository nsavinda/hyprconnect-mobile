package dev.hyprconnect.app.ui.filetransfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val client: HyprConnectClient
) : ViewModel() {
    private val chunkSize = 2 * 1024 * 1024
    private val hashVerificationThreshold = 32L * 1024L * 1024L
    private var requestIdCounter: Int = 5000

    private data class OfferResult(
        val transferId: String
    )

    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers: StateFlow<List<FileTransfer>> = _transfers.asStateFlow()

    private enum class FinalizeOutcome {
        SUCCESS,
        TIMEOUT,
        ERROR
    }

    fun sendSharedUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val localId = java.util.UUID.randomUUID().toString()
            val contentResolver = context.contentResolver
            val fileName = queryDisplayName(contentResolver, uri) ?: "shared-file"
            val fileSize = querySize(contentResolver, uri)

            val newTransfer = FileTransfer(
                id = localId,
                name = fileName,
                size = fileSize,
                isIncoming = false,
                progress = 0f,
                speed = 0L
            )
            _transfers.value += newTransfer

            if (!client.isConnected.value) {
                updateStatus(localId, "Not connected")
                return@launch
            }

            try {
                val offer = offerTransfer(fileName, fileSize)
                if (offer == null) {
                    updateStatus(localId, "Offer rejected or timed out")
                    return@launch
                }

                val tcpOk = sendViaTcpChunks(
                    context, uri, offer.transferId, localId, fileSize
                )
                if (!tcpOk) return@launch

                // Compute hash for verification.
                val hashHex = computeHash(contentResolver, uri, fileSize)

                val finalizeResult = completeTransfer(offer.transferId, fileName, hashHex)
                val completed = when (finalizeResult) {
                    FinalizeOutcome.SUCCESS -> true
                    FinalizeOutcome.ERROR -> false
                    FinalizeOutcome.TIMEOUT -> {
                        when (completeTransfer(offer.transferId, fileName, hashHex)) {
                            FinalizeOutcome.SUCCESS -> true
                            FinalizeOutcome.ERROR -> false
                            FinalizeOutcome.TIMEOUT -> {
                                updateProgress(localId, 1f, 0L)
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

                updateProgress(localId, 1f, 0L)
                updateStatus(localId, "Completed (TCP)")
            } catch (e: Exception) {
                updateStatus(localId, "Failed: ${e.message}")
            }
        }
    }

    private suspend fun sendViaTcpChunks(
        context: Context,
        uri: Uri,
        transferId: String,
        localId: String,
        fileSize: Long
    ): Boolean {
        val contentResolver = context.contentResolver
        var bytesSent = 0L
        var lastUpdateTime = System.currentTimeMillis()
        var lastBytesSent = 0L

        contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                updateStatus(localId, "Cannot open file")
                return false
            }

            val buffer = ByteArray(chunkSize)
            var offset = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                val ok = sendChunk(transferId, offset, encoded)
                if (!ok) {
                    updateStatus(localId, "Chunk upload failed")
                    return false
                }

                offset += read
                bytesSent += read

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 1000) {
                    val durationSeconds = (currentTime - lastUpdateTime) / 1000.0
                    val currentSpeed = ((bytesSent - lastBytesSent) / durationSeconds).toLong()
                    val progress = if (fileSize > 0) (bytesSent.toFloat() / fileSize).coerceIn(0f, 1f) else 0f
                    updateProgress(localId, progress, currentSpeed)
                    lastUpdateTime = currentTime
                    lastBytesSent = bytesSent
                }
            }
        }
        return true
    }

    private fun computeHash(contentResolver: ContentResolver, uri: Uri, fileSize: Long): String? {
        if (fileSize !in 1..hashVerificationThreshold) return null
        return contentResolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private suspend fun offerTransfer(fileName: String, fileSize: Long): OfferResult? {
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
        val transferId = result["transfer_id"]?.jsonPrimitive?.contentOrNull ?: return null
        return OfferResult(transferId)
    }

    private suspend fun sendChunk(transferId: String, offset: Long, base64Data: String): Boolean {
        val req = JsonRpcRequest(
            method = "file.chunk",
            params = buildJsonObject {
                put("transfer_id", transferId)
                put("offset", offset)
                put("data", base64Data)
            }
        )
        return client.sendRequest(req)
    }

    private suspend fun completeTransfer(transferId: String, fileName: String, sha256: String?): FinalizeOutcome {
        val requestId = nextRequestId()
        val req = JsonRpcRequest(
            method = "file.complete",
            params = buildJsonObject {
                put("transfer_id", transferId)
                put("filename", fileName)
                if (!sha256.isNullOrBlank()) {
                    put("sha256", sha256)
                }
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
        if (response.error != null) return FinalizeOutcome.ERROR

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
            if (it.id == id) it.copy(status = status, speed = 0L) else it
        }
    }

    private fun updateProgress(id: String, progress: Float, speed: Long) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) it.copy(progress = progress, speed = speed) else it
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
    val speed: Long,
    val isIncoming: Boolean,
    val status: String = "Transferring"
)
