package com.example.visionbridge.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcCallManager(
    private val context: Context,
    private val isVolunteer: Boolean
) {

    val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioDeviceModule: AudioDeviceModule? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false
    private var isConnectedNotified = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
    )

    interface CallEvents {
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteStreamAdded(mediaStream: MediaStream)
        fun onCallConnected()
        fun onCallDisconnected()
    }

    private var events: CallEvents? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Using SoftwareVideoEncoderFactory with modern WebRTC to guarantee VP8 works on Samsung chips
        val encoderFactory = SoftwareVideoEncoderFactory()
        val decoderFactory = SoftwareVideoDecoderFactory()

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        audioDeviceModule = adm

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun notifyCallConnected() {
        if (!isConnectedNotified) {
            isConnectedNotified = true
            Log.i(TAG, "WebRTC Call fully CONNECTED! Notifying onCallConnected listener.")
            events?.onCallConnected()
        }
    }

    fun startCall(events: CallEvents) {
        this.events = events
        isConnectedNotified = false

        // Configure phone audio routing for live WebRTC communication via speakerphone
        setSpeakerphoneEnabled(true)

        createPeerConnection()

        val factory = peerConnectionFactory ?: return

        // 1. Low-Vision User sends video. Volunteer only receives video.
        if (!isVolunteer) {
            videoCapturer = createCameraCapturer()
            if (videoCapturer != null) {
                Log.i(TAG, "CAMERA_CAPTURER_CREATED")
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                val videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
                Log.i(TAG, "VIDEO_SOURCE_CREATED")
                videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                Log.i(TAG, "CAMERA_INITIALIZED")
                videoCapturer!!.startCapture(640, 480, 30)
                Log.i(TAG, "CAMERA_START_CAPTURE_CALLED")

                localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
                Log.i(TAG, "VIDEO_TRACK_CREATED")
                peerConnection?.addTransceiver(
                    localVideoTrack,
                    RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.SEND_ONLY,
                        listOf("ARDAMS")
                    )
                )
                Log.i(TAG, "User back camera capture started and added via addTransceiver (SEND_ONLY)")
            } else {
                Log.w(TAG, "No suitable camera found to create video track")
            }
        } else {
            // Volunteer requests to receive video only
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
            Log.i(TAG, "Volunteer video recvonly transceiver added")
        }

        // 2. Audio track for both roles (bidirectional audio)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("highpassFilter", "true"))
        }
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)
        localAudioTrack?.setVolume(1.0)
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))

        // Diagnostic logging
        val pc = peerConnection
        if (pc != null) {
            val senders = pc.senders
            val receivers = pc.receivers
            val transceivers = pc.transceivers
            Log.i(TAG, "--- PeerConnection Track State ---")
            Log.i(TAG, "Senders: ${senders.size}, Receivers: ${receivers.size}, Transceivers: ${transceivers.size}")
            transceivers.forEachIndexed { i, t ->
                Log.i(TAG, "Transceiver $i: direction=${t.direction}, mid=${t.mid}, senderTrack=${t.sender.track()?.kind()}")
            }
            Log.i(TAG, "Local Audio Track enabled: ${localAudioTrack?.enabled()}")
            Log.i(TAG, "Local Video Track enabled: ${localVideoTrack?.enabled()}")
            Log.i(TAG, "----------------------------------")
        }
    }

    fun attachLocalPreview(renderer: VideoSink) {
        localVideoTrack?.addSink(renderer)
        Log.i(TAG, "Attached local preview renderer")
    }

    fun detachLocalPreview(renderer: VideoSink) {
        localVideoTrack?.removeSink(renderer)
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "Local ICE Candidate generated: ${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                    events?.onIceCandidate(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "SignalingState changed: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState changed: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        notifyCallConnected()
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        Log.i(TAG, "WebRTC PeerConnection DISCONNECTED/FAILED/CLOSED ($state)")
                        events?.onCallDisconnected()
                    }
                    else -> {}
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "PeerConnectionState changed: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        notifyCallConnected()
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.CLOSED -> {
                        events?.onCallDisconnected()
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "IceGatheringState: $state")
            }
            override fun onAddStream(stream: MediaStream?) {
                if (stream != null) {
                    Log.i(TAG, "Remote stream added: audio=${stream.audioTracks.size}, video=${stream.videoTracks.size}")
                    for (track in stream.audioTracks) {
                        track.setEnabled(true)
                        track.setVolume(1.0)
                        Log.i(TAG, "Enabled remote audio track: ${track.id()}")
                    }
                    notifyCallConnected()
                    events?.onRemoteStreamAdded(stream)
                }
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                Log.i(TAG, "Remote track added: ${track?.kind()}, id=${track?.id()}")
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                    Log.i(TAG, "Explicitly enabled incoming AudioTrack at full volume")
                    notifyCallConnected()
                }
                if (mediaStreams != null && mediaStreams.isNotEmpty()) {
                    events?.onRemoteStreamAdded(mediaStreams[0])
                }
            }
        })
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local offer set successfully")
                            callback(desc)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "setLocalDescription onCreateFailure: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setLocalDescription onSetFailure: $error")
                        }
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun createAnswer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    // Diagnostic logging for SDP answer content
                    val hasAudio = desc.description.contains("m=audio")
                    val hasVideo = desc.description.contains("m=video")
                    Log.i(TAG, "Created Answer SDP. Contains Audio: $hasAudio, Contains Video: $hasVideo")
                    
                    val modifiedDescStr = preferH264(desc.description)
                    val modifiedDesc = SessionDescription(desc.type, modifiedDescStr)
                    
                    Log.d(TAG, "Full Modified Answer SDP:\n${modifiedDesc.description}")

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local answer set successfully")
                            callback(modifiedDesc)
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "setLocalDescription (answer) onCreateFailure: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setLocalDescription (answer) onSetFailure: $error")
                        }
                    }, modifiedDesc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(sdp: String, type: SessionDescription.Type, onSet: () -> Unit = {}) {
        val desc = SessionDescription(type, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote description set successfully ($type)")
                isRemoteDescriptionSet = true
                drainPendingCandidates()
                onSet()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "setRemoteDescription failure: $p0")
            }
        }, desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (isRemoteDescriptionSet && peerConnection != null) {
            peerConnection?.addIceCandidate(candidate)
            Log.d(TAG, "Added remote ICE candidate directly")
        } else {
            pendingRemoteCandidates.add(candidate)
            Log.d(TAG, "Queued remote ICE candidate (queue size=${pendingRemoteCandidates.size})")
        }
    }

    private fun drainPendingCandidates() {
        if (pendingRemoteCandidates.isNotEmpty()) {
            Log.d(TAG, "Draining ${pendingRemoteCandidates.size} queued ICE candidates")
            for (candidate in pendingRemoteCandidates) {
                peerConnection?.addIceCandidate(candidate)
            }
            pendingRemoteCandidates.clear()
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        Log.i(TAG, "CAMERA_ENUMERATOR_CREATED, found ${deviceNames.size} devices")

        // Prefer back camera for low-vision user environment sharing
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                Log.i(TAG, "CAMERA_DEVICE_SELECTED: Back facing - $deviceName")
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.i(TAG, "CAMERA_DEVICE_SELECTED: Front facing - $deviceName")
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun cleanup() {
        try {
            isRemoteDescriptionSet = false
            isConnectedNotified = false
            pendingRemoteCandidates.clear()

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video capture", e)
        }

        try {
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            localAudioTrack = null
            localVideoTrack = null
            events = null

            // Restore normal audio routing
            setSpeakerphoneEnabled(false)

            audioDeviceModule = null

            Log.i(TAG, "WebRtcCallManager cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing peerConnection and audio resources", e)
        }
    }

    private fun setSpeakerphoneEnabled(enable: Boolean) {
        val am = audioManager ?: return
        try {
            if (enable) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val speakerDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        am.setCommunicationDevice(speakerDevice)
                    } else {
                        @Suppress("DEPRECATION")
                        am.isSpeakerphoneOn = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
                Log.i(TAG, "Speakerphone enabled for WebRTC call")
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
                Log.i(TAG, "Speakerphone disabled, AudioManager restored to MODE_NORMAL")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting speakerphone state: $enable", e)
        }
    }

    private fun preferH264(sdp: String): String {
        val lines = sdp.split(Regex("\\r?\\n")).toMutableList()
        var h264PayloadType = -1
        
        for (line in lines) {
            if (line.startsWith("a=rtpmap:") && line.contains("H264")) {
                val match = Regex("a=rtpmap:(\\d+) H264").find(line)
                if (match != null) {
                    h264PayloadType = match.groupValues[1].toInt()
                    break
                }
            }
        }
        
        if (h264PayloadType == -1) return sdp
        
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("m=video ")) {
                val parts = line.split(" ").toMutableList()
                if (parts.size > 3) {
                    val payloads = parts.subList(3, parts.size)
                    val h264Str = h264PayloadType.toString()
                    if (payloads.contains(h264Str)) {
                        payloads.remove(h264Str)
                        payloads.add(0, h264Str)
                    }
                    lines[i] = parts.subList(0, 3).joinToString(" ") + " " + payloads.joinToString(" ")
                }
            }
        }
        return lines.joinToString("\r\n")
    }

    companion object {
        private const val TAG = "VB-WebRtcCall"
    }
}
