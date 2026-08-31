package com.example.visionbridge.ui.screens.volunteer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visionbridge.api.ApiResult
import com.example.visionbridge.api.VolunteerApi
import com.example.visionbridge.data.HelpRequest
import com.example.visionbridge.data.SessionManager
import com.example.visionbridge.webrtc.WebRtcCallManager
import com.example.visionbridge.webrtc.WebRtcSignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

sealed interface VolunteerDashboardState {
    object Loading : VolunteerDashboardState
    data class RequestList(val requests: List<HelpRequest>) : VolunteerDashboardState
    data class InCall(val request: HelpRequest, val isMuted: Boolean = false) : VolunteerDashboardState
    data class Error(val message: String) : VolunteerDashboardState
}

class VolunteerDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val volunteerApi = VolunteerApi(application)
    private val sessionManager = SessionManager.getInstance(application)

    private val _dashboardState = MutableStateFlow<VolunteerDashboardState>(VolunteerDashboardState.Loading)
    val dashboardState: StateFlow<VolunteerDashboardState> = _dashboardState.asStateFlow()

    var callManager: WebRtcCallManager? = null
        private set
    private var signalingClient: WebRtcSignalingClient? = null
    private var activeRequest: HelpRequest? = null
    private var isMuted = false

    private val _remoteMediaStream = MutableStateFlow<MediaStream?>(null)
    val remoteMediaStream: StateFlow<MediaStream?> = _remoteMediaStream.asStateFlow()

    fun loadRequests() {
        _dashboardState.value = VolunteerDashboardState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                volunteerApi.getRequests()
            }
            when (result) {
                is ApiResult.Success -> {
                    _dashboardState.value = VolunteerDashboardState.RequestList(result.value)
                }
                is ApiResult.Failure -> {
                    _dashboardState.value = VolunteerDashboardState.Error(result.error.userMessage)
                }
            }
        }
    }

    fun acceptRequest(request: HelpRequest) {
        val user = sessionManager.currentUser.value
        val volunteerId = user?.id ?: "volunteer-1"

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                volunteerApi.acceptRequest(request.id, volunteerId)
            }
            when (result) {
                is ApiResult.Success -> {
                    activeRequest = request
                    startVolunteerCall(request)
                }
                is ApiResult.Failure -> {
                    _dashboardState.value = VolunteerDashboardState.Error(result.error.userMessage)
                }
            }
        }
    }

    private fun startVolunteerCall(request: HelpRequest) {
        val manager = WebRtcCallManager(getApplication(), isVolunteer = true)
        callManager = manager

        val signaling = WebRtcSignalingClient()
        signalingClient = signaling

        signaling.connect(request.id, object : WebRtcSignalingClient.SignalingListener {
            override fun onConnected() {
                Log.d("VolunteerDashVM", "Signaling connected to room ${request.id}. Creating offer.")
                manager.createOffer { offer ->
                    signaling.sendOffer(offer.description)
                }
            }

            override fun onOfferReceived(sdp: String, type: String) {
                manager.setRemoteDescription(sdp, SessionDescription.Type.OFFER) {
                    manager.createAnswer { answer ->
                        signaling.sendAnswer(answer.description)
                    }
                }
            }

            override fun onAnswerReceived(sdp: String, type: String) {
                Log.d("VolunteerDashVM", "Received answer from user device")
                manager.setRemoteDescription(sdp, SessionDescription.Type.ANSWER)
            }

            override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
                manager.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
            }

            override fun onCallEnded() {
                endCall(isLocal = false)
            }
        })

        manager.startCall(object : WebRtcCallManager.CallEvents {
            override fun onIceCandidate(candidate: IceCandidate) {
                signaling.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }

            override fun onRemoteStreamAdded(mediaStream: MediaStream) {
                Log.d("VolunteerDashVM", "Remote media stream added")
                _remoteMediaStream.value = mediaStream
            }

            override fun onCallConnected() {
                Log.d("VolunteerDashVM", "Volunteer call connected")
                _dashboardState.value = VolunteerDashboardState.InCall(request, isMuted)
            }

            override fun onCallDisconnected() {
                endCall(isLocal = false)
            }
        })

        _dashboardState.value = VolunteerDashboardState.InCall(request, isMuted)
    }

    fun toggleMute() {
        isMuted = !isMuted
        callManager?.setMuted(isMuted)
        val cur = _dashboardState.value
        if (cur is VolunteerDashboardState.InCall) {
            _dashboardState.value = cur.copy(isMuted = isMuted)
        }
    }

    fun endCall(isLocal: Boolean = true) {
        val req = activeRequest
        if (req != null && isLocal) {
            signalingClient?.sendCallEnded()
            viewModelScope.launch(Dispatchers.IO) {
                volunteerApi.completeRequest(req.id)
            }
        }
        cleanup()
        loadRequests()
    }

    private fun cleanup() {
        try {
            callManager?.cleanup()
            callManager = null
            signalingClient?.disconnect()
            signalingClient = null
            _remoteMediaStream.value = null
            activeRequest = null
        } catch (e: Exception) {
            Log.e("VolunteerDashVM", "Cleanup error", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
