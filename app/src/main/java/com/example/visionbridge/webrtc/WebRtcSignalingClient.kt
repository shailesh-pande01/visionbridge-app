package com.example.visionbridge.webrtc

import android.util.Log
import com.example.visionbridge.api.BackendLocator
import com.example.visionbridge.api.VolunteerApi
import com.example.visionbridge.data.HelpRequest
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class WebRtcSignalingClient {

    private var socket: Socket? = null
    private var currentRoomId: String? = null

    interface SignalingListener {
        fun onRequestAccepted(request: HelpRequest) {}
        fun onRequestUpdated(request: HelpRequest) {}
        fun onRequestCancelled() {}
        fun onRequestCompleted() {}
        fun onRequestRejected() {}
        fun onOfferReceived(sdp: String, type: String)
        fun onAnswerReceived(sdp: String, type: String)
        fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String)
        fun onCallEnded()
        fun onConnected() {}
        fun onDisconnected() {}
    }

    private var listener: SignalingListener? = null

    fun connect(roomId: String, listener: SignalingListener, resolvedBaseUrl: String? = null) {
        this.currentRoomId = roomId
        this.listener = listener

        val baseUrl = resolvedBaseUrl ?: when (val res = BackendLocator.resolve()) {
            is BackendLocator.Resolution.Found -> res.baseUrl
            is BackendLocator.Resolution.NotFound -> "http://10.0.2.2:5000"
        }

        try {
            Log.d(TAG, "Connecting to Socket.io at $baseUrl for room $roomId")
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 1000
                transports = arrayOf("websocket", "polling")
            }
            socket = IO.socket(baseUrl, opts).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Socket connected: ${id()}")
                    emit("join_request_room", roomId)
                    val pingPayload = JSONObject().put("roomId", roomId)
                    socket?.emit("call:ping", pingPayload)
                    Log.d(TAG, "Emitted join_request_room & call:ping to room $roomId")
                    listener.onConnected()
                }

                // ── Help Request Lifecycle Events ──
                on("request_accepted") { args ->
                    Log.d(TAG, "Received socket event: request_accepted")
                    if (args.isNotEmpty() && args[0] is JSONObject) {
                        try {
                            val req = VolunteerApi.parseHelpRequest(args[0] as JSONObject)
                            Log.d(TAG, "Parsed accepted request: ID=${req.id}, Volunteer=${req.volunteerName}")
                            listener.onRequestAccepted(req)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing request_accepted payload", e)
                        }
                    }
                }

                on("request_updated") { args ->
                    Log.d(TAG, "Received socket event: request_updated")
                    if (args.isNotEmpty() && args[0] is JSONObject) {
                        try {
                            val req = VolunteerApi.parseHelpRequest(args[0] as JSONObject)
                            if (req.id == currentRoomId || currentRoomId.isNullOrBlank()) {
                                Log.d(TAG, "Parsed updated request: ID=${req.id}, Status=${req.status}")
                                listener.onRequestUpdated(req)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing request_updated payload", e)
                        }
                    }
                }

                on("request_cancelled") {
                    Log.d(TAG, "Received socket event: request_cancelled")
                    listener.onRequestCancelled()
                }

                on("request_completed") {
                    Log.d(TAG, "Received socket event: request_completed")
                    listener.onRequestCompleted()
                }

                on("request_rejected") {
                    Log.d(TAG, "Received socket event: request_rejected")
                    listener.onRequestRejected()
                }

                // ── WebRTC Signaling Events ──
                on("call:ping") {
                    Log.d(TAG, "Received call:ping -> sending call:pong")
                    socket?.emit("call:pong", JSONObject().put("roomId", roomId))
                }

                on("call:pong") {
                    Log.d(TAG, "Received call:pong")
                }

                on("call:offer") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val raw = args[0]
                            val obj = if (raw is JSONObject) raw else JSONObject(raw.toString())
                            val targetObj = if (obj.has("offer") && obj.optJSONObject("offer") != null) {
                                obj.getJSONObject("offer")
                            } else {
                                obj
                            }
                            val sdp = targetObj.optString("sdp", "")
                            val type = targetObj.optString("type", "offer")
                            if (sdp.isNotBlank()) {
                                Log.i(TAG, "Received call:offer (sdp length: ${sdp.length})")
                                listener.onOfferReceived(sdp, type)
                            } else {
                                Log.w(TAG, "Received call:offer with blank sdp: $obj")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing call:offer payload", e)
                        }
                    }
                }

                on("call:answer") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val raw = args[0]
                            val obj = if (raw is JSONObject) raw else JSONObject(raw.toString())
                            val targetObj = if (obj.has("answer") && obj.optJSONObject("answer") != null) {
                                obj.getJSONObject("answer")
                            } else {
                                obj
                            }
                            val sdp = targetObj.optString("sdp", "")
                            val type = targetObj.optString("type", "answer")
                            if (sdp.isNotBlank()) {
                                Log.i(TAG, "Received call:answer (sdp length: ${sdp.length})")
                                listener.onAnswerReceived(sdp, type)
                            } else {
                                Log.w(TAG, "Received call:answer with blank sdp: $obj")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing call:answer payload", e)
                        }
                    }
                }

                on("call:ice-candidate") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val raw = args[0]
                            val obj = if (raw is JSONObject) raw else JSONObject(raw.toString())
                            
                            val targetObj = if (obj.has("candidate") && obj.optJSONObject("candidate") != null) {
                                obj.getJSONObject("candidate")
                            } else {
                                obj
                            }

                            val sdpMid = if (targetObj.has("sdpMid") && !targetObj.isNull("sdpMid")) {
                                targetObj.optString("sdpMid", "0")
                            } else {
                                "0"
                            }
                            val sdpMLineIndex = targetObj.optInt("sdpMLineIndex", 0)
                            val candidateStr = if (targetObj.has("candidate") && targetObj.get("candidate") is String) {
                                targetObj.getString("candidate")
                            } else if (obj.has("candidate") && obj.get("candidate") is String) {
                                obj.getString("candidate")
                            } else {
                                targetObj.optString("sdp", "")
                            }

                            if (candidateStr.isNotBlank()) {
                                Log.d(TAG, "Received valid call:ice-candidate (sdpMid=$sdpMid, index=$sdpMLineIndex, cand=${candidateStr.take(35)}...)")
                                listener.onIceCandidateReceived(sdpMid, sdpMLineIndex, candidateStr)
                            } else {
                                Log.w(TAG, "Received call:ice-candidate with empty candidate string: $obj")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing call:ice-candidate payload", e)
                        }
                    }
                }

                on("call:ended") {
                    Log.i(TAG, "Received call:ended from peer")
                    listener.onCallEnded()
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Socket disconnected")
                    listener.onDisconnected()
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val err = if (args.isNotEmpty()) args[0].toString() else "Unknown"
                    Log.w(TAG, "Socket connect error: $err")
                }

                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting socket", e)
        }
    }

    fun sendOffer(sdp: String) {
        val roomId = currentRoomId ?: return
        val offerObj = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdp)
        }
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("offer", offerObj)
        }
        socket?.emit("call:offer", data)
        Log.d(TAG, "Sent call:offer to room $roomId")
    }

    fun sendAnswer(sdp: String) {
        val roomId = currentRoomId ?: return
        val answerObj = JSONObject().apply {
            put("type", "answer")
            put("sdp", sdp)
        }
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("answer", answerObj)
        }
        socket?.emit("call:answer", data)
        Log.d(TAG, "Sent call:answer to room $roomId")
    }

    fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val roomId = currentRoomId ?: return
        val candObj = JSONObject().apply {
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("candidate", candObj)
        }
        socket?.emit("call:ice-candidate", data)
        Log.d(TAG, "Sent call:ice-candidate to room $roomId")
    }

    fun sendCallEnded() {
        val roomId = currentRoomId ?: return
        val data = JSONObject().put("roomId", roomId)
        socket?.emit("call:ended", data)
        Log.d(TAG, "Sent call:ended to room $roomId")
    }

    fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            currentRoomId = null
            listener = null
            Log.d(TAG, "Signaling client disconnected and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error in disconnect", e)
        }
    }

    companion object {
        private const val TAG = "VB-Signaling"
    }
}
