package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.LocalAppStrings
import com.example.core.TimeFormatter
import com.example.data.model.CallSession
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CoralError
import com.example.ui.theme.OnlineGreen
import com.example.webrtc.ActiveCallPhase
import com.example.webrtc.ActiveCallUiState
import com.example.webrtc.WebRtcCallManager
import org.webrtc.SurfaceViewRenderer

@Composable
fun IncomingCallBanner(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF10241D),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .border(1.dp, Color(0xFF34D399).copy(alpha = 0.35f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    name = call.callerName,
                    avatarUrl = call.callerAvatar,
                    size = 52.dp,
                    isOnline = true
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = call.callerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (call.callType == "video") strings.incomingVideoCall else strings.incomingAudioCall,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA7F3D0)
                    )
                }
                Icon(
                    imageVector = if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = null,
                    tint = Color(0xFF34D399)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("decline_call_button")
                ) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.decline)
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("accept_call_button")
                ) {
                    Icon(
                        imageVector = if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.accept)
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    callState: ActiveCallUiState,
    callManager: WebRtcCallManager
) {
    val strings = LocalAppStrings.current
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    if (callState.isVideo) {
        DisposableEffect(localRenderer, remoteRenderer, callState.callId) {
            if (localRenderer != null || remoteRenderer != null) {
                callManager.attachRenderers(localRenderer, remoteRenderer)
            }
            onDispose {
                callManager.detachRenderers(localRenderer, remoteRenderer)
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1B15), Color(0xFF06100C), Color(0xFF0C221A))
                )
            )
            .safeDrawingPadding()
    ) {
        // Remote Video Surface for Video Calls
        if (callState.isVideo && callState.phase == ActiveCallPhase.CONNECTED) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).also { remoteRenderer = it }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Audio Call Center Avatar OR Connecting Overlay
        if (!callState.isVideo || callState.phase != ActiveCallPhase.CONNECTED) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(146.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFF34D399).copy(alpha = 0.16f))
                    )
                    UserAvatar(
                        name = callState.peerName,
                        avatarUrl = callState.peerAvatar,
                        size = 112.dp
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = callState.peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (callState.phase) {
                        ActiveCallPhase.OUTGOING_RINGING -> strings.callingStatus
                        ActiveCallPhase.CONNECTING -> strings.connectingStatus
                        ActiveCallPhase.CONNECTED -> TimeFormatter.formatSeconds(callState.elapsedSeconds)
                        ActiveCallPhase.ENDED -> strings.callEnded
                        ActiveCallPhase.IDLE -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFA7F3D0)
                )
            }
        } else {
            // Top peer bar during active video call
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(callState.peerName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        TimeFormatter.formatSeconds(callState.elapsedSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA7F3D0)
                    )
                }
            }
        }

        // Local PiP Camera View for Video Calls
        if (callState.isVideo && callState.isCameraEnabled) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 108.dp, height = 148.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).also {
                            it.setZOrderMediaOverlay(true)
                            localRenderer = it
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Controls Dock
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF10241D).copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (callState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (callState.isMuted) strings.unmuteMic else strings.muteMic,
                    active = callState.isMuted,
                    onClick = { callManager.toggleMute() }
                )

                CallControlButton(
                    icon = if (callState.isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    label = if (callState.isSpeakerOn) strings.speakerOn else strings.speakerOff,
                    active = callState.isSpeakerOn,
                    onClick = { callManager.toggleSpeaker() }
                )

                if (callState.isVideo) {
                    CallControlButton(
                        icon = if (callState.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        label = if (callState.isCameraEnabled) strings.cameraOn else strings.cameraOff,
                        active = !callState.isCameraEnabled,
                        onClick = { callManager.toggleCamera() }
                    )
                    CallControlButton(
                        icon = Icons.Rounded.Cameraswitch,
                        label = strings.flipCamera,
                        active = false,
                        onClick = { callManager.switchCamera() }
                    )
                }

                FilledIconButton(
                    onClick = { callManager.endActiveCall() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CoralError,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(58.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = strings.endCall,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) Color.White else Color.White.copy(alpha = 0.14f),
                contentColor = if (active) Color(0xFF0D1512) else Color.White
            ),
            modifier = Modifier
                .size(50.dp)
                .minimumInteractiveComponentSize()
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}
