package dev.hyprconnect.app.domain.repository

import dev.hyprconnect.app.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val discoveredDevices: Flow<List<Device>>
    val pairedDevices: Flow<List<Device>>
    
    fun startDiscovery()
    fun stopDiscovery()
    suspend fun pairDevice(device: Device): Boolean
    suspend fun unpairDevice(deviceId: String)
    suspend fun connectToDevice(device: Device): Boolean
    fun disconnect()
    suspend fun handlePairingCompleted(deviceId: String, deviceName: String, plugins: List<String>): Boolean
}
