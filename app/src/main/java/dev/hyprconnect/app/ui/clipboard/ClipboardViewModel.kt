package dev.hyprconnect.app.ui.clipboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hyprconnect.app.domain.model.ClipboardEntry
import dev.hyprconnect.app.service.ClipboardMonitor
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val clipboardMonitor: ClipboardMonitor
) : ViewModel() {

    val history: StateFlow<List<ClipboardEntry>> = clipboardMonitor.history

    fun resend(entry: ClipboardEntry) {
        clipboardMonitor.resend(entry.content)
    }
}
