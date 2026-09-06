package com.example.visionbridge.webrtc

import android.util.Log
import com.example.visionbridge.api.VolunteerApi
import com.example.visionbridge.data.HelpRequest
import com.example.visionbridge.supabase.SupabaseRealtimeClient
import org.json.JSONObject

class WebRtcSignalingClient {

    private val realtimeClient = SupabaseRealtimeClient()
    private var currentRoomId: String? = null

    interface SignalingListener {
        fun onRequestAccepted(request: HelpRequest) {}
        fun onRequestUpdated(request: HelpRequest) {}
        fun onRequestCancelled(request: HelpRequest? = null) {}
        fun onRequestCompleted(request: HelpRequest? = null) {}
        fun onRequestRejected(request: HelpRequest? = null) {}
        fun onOfferReceived(sdp: String, type: String)
        fun onAnswerReceived(sdp: String, type: String)
        fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String)
        fun onCallEnded()
        fun onConnected() {}
        fun onDisconnected() {}
    }

    private var listener: SignalingListener? = null

    fun connect(roomId: String, listener: SignalingListener, resolvedBaseUrl: String? = null, authToken: String? = null) {
        this.currentRoomId = roomId
        this.listener = listener

        Log.d(TAG, "Connecting to Supabase Realtime for room/channel $roomId (authenticated=${!authToken.isNullOrBlank()})")

        realtimeClient.connectAndSubscribe(
            channelName = "request:$roomId",
            authToken = authToken,
            listener = object : SupabaseRealtimeClient.RealtimeEventListener {
                override fun onConnected() {
                    Log.d(TAG, "Supabase Realtime connected on room $roomId")
                    listener.onConnected()
                    // Send initial ping
                    val pingPayload = JSONObject().put("roomId", roomId)
                    realtimeClient.broadcast("call:ping", pingPayload)
                }

                override fun onDisconnected() {
                    Log.d(TAG, "Supabase Realtime disconnected on room $roomId")
                    listener.onDisconnected()
                }

                override fun onBroadcastReceived(event: String, payload: JSONObject) {
                    when (event) {
                        "call:ping" -> {
                            Log.d(TAG, "Received call:ping -> sending call:pong")
                            val pongPayload = JSONObject().put("roomId", roomId)
                            realtimeClient.broadcast("call:pong", pongPayload)
                        }

                        "call:pong" -> {
                            Log.d(TAG, "Received call:pong")
                        }

                        "call:offer" -> {
                            try {
                                val targetObj = if (payload.has("offer") && payload.optJSONObject("offer") != null) {
                                    payload.getJSONObject("offer")
                                } else {
                                    payload
                                }
                                val sdp = targetObj.optString("sdp", "")
                                val type = targetObj.optString("type", "offer")
                                if (sdp.isNotBlank()) {
                                    Log.i(TAG, "Received call:offer (sdp length: ${sdp.length})")
                                    listener.onOfferReceived(sdp, type)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing call:offer", e)
                            }
                        }

                        "call:answer" -> {
                            try {
                                val targetObj = if (payload.has("answer") && payload.optJSONObject("answer") != null) {
                                    payload.getJSONObject("answer")
                                } else {
                                    payload
                                }
                                val sdp = targetObj.optString("sdp", "")
                                val type = targetObj.optString("type", "answer")
                                if (sdp.isNotBlank()) {
                                    Log.i(TAG, "Received call:answer (sdp length: ${sdp.length})")
                                    listener.onAnswerReceived(sdp, type)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing call:answer", e)
                            }
                        }

                        "call:ice-candidate" -> {
                            try {
                                val targetObj = if (payload.has("candidate") && payload.optJSONObject("candidate") != null) {
                                    payload.getJSONObject("candidate")
                                } else {
                                    payload
                                }
                                val sdpMid = targetObj.optString("sdpMid", "0")
                                val sdpMLineIndex = targetObj.optInt("sdpMLineIndex", 0)
                                val candidateStr = if (targetObj.has("candidate") && targetObj.get("candidate") is String) {
                                    targetObj.getString("candidate")
                                } else if (payload.has("candidate") && payload.get("candidate") is String) {
                                    payload.getString("candidate")
                                } else {
                                    targetObj.optString("sdp", "")
                                }

                                if (candidateStr.isNotBlank()) {
                                    Log.d(TAG, "Received call:ice-candidate (sdpMid=$sdpMid, index=$sdpMLineIndex)")
                                    listener.onIceCandidateReceived(sdpMid, sdpMLineIndex, candidateStr)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing call:ice-candidate", e)
                            }
                        }

                        "call:ended" -> {
                            Log.i(TAG, "Received call:ended from peer")
                            listener.onCallEnded()
                        }

                        "request_accepted" -> {
                            try {
                                val req = VolunteerApi.parseHelpRequest(payload)
                                listener.onRequestAccepted(req)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing request_accepted", e)
                            }
                        }

                        "request_updated" -> {
                            try {
                                val req = VolunteerApi.parseHelpRequest(payload)
                                listener.onRequestUpdated(req)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing request_updated", e)
                            }
                        }

                        "request_cancelled" -> {
                            val req = try { VolunteerApi.parseHelpRequest(payload) } catch (_: Exception) { null }
                            listener.onRequestCancelled(req)
                        }
                        "request_completed" -> {
                            val req = try { VolunteerApi.parseHelpRequest(payload) } catch (_: Exception) { null }
                            listener.onRequestCompleted(req)
                        }
                        "request_rejected" -> {
                            val req = try { VolunteerApi.parseHelpRequest(payload) } catch (_: Exception) { null }
                            listener.onRequestRejected(req)
                        }
                    }
                }

                override fun onPostgresChangeReceived(
                    event: String,
                    schema: String,
                    table: String,
                    record: JSONObject
                ) {
                    if (table == "help_requests") {
                        try {
                            val req = VolunteerApi.parseHelpRequest(record)
                            if (req.id == currentRoomId || currentRoomId == "dashboard" || currentRoomId.isNullOrBlank()) {
                                when (req.status.uppercase()) {
                                    "ACCEPTED" -> listener.onRequestAccepted(req)
                                    "CANCELLED" -> listener.onRequestCancelled(req)
                                    "COMPLETED" -> listener.onRequestCompleted(req)
                                    "REJECTED" -> listener.onRequestRejected(req)
                                    else -> listener.onRequestUpdated(req)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing Postgres change on help_requests", e)
                        }
                    }
                }
            }
        )
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
        realtimeClient.broadcast("call:offer", data)
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
        realtimeClient.broadcast("call:answer", data)
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
        realtimeClient.broadcast("call:ice-candidate", data)
        Log.d(TAG, "Sent call:ice-candidate to room $roomId")
    }

    fun sendCallEnded() {
        val roomId = currentRoomId ?: return
        val data = JSONObject().put("roomId", roomId)
        realtimeClient.broadcast("call:ended", data)
        Log.d(TAG, "Sent call:ended to room $roomId")
    }

    fun disconnect() {
        try {
            realtimeClient.disconnect()
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
