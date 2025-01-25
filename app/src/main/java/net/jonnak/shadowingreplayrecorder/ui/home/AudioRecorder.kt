package net.jonnak.shadowingreplayrecorder.ui.home
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null
    private var recordingStartTime: Long = 0
    private var isRecording = false
    private var currentEncoder = AudioEncoder.AAC

    enum class AudioEncoder(val encoderId: Int) {
        AAC(MediaRecorder.AudioEncoder.AAC),
        HE_AAC(MediaRecorder.AudioEncoder.HE_AAC),
        AAC_ELD(MediaRecorder.AudioEncoder.AAC_ELD),
        AMR_NB(MediaRecorder.AudioEncoder.AMR_NB),
        AMR_WB(MediaRecorder.AudioEncoder.AMR_WB)
    }

    sealed class RecordingMode {
        data object HoldToRecord : RecordingMode()
        data object ToggleRecord : RecordingMode()
    }

    fun setAudioEncoder(encoder: AudioEncoder) {
        Log.d(TAG, "Setting audio encoder to: ${encoder.name}")
        currentEncoder = encoder
    }

    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Attempted to start recording while already recording")
            return false
        }

        Log.i(TAG, "Starting recording with encoder: ${currentEncoder.name}")

        try {
            cleanupMediaRecorder() // Clean up any existing recorder

            // Create new temp file
            tempAudioFile = File.createTempFile("tempAudio", ".mp4", context.cacheDir).also {
                Log.d(TAG, "Created temp file: ${it.absolutePath}")
            }

            // Create and configure new MediaRecorder
            mediaRecorder = MediaRecorder(context).apply {
                Log.v(TAG, "Configuring MediaRecorder...")
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(currentEncoder.encoderId)
                setOutputFile(tempAudioFile!!.absolutePath)

                try {
                    Log.v(TAG, "Preparing MediaRecorder...")
                    prepare()
                } catch (e: IOException) {
                    Log.e(TAG, "MediaRecorder prepare() failed", e)
                    cleanupMediaRecorder()
                    return false
                }

                try {
                    Log.v(TAG, "Starting MediaRecorder...")
                    start()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "MediaRecorder start() failed", e)
                    cleanupMediaRecorder()
                    return false
                }
            }

            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            Log.i(TAG, "Recording started successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in startRecording", e)
            cleanupMediaRecorder()
            return false
        }
    }

    fun stopRecording(): Boolean {
        if (!isRecording || mediaRecorder == null) {
            Log.w(TAG, "Attempted to stop recording while not recording")
            return false
        }

        Log.i(TAG, "Stopping recording...")
        try {
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            Log.d(TAG, "Recording duration: ${recordingDuration}ms")

            if (recordingDuration < MIN_RECORDING_DURATION_MS) {
                Log.w(TAG, "Recording too short (${recordingDuration}ms), discarding")
                cleanupMediaRecorder()
                tempAudioFile?.delete()
                return false
            }

            mediaRecorder?.apply {
                try {
                    Log.v(TAG, "Stopping MediaRecorder...")
                    stop()
                    Log.v(TAG, "Resetting MediaRecorder...")
                    reset()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Failed to stop MediaRecorder", e)
                    tempAudioFile?.delete()
                    return false
                } finally {
                    Log.v(TAG, "Releasing MediaRecorder...")
                    release()
                    mediaRecorder = null
                    isRecording = false
                }
            }

            val fileExists = tempAudioFile?.exists() == true
            val fileSize = tempAudioFile?.length() ?: 0
            Log.d(TAG, "Recording file status - exists: $fileExists, size: $fileSize bytes")

            return fileExists && fileSize > 0

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in stopRecording", e)
            cleanupMediaRecorder()
            tempAudioFile?.delete()
            return false
        }
    }

    interface PlaybackListener {
        fun onProgressChanged(position: Int, duration: Int)
        fun onPlaybackCompleted()
        fun onError(error: String)
    }

    private var playbackListener: PlaybackListener? = null
    private var progressUpdateHandler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    fun setPlaybackListener(listener: PlaybackListener) {
        playbackListener = listener
    }

    fun startPlayback(onComplete: () -> Unit = {}, onError: () -> Unit = {}) {
        if (tempAudioFile == null || !tempAudioFile!!.exists() || tempAudioFile!!.length() == 0L) {
            Log.w(TAG, "Attempted to play invalid or missing recording")
            onError()
            return
        }

        Log.i(TAG, "Starting playback...")
        try {
            stopPlayback() // Stop any existing playback

            mediaPlayer = MediaPlayer().apply {
                Log.v(TAG, "Configuring MediaPlayer...")
                setDataSource(tempAudioFile!!.absolutePath)
                setOnPreparedListener {
                    Log.v(TAG, "MediaPlayer prepared, starting playback")
                    start()
                    startProgressUpdates()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stopProgressUpdates()
                    playbackListener?.onError("Playback error: $what")
                    onError()
                    true
                }
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    stopProgressUpdates()
                    playbackListener?.onPlaybackCompleted()
                    onComplete()
                    it.release()
                    mediaPlayer = null
                }
                Log.v(TAG, "Preparing MediaPlayer asynchronously...")
                prepareAsync()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start playback", e)
            onError()
        }
    }

    private fun startProgressUpdates() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        playbackListener?.onProgressChanged(
                            player.currentPosition,
                            player.duration
                        )
                        progressUpdateHandler.postDelayed(this, 100) // Update every 100ms
                    }
                }
            }
        }
        progressUpdateHandler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { progressUpdateHandler.removeCallbacks(it) }
        progressUpdateRunnable = null
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun stopPlayback() {
        Log.i(TAG, "Stopping playback...")
        stopProgressUpdates()
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
                Log.d(TAG, "Playback stopped")
            }
            release()
            Log.v(TAG, "MediaPlayer released")
        }
        mediaPlayer = null
    }

    private fun cleanupMediaRecorder() {
        Log.v(TAG, "Cleaning up MediaRecorder...")
        try {
            mediaRecorder?.apply {
                try {
                    if (isRecording) {
                        Log.v(TAG, "Stopping active recording during cleanup")
                        stop()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MediaRecorder during cleanup", e)
                }
                reset()
                release()
                Log.v(TAG, "MediaRecorder cleaned up")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaRecorder cleanup", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    fun release() {
        Log.i(TAG, "Releasing AudioRecorder resources...")
        cleanupMediaRecorder()
        stopPlayback()
        tempAudioFile?.delete()
        tempAudioFile = null
        Log.d(TAG, "AudioRecorder resources released")
    }

    companion object {
        private const val TAG = "AudioRecorder"
        private const val MIN_RECORDING_DURATION_MS = 500L
    }
}