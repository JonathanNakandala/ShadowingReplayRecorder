package net.jonnak.shadowingreplayrecorder.ui.home
import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import net.jonnak.shadowingreplayrecorder.databinding.FragmentHomeBinding
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import net.jonnak.shadowingreplayrecorder.R
import net.jonnak.shadowingreplayrecorder.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException

private const val LOG_TAG = "AudioRecorder"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200


class HomeFragment : Fragment() {
    companion object {
        val TAG: String = HomeFragment::class.java.simpleName
        var PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
        )
    }
    private var _binding: FragmentHomeBinding? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordButton: ImageButton? = null
    private var tempAudioFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var chronometer: Chronometer
    private lateinit var homeViewModel: HomeViewModel
    private var recordingStartTime: Long = 0

    private var isRecordingStarted = false

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Permission request launcher
    private val permReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value
            }
            if (granted) {
                Toast.makeText(activity, "Allowed to Record", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Permission to record required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        // Request permissions
        permReqLauncher.launch(PERMISSIONS)


        recordButton = binding.recordButton
        chronometer = binding.chronometer

        recordButton!!.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopRecording()
                    true
                }


                else -> false
            }}
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mediaRecorder?.release()
        mediaPlayer?.release()
    }


    private fun startRecording() {
        if(isRecordingStarted) {
            return
        }
        try {
            if (tempAudioFile == null || !tempAudioFile!!.name.endsWith(".mp4")) {
                tempAudioFile = File.createTempFile("tempAudio", ".mp4", requireContext().cacheDir)
            }

            try {
                mediaRecorder = MediaRecorder(requireContext()).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
                    setOutputFile(tempAudioFile!!.absolutePath) // Consider checking nullity more gracefully
                    prepare()
                }
                mediaRecorder?.start()
                chronometer.base = SystemClock.elapsedRealtime()
                chronometer.start()
                recordingStartTime = System.currentTimeMillis()
                isRecordingStarted = true
                recordButton?.isActivated = true
                homeViewModel.updateText("Recording")
            } catch (e: IOException) {
                Log.e(TAG, "startRecording: IOException during preparation", e)
                Toast.makeText(requireContext(), "Failed to prepare recorder", Toast.LENGTH_SHORT).show()
                return
            } catch (e: IllegalStateException) {
                Log.e(TAG, "startRecording: IllegalStateException", e)
                Toast.makeText(requireContext(), "Recorder not properly initialized", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "startRecording: Failed to start recording", e)
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Error starting recording.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun stopRecording() {
        if (!isRecordingStarted) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Nothing recorded.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val recordingDuration = System.currentTimeMillis() - recordingStartTime
        if (recordingDuration < 500) { // 500ms as minimal recording duration
            Handler(Looper.getMainLooper()).postDelayed({
                actuallyStopRecording()
                startPlayback() // Moved playback start here
            }, 500 - recordingDuration)
        } else {
            actuallyStopRecording()
            startPlayback() // Moved playback start here
        }
    }

    private fun actuallyStopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "stopRecording: stop() called in an invalid state", e)
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Recording stopped unexpectedly.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            cleanupAfterStop()
        }
    }

    private fun cleanupAfterStop() {
        mediaRecorder?.release()
        mediaRecorder = null
        recordButton?.isActivated = false
        isRecordingStarted = false
        chronometer.stop()
        homeViewModel.updateText("Hold to Record")
    }

    private fun startPlayback() {
        try {
            mediaPlayer?.release() // Ensure any previous player is released
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempAudioFile!!.absolutePath)
                setOnPreparedListener { start() } // Start playback once preparation is complete
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what $what extra $extra")
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Playback error.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                prepareAsync() // Prepare the player asynchronously
            }
        } catch (e: IOException) {
            Log.e(TAG, "startPlayback: Failed to prepare MediaPlayer", e)
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Playback error.", Toast.LENGTH_SHORT).show()
            }
        }
    }


}