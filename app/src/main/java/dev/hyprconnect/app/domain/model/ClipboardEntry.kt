package dev.hyprconnect.app.domain.model

data class ClipboardEntry(
    val content: String,
    val source: Source,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Source { LOCAL, REMOTE }
}
