package com.example.visionbridge.supabase

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SupabaseRealtimeClient {

    interface RealtimeEventListener {
        fun onConnected() {}
        fun onDisconnected() {}
        fun onBroadcastReceived(event: String, payload: JSONObject) {}
        fun onPostgresChangeReceived(event: String, schema: String, table: String, record: JSONObject) {}
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var listener: RealtimeEventListener? = null
    private var currentTopic: String? = null
    private val refCounter = AtomicInteger(1)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var isConnected = false
    private var isSubscribed = false

    fun connectAndSubscribe(
        channelName: String,
        listener: RealtimeEventListener,
        authToken: String? = null
    ) {
        this.listener = listener
        this.currentTopic = "realtime:$channelName"

        disconnect()

        val wsUrl = SupabaseConfig.REALTIME_WS_URL
        Log.d(TAG, "Connecting to Supabase Realtime: $wsUrl for topic $currentTopic")

        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                isConnected = true
                startHeartbeat()
                joinTopic(currentTopic ?: "realtime:$channelName", authToken)
                mainHandler.post { listener.onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                isSubscribed = false
                stopHeartbeat()
                mainHandler.post { listener.onDisconnected() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                isConnected = false
                isSubscribed = false
                stopHeartbeat()
                mainHandler.post { listener.onDisconnected() }
            }
        })
    }

    fun broadcast(event: String, payload: JSONObject) {
        val topic = currentTopic ?: return
        val ref = refCounter.getAndIncrement().toString()

        val broadcastPayload = JSONObject().apply {
            put("type", "broadcast")
            put("event", event)
            put("payload", payload)
        }

        val message = JSONObject().apply {
            put("topic", topic)
            put("event", "broadcast")
            put("payload", broadcastPayload)
            put("ref", ref)
        }

        Log.d(TAG, "Sending Realtime Broadcast event: $event on topic $topic")
        webSocket?.send(message.toString())
    }

    fun disconnect() {
        stopHeartbeat()
        isConnected = false
        isSubscribed = false
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (_: Exception) {}
        webSocket = null
    }

    private fun joinTopic(topic: String, authToken: String?) {
        val ref = refCounter.getAndIncrement().toString()
        val joinPayload = JSONObject().apply {
            put("config", JSONObject().apply {
                put("broadcast", JSONObject().apply {
                    put("self", false)
                    put("ack", false)
                })
                put("presence", JSONObject().apply {
                    put("key", "")
                })
                
                val postgresChanges = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("event", "*")
                        put("schema", "public")
                        put("table", "help_requests")
                    })
                }
                put("postgres_changes", postgresChanges)
            })
            if (!authToken.isNullOrBlank()) {
                put("access_token", authToken)
            }
        }

        val joinMessage = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", joinPayload)
            put("ref", ref)
        }

        Log.d(TAG, "Joining topic: $topic (ref: $ref)")
        webSocket?.send(joinMessage.toString())
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val topic = json.optString("topic", "")
            val event = json.optString("event", "")
            val payload = json.optJSONObject("payload") ?: JSONObject()

            if (event == "phx_reply") {
                val status = payload.optString("status", "")
                if (status == "ok") {
                    Log.d(TAG, "Joined topic $topic successfully")
                    isSubscribed = true
                }
                return
            }

            if (event == "broadcast") {
                val innerEvent = payload.optString("event", "")
                val innerPayload = payload.optJSONObject("payload") ?: JSONObject()
                Log.d(TAG, "Received Broadcast: $innerEvent on $topic")
                mainHandler.post { listener?.onBroadcastReceived(innerEvent, innerPayload) }
                return
            }

            if (event == "postgres_changes") {
                val data = payload.optJSONObject("data") ?: JSONObject()
                val eventType = data.optString("type", "")
                val schema = data.optString("schema", "public")
                val table = data.optString("table", "")
                val record = data.optJSONObject("record") ?: data.optJSONObject("new") ?: JSONObject()

                Log.d(TAG, "Received Postgres Changes: $eventType on $table")
                mainHandler.post { listener?.onPostgresChangeReceived(eventType, schema, table, record) }
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Realtime message: $text", e)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    val ref = refCounter.getAndIncrement().toString()
                    val pingMessage = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", ref)
                    }
                    webSocket?.send(pingMessage.toString())
                    mainHandler.postDelayed(this, 25000)
                }
            }
        }
        mainHandler.postDelayed(heartbeatRunnable!!, 25000)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { mainHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    companion object {
        private const val TAG = "VB-RealtimeClient"
    }
}
