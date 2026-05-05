package dev.hyprconnect.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val type: DeviceType,
    val status: DeviceStatus = DeviceStatus.Disconnected,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val isPaired: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val fingerprint: String? = null,
    val availablePlugins: List<String> = emptyList(),
    // Alternate IPs advertised by the daemon (mDNS TXT "addresses").
    // Used when the resolved host is unreachable (stale cache, multi-NIC).
    val addresses: List<String> = emptyList()
)

enum class DeviceType {
    DESKTOP, LAPTOP, PHONE, TABLET, OTHER
}
