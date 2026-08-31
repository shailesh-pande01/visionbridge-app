package com.example.visionbridge.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Finds the running VisionBridge backend instead of assuming an address.
 *
 * Each candidate from [Config.candidateBaseUrls] is probed with the backend's own
 * `GET /api/health` route. The first one that answers is cached for the process; if a
 * later request fails at the socket level the cache is dropped so the next attempt
 * re-probes (the phone may have moved between USB and Wi-Fi).
 */
object BackendLocator {

    private const val TAG = "VB-Backend"
    private const val PROBE_TIMEOUT_SECONDS = 45L

    @Volatile
    private var cachedBaseUrl: String? = null

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Result of locating the backend: either a usable base URL or why none worked. */
    sealed interface Resolution {
        data class Found(val baseUrl: String) : Resolution
        data class NotFound(val attempts: List<String>) : Resolution
    }

    /** Blocking — call from a background dispatcher. */
    fun resolve(): Resolution {
        cachedBaseUrl?.let { return Resolution.Found(it) }

        val attempts = mutableListOf<String>()

        for (baseUrl in Config.candidateBaseUrls) {
            val outcome = probe(baseUrl)
            attempts += "$baseUrl → $outcome"

            if (outcome == PROBE_OK) {
                Log.i(TAG, "Backend located at $baseUrl (emulator=${Config.isEmulator})")
                cachedBaseUrl = baseUrl
                return Resolution.Found(baseUrl)
            }
            Log.w(TAG, "Health probe failed: $baseUrl → $outcome")
        }

        return Resolution.NotFound(attempts)
    }

    /** Called when a request fails at the socket level, so the next call re-probes. */
    fun invalidate() {
        if (cachedBaseUrl != null) {
            Log.w(TAG, "Dropping cached backend URL $cachedBaseUrl — will re-probe")
            cachedBaseUrl = null
        }
    }

    private const val PROBE_OK = "ok"

    private fun probe(baseUrl: String): String {
        val request = Request.Builder()
            .url(baseUrl + Config.HEALTH_PATH)
            .get()
            .build()

        return try {
            probeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) PROBE_OK else "HTTP ${response.code}"
            }
        } catch (e: IOException) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
