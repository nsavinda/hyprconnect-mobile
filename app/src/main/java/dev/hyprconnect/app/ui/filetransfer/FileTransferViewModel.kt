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
import androidx.documentfile.provider.DocumentFile
import dev.hyprconnect.app.data.remote.QuicFileUploader
import dev.hyprconnect.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val client: HyprConnectClient,
    private val quicUploader: QuicFileUploader,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val TAG = "FileTransferVM"
    private val chunkSize = 2 * 1024 * 1024
    private val hashVerificationThreshold = 32L * 1024L * 1024L
    private var requestIdCounter: Int = 5000

    private data class OfferResult(
        val transferId: String,
        val uploadToken: String? = null,
        val quicPort: Int? = null
    )

    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers: StateFlow<List<FileTransfer>> = _transfers.asStateFlow()

    private val canceledBatchIds = ConcurrentHashMap.newKeySet<String>()
    private val canceledTransferIds = ConcurrentHashMap.newKeySet<String>()

    private val _overallStartMs = MutableStateFlow<Long?>(null)
    private val _overallEndMs = MutableStateFlow<Long?>(null)

    private val tickFlow = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    val overallElapsedMs: StateFlow<Long> = combine(
        _overallStartMs,
        _overallEndMs,
        _transfers,
        tickFlow
    ) { start, end, _, _ ->
        when {
            start == null -> 0L
            end != null -> (end - start).coerceAtLeast(0L)
            else -> (System.currentTimeMillis() - start).coerceAtLeast(0L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val batches: StateFlow<List<FolderBatch>> = _transfers
        .map { list -> aggregateBatches(list) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun aggregateBatches(list: List<FileTransfer>): List<FolderBatch> {
        val grouped = list.filter { it.batchId != null }.groupBy { it.batchId!! }
        return grouped.map { (id, files) ->
            val root = files.firstOrNull { it.batchRoot != null }?.batchRoot ?: "folder"
            val total = files.size
            val completed = files.count { it.progress >= 1f || it.status.startsWith("Completed") }
            val totalSize = files.sumOf { it.size }
            val transferredBytes = files.sumOf {
                ((it.size.toDouble()) * it.progress.coerceIn(0f, 1f)).toLong()
            }
            val activeSpeed = files.filter { it.progress < 1f }.sumOf { it.speed }
            val active = files.any { isTransferActive(it) }
            val startMs = files.mapNotNull { it.startMs }.minOrNull()
            val endMs = if (!active) files.mapNotNull { it.endMs }.maxOrNull() else null
            FolderBatch(
                id = id,
                rootName = root,
                totalFiles = total,
                completedFiles = completed,
                totalSize = totalSize,
                transferredBytes = transferredBytes,
                activeSpeed = activeSpeed,
                active = active,
                startMs = startMs,
                endMs = endMs
            )
        }.sortedBy { it.rootName }
    }

    // Caps in-flight uploads so a folder pick of N files doesn't overrun the
    // JSON-RPC channel and the daemon. Resized when the user changes the
    // setting; in-flight uploads continue against the old semaphore.
    @Volatile private var gate: Semaphore = Semaphore(4)

    // FIFO pending queue feeding a fixed pool of workers. Producers
    // (folder walk, file picker) submitPending() as files are discovered;
    // workers consume in arrival order. The walk does NOT wait to enumerate
    // all files — workers start consuming as soon as the first file is
    // enqueued.
    private val pendingMutex = Mutex()
    private val pending = ArrayDeque<PendingUpload>()
    private val pendingSignal = Channel<Unit>(Channel.UNLIMITED)
    private val maxWorkers = 16

    val activeCount: StateFlow<Int> = _transfers
        .map { list -> list.count { isTransferActive(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            settingsRepository.maxConcurrentTransfers.collect { n ->
                gate = Semaphore(n.coerceIn(1, 16))
            }
        }
        repeat(maxWorkers) {
            viewModelScope.launch(Dispatchers.IO) { workerLoop() }
        }
    }

    private suspend fun workerLoop() {
        while (true) {
            val task = nextPending()
            try {
                runUpload(task)
            } catch (e: Exception) {
                Log.e(TAG, "Worker error: ${e.message}", e)
            }
        }
    }

    private suspend fun nextPending(): PendingUpload {
        while (true) {
            val task = pendingMutex.withLock {
                if (pending.isEmpty()) null else pending.removeFirst()
            }
            if (task != null) return task
            pendingSignal.receive()
        }
    }

    private suspend fun submitPending(task: PendingUpload) {
        pendingMutex.withLock { pending.add(task) }
        pendingSignal.trySend(Unit)
    }

    private fun dropPendingForBatch(batchId: String) {
        // Run on the same dispatcher as the rest; cancellation paths already
        // hold the mutex briefly elsewhere.
        viewModelScope.launch(Dispatchers.IO) {
            pendingMutex.withLock {
                pending.removeAll { it.batchId == batchId }
            }
        }
    }

    private enum class FinalizeOutcome {
        SUCCESS,
        TIMEOUT,
        ERROR
    }

    fun sendSharedUri(context: Context, uri: Uri) {
        sendUri(context, uri, relativePath = null)
    }

    /**
     * Send every file in a SAF-picked folder tree, preserving the folder
     * structure. The daemon creates subdirectories as needed.
     */
    fun sendFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root == null || !root.isDirectory) return@launch
            val rootName = root.name?.takeIf { it.isNotBlank() } ?: "folder"
            val batchId = java.util.UUID.randomUUID().toString()
            walkAndSend(context, root, rootName, batchId, rootName)
        }
    }

    private fun walkAndSend(
        context: Context,
        dir: DocumentFile,
        relPath: String,
        batchId: String,
        batchRoot: String
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                val name = child.name?.takeIf { it.isNotBlank() } ?: continue
                walkAndSend(context, child, "$relPath/$name", batchId, batchRoot)
            } else if (child.isFile) {
                enqueueUpload(context, child.uri, relPath, batchId, batchRoot)
            }
        }
    }

    /**
     * Send a single file. If [relativePath] is non-null, it is prefixed to the
     * filename so the daemon recreates the folder structure on the receiving side.
     */
    fun sendUri(context: Context, uri: Uri, relativePath: String?) {
        enqueueUpload(context, uri, relativePath, batchId = null, batchRoot = null)
    }

    fun cancelBatch(batchId: String) {
        canceledBatchIds.add(batchId)
        dropPendingForBatch(batchId)
        val updated = _transfers.value.map { transfer ->
            if (transfer.batchId == batchId && isTransferActive(transfer)) {
                canceledTransferIds.add(transfer.id)
                transfer.copy(
                    status = "Canceled",
                    speed = 0L,
                    endMs = transfer.endMs ?: System.currentTimeMillis()
                )
            } else {
                transfer
            }
        }
        _transfers.value = updated
        updateSessionCompletion(updated)
    }

    private fun enqueueUpload(
        context: Context,
        uri: Uri,
        relativePath: String?,
        batchId: String?,
        batchRoot: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val localId = java.util.UUID.randomUUID().toString()
            val contentResolver = context.contentResolver
            val displayName = queryDisplayName(contentResolver, uri) ?: "shared-file"
            val fileSize = querySize(contentResolver, uri)
            val sentName = if (relativePath.isNullOrBlank()) displayName
                else "${relativePath.trim('/')}/$displayName"

            val newTransfer = FileTransfer(
                id = localId,
                name = sentName,
                size = fileSize,
                isIncoming = false,
                progress = 0f,
                speed = 0L,
                status = "Queued",
                batchId = batchId,
                batchRoot = batchRoot,
                startMs = System.currentTimeMillis()
            )
            markSessionStart()
            _transfers.value = _transfers.value + newTransfer

            if (isCanceled(localId, batchId)) {
                updateStatus(localId, "Canceled")
                return@launch
            }

            if (!client.isConnected.value) {
                updateStatus(localId, "Not Connected")
                return@launch
            }

            submitPending(
                PendingUpload(
                    context = context,
                    uri = uri,
                    sentName = sentName,
                    fileSize = fileSize,
                    localId = localId,
                    batchId = batchId,
                    contentResolver = contentResolver
                )
            )
        }
    }

    private suspend fun runUpload(task: PendingUpload) {
        val context = task.context
        val uri = task.uri
        val sentName = task.sentName
        val fileSize = task.fileSize
        val localId = task.localId
        val batchId = task.batchId
        val contentResolver = task.contentResolver

        try {
            if (checkCanceled(localId, batchId)) return
            val quicEnabled = settingsRepository.quicTransfer.first()

            // gate covers offer + bulk data upload only. Finalize runs in a
            // separate coroutine so the worker is free to pick up the next
            // pending file the instant bytes are on the wire.
            val gateResult: Pair<OfferResult, Boolean> = gate.withPermit {
                if (checkCanceled(localId, batchId)) return
                updateStatus(localId, "Starting")
                val outcome = offerTransfer(sentName, fileSize, quicEnabled)
                if (outcome is OfferOutcome.Failed) {
                    updateStatus(localId, outcome.reason)
                    return
                }
                val offer = (outcome as OfferOutcome.Success).result

                if (checkCanceled(localId, batchId)) return

                val quicOk = if (quicEnabled && offer.uploadToken != null && offer.quicPort != null) {
                    sendViaQuic(
                        context, uri, offer.uploadToken, offer.quicPort,
                        localId, batchId, fileSize
                    )
                } else false

                if (checkCanceled(localId, batchId)) return

                val ok = if (quicOk) true else {
                    val tcpOk = sendViaTcpChunks(
                        context, uri, offer.transferId, localId, batchId, fileSize
                    )
                    if (!tcpOk) return
                    false
                }
                offer to ok
            }

            val (offer, dataOk) = gateResult

            if (checkCanceled(localId, batchId)) return

            // Hand off finalize to a background coroutine. The worker returns
            // immediately to consume the next priority task.
            viewModelScope.launch(Dispatchers.IO) {
                finalizeUpload(offer, sentName, fileSize, localId, batchId, contentResolver, uri, dataOk)
            }
        } catch (e: Exception) {
            updateStatus(localId, "Failed: ${e.message}")
        }
    }

    private suspend fun finalizeUpload(
        offer: OfferResult,
        sentName: String,
        fileSize: Long,
        localId: String,
        batchId: String?,
        contentResolver: ContentResolver,
        uri: Uri,
        dataOk: Boolean
    ) {
        try {
            updateStatus(localId, "Finalizing")
            updateProgress(localId, 1f, 0L)

            val hashHex = computeHash(contentResolver, uri, fileSize)
            val finalizeResult = completeTransfer(offer.transferId, sentName, hashHex)
            val completed = when (finalizeResult) {
                FinalizeOutcome.SUCCESS -> true
                FinalizeOutcome.ERROR -> false
                FinalizeOutcome.TIMEOUT -> {
                    when (completeTransfer(offer.transferId, sentName, hashHex)) {
                        FinalizeOutcome.SUCCESS -> true
                        FinalizeOutcome.ERROR -> false
                        FinalizeOutcome.TIMEOUT -> {
                            updateProgress(localId, 1f, 0L)
                            updateStatus(localId, "Completed (Confirmation Timeout)")
                            true
                        }
                    }
                }
            }

            if (checkCanceled(localId, batchId)) return
            if (!completed) {
                updateStatus(localId, "Finalize Failed")
                return
            }

            val method = if (dataOk) "QUIC" else "TCP"
            updateProgress(localId, 1f, 0L)
            updateStatus(localId, "Completed ($method)")
        } catch (e: Exception) {
            updateStatus(localId, "Failed: ${e.message}")
        }
    }

    /**
     * Upload file via QUIC/HTTP3 using Cronet.
     * Returns true if upload succeeded, false to trigger TCP fallback.
     */
    private suspend fun sendViaQuic(
        context: Context,
        uri: Uri,
        uploadToken: String,
        quicPort: Int,
        localId: String,
        batchId: String?,
        fileSize: Long
    ): Boolean {
        if (checkCanceled(localId, batchId)) return false
        val host = client.connectedHost ?: return false
        Log.d(TAG, "Attempting QUIC upload to $host:$quicPort")
        updateStatus(localId, "Uploading (QUIC)")

        var lastUpdateTime = System.currentTimeMillis()
        var lastBytesSent = 0L

        val result = quicUploader.upload(
            host = host,
            quicPort = quicPort,
            uploadToken = uploadToken,
            uri = uri,
            fileSize = fileSize,
            contentResolver = context.contentResolver,
            shouldCancel = { isCanceled(localId, batchId) },
            onProgress = { bytesSent ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 500) {
                    val durationSeconds = (currentTime - lastUpdateTime) / 1000.0
                    val currentSpeed = ((bytesSent - lastBytesSent) / durationSeconds).toLong()
                    val progress = if (fileSize > 0) (bytesSent.toFloat() / fileSize).coerceIn(0f, 1f) else 0f
                    updateProgress(localId, progress, currentSpeed)
                    lastUpdateTime = currentTime
                    lastBytesSent = bytesSent
                }
            }
        )

        if (result.success) {
            Log.d(TAG, "QUIC upload succeeded: ${result.bytesReceived} bytes")
            return true
        }

        if (isCanceled(localId, batchId) || result.error == "Canceled") {
            updateStatus(localId, "Canceled")
            return false
        }

        Log.w(TAG, "QUIC upload failed: ${result.error}, falling back to TCP")
        updateStatus(localId, "QUIC Failed, Using TCP")
        return false
    }

    private suspend fun sendViaTcpChunks(
        context: Context,
        uri: Uri,
        transferId: String,
        localId: String,
        batchId: String?,
        fileSize: Long
    ): Boolean {
        if (checkCanceled(localId, batchId)) return false
        val contentResolver = context.contentResolver
        var bytesSent = 0L
        var lastUpdateTime = System.currentTimeMillis()
        var lastBytesSent = 0L

        contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                updateStatus(localId, "Cannot Open File")
                return false
            }

            val buffer = ByteArray(chunkSize)
            var offset = 0L
            while (true) {
                if (checkCanceled(localId, batchId)) return false
                val read = input.read(buffer)
                if (read <= 0) break

                val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                val ok = sendChunk(transferId, offset, encoded)
                if (!ok) {
                    updateStatus(localId, "Chunk Upload Failed")
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

    private sealed class OfferOutcome {
        data class Success(val result: OfferResult) : OfferOutcome()
        data class Failed(val reason: String) : OfferOutcome()
    }

    private suspend fun offerTransfer(fileName: String, fileSize: Long, quicEnabled: Boolean): OfferOutcome {
        val requestId = nextRequestId()
        val offer = JsonRpcRequest(
            method = "file.offer",
            params = buildJsonObject {
                put("filename", fileName)
                put("size", fileSize)
                put("mime_type", "application/octet-stream")
                put("quic", quicEnabled)
            },
            id = requestId
        )

        val responseDeferred = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
            awaitResponse(requestId)
        }

        if (!client.sendRequest(offer)) {
            responseDeferred.cancel()
            return OfferOutcome.Failed("Failed to send offer (connection lost)")
        }

        val response = responseDeferred.await()
            ?: return OfferOutcome.Failed("Offer timed out (no response from daemon)")

        if (response.error != null) {
            val msg = response.error?.message ?: "unknown error"
            return OfferOutcome.Failed("Daemon rejected offer: $msg")
        }

        val result = response.result as? JsonObject
            ?: return OfferOutcome.Failed("Invalid offer response format")

        val accepted = result["accepted"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!accepted) return OfferOutcome.Failed("Offer rejected by daemon")

        val transferId = result["transfer_id"]?.jsonPrimitive?.contentOrNull
            ?: return OfferOutcome.Failed("Missing transfer_id in response")

        // Extract QUIC upload info if available
        val uploadToken = result["upload_token"]?.jsonPrimitive?.contentOrNull
        val quicPort = result["quic_port"]?.jsonPrimitive?.intOrNull

        return OfferOutcome.Success(OfferResult(transferId, uploadToken, quicPort))
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
        val updated = _transfers.value.map {
            if (it.id == id) {
                val endMs = if (isTerminalStatus(status)) it.endMs ?: System.currentTimeMillis() else it.endMs
                it.copy(status = status, speed = 0L, endMs = endMs)
            } else {
                it
            }
        }
        _transfers.value = updated
        updateSessionCompletion(updated)
    }

    private fun updateProgress(id: String, progress: Float, speed: Long) {
        val updated = _transfers.value.map {
            if (it.id == id) it.copy(progress = progress, speed = speed) else it
        }
        _transfers.value = updated
        updateSessionCompletion(updated)
    }

    private fun markSessionStart() {
        if (_overallStartMs.value == null || _overallEndMs.value != null) {
            _overallStartMs.value = System.currentTimeMillis()
            _overallEndMs.value = null
        }
    }

    private fun updateSessionCompletion(transfers: List<FileTransfer>) {
        val anyActive = transfers.any { isTransferActive(it) }
        if (!anyActive && _overallStartMs.value != null && _overallEndMs.value == null) {
            _overallEndMs.value = System.currentTimeMillis()
        }
    }

    private fun isTransferActive(transfer: FileTransfer): Boolean {
        if (transfer.progress >= 1f) return false
        val status = transfer.status.lowercase()
        if (status.startsWith("completed")) return false
        if (status.contains("failed") || status.contains("error") || status.contains("not connected") || status.contains("canceled")) {
            return false
        }
        return true
    }

    private fun isTerminalStatus(status: String): Boolean {
        val normalized = status.lowercase()
        return normalized.startsWith("completed") ||
            normalized.contains("failed") ||
            normalized.contains("error") ||
            normalized.contains("not connected") ||
            normalized.contains("canceled")
    }

    private fun isCanceled(localId: String, batchId: String?): Boolean {
        return canceledTransferIds.contains(localId) || (batchId != null && canceledBatchIds.contains(batchId))
    }

    private fun checkCanceled(localId: String, batchId: String?): Boolean {
        if (isCanceled(localId, batchId)) {
            updateStatus(localId, "Canceled")
            return true
        }
        return false
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

private data class PendingUpload(
    val context: Context,
    val uri: Uri,
    val sentName: String,
    val fileSize: Long,
    val localId: String,
    val batchId: String?,
    val contentResolver: ContentResolver
)

data class FileTransfer(
    val id: String,
    val name: String,
    val size: Long,
    val progress: Float,
    val speed: Long,
    val isIncoming: Boolean,
    val status: String = "Transferring",
    val batchId: String? = null,
    val batchRoot: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null
)

data class FolderBatch(
    val id: String,
    val rootName: String,
    val totalFiles: Int,
    val completedFiles: Int,
    val totalSize: Long,
    val transferredBytes: Long,
    val activeSpeed: Long,
    val active: Boolean,
    val startMs: Long? = null,
    val endMs: Long? = null
)
