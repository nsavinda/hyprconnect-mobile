package dev.hyprconnect.app.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hyprconnect.app.domain.model.NowPlaying
import dev.hyprconnect.app.domain.repository.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaControlViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshNowPlaying()
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshNowPlaying() {
        viewModelScope.launch {
            try {
                _nowPlaying.value = deviceRepository.getNowPlaying()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun play() {
        viewModelScope.launch {
            deviceRepository.mediaAction("play")
            delay(300)
            refreshNowPlaying()
        }
    }

    fun pause() {
        viewModelScope.launch {
            deviceRepository.mediaAction("pause")
            delay(300)
            refreshNowPlaying()
        }
    }

    fun next() {
        viewModelScope.launch {
            deviceRepository.mediaAction("next")
            delay(500)
            refreshNowPlaying()
        }
    }

    fun previous() {
        viewModelScope.launch {
            deviceRepository.mediaAction("previous")
            delay(500)
            refreshNowPlaying()
        }
    }

    fun setVolume(level: Float) {
        viewModelScope.launch {
            deviceRepository.mediaAction("volume")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
