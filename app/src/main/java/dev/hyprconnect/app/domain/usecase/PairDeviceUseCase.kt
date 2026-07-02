package dev.hyprconnect.app.domain.usecase

import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.repository.DeviceRepository
import dev.hyprconnect.app.domain.repository.PairingSession
import javax.inject.Inject

class PairDeviceUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(device: Device): PairingSession? {
        return deviceRepository.pairDevice(device)
    }
}
