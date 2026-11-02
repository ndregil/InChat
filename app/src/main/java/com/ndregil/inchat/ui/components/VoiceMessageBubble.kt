package com.ndregil.inchat.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.util.VoicePlayerManager
import java.util.Locale

@Composable
fun VoiceMessageBubbleContent(
    message: XmppMessage,
    textColor: Color,
    isMine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioSource = message.fileUrl ?: ""

    val currentTrack by VoicePlayerManager.currentTrackUrl.collectAsState()
    val isPlayingTrack by VoicePlayerManager.isPlaying.collectAsState()
    val currentPositionMs by VoicePlayerManager.playbackPositionMs.collectAsState()
    val totalDurationMs by VoicePlayerManager.totalDurationMs.collectAsState()

    val isThisTrackActive = currentTrack == audioSource
    val isThisPlaying = isThisTrackActive && isPlayingTrack

    val fallbackDurationSeconds = message.voiceDurationSeconds ?: 5
    val effectiveDurationMs = if (isThisTrackActive && totalDurationMs > 0) totalDurationMs else fallbackDurationSeconds * 1000
    val effectivePosMs = if (isThisTrackActive) currentPositionMs else 0

    val progress = if (effectiveDurationMs > 0) {
        (effectivePosMs.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "audio_progress")

    val containerColor = textColor.copy(alpha = 0.08f)
    val buttonColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = 210.dp, max = 290.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Play / Pause Circle Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable {
                    VoicePlayerManager.togglePlay(context, audioSource, fallbackDurationSeconds)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "Jeda Pesan Suara" else "Putar Pesan Suara",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Waveform Visualizer & Duration Bar
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Equalizer bars waveform
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                val waveHeights = listOf(
                    0.35f, 0.65f, 0.45f, 0.85f, 0.55f, 0.95f, 0.40f, 0.70f,
                    0.50f, 0.80f, 0.35f, 0.90f, 0.60f, 0.75f, 0.45f, 0.70f
                )

                waveHeights.forEachIndexed { index, weight ->
                    val barThreshold = (index + 1) / waveHeights.size.toFloat()
                    val isPassed = animatedProgress >= barThreshold
                    val barColor = if (isPassed) buttonColor else textColor.copy(alpha = 0.3f)
                    val barHeight = (18 * weight).coerceAtLeast(3f).dp

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor)
                    )
                }
            }

            // Duration and Badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displaySeconds = if (isThisTrackActive && isThisPlaying) {
                    effectivePosMs / 1000
                } else {
                    fallbackDurationSeconds
                }

                Text(
                    text = String.format(Locale.US, "%02d:%02d", displaySeconds / 60, displaySeconds % 60),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor.copy(alpha = 0.85f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = buttonColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Suara",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = buttonColor
                    )
                }
            }
        }
    }
}
