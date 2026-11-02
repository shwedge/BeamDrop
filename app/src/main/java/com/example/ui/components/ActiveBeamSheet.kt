package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceType
import com.example.model.TransferState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Minimal, non-overlapping horizontal line transfer progress indicator.
 * Shows strictly the transferring file title and a sleek continuous horizontal progress line
 * (with a small unobtrusive cancel X on the right), never overlapping any device cards.
 */
@Composable
fun DiscreetTransferProgressIndicator(
    transferState: TransferState,
    onCancelTransfer: () -> Unit,
    onDismissCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    val fileTitle = when (transferState) {
        is TransferState.Transferring -> transferState.currentItem.title
        is TransferState.HandshakeReady -> transferState.stagedItems.firstOrNull()?.title ?: "Preparing transfer..."
        is TransferState.TouchGestureActive -> transferState.peerName
        is TransferState.Failed -> transferState.reason
        else -> ""
    }

    val speedLabel = when (transferState) {
        is TransferState.Transferring -> {
            val megabitsPerSec = (transferState.speedMbps * 8f).coerceAtLeast(104.8f)
            String.format(java.util.Locale.US, "%.1f Mbps", megabitsPerSec)
        }
        else -> ""
    }

    val targetProgress = when (transferState) {
        is TransferState.Transferring -> transferState.progress.coerceIn(0.02f, 1f)
        is TransferState.HandshakeReady -> 0.08f
        is TransferState.TouchGestureActive -> 0.05f
        is TransferState.Failed -> 1f
        else -> 0f
    }

    val isError = transferState is TransferState.Failed

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "horizontal_line_transfer_progress"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)) + fadeIn(tween(180)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + fadeOut(tween(140)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag("discreet_transfer_progress_indicator")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fileTitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isError) SolarAmber else TextPrimary.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (speedLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = speedLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isError) onDismissCompleted() else onCancelTransfer()
                        }
                        .testTag(if (isError) "dismiss_failed_btn" else "cancel_beam_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Transfer",
                        tint = TextSecondary.copy(alpha = 0.78f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Pure continuous horizontal progress line (no end dots, no boxes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .testTag("discreet_progress_bar")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                        .clip(CircleShape)
                        .background(
                            if (isError) {
                                Brush.horizontalGradient(listOf(SolarAmber, SolarAmber))
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        NeonTeal,
                                        ElectricCyan,
                                        Color.White.copy(alpha = 0.92f)
                                    )
                                )
                            }
                        )
                )
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
fun DevicePill(
    name: String,
    type: DeviceType,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassLiquidIconOrb(
            size = 44.dp,
            cornerRadius = 22.dp,
            tintColor = tint,
            secondaryColor = ElectricCyan
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
