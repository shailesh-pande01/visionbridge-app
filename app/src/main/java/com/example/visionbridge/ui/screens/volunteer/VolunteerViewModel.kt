package com.example.visionbridge.ui.screens.volunteer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.VolunteerApi
import com.example.visionbridge.data.HelpRequest
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.utils.LocationHelper
import com.example.visionbridge.webrtc.WebRtcCallManager
import com.example.visionbridge.webrtc.WebRtcSignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

sealed interface VolunteerCallState {
    object Idle : VolunteerCallState
    object LocatingAndBroadcasting : VolunteerCallState
    data class SearchingVolunteer(val request: HelpRequest) : VolunteerCallState
    data class Accepted(val request: HelpRequest, val volunteerName: String? = null) : VolunteerCallState
    data class Connecting(val request: HelpRequest, val volunteerName: String? = null) : VolunteerCallState
    data class Connected(val request: HelpRequest, val isMuted: Boolean = false) : VolunteerCallState
    data class Completed(val message: String) : VolunteerCallState
    data class Failed(val message: String) : VolunteerCallState
}

class VolunteerViewModel(application: Application) : AndroidViewModel(application) {

    private val volunteerApi = VolunteerApi(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _callState = MutableStateFlow<VolunteerCallState>(VolunteerCallState.Idle)
    val callState: StateFlow<VolunteerCallState> = _callState.asStateFlow()

    private var activeRequest: HelpRequest? = null
    var callManager: WebRtcCallManager? = null
        private set
    private var signalingClient: WebRtcSignalingClient? = null
    private var pollingJob: Job? = null

    private var isMuted = false

    fun startHelpRequest(activityContext: android.content.Context, description: String = "Need visual assistance") {
        if (_callState.value is VolunteerCallState.LocatingAndBroadcasting ||
            _callState.value is VolunteerCallState.SearchingVolunteer ||
            _callState.value is VolunteerCallState.Accepted ||
            _callState.value is VolunteerCallState.Connected
        ) {
            Log.d(TAG, "Help request already active or in progress. State: ${_callState.value}")
            return
        }

        _callState.value = VolunteerCallState.LocatingAndBroadcasting
        viewModelScope.launch {
            val user = sessionManager.currentUser.value
            if (user == null || user.id.isBlank()) {
                _callState.value = VolunteerCallState.Failed("You must be signed in to request volunteer assistance.")
                return@launch
            }
            val userId = user.id
            val userName = user.name.ifBlank { "User" }

            val (lat, lng) = LocationHelper.getCurrentLocation(getApplication())

            val result = withContext(Dispatchers.IO) {
                volunteerApi.createRequest(
                    requester = userId,
                    requesterName = userName,
                    latitude = lat,
                    longitude = lng,
                    helpDescription = description
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    val request = result.value
                    activeRequest = request
                    Log.i(TAG, "Volunteer help request created successfully: ${request.id}, status=${request.status}")
                    
                    if (request.status.equals("ACCEPTED", ignoreCase = true)) {
                        _callState.value = VolunteerCallState.Accepted(request, request.volunteerName)
                    } else {
                        _callState.value = VolunteerCallState.SearchingVolunteer(request)
                    }

                    initiateSignalingAndPreparation(request, activityContext)
                    startStatusPolling(request.id)
                }
                is ApiResult.Failure -> {
                    Log.e(TAG, "Failed to create help request: ${result.error.userMessage}")
                    _callState.value = VolunteerCallState.Failed(result.error.userMessage)
                }
            }
        }
    }

    private fun initiateSignalingAndPreparation(request: HelpRequest, activityContext: android.content.Context) {
        val manager = WebRtcCallManager(activityContext, isVolunteer = false)
        callManager = manager

        val signaling = WebRtcSignalingClient()
        signalingClient = signaling

        viewModelScope.launch {
            // 1. Start camera and microphone tracks so peer connection is ready before signaling connects
            manager.startCall(object : WebRtcCallManager.CallEvents {
                override fun onIceCandidate(candidate: IceCandidate) {
                    signaling.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                }

                override fun onRemoteStreamAdded(mediaStream: MediaStream) {
                    Log.i(TAG, "[WEBRTC_CONNECTION] Remote media stream added (Volunteer audio received)")
                }

                override fun onCallConnected() {
                    Log.i(TAG, "[VOLUNTEER_CALL] WebRTC PeerConnection fully connected with volunteer!")
                    stopStatusPolling()
                    val req = activeRequest ?: request
                    _callState.value = VolunteerCallState.Connected(req, isMuted)
                    viewModelScope.launch(Dispatchers.IO) {
                        volunteerApi.startCallLog(req.id)
                    }
                }

                override fun onCallDisconnected() {
                    Log.i(TAG, "[VOLUNTEER_CALL] WebRTC call disconnected")
                    val cur = _callState.value
                    if (cur is VolunteerCallState.Connected || cur is VolunteerCallState.Connecting) {
                        endCall(isLocal = false)
                    }
                }
            })

            // 2. Connect signaling and await volunteer offer
            val token = sessionManager.token
            signaling.connect(request.id, object : WebRtcSignalingClient.SignalingListener {
                override fun onConnected() {
                    Log.i(TAG, "[WEBRTC_CONNECTION] Signaling connected to room ${request.id}")
                }

                override fun onRequestAccepted(request: HelpRequest) {
                    Log.i(TAG, "[VOLUNTEER_CALL] Received onRequestAccepted event: ${request.id}, Volunteer=${request.volunteerName}")
                    activeRequest = request
                    val cur = _callState.value
                    if (cur is VolunteerCallState.SearchingVolunteer || cur is VolunteerCallState.LocatingAndBroadcasting) {
                        _callState.value = VolunteerCallState.Accepted(request, request.volunteerName)
                    }
                }

                override fun onRequestUpdated(request: HelpRequest) {
                    Log.i(TAG, "[VOLUNTEER_CALL] Received onRequestUpdated event: ${request.id}, Status=${request.status}")
                    activeRequest = request
                    val status = request.status.uppercase()
                    when (status) {
                        "ACCEPTED", "ACTIVE" -> {
                            val cur = _callState.value
                            if (cur is VolunteerCallState.SearchingVolunteer || cur is VolunteerCallState.LocatingAndBroadcasting) {
                                _callState.value = VolunteerCallState.Accepted(request, request.volunteerName)
                            }
                        }
                        "COMPLETED" -> {
                            endCall(isLocal = false)
                        }
                        "CANCELLED" -> {
                            cleanup()
                            _callState.value = VolunteerCallState.Completed("Help request was cancelled.")
                        }
                        "REJECTED" -> {
                            cleanup()
                            _callState.value = VolunteerCallState.Failed("Volunteer was unable to take the call. Please try again.")
                        }
                    }
                }

                override fun onRequestCancelled(request: HelpRequest?) {
                    Log.i(TAG, "[VOLUNTEER_CALL] Received onRequestCancelled event")
                    cleanup()
                    _callState.value = VolunteerCallState.Completed("Help request cancelled.")
                }

                override fun onRequestCompleted(request: HelpRequest?) {
                    Log.i(TAG, "[VOLUNTEER_CALL] Received onRequestCompleted event")
                    cleanup()
                    _callState.value = VolunteerCallState.Completed("Assistance session finished.")
                }

                override fun onRequestRejected(request: HelpRequest?) {
                    Log.i(TAG, "[VOLUNTEER_CALL] Received onRequestRejected event")
                    cleanup()
                    _callState.value = VolunteerCallState.Failed("No volunteer available right now. Please try again.")
                }

                override fun onOfferReceived(sdp: String, type: String) {
                    Log.i(TAG, "[WEBRTC_CONNECTION] Received WebRTC offer from volunteer. Setting remote description and creating answer.")
                    val req = activeRequest ?: request
                    _callState.value = VolunteerCallState.Connecting(req, req.volunteerName)
                    
                    manager.setRemoteDescription(sdp, SessionDescription.Type.OFFER) {
                        manager.createAnswer { answer ->
                            Log.i(TAG, "[WEBRTC_CONNECTION] Created WebRTC answer. Sending to volunteer.")
                            signaling.sendAnswer(answer.description)
                        }
                    }
                }

                override fun onAnswerReceived(sdp: String, type: String) {
                    Log.i(TAG, "[WEBRTC_CONNECTION] Received WebRTC answer from peer")
                    manager.setRemoteDescription(sdp, SessionDescription.Type.ANSWER)
                }

                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
                    Log.d(TAG, "[WEBRTC_CONNECTION] Received ICE candidate from volunteer: $sdpMid ($sdpMLineIndex)")
                    manager.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                }

                override fun onCallEnded() {
                    Log.i(TAG, "[VOLUNTEER_CALL] Volunteer ended the call")
                    endCall(isLocal = false)
                }

                override fun onDisconnected() {
                    Log.w(TAG, "[WEBRTC_CONNECTION] Signaling socket disconnected")
                }
            }, authToken = token)
        }
    }

