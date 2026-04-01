package dev.hyprconnect.app.domain.usecase

import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.repository.DeviceRepository
import javax.inject.Inject

class PairDeviceUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(device: Device): Boolean {
        return deviceRepository.pairDevice(device)
    }
}
