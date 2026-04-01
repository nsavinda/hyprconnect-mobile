package dev.hyprconnect.app.domain.usecase

import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DiscoverDevicesUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    operator fun invoke(): Flow<List<Device>> {
        return deviceRepository.discoveredDevices
    }

    fun startDiscovery() {
        deviceRepository.startDiscovery()
    }

    fun stopDiscovery() {
        deviceRepository.stopDiscovery()
    }
}
