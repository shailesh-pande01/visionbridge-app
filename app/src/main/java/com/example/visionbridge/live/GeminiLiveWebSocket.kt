package com.example.visionbridge.live

import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket client for bidirectional streaming with Google's Gemini Multimodal Live API.
 *
 * Ephemeral Token Authentication:
 *   Connects to BidiGenerateContentConstrained using short-lived ephemeral credentials.
 *   The permanent API key is never used here.
 */
class GeminiLiveWebSocket(
    private val sessionData: LiveSessionData,
    private val onConnected: () -> Unit,
    private val onSetupComplete: () -> Unit,
    private val onAudioReceived: (base64Pcm: String) -> Unit,
    private val onTextTranscriptReceived: (text: String) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onToolCallReceived: (name: String, args: JSONObject) -> Unit,
    private val onDisconnected: (code: Int, reason: String, error: Throwable?) -> Unit
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for real-time WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    val isSetupComplete = AtomicBoolean(false)

    /**
     * Connects WebSocket to Gemini Live.
     */
    fun connect() {
        val baseEndpoint = sessionData.endpoint
        val token = sessionData.ephemeralToken

        val finalUrl = if (!token.isNullOrBlank()) {
            if (baseEndpoint.contains("?")) {
                "$baseEndpoint&access_token=${Uri.encode(token)}"
            } else {
                "$baseEndpoint?access_token=${Uri.encode(token)}"
            }
        } else {
            baseEndpoint
        }

        Log.d(TAG, "Connecting to Gemini Live WebSocket: $baseEndpoint (hasToken=${!token.isNullOrBlank()})...")

        val request = Request.Builder()
            .url(finalUrl)
            .build()

        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Gemini Live WebSocket OPEN! Sending setup handshake...")
            isConnected.set(true)
            onConnected()

            // Send setup handshake payload
            val setupJson = sessionData.setupPayload.toString()
            webSocket.send(setupJson)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleIncomingMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleIncomingMessage(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Gemini Live WebSocket closing: $code ($reason)")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Gemini Live WebSocket CLOSED: $code ($reason)")
            isConnected.set(false)
            isSetupComplete.set(false)
            onDisconnected(code, reason, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Gemini Live WebSocket FAILURE: ${t.message}")
            isConnected.set(false)
            isSetupComplete.set(false)
            onDisconnected(response?.code ?: -1, t.message ?: "Network error", t)
        }
    }

    private fun handleIncomingMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)

            // 1. Setup complete -> Unblock real-time media streaming!
            if (root.has("setupComplete")) {
                Log.d(TAG, "Setup complete received from Gemini! Live streaming active.")
                isSetupComplete.set(true)
                onSetupComplete()
                return
            }

            // 2. Server content / Model Turn
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Gemini server reported turn interrupted.")
                    onInterrupted()
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // 24kHz PCM Audio Chunk
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val base64Pcm = inlineData.optString("data")
                            if (base64Pcm.isNotBlank()) {
                                onAudioReceived(base64Pcm)
                            }
                        }

                        // Text transcript
                        if (part.has("text")) {
                            val text = part.optString("text")
                            if (text.isNotBlank()) {
                                onTextTranscriptReceived(text)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    onTurnComplete()
                }
            }

            // 3. Tool Calls
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()
                for (i in 0 until functionCalls.length()) {
                    val call = functionCalls.getJSONObject(i)
                    val name = call.optString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()
                    Log.d(TAG, "Received tool call from Gemini: $name args=$args")
                    onToolCallReceived(name, args)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Gemini Live message: ${e.message}")
        }
    }

    // ── Media Streaming Senders ────────────────────────────────────────

    /**
     * Streams 16kHz PCM audio chunk to Gemini.
     */
    fun sendRealtimeAudio(base64Pcm: String): Boolean {
        if (!isConnected.get() || !isSetupComplete.get() || base64Pcm.isBlank()) return false

        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Pcm)
                    })
                })
            })
        }

        return try {
            webSocket?.send(payload.toString()) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send audio chunk: ${e.message}")
            false
        }
    }

    /**
     * Streams JPEG camera frame to Gemini (Vision Live).
     */
    fun sendRealtimeFrame(base64Jpeg: String): Boolean {
        if (!isConnected.get() || !isSetupComplete.get() || base64Jpeg.isBlank()) return false

        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Jpeg)
                    })
                })
            })
        }

        return try {
            webSocket?.send(payload.toString()) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send camera frame: ${e.message}")
            false
        }
    }

    /**
     * Dispatches explicit user conversational turn (text query + optional fresh camera frame).
     */
    fun sendUserTurn(text: String, base64Jpeg: String? = null): Boolean {
        if (!isConnected.get() || !isSetupComplete.get()) return false

        val partsArray = JSONArray()

        if (!base64Jpeg.isNullOrBlank()) {
            partsArray.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Jpeg)
                })
            })
        }

        if (text.isNotBlank()) {
            partsArray.put(JSONObject().apply {
                put("text", text.trim())
            })
        }

        val payload = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", partsArray)
                    })
                })
                put("turnComplete", true)
            })
        }

        return try {
            webSocket?.send(payload.toString()) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send user turn", e)
            false
        }
    }

    /**
     * Closes the WebSocket connection cleanly.
     */
    fun close() {
        isConnected.set(false)
        isSetupComplete.set(false)
        try {
            webSocket?.close(1000, "Client session closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
    }

    companion object {
        private const val TAG = "VB-GeminiWs"
    }
}
