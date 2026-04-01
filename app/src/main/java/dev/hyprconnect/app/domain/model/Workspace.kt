package dev.hyprconnect.app.domain.model

data class Workspace(
    val id: Int,
    val name: String,
    val monitor: String? = null,
    val windows: Int = 0,
    val isActive: Boolean = false
)
