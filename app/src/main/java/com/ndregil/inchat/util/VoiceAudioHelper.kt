package com.ndregil.inchat.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Helper to record high-quality AAC voice notes using MediaRecorder.
 */
object VoiceRecorderManager {
    private const val TAG = "VoiceRecorderManager"
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var isRecording = false

    fun startRecording(context: Context): Result<File> {
        return try {
            cancelRecording() // Clean up any lingering recording

            val voiceDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outputFile = File(voiceDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            Log.i(TAG, "Started voice recording to ${outputFile.absolutePath}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            cancelRecording()
            Result.failure(e)
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        if (!isRecording) return Pair(null, 0)
        val file = currentOutputFile
        val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt().coerceAtLeast(1)

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null
        }

        return Pair(file, durationSeconds)
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            // Ignore cancel cleanup errors
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile?.let {
                if (it.exists()) it.delete()
            }
            currentOutputFile = null
        }
    }

    fun getAmplitudeRatio(): Float {
        if (!isRecording) return 0f
        return try {
            val maxAmp = mediaRecorder?.maxAmplitude ?: 0
            (maxAmp / 32767f).coerceIn(0f, 1f)
        } catch (e: Exception) {
            0f
        }
    }

    fun getElapsedSeconds(): Int {
        if (!isRecording) return 0
        return ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}

/**
 * Singleton Audio Player to ensure only one voice note plays at any time.
 */
object VoicePlayerManager {
    private const val TAG = "VoicePlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentTrackUrl = MutableStateFlow<String?>(null)
    val currentTrackUrl: StateFlow<String?> = _currentTrackUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0)
    val playbackPositionMs: StateFlow<Int> = _playbackPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0)
    val totalDurationMs: StateFlow<Int> = _totalDurationMs.asStateFlow()

    fun togglePlay(context: Context, audioSource: String, durationEstimateSeconds: Int = 0) {
        val cleanSource = audioSource.trim()
        if (cleanSource.isBlank()) return

        // If the same track is active
        if (_currentTrackUrl.value == cleanSource && mediaPlayer != null) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
            return
        }

        // Start playing new track
        play(context, cleanSource, durationEstimateSeconds)
    }

    fun play(context: Context, audioSource: String, durationEstimateSeconds: Int = 0) {
        stop()

        _currentTrackUrl.value = audioSource
        _totalDurationMs.value = durationEstimateSeconds * 1000
        _playbackPositionMs.value = 0

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                if (audioSource.startsWith("http://") || audioSource.startsWith("https://")) {
                    setDataSource(audioSource)
                } else {
                    val fileUri = if (audioSource.startsWith("file://")) {
                        Uri.parse(audioSource)
                    } else {
                        Uri.fromFile(File(audioSource))
                    }
                    setDataSource(context, fileUri)
                }

                setOnPreparedListener { mp ->
                    _totalDurationMs.value = mp.duration.coerceAtLeast(durationEstimateSeconds * 1000)
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracker()
                }

                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackPositionMs.value = _totalDurationMs.value
                    progressJob?.cancel()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    true
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize playback for $audioSource: ${e.message}", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
        progressJob?.cancel()
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _playbackPositionMs.value = positionMs
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentTrackUrl.value = null
            _playbackPositionMs.value = 0
            _totalDurationMs.value = 0
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _playbackPositionMs.value = mp.currentPosition
                    }
                }
                delay(100)
            }
        }
    }
}
