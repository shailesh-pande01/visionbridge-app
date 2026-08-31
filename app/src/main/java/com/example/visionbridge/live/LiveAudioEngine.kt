package com.example.visionbridge.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.util.Base64
import android.util.Log
import com.example.visionbridge.audio.ActiveAudioFeature
import com.example.visionbridge.audio.EntertainmentAudioSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * High-performance, ultra low-latency audio capture and playback engine for Gemini Live.
 *
 * Input Pipeline:
 *   Microphone -> 16kHz 16-bit PCM Mono -> Hardware AEC/NS/AGC -> Playback-Aware Dynamic VAD
 *   -> Base64 chunks (~45ms) -> Gemini Live WebSocket (audio/pcm;rate=16000)
 *
 * Output Pipeline:
 *   Gemini Live 24kHz 16-bit PCM Mono -> AudioTrack (MODE_STREAM) -> Speaker / Headset
 *   with Instant Barge-in / Interruption.
 */
class LiveAudioEngine(
    private val context: Context,
    private val onAudioChunk: (base64Pcm: String) -> Unit,
    private val onBargeIn: (rms: Float) -> Unit,
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit,
    private val onLevel: (rms: Float) -> Unit,
    private val onPlaybackStateChange: (isPlaying: Boolean) -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // AudioRecord (Capture)
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    val isMuted = AtomicBoolean(false)
    val isAiSpeaking = AtomicBoolean(false)

    // AudioTrack (Playback)
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isPlaying = AtomicBoolean(false)
    private val isPlaybackRunning = AtomicBoolean(false)

    // Dynamic VAD state
    private var isUserSpeaking = false
    private var lastVoiceDetectedTime = 0L
    private var silenceMonitorThread: Thread? = null

    // Diagnostics
    var aecActive = false
        private set
    var nsActive = false
        private set
    var agcActive = false
        private set

    // ── Public Lifecycle APIs ──────────────────────────────────────────

    /**
     * Initializes hardware audio pipelines, claims exclusive audio focus,
     * and starts recording and playback threads.
     */
    @Synchronized
    fun start() {
        if (isRecording.get()) return

        Log.d(TAG, "Starting LiveAudioEngine...")

        // 1. Claim exclusive audio session (pauses Radio, Stories, Games)
        EntertainmentAudioSession.requestSession(ActiveAudioFeature.ASSISTANT)
        requestAudioFocus()

        // 2. Start AudioTrack Playback Engine (24kHz PCM Mono)
        initAudioTrack()

        // 3. Start AudioRecord Capture Engine (16kHz PCM Mono)
        initAudioRecord()

        isRecording.set(true)
        isPlaybackRunning.set(true)

        // Launch dedicated capture worker thread with THREAD_PRIORITY_URGENT_AUDIO
        recordingThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            recordLoop()
        }, "LiveAudio-RecordThread").apply { start() }

        // Launch dedicated playback worker thread
        playbackThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            playbackLoop()
        }, "LiveAudio-PlaybackThread").apply { start() }

        // Launch VAD silence monitor thread
        silenceMonitorThread = Thread({
            silenceMonitorLoop()
        }, "LiveAudio-VadSilenceThread").apply { start() }

        Log.d(TAG, "LiveAudioEngine started successfully. (AEC=$aecActive, NS=$nsActive, AGC=$agcActive)")
    }

    /**
     * Plays a Base64-encoded 24kHz 16-bit PCM chunk received from Gemini Live.
     */
    fun playChunk(base64Pcm: String) {
        if (base64Pcm.isBlank() || !isPlaybackRunning.get()) return
        try {
            val pcmBytes = Base64.decode(base64Pcm, Base64.DEFAULT)
            if (pcmBytes.isNotEmpty()) {
                playbackQueue.add(pcmBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode 24kHz audio chunk: ${e.message}")
        }
    }

    /**
     * Instantly flushes pending playback audio and pauses track (Barge-in / Interrupt).
     */
    fun interrupt() {
        playbackQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing AudioTrack during interrupt: ${e.message}")
        }

        if (isPlaying.getAndSet(false)) {
            isAiSpeaking.set(false)
            onPlaybackStateChange(false)
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
        Log.d(TAG, "Microphone muted: $muted")
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "Stopping LiveAudioEngine...")
        isRecording.set(false)
        isPlaybackRunning.set(false)
        isAiSpeaking.set(false)
        isUserSpeaking = false

        interrupt()

        // Stop AudioRecord
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        // Stop AudioTrack
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }

        // Release hardware audiofx
        try {
            aec?.release()
            ns?.release()
            agc?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects: ${e.message}")
        }
        aec = null
        ns = null
        agc = null

        // Release AudioRecord & AudioTrack
        try {
            audioRecord?.release()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio track/record: ${e.message}")
        }
        audioRecord = null
        audioTrack = null

        // Interrupt threads
        recordingThread?.interrupt()
        playbackThread?.interrupt()
        silenceMonitorThread?.interrupt()
        recordingThread = null
        playbackThread = null
        silenceMonitorThread = null

        // Release exclusive session & audio focus
        abandonAudioFocus()
        EntertainmentAudioSession.releaseSession(ActiveAudioFeature.ASSISTANT)

        Log.d(TAG, "LiveAudioEngine stopped.")
    }

    // ── Internal Audio Capture Loop ───────────────────────────────────

    private fun initAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE_BYTES * 4)

        // Prefer VOICE_COMMUNICATION for built-in platform echo cancellation
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open VOICE_COMMUNICATION audio source, falling back to MIC", e)
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }

        val sessionId = record.audioSessionId

        // Attach hardware AcousticEchoCanceler if available
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(sessionId)?.apply {
                enabled = true
                aecActive = enabled
            }
        }

        // Attach NoiseSuppressor if available
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(sessionId)?.apply {
                enabled = true
                nsActive = enabled
            }
        }

        // Attach AutomaticGainControl if available
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(sessionId)?.apply {
                enabled = true
                agcActive = enabled
            }
        }

        record.startRecording()
        audioRecord = record
    }

    private fun recordLoop() {
        val record = audioRecord ?: return
        val audioBuffer = ShortArray(CHUNK_SAMPLES)
        val byteBuffer = ByteBuffer.allocate(CHUNK_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val silentBytes = ByteArray(CHUNK_SIZE_BYTES) // All zeros for bleed suppression

        while (isRecording.get() && !Thread.currentThread().isInterrupted) {
            val shortsRead = record.read(audioBuffer, 0, audioBuffer.size)
            if (shortsRead <= 0) continue

            if (isMuted.get()) continue

            // 1. Calculate RMS energy
            val rms = calculateRms(audioBuffer, shortsRead)
            onLevel(rms)

            val aiSpeaking = isAiSpeaking.get()
            val activeThreshold = if (aiSpeaking) VAD_BARGE_IN_THRESHOLD else VAD_NORMAL_THRESHOLD

            // 2. Playback-Aware Voice Activity Detection
            if (rms > activeThreshold) {
                lastVoiceDetectedTime = System.currentTimeMillis()

                if (aiSpeaking) {
                    // User is speaking while AI is speaking -> Instant Barge-in!
                    Log.d(TAG, "Barge-in triggered! User voice energy ($rms) > threshold ($activeThreshold)")
                    interrupt()
                    onBargeIn(rms)
                }

                if (!isUserSpeaking) {
                    isUserSpeaking = true
                    onSpeechStart()
                }
            }

            // 3. Speaker Echo / Bleed Suppression:
            // When AI is playing and user voice is below barge-in threshold,
            // send silent PCM frame to Gemini so Gemini never hears its own voice.
            if (aiSpeaking && rms < VAD_BARGE_IN_THRESHOLD) {
                val base64Silence = Base64.encodeToString(silentBytes, Base64.NO_WRAP)
                onAudioChunk(base64Silence)
                continue
            }

            // 4. Normal voice transmission
            byteBuffer.clear()
            for (i in 0 until shortsRead) {
                byteBuffer.putShort(audioBuffer[i])
            }
            val pcmBytes = byteBuffer.array()
            val base64 = Base64.encodeToString(pcmBytes, 0, shortsRead * 2, Base64.NO_WRAP)
            onAudioChunk(base64)
        }
    }

    private fun silenceMonitorLoop() {
        while (isRecording.get() && !Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                break
            }

            if (isUserSpeaking && lastVoiceDetectedTime > 0) {
                val elapsed = System.currentTimeMillis() - lastVoiceDetectedTime
                if (elapsed > SPEECH_SILENCE_TIMEOUT_MS) {
                    isUserSpeaking = false
                    onSpeechEnd()
                }
            }
        }
    }

    // ── Internal Audio Playback Loop ──────────────────────────────────

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, 24000 * 2 / 5) // ~200ms buffer

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(OUTPUT_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        audioTrack = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun playbackLoop() {
        val track = audioTrack ?: return
        try {
            track.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack play: ${e.message}")
        }

        while (isPlaybackRunning.get() && !Thread.currentThread().isInterrupted) {
            val chunk = playbackQueue.poll()
            if (chunk == null) {
                if (isPlaying.get() && playbackQueue.isEmpty()) {
                    // Small delay to verify stream end
                    try {
                        Thread.sleep(30)
                    } catch (e: InterruptedException) {
                        break
                    }
                    if (playbackQueue.isEmpty() && isPlaying.getAndSet(false)) {
                        isAiSpeaking.set(false)
                        onPlaybackStateChange(false)
                    }
                } else {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                continue
            }

            if (!isPlaying.getAndSet(true)) {
                isAiSpeaking.set(true)
                onPlaybackStateChange(true)
            }

            try {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                var bytesWritten = 0
                while (bytesWritten < chunk.size && isPlaybackRunning.get()) {
                    val written = track.write(chunk, bytesWritten, chunk.size - bytesWritten)
                    if (written > 0) {
                        bytesWritten += written
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack write error: ${e.message}")
            }
        }
    }

    // ── Audio Focus Helpers ───────────────────────────────────────────

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                    }
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> Log.d(TAG, "Audio focus changed: $focusChange") },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Older Android version compat fallback
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release audio focus", e)
        }
    }

    // ── Math & RMS Calculations ───────────────────────────────────────

    private fun calculateRms(buffer: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i] / 32768.0
            sum += sample * sample
        }
        return sqrt(sum / length).toFloat()
    }

    companion object {
        private const val TAG = "VB-LiveAudio"

        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000

        // 720 samples = 45ms chunk at 16kHz Mono
        const val CHUNK_SAMPLES = 720
        const val CHUNK_SIZE_BYTES = CHUNK_SAMPLES * 2

        const val VAD_NORMAL_THRESHOLD = 0.018f
        const val VAD_BARGE_IN_THRESHOLD = 0.055f
        const val SPEECH_SILENCE_TIMEOUT_MS = 380L
    }
}
