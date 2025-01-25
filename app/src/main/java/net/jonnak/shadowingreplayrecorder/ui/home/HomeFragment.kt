package net.jonnak.shadowingreplayrecorder.ui.home
import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import net.jonnak.shadowingreplayrecorder.databinding.FragmentHomeBinding

private const val LOG_TAG = "AudioRecorder"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var homeViewModel: HomeViewModel

    private val permReqLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
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
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupUI()
        observeViewModel()
        setupRecordingModeSwitch()
        setupRecordButton()
        setupPlaybackControls()

        // Request permissions
        permReqLauncher.launch(PERMISSIONS)

        return binding.root
    }

    private fun setupUI() {
        audioRecorder = AudioRecorder(requireContext())
        // Set your preferred encoder here
        audioRecorder.setAudioEncoder(AudioRecorder.AudioEncoder.AAC)
    }

    private fun observeViewModel() {
        homeViewModel.text.observe(viewLifecycleOwner) {
            binding.textHome.text = it
        }

        homeViewModel.isRecording.observe(viewLifecycleOwner) { isRecording ->
            binding.recordButton.isActivated = isRecording
            // Update record button icon based on recording state
            binding.recordButton.setImageResource(
                if (isRecording) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_btn_speak_now
            )
            if (isRecording) {
                binding.chronometer.apply {
                    base = SystemClock.elapsedRealtime()
                    start()
                }
                binding.playbackControls.visibility = View.GONE
            } else {
                binding.chronometer.stop()
            }
        }

        homeViewModel.hasRecording.observe(viewLifecycleOwner) { hasRecording ->
            binding.playbackControls.visibility = if (hasRecording && homeViewModel.isRecording.value != true) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        homeViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            binding.playButton.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
    }


    private fun setupRecordingModeSwitch() {
        binding.recordingModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) {
                AudioRecorder.RecordingMode.ToggleRecord
            } else {
                AudioRecorder.RecordingMode.HoldToRecord
            }
            homeViewModel.setRecordingMode(newMode)
        }
    }

    private fun setupRecordButton() {
        binding.recordButton.setOnTouchListener { _, event ->
            when (homeViewModel.recordingMode.value) {
                AudioRecorder.RecordingMode.HoldToRecord -> handleHoldToRecord(event)
                AudioRecorder.RecordingMode.ToggleRecord -> handleToggleRecord(event)
                else -> false
            }
        }
    }

    private fun setupPlaybackControls() {
        audioRecorder.setPlaybackListener(object : AudioRecorder.PlaybackListener {
            override fun onProgressChanged(position: Int, duration: Int) {
                binding.playbackSeekBar.max = duration
                binding.playbackSeekBar.progress = position
                binding.playbackPosition.text = formatTime(position)
                binding.playbackDuration.text = formatTime(duration)
            }

            override fun onPlaybackCompleted() {
                homeViewModel.setPlaying(false)
            }

            override fun onError(error: String) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        })

        binding.playbackSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioRecorder.seekTo(progress)
                    binding.playbackPosition.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.playButton.setOnClickListener {
            if (homeViewModel.isPlaying.value == true) {
                audioRecorder.stopPlayback()
                homeViewModel.setPlaying(false)
            } else {
                audioRecorder.startPlayback(
                    onComplete = {
                        activity?.runOnUiThread {
                            homeViewModel.setPlaying(false)
                        }
                    },
                    onError = {
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Playback error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                homeViewModel.setPlaying(true)
            }
        }


    }

    private fun formatTime(millis: Int): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun handleHoldToRecord(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (audioRecorder.startRecording()) {
                    homeViewModel.setRecording(true)
                    homeViewModel.setHasRecording(false)
                }
            }
            MotionEvent.ACTION_UP -> {
                val recordingSuccessful = audioRecorder.stopRecording()
                homeViewModel.setRecording(false)
                if (recordingSuccessful) {
                    homeViewModel.setHasRecording(true)
                }
            }
        }
        return true
    }

    private fun handleToggleRecord(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        val isCurrentlyRecording = homeViewModel.isRecording.value ?: false

        if (isCurrentlyRecording) {
            // Stop recording
            val recordingSuccessful = audioRecorder.stopRecording()
            homeViewModel.setRecording(false)
            if (recordingSuccessful) {
                homeViewModel.setHasRecording(true)
            }
        } else {
            // Start recording
            if (audioRecorder.startRecording()) {
                homeViewModel.setRecording(true)
            }
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioRecorder.release()
        _binding = null
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
    }
}