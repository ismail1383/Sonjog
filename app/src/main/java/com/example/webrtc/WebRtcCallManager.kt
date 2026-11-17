package com.example.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.model.CallSession
import com.example.data.repository.SongjogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

enum class ActiveCallPhase {
    IDLE,
    OUTGOING_RINGING,
    CONNECTING,
    CONNECTED,
    ENDED
}

data class ActiveCallUiState(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerAvatar: String? = null,
    val isVideo: Boolean = false,
    val isCaller: Boolean = true,
    val phase: ActiveCallPhase = ActiveCallPhase.IDLE,
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true
)

class WebRtcCallManager(
    private val context: Context,
    private val repository: SongjogRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val eglBase: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var callObserverJob: Job? = null
    private var iceObserverJob: Job? = null
    private var timerJob: Job? = null
    private var remoteSdpApplied = false
    private val addedCandidateIds = mutableSetOf<String>()

    private val _callState = MutableStateFlow(ActiveCallUiState())
    val callState: StateFlow<ActiveCallUiState> = _callState.asStateFlow()

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
            .also { factory = it }
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local
        remoteRenderer = remote
        local?.let { renderer ->
            runCatching {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setMirror(true)
                renderer.setEnableHardwareScaler(true)
                localVideoTrack?.addSink(renderer)
            }
        }
        remote?.let { renderer ->
            runCatching {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setMirror(false)
                renderer.setEnableHardwareScaler(true)
                remoteVideoTrack?.addSink(renderer)
            }
        }
    }

    fun detachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        local?.let { renderer ->
            runCatching {
                localVideoTrack?.removeSink(renderer)
                renderer.release()
            }
        }
        remote?.let { renderer ->
            runCatching {
                remoteVideoTrack?.removeSink(renderer)
                renderer.release()
            }
        }
        if (localRenderer === local) localRenderer = null
        if (remoteRenderer === remote) remoteRenderer = null
    }

    fun startOutgoingCall(
        currentUserId: String,
        currentUserName: String,
        currentUserAvatar: String?,
        peerId: String,
        peerName: String,
        peerAvatar: String?,
        isVideo: Boolean
    ) {
        cleanupSession()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val participantIds = listOf(currentUserId, peerId)
        remoteSdpApplied = false
        addedCandidateIds.clear()

        configureAudio(speakerOn = isVideo)
        _callState.value = ActiveCallUiState(
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            peerAvatar = peerAvatar,
            isVideo = isVideo,
            isCaller = true,
            phase = ActiveCallPhase.OUTGOING_RINGING,
            isSpeakerOn = isVideo,
            isCameraEnabled = isVideo
        )

        scope.launch(Dispatchers.Default) {
            val pc = buildPeerConnection(callId, isCaller = true, participantIds = participantIds, isVideo = isVideo)
            if (pc == null) {
                endActiveCall(statusOverride = "ended")
                return@launch
            }
            peerConnection = pc

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
            }

            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    val sdp = desc ?: return
                    pc.setLocalDescription(SimpleSdpObserver(), sdp)
                    scope.launch {
                        runCatching {
                            repository.createCallOffer(
                                callId = callId,
                                callerName = currentUserName,
                                callerAvatar = currentUserAvatar,
                                calleeId = peerId,
                                calleeName = peerName,
                                calleeAvatar = peerAvatar,
                                callType = if (isVideo) "video" else "audio",
                                offerSdp = sdp.description
                            )
                            observeSignalingForCaller(callId, currentUserId)
                        }.onFailure {
                            Log.e(TAG, "Failed creating call offer", it)
                            endActiveCall()
                        }
                    }
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create offer failed: $error")
                    endActiveCall()
                }
            }, constraints)
        }
    }

    fun acceptIncomingCall(session: CallSession, currentUserId: String) {
        cleanupSession()
        val isVideo = session.callType == "video"
        remoteSdpApplied = false
        addedCandidateIds.clear()

        configureAudio(speakerOn = isVideo)
        _callState.value = ActiveCallUiState(
            callId = session.id,
            peerId = session.callerId,
            peerName = session.callerName,
            peerAvatar = session.callerAvatar,
            isVideo = isVideo,
            isCaller = false,
            phase = ActiveCallPhase.CONNECTING,
            isSpeakerOn = isVideo,
            isCameraEnabled = isVideo
        )

        scope.launch(Dispatchers.Default) {
            val pc = buildPeerConnection(
                callId = session.id,
                isCaller = false,
                participantIds = session.participantIds,
                isVideo = isVideo
            )
            if (pc == null) {
                endActiveCall(statusOverride = "ended")
                return@launch
            }
            peerConnection = pc

            val offerSdp = session.offerSdp
            if (offerSdp.isNullOrBlank()) {
                endActiveCall(statusOverride = "ended")
                return@launch
            }

            pc.setRemoteDescription(
                object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteSdpApplied = true
                        val constraints = MediaConstraints().apply {
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
                        }
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(desc: SessionDescription?) {
                                val answer = desc ?: return
                                pc.setLocalDescription(SimpleSdpObserver(), answer)
                                scope.launch {
                                    runCatching {
                                        repository.answerCall(session.id, answer.description)
                                        observeSignalingForCallee(session.id, currentUserId)
                                    }.onFailure {
                                        Log.e(TAG, "Failed answering call", it)
                                        endActiveCall()
                                    }
                                }
                            }
                        }, constraints)
                    }
                },
                SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            )
        }
    }

    private fun observeSignalingForCaller(callId: String, currentUserId: String) {
        callObserverJob?.cancel()
        callObserverJob = scope.launch {
            repository.observeCall(callId).collect { session ->
                if (session == null) return@collect
                when (session.status) {
                    "accepted" -> {
                        if (!remoteSdpApplied && !session.answerSdp.isNullOrBlank()) {
                            remoteSdpApplied = true
                            _callState.value = _callState.value.copy(phase = ActiveCallPhase.CONNECTING)
                            peerConnection?.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(SessionDescription.Type.ANSWER, session.answerSdp)
                            )
                            startCallTimer()
                        }
                    }
                    "declined", "ended", "missed" -> {
                        finishLocally()
                    }
                }
            }
        }

        iceObserverJob?.cancel()
        iceObserverJob = scope.launch {
            repository.observeIceCandidates(callId, fromCaller = false, userId = currentUserId).collect { list ->
                list.forEach { rec ->
                    if (addedCandidateIds.add(rec.id)) {
                        peerConnection?.addIceCandidate(
                            IceCandidate(rec.sdpMid, rec.sdpMLineIndex.toInt(), rec.candidate)
                        )
                    }
                }
            }
        }
    }

    private fun observeSignalingForCallee(callId: String, currentUserId: String) {
        startCallTimer()
        callObserverJob?.cancel()
        callObserverJob = scope.launch {
            repository.observeCall(callId).collect { session ->
                if (session == null) return@collect
                if (session.status in listOf("declined", "ended", "missed")) {
                    finishLocally()
                }
            }
        }

        iceObserverJob?.cancel()
        iceObserverJob = scope.launch {
            repository.observeIceCandidates(callId, fromCaller = true, userId = currentUserId).collect { list ->
                list.forEach { rec ->
                    if (addedCandidateIds.add(rec.id)) {
                        peerConnection?.addIceCandidate(
                            IceCandidate(rec.sdpMid, rec.sdpMLineIndex.toInt(), rec.candidate)
                        )
                    }
                }
            }
        }
    }

    private fun startCallTimer() {
        if (timerJob?.isActive == true) return
        _callState.value = _callState.value.copy(phase = ActiveCallPhase.CONNECTED)
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                _callState.value = _callState.value.copy(
                    elapsedSeconds = _callState.value.elapsedSeconds + 1L
                )
            }
        }
    }

    private fun buildPeerConnection(
        callId: String,
        isCaller: Boolean,
        participantIds: List<String>,
        isVideo: Boolean
    ): PeerConnection? {
        return try {
            val pcf = ensureFactory()
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            val pc = pcf.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    val cand = candidate ?: return
                    scope.launch {
                        repository.addIceCandidate(
                            callId = callId,
                            isCaller = isCaller,
                            participantIds = participantIds,
                            sdpMid = cand.sdpMid ?: "0",
                            sdpMLineIndex = cand.sdpMLineIndex,
                            candidate = cand.sdp
                        )
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        remoteRenderer?.let { renderer ->
                            runCatching { track.addSink(renderer) }
                        }
                    }
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        startCallTimer()
                    }
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            }) ?: return null

            val audioConstraints = MediaConstraints()
            audioSource = pcf.createAudioSource(audioConstraints)
            localAudioTrack = pcf.createAudioTrack("ARDAMSa0", audioSource).also {
                it.setEnabled(true)
                pc.addTrack(it, listOf("ARDAMS"))
            }

            if (isVideo) {
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    videoSource = pcf.createVideoSource(capturer.isScreencast)
                    capturer.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
                    runCatching { capturer.startCapture(640, 480, 24) }
                    localVideoTrack = pcf.createVideoTrack("ARDAMSv0", videoSource).also { track ->
                        track.setEnabled(true)
                        pc.addTrack(track, listOf("ARDAMS"))
                        localRenderer?.let { renderer -> runCatching { track.addSink(renderer) } }
                    }
                }
            }
            pc
        } catch (e: Exception) {
            Log.e(TAG, "Error creating WebRTC PeerConnection", e)
            null
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(appContext)
            val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            val anyCam = front ?: enumerator.deviceNames.firstOrNull()
            anyCam?.let { enumerator.createCapturer(it, null) }
        } catch (_: Exception) {
            null
        }
    }

    fun toggleMute() {
        val next = !_callState.value.isMuted
        localAudioTrack?.setEnabled(!next)
        _callState.value = _callState.value.copy(isMuted = next)
    }

    fun toggleSpeaker() {
        val next = !_callState.value.isSpeakerOn
        configureAudio(speakerOn = next)
        _callState.value = _callState.value.copy(isSpeakerOn = next)
    }

    fun toggleCamera() {
        val next = !_callState.value.isCameraEnabled
        localVideoTrack?.setEnabled(next)
        _callState.value = _callState.value.copy(isCameraEnabled = next)
    }

    fun switchCamera() {
        runCatching { videoCapturer?.switchCamera(null) }
    }

    fun endActiveCall(statusOverride: String? = null) {
        val current = _callState.value
        val callId = current.callId
        val nextStatus = statusOverride ?: when (current.phase) {
            ActiveCallPhase.OUTGOING_RINGING -> "missed"
            else -> "ended"
        }
        val duration = current.elapsedSeconds
        finishLocally()
        if (callId.isNotBlank()) {
            scope.launch {
                repository.updateCallStatus(callId, nextStatus, duration)
            }
        }
    }

    private fun finishLocally() {
        cleanupSession()
        _callState.value = ActiveCallUiState(phase = ActiveCallPhase.IDLE)
    }

    private fun configureAudio(speakerOn: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn
        }
    }

    fun cleanupSession() {
        callObserverJob?.cancel()
        iceObserverJob?.cancel()
        timerJob?.cancel()
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        remoteVideoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
    }

    fun release() {
        cleanupSession()
        runCatching { eglBase.release() }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    private companion object {
        const val TAG = "WebRtcCallManager"
    }
}
