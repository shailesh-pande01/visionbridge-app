package com.example.visionbridge.ocr

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStream
import kotlin.math.min

/**
 * Singleton orchestrator for offline OCR inference on Android.
 *
 * Responsibilities:
 * - Owns single OrtEnvironment and OrtSessions for detector and recognizer.
 * - Thread-safe execution under a Mutex on Dispatchers.Default.
 * - Automatic session release on ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, reload lazily.
 * - Warm up on ViewModel initialization.
 * - Structured privacy-safe logging under tag "VB-OfflineOcr".
 */
class OfflineOcrEngine private constructor(context: Context) : ComponentCallbacks2 {

    private val appContext: Context = context.applicationContext
    private val mutex = Mutex()

    private var ortEnv: OrtEnvironment? = null
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var detector: TextDetector? = null
    private var recognizer: TextRecognizer? = null

    private var isInitialized = false
    private var modelLoadTimeMs: Long = 0L

    init {
        appContext.registerComponentCallbacks(this)
    }

    private fun ensureInitializedLocked() {
        if (isInitialized && detSession != null && recSession != null) {
            return
        }

        val start = System.currentTimeMillis()
        val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }

        val cores = Runtime.getRuntime().availableProcessors()
        val threads = min(4, maxOf(1, cores))

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        val detBytes = readAssetBytes("ocr/ppocrv5_mobile_det.onnx")
        val recBytes = readAssetBytes("ocr/en_rec.onnx")

        val det = env.createSession(detBytes, sessionOptions)
        val rec = env.createSession(recBytes, sessionOptions)

        // Read character dictionary
        val dictJsonStr = readAssetString("ocr/en_rec_dict.json")
        val jsonArray = JSONArray(dictJsonStr)
        val charList = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            charList.add(jsonArray.getString(i))
        }

        val ctcDecoder = CtcDecoder(charList)
        val textDetector = TextDetector(det, env)
        val textRecognizer = TextRecognizer(rec, env, ctcDecoder)

        detSession = det
        recSession = rec
        detector = textDetector
        recognizer = textRecognizer
        isInitialized = true

        modelLoadTimeMs = System.currentTimeMillis() - start
        Log.i(TAG, "Models loaded in ${modelLoadTimeMs}ms (threads=$threads, dictChars=${charList.size})")
    }

    private fun readAssetBytes(path: String): ByteArray {
        return appContext.assets.open(path).use { it.readBytes() }
    }

    private fun readAssetString(path: String): String {
        return appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    suspend fun warmUp() = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureInitializedLocked()
            Log.d(TAG, "Engine warmed up successfully")
        }
    }

    suspend fun read(bitmap: Bitmap): OcrOutput = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureInitializedLocked()

            val overallStart = System.currentTimeMillis()
            val detInstance = checkNotNull(detector) { "TextDetector not initialized" }
            val recInstance = checkNotNull(recognizer) { "TextRecognizer not initialized" }

            // 1. Detection
            val detResult = detInstance.detect(bitmap)

            // 2. Recognition
            val recResult = recInstance.recognize(bitmap, detResult.boxes)

            val totalElapsedMs = System.currentTimeMillis() - overallStart
            val lines = recResult.lines

            // Full text and character-weighted overall confidence
            val fullText = lines.joinToString("\n") { it.text }
            var totalChars = 0
            var weightedScoreSum = 0.0

            for (line in lines) {
                val len = line.text.length
                totalChars += len
                weightedScoreSum += (line.score * len)
            }

            val overallConfidence = if (totalChars > 0) {
                weightedScoreSum / totalChars
            } else {
                0.0
            }

            val timings = mapOf(
                "modelLoadMs" to modelLoadTimeMs,
                "detPrepMs" to detResult.prepTimeMs,
                "detInferMs" to detResult.inferTimeMs,
                "detPostMs" to detResult.postTimeMs,
                "recPrepMs" to recResult.prepTimeMs,
                "recInferMs" to recResult.inferTimeMs,
                "recPostMs" to recResult.postTimeMs,
                "totalMs" to totalElapsedMs
            )

            // Privacy-safe logging: log lengths and metrics only, NEVER the text content or images
            Log.i(
                TAG,
                "OCR complete: size=${bitmap.width}x${bitmap.height}, boxes=${detResult.boxes.size}, " +
                        "lines=${lines.size}, chars=${fullText.length}, conf=${"%.3f".format(overallConfidence)}, " +
                        "detMs=${detResult.inferTimeMs}, recMs=${recResult.inferTimeMs}, totalMs=${totalElapsedMs}ms"
            )

            OcrOutput(
                lines = lines,
                fullText = fullText,
                confidence = overallConfidence,
                timingsMs = timings,
                modelId = "PP-OCRv5-en"
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // Under lock, release sessions to free native memory
            CoroutineScope(Dispatchers.Default).launch {
                mutex.withLock {
                    try {
                        detSession?.close()
                        recSession?.close()
                        detSession = null
                        recSession = null
                        detector = null
                        recognizer = null
                        isInitialized = false
                        Log.i(TAG, "Released OrtSessions on onTrimMemory (level=$level)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error releasing OrtSessions", e)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    companion object {
        private const val TAG = "VB-OfflineOcr"

        @Volatile
        private var INSTANCE: OfflineOcrEngine? = null

        fun getInstance(context: Context): OfflineOcrEngine {
            val app = context.applicationContext
            com.example.visionbridge.data.SmartReadingRepository.appContext = app
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineOcrEngine(app).also { INSTANCE = it }
            }
        }
    }
}
