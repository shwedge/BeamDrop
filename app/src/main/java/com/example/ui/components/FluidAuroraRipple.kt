package com.example.ui.components

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
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AuraPathState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.RadiantPurple
import com.example.ui.theme.SolarAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom Compose Modifier that renders a glowing, fluid aura ripple effect on a file-transfer button
 * using Canvas drawing (`drawWithContent`), triggered upon successful discovery of a peer device.
 */
fun Modifier.fluidAuraRippleButton(
    isPeerDiscovered: Boolean,
    cornerRadius: Dp = 14.dp,
    primaryAuraColor: Color = Color(0xFF5EEAD4),
    secondaryAuraColor: Color = ElectricCyan,
    tertiaryAuraColor: Color = RadiantPurple
): Modifier = composed {
    val activationAlpha by animateFloatAsState(
        targetValue = if (isPeerDiscovered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "peer_discovered_aura_activation"
    )

    if (activationAlpha <= 0.01f) {
        return@composed this
    }

    val infiniteTransition = rememberInfiniteTransition(label = "button_fluid_aura_ripple")

    val rippleCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "button_ripple_cycle"
    )

    val fluidPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "button_fluid_phase"
    )

    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "button_halo_pulse"
    )

    this.drawWithContent {
        val cornerPx = cornerRadius.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxDim = max(size.width, size.height)

        // 1. Soft Ambient Radial Aura Bloom behind the button
        val bloomRadius = (maxDim * 0.78f * haloPulse).coerceAtLeast(24f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryAuraColor.copy(alpha = 0.42f * activationAlpha),
                    secondaryAuraColor.copy(alpha = 0.24f * activationAlpha),
                    tertiaryAuraColor.copy(alpha = 0.12f * activationAlpha),
                    Color.Transparent
                ),
                center = center,
                radius = bloomRadius
            ),
            radius = bloomRadius,
            center = center
        )

        // 2. Three Staggered Organic Fluid Ripple Rings expanding outward from button perimeter
        val rippleCount = 3
        for (idx in 0 until rippleCount) {
            val progress = (rippleCycle + idx / rippleCount.toFloat()) % 1f
            val expansionPx = progress * 16.dp.toPx()
            val ringAlpha = ((1f - progress) * (1f - progress) * 0.78f * activationAlpha).coerceIn(0f, 1f)
            val strokeW = ((1f - progress * 0.55f) * 2.8.dp.toPx()).coerceAtLeast(1f)

            val ringColor = when (idx % 3) {
                0 -> primaryAuraColor
                1 -> secondaryAuraColor
                else -> tertiaryAuraColor
            }

            drawFluidContourRipple(
                width = size.width,
                height = size.height,
                expansion = expansionPx,
                phase = fluidPhase + idx * 1.15f,
                wobbleAmplitude = (2.8f + progress * 3.5f),
                color = ringColor.copy(alpha = ringAlpha),
                strokeWidth = strokeW
            )
        }

        // 3. Draw the actual button content
        drawContent()

        // 4. Luminous Rotating Aurora Rim & Specular Sheen on the button perimeter
        val rimBrush = Brush.sweepGradient(
            colors = listOf(
                primaryAuraColor.copy(alpha = 0.9f * activationAlpha),
                secondaryAuraColor.copy(alpha = 0.95f * activationAlpha),
                Color.White.copy(alpha = 0.85f * activationAlpha),
                tertiaryAuraColor.copy(alpha = 0.75f * activationAlpha),
                primaryAuraColor.copy(alpha = 0.9f * activationAlpha)
            ),
            center = center
        )

        drawRoundRect(
            brush = rimBrush,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Draws an organic, sinusoidal-wobbled pill contour around a button's bounds on the Canvas.
 */
private fun DrawScope.drawFluidContourRipple(
    width: Float,
    height: Float,
    expansion: Float,
    phase: Float,
    wobbleAmplitude: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path()
    val cx = width / 2f
    val cy = height / 2f
    val rx = (width / 2f) + expansion
    val ry = (height / 2f) + expansion
    val steps = 60

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val angle = t * (2.0 * PI).toFloat()
        val harmonicWobble =
            sin(angle * 6f + phase) * wobbleAmplitude +
            cos(angle * 4f - phase * 1.3f) * (wobbleAmplitude * 0.55f)

        // Superellipse / rounded pill contour with fluid harmonic perturbation
        val cosA = cos(angle)
        val sinA = sin(angle)
        val x = cx + (rx + harmonicWobble) * cosA
        val y = cy + (ry + harmonicWobble * 0.75f) * sinA

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private class BeamParticle(
    var progress: Float = Random.nextFloat(),
    var speed: Float = 0.012f + Random.nextFloat() * 0.018f,
    var lateralFactor: Float = (Random.nextFloat() - 0.5f) * 2f,
    var size: Float = 3.5f + Random.nextFloat() * 5.5f,
    var color: Color = if (Random.nextBoolean()) ElectricCyan else NeonTeal
)

@Composable
fun FluidAuroraRipple(
    modifier: Modifier = Modifier,
    intensity: Float = 1.0f,
    isContactLocked: Boolean = false
) {
    if (intensity <= 0.01f) return

    var frameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(intensity, isContactLocked) {
        while (true) {
            withInfiniteAnimationFrameMillis { millis ->
                frameTimeMs = millis
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "onetansfer_liquid_portal")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "portal_pulse"
    )

    val particles = remember {
        List(56) {
            BeamParticle(
                color = when (it % 4) {
                    0 -> ElectricCyan
                    1 -> NeonTeal
                    2 -> RadiantPurple
                    else -> SolarAmber
                }
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read frameTimeMs to guarantee 60fps redraw
        val timeSec = (frameTimeMs % 100000L) / 1000f
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val topPortalY = 0f
        val centerOrbY = height * 0.48f

        // 1. Top Liquid Portal Ambient Halo (OneTransfer / iOS 17 AirDrop style)
        val haloRadius = (width * 0.85f * intensity * pulseScale).coerceAtLeast(20f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = 0.55f * intensity),
                    NeonTeal.copy(alpha = 0.35f * intensity),
                    RadiantPurple.copy(alpha = 0.22f * intensity),
                    Color.Transparent
                ),
                center = Offset(centerX, topPortalY),
                radius = haloRadius
            ),
            radius = haloRadius,
            center = Offset(centerX, topPortalY)
        )

        // 2. Liquid Morphing Droplet Portal at Top Edge
        if (isContactLocked || intensity > 0.4f) {
            drawLiquidPortalDroplet(
                centerX = centerX,
                width = width,
                stretchDepth = height * 0.22f * intensity * pulseScale,
                phase = phase
            )
        }

        // 3. Multi-layered Harmonic Shockwave Rings Expanding Across Screen
        val waveLayers = 5
        for (layer in 0 until waveLayers) {
            val layerOffset = layer * 0.20f
            val normalizedWave = ((timeSec * 0.65f + layerOffset) % 1f)
            val baseRadius = (height * 0.75f * normalizedWave * intensity)
            if (baseRadius <= 8f) continue

            val alphaProgress = (1f - normalizedWave).coerceIn(0f, 1f)
            val strokeAlpha = (alphaProgress * 0.72f * intensity).coerceIn(0f, 1f)

            val waveColor = when (layer % 3) {
                0 -> ElectricCyan
                1 -> NeonTeal
                else -> RadiantPurple
            }

            drawWaveArc(
                centerX = centerX,
                centerY = topPortalY,
                radius = baseRadius,
                phase = phase + layer * 0.9f,
                strokeColor = waveColor.copy(alpha = strokeAlpha),
                strokeWidth = (7f - layer).coerceAtLeast(2f) * intensity
            )
        }

        // 4. High-Velocity OneTransfer Energy Stream Particles (Curved stream between Center & Top Portal)
        particles.forEach { p ->
            p.progress += p.speed * (0.6f + intensity * 0.9f)
            if (p.progress >= 1f) {
                p.progress -= 1f
                p.lateralFactor = (Random.nextFloat() - 0.5f) * 2f
            }

            val t = p.progress
            // Particles converge toward top portal or radiate during burst
            val startY = centerOrbY + 120f
            val endY = topPortalY + 12f
            val currentY = startY + (endY - startY) * t

            // Curved magnetic funnel width (wide in middle, pinched at top portal)
            val funnelSpread = sin(t * PI.toFloat()) * (width * 0.36f)
            val currentX = centerX + p.lateralFactor * funnelSpread

            val prevT = (t - 0.045f).coerceAtLeast(0f)
            val prevY = startY + (endY - startY) * prevT
            val prevSpread = sin(prevT * PI.toFloat()) * (width * 0.36f)
            val prevX = centerX + p.lateralFactor * prevSpread

            val particleAlpha = (sin(t * PI.toFloat()) * 0.92f * intensity).coerceIn(0f, 1f)

            // Luminous tail
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        p.color.copy(alpha = 0f),
                        p.color.copy(alpha = particleAlpha)
                    ),
                    start = Offset(prevX, prevY),
                    end = Offset(currentX, currentY)
                ),
                start = Offset(prevX, prevY),
                end = Offset(currentX, currentY),
                strokeWidth = p.size * 0.85f,
                cap = StrokeCap.Round
            )

            // Glowing particle head
            drawCircle(
                color = Color.White.copy(alpha = particleAlpha),
                radius = p.size * 0.55f,
                center = Offset(currentX, currentY)
            )
            drawCircle(
                color = p.color.copy(alpha = particleAlpha * 0.65f),
                radius = p.size * 1.4f,
                center = Offset(currentX, currentY)
            )
        }
    }
}

