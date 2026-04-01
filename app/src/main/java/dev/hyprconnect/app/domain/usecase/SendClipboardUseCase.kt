package dev.hyprconnect.app.domain.usecase

import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SendClipboardUseCase @Inject constructor(
    private val client: HyprConnectClient
) {
    suspend operator fun invoke(content: String): Boolean {
        val request = JsonRpcRequest(
            method = "clipboard.set",
            params = buildJsonObject {
                put("content", content)
            }
        )
        return client.sendRequest(request)
    }
}
