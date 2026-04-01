package dev.hyprconnect.app.ui.filetransfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val client: HyprConnectClient
) : ViewModel() {

    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers: StateFlow<List<FileTransfer>> = _transfers.asStateFlow()

    fun sendFile(file: File) {
        viewModelScope.launch {
            val transferId = java.util.UUID.randomUUID().toString()
            val newTransfer = FileTransfer(
                id = transferId,
                name = file.name,
                size = file.length(),
                isIncoming = false,
                progress = 0f
            )
            _transfers.value += newTransfer

            val offer = JsonRpcRequest(
                method = "file.offer",
                params = buildJsonObject {
                    put("id", transferId)
                    put("name", file.name)
                    put("size", file.length())
                }
            )
            
            if (client.sendRequest(offer)) {
                // Chunking logic would go here
                // For now just mark as completed in UI for demo
                updateProgress(transferId, 1f)
            }
        }
    }

    private fun updateProgress(id: String, progress: Float) {
        _transfers.value = _transfers.value.map {
            if (it.id == id) it.copy(progress = progress) else it
        }
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