    private fun startStatusPolling(requestId: String) {
        stopStatusPolling()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting status polling fallback for request $requestId")
            while (isActive) {
                delay(3000)
                val curState = _callState.value
                if (curState !is VolunteerCallState.SearchingVolunteer &&
                    curState !is VolunteerCallState.Accepted &&
                    curState !is VolunteerCallState.Connecting
                ) {
                    Log.d(TAG, "Stopping polling: current state is $curState")
                    break
                }

                when (val res = volunteerApi.getRequestStatus(requestId)) {
                    is ApiResult.Success -> {
                        val latest = res.value
                        activeRequest = latest
                        val status = latest.status.uppercase()
                        Log.d(TAG, "Polled status for $requestId: $status, volunteer=${latest.volunteerName}")
                        
                        withContext(Dispatchers.Main) {
                            val stateNow = _callState.value
                            if ((status == "ACCEPTED" || status == "ACTIVE") &&
                                (stateNow is VolunteerCallState.SearchingVolunteer || stateNow is VolunteerCallState.LocatingAndBroadcasting)
                            ) {
                                Log.i(TAG, "Polling detected accepted request! Updating state to Accepted.")
                                _callState.value = VolunteerCallState.Accepted(latest, latest.volunteerName)
                            } else if (status == "COMPLETED" && stateNow !is VolunteerCallState.Completed) {
                                endCall(isLocal = false)
                            } else if (status == "CANCELLED" && stateNow !is VolunteerCallState.Completed) {
                                cleanup()
                                _callState.value = VolunteerCallState.Completed("Help request was cancelled.")
                            } else if (status == "REJECTED" && stateNow !is VolunteerCallState.Failed) {
                                cleanup()
                                _callState.value = VolunteerCallState.Failed("Volunteer was unable to accept. Please try again.")
                            }
                        }
                    }
                    is ApiResult.Failure -> {
                        Log.w(TAG, "Polling request status check returned failure: ${res.error.userMessage}")
                    }
                }
            }
        }
    }

    private fun stopStatusPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun toggleMute() {
        isMuted = !isMuted
        callManager?.setMuted(isMuted)
        val cur = _callState.value
        if (cur is VolunteerCallState.Connected) {
            _callState.value = cur.copy(isMuted = isMuted)
        }
    }

    fun cancelRequest() {
        val req = activeRequest
        stopStatusPolling()
        if (req != null) {
            viewModelScope.launch(Dispatchers.IO) {
                volunteerApi.cancelRequest(req.id)
            }
        }
        cleanup()
        _callState.value = VolunteerCallState.Completed("Help request cancelled.")
    }

    fun endCall(isLocal: Boolean = true) {
        val req = activeRequest
        stopStatusPolling()
        if (req != null) {
            if (isLocal) {
                signalingClient?.sendCallEnded()
            }
            viewModelScope.launch(Dispatchers.IO) {
                volunteerApi.completeRequest(req.id)
                volunteerApi.endCallLog(req.id, "COMPLETED")
            }
        }
        cleanup()
        _callState.value = VolunteerCallState.Completed("Assistance session finished.")
    }

    private fun cleanup() {
        try {
            stopStatusPolling()
            callManager?.cleanup()
            callManager = null
            signalingClient?.disconnect()
            signalingClient = null
            activeRequest = null
            Log.d(TAG, "VolunteerViewModel cleanup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error in VolunteerViewModel", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    companion object {
        private const val TAG = "VB-VolunteerVM"
    }
}
