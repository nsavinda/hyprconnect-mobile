package dev.hyprconnect.app.util

import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Long.toFormattedDate(): String {
    val date = Date(this)
    val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return format.format(date)
}

fun Long.toHumanReadableSize(): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        this < kb -> "$this B"
        this < mb -> String.format("%.2f KB", this.toDouble() / kb)
        this < gb -> String.format("%.2f MB", this.toDouble() / mb)
        else -> String.format("%.2f GB", this.toDouble() / gb)
    }
}
