package dev.hyprconnect.app.domain.model

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val status: String // "Playing", "Paused", "Stopped"
)