private fun DrawScope.drawLiquidPortalDroplet(
    centerX: Float,
    width: Float,
    stretchDepth: Float,
    phase: Float
) {
    val path = Path()
    val halfSpan = width * 0.38f
    val wobble = sin(phase * 2f) * 14f
    val tipY = (stretchDepth + wobble).coerceAtLeast(24f)

    path.moveTo(centerX - halfSpan, 0f)
    path.cubicTo(
        centerX - halfSpan * 0.45f, 0f,
        centerX - 48f, tipY,
        centerX, tipY
    )
    path.cubicTo(
        centerX + 48f, tipY,
        centerX + halfSpan * 0.45f, 0f,
        centerX + halfSpan, 0f
    )
    path.close()

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                ElectricCyan.copy(alpha = 0.65f),
                NeonTeal.copy(alpha = 0.25f),
                Color.Transparent
            ),
            startY = 0f,
            endY = tipY
        )
    )
}

private fun DrawScope.drawWaveArc(
    centerX: Float,
    centerY: Float,
    radius: Float,
    phase: Float,
    strokeColor: Color,
    strokeWidth: Float
) {
    val path = Path()
    val steps = 64
    val startAngle = 0.12f * PI.toFloat()
    val endAngle = 0.88f * PI.toFloat()

    var isFirst = true
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val angle = startAngle + t * (endAngle - startAngle)
        val wobble = sin(angle * 7f + phase) * 12f + cos(angle * 3f - phase) * 7f
        val currentR = radius + wobble
        val x = centerX + currentR * cos(angle)
        val y = centerY + currentR * sin(angle)

        if (isFirst) {
            path.moveTo(x, y)
            isFirst = false
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * Renders the shared cross-platform Canvas-based 'Aura Path' visually representing the file payload
 * migrating from the source device's button to the target device in real-time lockstep with Windows/Android.
 */
@Composable
fun AuraPathCanvasOverlay(
    auraPathState: AuraPathState,
    modifier: Modifier = Modifier
) {
    if (!auraPathState.isActive) return

    val animatedProgress by animateFloatAsState(
        targetValue = auraPathState.migrationProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "aura_path_migration_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "aura_path_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_path_wave_phase"
    )

    val orbPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_path_orb_pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("aura_path_canvas")
    ) {
        val w = size.width
        val h = size.height

        // Resolve Source Button & Target Device coordinates (measured or canonical fallback)
        val defaultSource = if (auraPathState.isOutgoing) {
            Offset(w * 0.5f, h * 0.62f)
        } else {
            Offset(w * 0.5f, h * 0.08f)
        }
        val defaultTarget = if (auraPathState.isOutgoing) {
            Offset(w * 0.5f, h * 0.14f)
        } else {
            Offset(w * 0.5f, h * 0.56f)
        }

        val p0 = auraPathState.sourceButtonOffset?.takeIf { it.x > 0f && it.y > 0f } ?: defaultSource
        val p3 = auraPathState.targetDeviceOffset?.takeIf { it.x > 0f && it.y > 0f } ?: defaultTarget

        val dy = p3.y - p0.y
        val curveSwing = (w * 0.24f) * sin(wavePhase * 0.5f).coerceIn(-1f, 1f)
        val p1 = Offset(
            x = (p0.x + (p3.x - p0.x) * 0.25f - w * 0.18f + curveSwing * 0.35f).coerceIn(24f, w - 24f),
            y = p0.y + dy * 0.32f
        )
        val p2 = Offset(
            x = (p0.x + (p3.x - p0.x) * 0.75f + w * 0.18f - curveSwing * 0.35f).coerceIn(24f, w - 24f),
            y = p0.y + dy * 0.68f
        )

        // 1. Full Bezier Aura Path Conduit (Track)
        val fullAuraPath = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }

        // Outer diffused aura glow along full path
        drawPath(
            path = fullAuraPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF5EEAD4).copy(alpha = 0.24f),
                    ElectricCyan.copy(alpha = 0.28f),
                    RadiantPurple.copy(alpha = 0.24f)
                ),
                start = p0,
                end = p3
            ),
            style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
        )

        // Inner subtle baseline track
        drawPath(
            path = fullAuraPath,
            color = ElectricCyan.copy(alpha = 0.32f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 2. Active Illuminated Migration Segment (0.0 -> animatedProgress)
        val activeSteps = 48
        val activePath = Path()
        val maxStep = (activeSteps * animatedProgress).toInt().coerceAtLeast(1)
        for (step in 0..maxStep) {
            val t = (step.toFloat() / activeSteps).coerceAtMost(animatedProgress)
            val pt = evaluateCubicBezier(p0, p1, p2, p3, t)
            if (step == 0) activePath.moveTo(pt.x, pt.y) else activePath.lineTo(pt.x, pt.y)
        }

        drawPath(
            path = activePath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF5EEAD4),
                    ElectricCyan,
                    RadiantPurple,
                    SolarAmber
                ),
                start = p0,
                end = p3
            ),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Harmonic Travelling Wave Filaments along the Aura Path
        for (i in 0 until 14) {
            val particleT = ((wavePhase / (2f * PI.toFloat())) + i / 14f) % 1f
            val pos = evaluateCubicBezier(p0, p1, p2, p3, particleT)
            val alpha = sin(particleT * PI.toFloat()).coerceIn(0f, 1f)
            drawCircle(
                color = if (i % 2 == 0) ElectricCyan.copy(alpha = alpha * 0.85f) else RadiantPurple.copy(alpha = alpha * 0.85f),
                radius = (3.5f + (i % 3) * 1.8f).dp.toPx() * 0.45f,
                center = pos
            )
        }

        // 4. Source Button Vortex Ring & Target Device Arrival Vortex Ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF5EEAD4).copy(alpha = 0.65f), Color.Transparent),
                center = p0,
                radius = 34.dp.toPx() * orbPulse
            ),
            radius = 34.dp.toPx() * orbPulse,
            center = p0
        )
        drawCircle(
            color = ElectricCyan.copy(alpha = 0.85f),
            radius = 12.dp.toPx() * orbPulse,
            center = p0,
            style = Stroke(width = 2.5.dp.toPx())
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(RadiantPurple.copy(alpha = 0.65f), ElectricCyan.copy(alpha = 0.35f), Color.Transparent),
                center = p3,
                radius = 40.dp.toPx() * orbPulse
            ),
            radius = 40.dp.toPx() * orbPulse,
            center = p3
        )
        drawCircle(
            color = Color(0xFF5EEAD4).copy(alpha = 0.9f),
            radius = 15.dp.toPx() * (2f - orbPulse * 0.7f),
            center = p3,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // 5. Migrating File Payload Energy Core at B(animatedProgress)
        val payloadPos = evaluateCubicBezier(p0, p1, p2, p3, animatedProgress)
        val payloadRadius = 28.dp.toPx() * orbPulse

        // Outer Aura Halo of migrating file payload
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    ElectricCyan.copy(alpha = 0.82f),
                    RadiantPurple.copy(alpha = 0.48f),
                    Color.Transparent
                ),
                center = payloadPos,
                radius = payloadRadius * 1.75f
            ),
            radius = payloadRadius * 1.75f,
            center = payloadPos
        )

        // Rotating / pulsing dual aura rings around the migrating payload
        drawCircle(
            color = SolarAmber.copy(alpha = 0.88f),
            radius = payloadRadius * 0.72f,
            center = payloadPos,
            style = Stroke(width = 2.2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = payloadPos
        )
    }
}

/**
 * Evaluates a point on a 2D Cubic Bezier curve B(t) for t in [0, 1].
 */
fun evaluateCubicBezier(
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    t: Float
): Offset {
    val u = 1f - t
    val tt = t * t
    val uu = u * u
    val uuu = uu * u
    val ttt = tt * t

    val x = uuu * p0.x + 3f * uu * t * p1.x + 3f * u * tt * p2.x + ttt * p3.x
    val y = uuu * p0.y + 3f * uu * t * p1.y + 3f * u * tt * p2.y + ttt * p3.y
    return Offset(x, y)
}


