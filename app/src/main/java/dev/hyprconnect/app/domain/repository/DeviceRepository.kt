package dev.hyprconnect.app.domain.repository

import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val discoveredDevices: Flow<List<Device>>
    val pairedDevices: Flow<List<Device>>
    
    fun startDiscovery()
    fun stopDiscovery()
    /** Returns the pairing device_id assigned by the daemon, or null on failure. */
    suspend fun pairDevice(device: Device): String?
    suspend fun unpairDevice(deviceId: String)
    suspend fun connectToDevice(device: Device): Boolean
    fun disconnect()
    suspend fun handlePairingCompleted(deviceId: String, deviceName: String, plugins: List<String>): Boolean
    suspend fun listWorkspaces(): List<Workspace>
    suspend fun switchWorkspace(id: Int): Boolean
    suspend fun getSystemVolume(): Float?
    suspend fun setSystemVolume(level: Float): Boolean
    suspend fun getSystemBrightness(): Float?
    suspend fun setSystemBrightness(level: Float): Boolean
}
