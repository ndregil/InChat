package com.ndregil.inchat.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.data.model.CallInfo
import com.ndregil.inchat.data.model.CallState
import com.ndregil.inchat.ui.XmppViewModel
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private const val TAG_CALL_SCREEN = "PanggilanScreen"

@Composable
fun PanggilanOverlay(viewModel: XmppViewModel) {
    val currentCall by viewModel.currentCall.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = currentCall != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        currentCall?.let { call ->
            PanggilanScreen(
                call = call,
                onAccept = { viewModel.acceptCall() },
                onReject = { viewModel.rejectCall() },
                onEnd = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleMute() },
                onToggleVideo = { viewModel.toggleVideo() },
                onToggleSpeaker = { viewModel.toggleSpeaker() },
                onSwitchCamera = { viewModel.switchCamera() },
                onDismissEnded = { viewModel.dismissEndedCall() }
            )
        }
    }
}

@Composable
fun PanggilanScreen(
    call: CallInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onDismissEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val isVideoCall = call.callType.equals("Video", ignoreCase = true)

    BackHandler {
        when (call.state) {
            CallState.ENDED -> onDismissEnded()
            CallState.INCOMING_RINGING -> onReject()
            else -> onEnd()
        }
    }

    // Runtime permission state
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraUnavailable by remember(call.callId) { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results[Manifest.permission.RECORD_AUDIO]?.let { hasMicPermission = it }
        results[Manifest.permission.CAMERA]?.let { hasCameraPermission = it }
    }

    LaunchedEffect(call.callId, isVideoCall) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val missing = buildList {
            if (!hasMicPermission) add(Manifest.permission.RECORD_AUDIO)
            if (isVideoCall && !hasCameraPermission) add(Manifest.permission.CAMERA)
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Manage AudioManager routing, mic mute, and cleanup
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    LaunchedEffect(call.state, call.isAudioMuted, call.isSpeakerOn) {
        val am = audioManager ?: return@LaunchedEffect
        try {
            if (call.state == CallState.CONNECTED) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isMicrophoneMute = call.isAudioMuted
                applySpeakerRouting(am, call.isSpeakerOn)
            } else if (call.state == CallState.ENDED) {
                resetAudioRouting(am)
            }
        } catch (e: Exception) {
            Log.w(TAG_CALL_SCREEN, "Audio routing error: ${e.message}")
        }
    }

    DisposableEffect(audioManager) {
        onDispose {
            audioManager?.let { resetAudioRouting(it) }
        }
    }

    // Incoming ringtone playback
    DisposableEffect(call.state, call.callId) {
        val ringtone = if (call.state == CallState.INCOMING_RINGING) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                RingtoneManager.getRingtone(context, uri)?.also { it.play() }
            } catch (e: Exception) {
                Log.w(TAG_CALL_SCREEN, "Ringtone play error: ${e.message}")
                null
            }
        } else {
            null
        }
        onDispose {
            try {
                if (ringtone?.isPlaying == true) {
                    ringtone.stop()
                }
            } catch (_: Exception) {
            }
        }
    }

    // Outgoing ringback tone
    LaunchedEffect(call.state, call.callId) {
        if (call.state == CallState.OUTGOING_RINGING) {
            var toneGen: ToneGenerator? = null
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 45)
                while (isActive) {
                    toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                    delay(3500)
                }
            } catch (e: Exception) {
                Log.w(TAG_CALL_SCREEN, "Ringback tone error: ${e.message}")
            } finally {
                try {
                    toneGen?.stopTone()
                    toneGen?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    val showLiveVideo = isVideoCall &&
        call.isVideoEnabled &&
        hasCameraPermission &&
        call.state != CallState.ENDED &&
        !cameraUnavailable

    val backdropBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B131E),
            Color(0xFF102235),
            Color(0xFF0A101A)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropBrush)
    ) {
        // Real CameraX live preview when Video is enabled
        if (showLiveVideo) {
            CameraPreviewSurface(
                isFrontCamera = call.isFrontCamera,
                onCameraError = { cameraUnavailable = true },
                modifier = Modifier.fillMaxSize()
            )
            // Subtle top & bottom gradient scrims for crisp readability over video
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header & Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideoCall) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isVideoCall) strings.callVideo else strings.callVoice,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (showLiveVideo && call.state == CallState.CONNECTED) 12.dp else 28.dp))

                if (!showLiveVideo || call.state != CallState.CONNECTED) {
                    Surface(
                        modifier = Modifier
                            .size(96.dp)
                            .border(
                                width = 2.dp,
                                color = if (call.state == CallState.CONNECTED) OnlineGreen else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            ),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = call.remoteNickname.take(1).uppercase(Locale.getDefault()),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                Text(
                    text = call.remoteNickname,
                    color = Color.White,
                    fontSize = if (showLiveVideo && call.state == CallState.CONNECTED) 20.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = call.remoteJid,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                val statusText = when (call.state) {
                    CallState.OUTGOING_RINGING -> call.statusDetail.ifBlank { strings.calling }
                    CallState.INCOMING_RINGING -> call.statusDetail.ifBlank {
                        "${strings.incomingCall} (${if (isVideoCall) strings.callVideo else strings.callVoice})"
                    }
                    CallState.CONNECTED -> formatDuration(call.durationSeconds)
                    CallState.ENDED -> call.statusDetail.ifBlank { strings.callEnded }
                    CallState.IDLE -> ""
                }

                Text(
                    text = statusText,
                    color = when (call.state) {
                        CallState.CONNECTED -> OnlineGreen
                        CallState.ENDED -> Color(0xFFFF8A80)
                        else -> Color.White
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // Middle Banners (Missing Permissions / Camera Off / Camera Unavailable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val missingMic = !hasMicPermission && call.state != CallState.ENDED
                val missingCam = isVideoCall && !hasCameraPermission && call.state != CallState.ENDED

                if (missingMic || missingCam) {
                    val missingList = buildList {
                        if (missingMic) add(Manifest.permission.RECORD_AUDIO)
                        if (missingCam) add(Manifest.permission.CAMERA)
                    }
                    val permLabel = when {
                        missingMic && missingCam -> "${strings.permissionMicRequired} ${strings.permissionCamRequired}"
                        missingCam -> strings.permissionCamRequired
                        else -> strings.permissionMicRequired
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.88f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = permLabel,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { permissionLauncher.launch(missingList.toTypedArray()) },
                                modifier = Modifier.testTag("grant_call_permission_button")
                            ) {
                                Text(strings.grantPermission, color = OnlineGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isVideoCall && call.state != CallState.ENDED && !showLiveVideo && hasCameraPermission) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (cameraUnavailable) {
                                    strings.cameraUnavailable
                                } else {
                                    strings.cameraOff
                                },
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bottom Control Dock
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.82f),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (call.state) {
                        CallState.ENDED -> {
                            Button(
                                onClick = onDismissEnded,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("call_dismiss_button")
                            ) {
                                Text(strings.close, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        CallState.INCOMING_RINGING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CallActionButton(
                                    icon = Icons.Default.Close,
                                    color = DndRed,
                                    onClick = onReject,
                                    label = strings.rejectCall,
                                    buttonSize = 64.dp,
                                    testTag = "call_reject_button"
                                )
                                CallActionButton(
                                    icon = if (isVideoCall) Icons.Default.VideoCall else Icons.Default.Call,
                                    color = OnlineGreen,
                                    onClick = onAccept,
                                    label = strings.acceptCall,
                                    buttonSize = 64.dp,
                                    testTag = "call_accept_button"
                                )
                            }
                        }

                        else -> {
                            if (isVideoCall) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CallActionButton(
                                        icon = if (call.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        color = if (call.isAudioMuted) Color(0xFF475569) else Color.White.copy(alpha = 0.18f),
                                        onClick = onToggleMute,
                                        label = if (call.isAudioMuted) strings.unmute else strings.mute,
                                        buttonSize = 52.dp,
                                        testTag = "call_mute_button"
                                    )
                                    CallActionButton(
                                        icon = if (call.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                        color = if (call.isSpeakerOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                        onClick = onToggleSpeaker,
                                        label = strings.speaker,
                                        buttonSize = 52.dp,
                                        testTag = "call_speaker_button"
                                    )
                                    CallActionButton(
                                        icon = if (call.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        color = if (call.isVideoEnabled) Color.White.copy(alpha = 0.18f) else Color(0xFF475569),
                                        onClick = onToggleVideo,
                                        label = "Video",
                                        buttonSize = 52.dp,
                                        testTag = "call_video_button"
                                    )
                                    CallActionButton(
                                        icon = Icons.Default.Cameraswitch,
                                        color = Color.White.copy(alpha = 0.18f),
                                        onClick = onSwitchCamera,
                                        label = strings.switchCamera,
                                        buttonSize = 52.dp,
                                        testTag = "call_switch_camera_button"
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onEnd,
                                    colors = ButtonDefaults.buttonColors(containerColor = DndRed),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(54.dp)
                                        .testTag("call_end_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = strings.endCall,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.endCall,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CallActionButton(
                                        icon = if (call.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        color = if (call.isAudioMuted) Color(0xFF475569) else Color.White.copy(alpha = 0.18f),
                                        onClick = onToggleMute,
                                        label = if (call.isAudioMuted) strings.unmute else strings.mute,
                                        buttonSize = 56.dp,
                                        testTag = "call_mute_button"
                                    )
                                    CallActionButton(
                                        icon = Icons.Default.CallEnd,
                                        color = DndRed,
                                        onClick = onEnd,
                                        label = strings.endCall,
                                        buttonSize = 64.dp,
                                        testTag = "call_end_button"
                                    )
                                    CallActionButton(
                                        icon = if (call.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                        color = if (call.isSpeakerOn) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                        onClick = onToggleSpeaker,
                                        label = strings.speaker,
                                        buttonSize = 56.dp,
                                        testTag = "call_speaker_button"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewSurface(
    isFrontCamera: Boolean,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(previewView, lifecycleOwner, isFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    providerRef = cameraProvider
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    val preferredSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val selector = when {
                        cameraProvider.hasCamera(preferredSelector) -> preferredSelector
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> {
                            onCameraError()
                            return@addListener
                        }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                } catch (e: Exception) {
                    Log.w(TAG_CALL_SCREEN, "CameraX bind failed: ${e.message}")
                    onCameraError()
                }
            },
            executor
        )

        onDispose {
            disposed = true
            try {
                providerRef?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    label: String,
    buttonSize: Dp = 60.dp,
    testTag: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(color)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

private fun applySpeakerRouting(audioManager: AudioManager, enableSpeaker: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetType = if (enableSpeaker) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (device != null) {
            audioManager.setCommunicationDevice(device)
            return
        }
    }
    @Suppress("DEPRECATION")
    audioManager.isSpeakerphoneOn = enableSpeaker
}

private fun resetAudioRouting(audioManager: AudioManager) {
    try {
        audioManager.isMicrophoneMute = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    } catch (_: Exception) {
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
