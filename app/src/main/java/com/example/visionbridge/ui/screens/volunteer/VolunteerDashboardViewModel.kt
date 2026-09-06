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
import org.webrtc.VideoTrack

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

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var dashboardSignalingClient: WebRtcSignalingClient? = null

    fun loadRequests() {
        _dashboardState.value = VolunteerDashboardState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                volunteerApi.getRequests()
            }
            when (result) {
                is ApiResult.Success -> {
                    val pendingRequests = result.value.filter {
                        it.status.equals("PENDING", ignoreCase = true) ||
                        it.status.equals("searching", ignoreCase = true)
                    }
                    _dashboardState.value = VolunteerDashboardState.RequestList(pendingRequests)
                    setupDashboardRealtime()
                }
                is ApiResult.Failure -> {
                    _dashboardState.value = VolunteerDashboardState.Error(result.error.userMessage)
                }
            }
        }
    }

    private fun setupDashboardRealtime() {
        if (dashboardSignalingClient != null) return
        dashboardSignalingClient = WebRtcSignalingClient().apply {
            connect("dashboard", object : WebRtcSignalingClient.SignalingListener {
                override fun onRequestUpdated(request: HelpRequest) {
                    updateRequestInList(request)
                }
                override fun onRequestAccepted(request: HelpRequest) {
                    updateRequestInList(request)
                }
                override fun onRequestCancelled(request: HelpRequest?) {
                    if (request != null) {
                        updateRequestInList(request)
                    } else {
                        loadRequests()
                    }
                }
                override fun onRequestCompleted(request: HelpRequest?) {
                    if (request != null) {
                        updateRequestInList(request)
                    } else {
                        loadRequests()
                    }
                }
                override fun onRequestRejected(request: HelpRequest?) {
                    if (request != null) {
                        updateRequestInList(request)
                    } else {
                        loadRequests()
                    }
                }
                override fun onOfferReceived(sdp: String, type: String) {}
                override fun onAnswerReceived(sdp: String, type: String) {}
                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {}
                override fun onCallEnded() {}
            }, authToken = sessionManager.token)
        }
    }

    private fun updateRequestInList(request: HelpRequest) {
        val currentState = _dashboardState.value
        if (currentState is VolunteerDashboardState.RequestList) {
            val currentList = currentState.requests.toMutableList()
            val index = currentList.indexOfFirst { it.id == request.id }
            val isPending = request.status.equals("PENDING", ignoreCase = true) ||
                    request.status.equals("searching", ignoreCase = true)

            if (index != -1) {
                if (!isPending) {
                    currentList.removeAt(index)
                    Log.i("VolunteerDashVM", "[VOLUNTEER_CALL] Removed non-pending request from list: ${request.id} (status=${request.status})")
                } else {
                    currentList[index] = request
                }
            } else if (isPending) {
                currentList.add(0, request)
                Log.i("VolunteerDashVM", "[VOLUNTEER_CALL] Added new pending request to list: ${request.id}")
            }
            _dashboardState.value = VolunteerDashboardState.RequestList(currentList)
        }
    }

    fun acceptRequest(request: HelpRequest) {
        val user = sessionManager.currentUser.value
        if (user == null || user.id.isBlank()) {
            _dashboardState.value = VolunteerDashboardState.Error("You must be signed in as a volunteer.")
            return
        }
        val volunteerId = user.id

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

        // 1. Initialize WebRTC PeerConnection, transceivers, and media pipeline FIRST
        manager.startCall(object : WebRtcCallManager.CallEvents {
            override fun onIceCandidate(candidate: IceCandidate) {
                signaling.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }

            override fun onRemoteVideoTrack(videoTrack: VideoTrack) {
                Log.i("VolunteerDashVM", "[WEBRTC_VIDEO] Volunteer received remote VideoTrack: ${videoTrack.id()}")
                _remoteVideoTrack.value = videoTrack
            }

            override fun onRemoteStreamAdded(mediaStream: MediaStream) {
                Log.d("VolunteerDashVM", "[WEBRTC_CONNECTION] Remote media stream added: tracks=${mediaStream.videoTracks.size}")
                _remoteMediaStream.value = mediaStream
                val videoTrack = mediaStream.videoTracks.firstOrNull()
                if (videoTrack != null) {
                    _remoteVideoTrack.value = videoTrack
                }
            }

            override fun onCallConnected() {
                Log.i("VolunteerDashVM", "[VOLUNTEER_CALL] Volunteer call connected")
                _dashboardState.value = VolunteerDashboardState.InCall(request, isMuted)
            }

            override fun onCallDisconnected() {
                Log.i("VolunteerDashVM", "[VOLUNTEER_CALL] Volunteer call disconnected")
                endCall(isLocal = false)
            }
        })

        // 2. Connect signaling and negotiate WebRTC offer
        val token = sessionManager.token
        signaling.connect(request.id, object : WebRtcSignalingClient.SignalingListener {
            override fun onConnected() {
                Log.i("VolunteerDashVM", "[WEBRTC_CONNECTION] Signaling connected to room ${request.id}. Creating offer.")
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
                Log.i("VolunteerDashVM", "[WEBRTC_CONNECTION] Received answer from user device")
                manager.setRemoteDescription(sdp, SessionDescription.Type.ANSWER)
            }

            override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
                manager.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
            }

            override fun onCallEnded() {
                Log.i("VolunteerDashVM", "[VOLUNTEER_CALL] Remote peer ended the call")
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
        viewModelScope.launch {
            if (req != null) {
                if (isLocal) {
                    signalingClient?.sendCallEnded()
                }
                withContext(Dispatchers.IO) {
                    volunteerApi.completeRequest(req.id)
                    volunteerApi.endCallLog(req.id, "COMPLETED")
                }
            }
            cleanup()
            loadRequests()
        }
    }

    private fun cleanup() {
        try {
            dashboardSignalingClient?.disconnect()
            dashboardSignalingClient = null
            callManager?.cleanup()
            callManager = null
            signalingClient?.disconnect()
            signalingClient = null
            _remoteVideoTrack.value = null
            _remoteMediaStream.value = null
            activeRequest = null
            Log.d("VolunteerDashVM", "Cleanup complete")
        } catch (e: Exception) {
            Log.e("VolunteerDashVM", "Cleanup error", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
