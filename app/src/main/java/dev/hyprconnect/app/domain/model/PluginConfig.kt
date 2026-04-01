package dev.hyprconnect.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    val notificationSync: Boolean = true,
    val clipboardSync: Boolean = true,
    val fileTransfer: Boolean = true,
    val mediaControl: Boolean = true,
    val batteryReporting: Boolean = true,
    val remoteInput: Boolean = false
)
