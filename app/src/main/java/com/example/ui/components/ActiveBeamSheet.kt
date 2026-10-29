package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BeamPeer
import com.example.model.DeviceType
import com.example.model.SharableItem
import com.example.model.TransferState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Discreet, self-dismissing progress capsule that replaces the standard notification queue
 * during active file transfers and clears immediately upon transfer completion.
 */
@Composable
fun DiscreetTransferProgressIndicator(
    transferState: TransferState,
    onCancelTransfer: () -> Unit,
    onDismissCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ensure immediate self-dismissal when transfer completes, and brief auto-dismissal on error
    LaunchedEffect(transferState) {
        when (transferState) {
            is TransferState.Completed -> {
                onDismissCompleted()
            }
            is TransferState.Failed -> {
                delay(2200)
                onDismissCompleted()
            }
            else -> {}
        }
    }

    val isVisible = when (transferState) {
        is TransferState.TouchGestureActive,
        is TransferState.HandshakeReady,
        is TransferState.Transferring,
        is TransferState.Failed -> true
        is TransferState.Idle,
        is TransferState.SearchingPeers,
        is TransferState.Completed -> false
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(150)
        ) + fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(140)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceElevated.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                ElectricCyan.copy(alpha = 0.75f),
                                NeonTeal.copy(alpha = 0.65f),
                                RadiantPurple.copy(alpha = 0.55f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("discreet_transfer_progress_indicator")
            ) {
                when (transferState) {
                    is TransferState.TouchGestureActive -> {
                        DiscreetHandshakePill(
                            title = "Linking with ${transferState.peerName}",
                            subtitle = "Establishing high-speed beam channel...",
                            onCancel = onCancelTransfer
                        )
                    }
                    is TransferState.HandshakeReady -> {
                        val sizeText = SharableItem.formatFileSize(
                            transferState.stagedItems.sumOf { it.sizeBytes }
                        )
                        val subtitle = if (transferState.stagedItems.isNotEmpty()) {
                            "${transferState.stagedItems.size} file(s) • $sizeText"
                        } else {
                            "Incoming high-speed beam..."
                        }
                        DiscreetHandshakePill(
                            title = "Connecting to ${transferState.peer.name}",
                            subtitle = subtitle,
                            onCancel = onCancelTransfer
                        )
                    }
                    is TransferState.Transferring -> {
                        DiscreetActiveTransferPill(
                            state = transferState,
                            onCancel = onCancelTransfer
                        )
                    }
                    is TransferState.Failed -> {
                        DiscreetFailedPill(
                            reason = transferState.reason,
                            onDismiss = onDismissCompleted
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ActiveBeamSheet(
    transferState: TransferState,
    onCancelTransfer: () -> Unit,
    onDismissCompleted: () -> Unit,
    onOpenFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    DiscreetTransferProgressIndicator(
        transferState = transferState,
        onCancelTransfer = onCancelTransfer,
        onDismissCompleted = onDismissCompleted,
        modifier = modifier
    )
}

@Composable
private fun DiscreetHandshakePill(
    title: String,
    subtitle: String,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "discreet_handshake_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan.copy(alpha = 0.16f * pulseAlpha))
                        .border(1.dp, ElectricCyan.copy(alpha = pulseAlpha), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("cancel_beam_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Transfer",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = ElectricCyan,
            trackColor = CardNavy,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun DiscreetActiveTransferPill(
    state: TransferState.Transferring,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "smooth_transfer_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "discreet_beam_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Discreet animated energy orb with peer device icon
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CardNavy)
                        .border(1.dp, NeonTeal.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(34.dp)) {
                        val cy = size.height / 2f
                        val w = size.width
                        val px = w * wavePhase
                        val py = cy + sin((wavePhase * 2f * PI).toFloat()) * 4f
                        drawCircle(
                            color = ElectricCyan.copy(alpha = 0.25f),
                            radius = 7f,
                            center = Offset(px, py)
                        )
                    }
                    Icon(
                        imageVector = when (state.peer.deviceType) {
                            DeviceType.PHONE -> Icons.Default.PhoneAndroid
                            DeviceType.TABLET -> Icons.Default.TabletAndroid
                            else -> Icons.Default.Computer
                        },
                        contentDescription = null,
                        tint = NeonTeal,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.currentItem.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricCyan,
                            modifier = Modifier.testTag("discreet_progress_percent")
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (state.totalItemCount > 1) {
                                "${state.currentItemIndex + 1}/${state.totalItemCount} • ${state.peer.name}"
                            } else {
                                state.peer.name
                            },
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = BorderSubtle
                        )

                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = NeonTeal,
                            modifier = Modifier.size(11.dp)
                        )

                        Text(
                            text = String.format(Locale.US, "%.1f MB/s", state.speedMbps),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("cancel_beam_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Transfer",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .testTag("discreet_progress_bar"),
            color = ElectricCyan,
            trackColor = CardNavy,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun DiscreetFailedPill(
    reason: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = SolarAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Transfer Interrupted",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SolarAmber
                )
                Text(
                    text = reason,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(48.dp)
                .testTag("dismiss_failed_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DevicePill(
    name: String,
    type: DeviceType,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CardNavy)
                .border(1.5.dp, tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (type) {
                    DeviceType.PHONE -> Icons.Default.PhoneAndroid
                    DeviceType.TABLET -> Icons.Default.TabletAndroid
                    else -> Icons.Default.Computer
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
