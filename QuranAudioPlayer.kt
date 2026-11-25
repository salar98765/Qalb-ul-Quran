package com.example.domain

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val surahNumber: Int? = null,
    val ayahNumberInSurah: Int? = null,
    val globalAyahNumber: Int? = null,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
)

class QuranAudioPlayer(
    private val scope: CoroutineScope,
    private val onAyahCompleted: (surahNumber: Int, completedAyahInSurah: Int) -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    fun playUrl(
        url: String,
        surahNumber: Int,
        ayahNumberInSurah: Int?,
        globalAyahNumber: Int?
    ) {
        stopInternal()
        _state.value = AudioPlaybackState(
            isPlaying = false,
            isLoading = true,
            surahNumber = surahNumber,
            ayahNumberInSurah = ayahNumberInSurah,
            globalAyahNumber = globalAyahNumber,
            progressFraction = 0f,
            errorMessage = null
        )

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                mp.start()
                _state.value = _state.value.copy(
                    isPlaying = true,
                    isLoading = false,
                    errorMessage = null
                )
                startProgressPolling()
            }
            setOnCompletionListener {
                progressJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false, progressFraction = 1f)
                if (ayahNumberInSurah != null) {
                    onAyahCompleted(surahNumber, ayahNumberInSurah)
                }
            }
            setOnErrorListener { _, what, extra ->
                progressJob?.cancel()
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isLoading = false,
                    errorMessage = "Audio unavailable (code $what/$extra). Check connection."
                )
                true
            }
        }
        mediaPlayer = player

        scope.launch(Dispatchers.IO) {
            try {
                player.setDataSource(url)
                player.prepareAsync()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isLoading = false,
                    errorMessage = "Unable to stream recitation: ${e.localizedMessage ?: "Network error"}"
                )
            }
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                progressJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                mp.start()
                _state.value = _state.value.copy(isPlaying = true)
                startProgressPolling()
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        stopInternal()
        _state.value = AudioPlaybackState()
    }

    private fun stopInternal() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val mp = mediaPlayer ?: break
                try {
                    if (mp.isPlaying && mp.duration > 0) {
                        val frac = (mp.currentPosition.toFloat() / mp.duration.toFloat()).coerceIn(0f, 1f)
                        _state.value = _state.value.copy(progressFraction = frac)
                    }
                } catch (_: Exception) {
                    break
                }
                delay(250L)
            }
        }
    }

    fun release() {
        stopInternal()
    }
}
