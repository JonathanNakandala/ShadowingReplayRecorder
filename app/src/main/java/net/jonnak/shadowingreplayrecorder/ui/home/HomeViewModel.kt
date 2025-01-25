package net.jonnak.shadowingreplayrecorder.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _text = MutableLiveData<String>()
    val text: LiveData<String> = _text

    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> = _isRecording

    private val _hasRecording = MutableLiveData<Boolean>()
    val hasRecording: LiveData<Boolean> = _hasRecording

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _recordingMode = MutableLiveData<AudioRecorder.RecordingMode>()
    val recordingMode: LiveData<AudioRecorder.RecordingMode> = _recordingMode

    init {
        _recordingMode.value = AudioRecorder.RecordingMode.HoldToRecord
        _isRecording.value = false
        _hasRecording.value = false
        _isPlaying.value = false
        updateUIText()
    }

    fun setRecordingMode(mode: AudioRecorder.RecordingMode) {
        _recordingMode.value = mode
        updateUIText()
    }

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
        if (!recording) {
            _hasRecording.value = true
        }
        updateUIText()
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setHasRecording(hasRecording: Boolean) {
        _hasRecording.value = hasRecording
    }

    private fun updateUIText() {
        _text.value = when (recordingMode.value) {
            AudioRecorder.RecordingMode.HoldToRecord -> {
                when {
                    isRecording.value == true -> "Recording..."
                    hasRecording.value == true -> "Recording ready for playback"
                    else -> "Hold to Record"
                }
            }
            AudioRecorder.RecordingMode.ToggleRecord -> {
                when {
                    isRecording.value == true -> "Recording (Press to Stop)"
                    hasRecording.value == true -> "Recording ready for playback"
                    else -> "Press to Record"
                }
            }
            else -> "Hold to Record"
        }
    }
}