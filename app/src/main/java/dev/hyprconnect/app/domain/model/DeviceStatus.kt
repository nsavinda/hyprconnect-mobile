package dev.hyprconnect.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class DeviceStatus {
    @Serializable
    object Connected : DeviceStatus() {
        override fun toString(): String = "Connected"
    }
    @Serializable
    object Disconnected : DeviceStatus() {
        override fun toString(): String = "Disconnected"
    }
    @Serializable
    object Connecting : DeviceStatus() {
        override fun toString(): String = "Connecting"
    }
    @Serializable
    object Pairing : DeviceStatus() {
        override fun toString(): String = "Pairing"
    }
    @Serializable
    data class Error(val message: String) : DeviceStatus()
}
