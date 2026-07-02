package dev.hyprconnect.app.domain.repository

import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.NowPlaying
import dev.hyprconnect.app.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val discoveredDevices: Flow<List<Device>>
    val pairedDevices: Flow<List<Device>>
    
    fun startDiscovery()
    fun stopDiscovery()
    /**
     * Initiates the pairing handshake. On success the daemon returns the
     * pairing session's device_id and the SAS (Short Authentication String)
     * that the user must visually verify against the desktop. Returns null
     * on connection failure.
     */
    suspend fun pairDevice(device: Device): PairingSession?
    /**
     * Drops local pairing for [deviceId]. When [notifyPeer] is true (the
     * default), best-effort notifies the desktop so it removes its trusted
     * cert and forces a fresh pair on next connect. Pass false when the
     * caller is itself reacting to a remote `device.unpaired` notification
     * to avoid notification loops.
     */
    suspend fun unpairDevice(deviceId: String, notifyPeer: Boolean = true)
    suspend fun connectToDevice(device: Device): Boolean
    fun disconnect()
    suspend fun handlePairingCompleted(deviceId: String, deviceName: String, plugins: List<String>): Boolean
    suspend fun listWorkspaces(): List<Workspace>
    suspend fun switchWorkspace(id: Int): Boolean
    suspend fun getSystemVolume(): Float?
    suspend fun setSystemVolume(level: Float): Boolean
    suspend fun getSystemBrightness(): Float?
    suspend fun setSystemBrightness(level: Float): Boolean

    // Media control
    suspend fun mediaAction(action: String, player: String? = null): Boolean
    suspend fun getNowPlaying(): NowPlaying?

    // Remote input
    suspend fun inputMouseMove(dx: Float, dy: Float): Boolean
    suspend fun inputMouseClick(button: String = "left"): Boolean
    suspend fun inputMouseScroll(dx: Float, dy: Float): Boolean
    suspend fun inputKeyboardType(text: String): Boolean
    suspend fun inputKeyboardKey(key: String, modifiers: List<String> = emptyList()): Boolean
}

/** Session returned by [DeviceRepository.pairDevice]. */
data class PairingSession(
    val deviceId: String,
    /** 6-digit SAS — both sides display this for visual comparison. */
    val sas: String
)
