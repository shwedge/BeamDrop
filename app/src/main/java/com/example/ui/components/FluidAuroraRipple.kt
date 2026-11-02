package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuraPathState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import com.example.ui.theme.ActiveBeamThemeState

/**
 * Apple iOS 26 Clear Frosted Control Center & Home Screen Glass Card Modifier.
 * Dynamically adapts to `ActiveBeamThemeState.current` so selecting Theme = "iOS 26 Clear"
 * or any Color palette transforms all cards across the app immediately.
 */
fun Modifier.glassLiquidCard(
    cornerRadius: Dp = 24.dp,
    tintColor: Color = ElectricCyan,
    secondaryTint: Color = NeonTeal,
    baseColor: Color = Color(0xFF18222E),
    baseAlpha: Float = 0.42f
): Modifier = composed {
    val activeTheme = ActiveBeamThemeState.current
    val isClear = activeTheme.isIos26ClearMode
    val dynamicPrimary = activeTheme.primaryAccent
    val dynamicSecondary = activeTheme.secondaryAccent
    val resolvedBaseColor = if (isClear) activeTheme.cardBaseColor else baseColor
    val frostAlpha = activeTheme.glassWhiteFrostAlpha
    val rimAlpha = activeTheme.glassRimAlpha

    this
        .clip(RoundedCornerShape(cornerRadius))
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val w = size.width
            val h = size.height
            val effectiveBaseAlpha = when {
                isClear -> 0.28f
                baseAlpha >= 0.90f -> 0.78f
                else -> baseAlpha.coerceIn(0.28f, 0.52f)
            }

            // Smoky-clear iOS 26 Control Center frosted glass fill
            val glassBodyBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = frostAlpha),
                    resolvedBaseColor.copy(alpha = effectiveBaseAlpha),
                    activeTheme.cardBaseColor.copy(alpha = effectiveBaseAlpha + 0.05f),
                    dynamicPrimary.copy(alpha = if (isClear) 0.18f else 0.12f)
                ),
                start = Offset.Zero,
                end = Offset(w, h)
            )

            // Subtle inner glass refraction cushion along the top-left and bottom-right
            val innerCushionBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isClear) 0.42f else 0.24f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = if (isClear) 0.26f else 0.14f)
                ),
                start = Offset.Zero,
                end = Offset(w, h)
            )

            val topLensSheen = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isClear) 0.26f else 0.14f),
                    Color.White.copy(alpha = if (isClear) 0.09f else 0.04f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = (h * 0.45f).coerceAtLeast(1f)
            )

            // Crisp iOS 26 corner-weighted white specular border
            val outerCrystalRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = rimAlpha),
                    Color.White.copy(alpha = if (isClear) 0.42f else 0.26f),
                    dynamicPrimary.copy(alpha = 0.34f),
                    Color.White.copy(alpha = if (isClear) 0.78f else 0.58f)
                ),
                start = Offset.Zero,
                end = Offset(w, h)
            )

            val insetPx = 1.5.dp.toPx()
            val innerCorner = (radiusPx - insetPx).coerceAtLeast(4f)

            onDrawBehind {
                drawRoundRect(
                    brush = glassBodyBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
                drawRoundRect(
                    brush = topLensSheen,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
                if (w > insetPx * 4 && h > insetPx * 4) {
                    drawRoundRect(
                        brush = innerCushionBrush,
                        topLeft = Offset(insetPx, insetPx),
                        size = Size(w - insetPx * 2f, h - insetPx * 2f),
                        cornerRadius = CornerRadius(innerCorner, innerCorner),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
                drawRoundRect(
                    brush = outerCrystalRim,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = if (isClear) 1.35.dp.toPx() else 1.1.dp.toPx())
                )
            }
        }
}

/**
 * Apple iOS 26 Control Center & Settings Pill Modifier.
 * Reacts to `ActiveBeamThemeState.current` so Theme = "iOS 26 Clear" and Color themes update all pills.
 */
fun Modifier.glassLiquidPill(
    cornerRadius: Dp = 12.dp,
    tintColor: Color = ElectricCyan,
    baseColor: Color = Color(0xFF28423F)
): Modifier = composed {
    val activeTheme = ActiveBeamThemeState.current
    val isClear = activeTheme.isIos26ClearMode
    val dynamicPrimary = activeTheme.primaryAccent
    val resolvedBase = if (isClear) activeTheme.pillBaseColor else baseColor

    this
        .clip(RoundedCornerShape(cornerRadius))
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val bodyBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isClear) 0.36f else 0.22f),
                    resolvedBase.copy(alpha = if (isClear) 0.42f else 0.58f),
                    dynamicPrimary.copy(alpha = 0.22f)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val borderBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isClear) 0.88f else 0.68f),
                    dynamicPrimary.copy(alpha = 0.36f),
                    Color.White.copy(alpha = if (isClear) 0.68f else 0.48f)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            onDrawBehind {
                drawRoundRect(
                    brush = bodyBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
                drawRoundRect(
                    brush = borderBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = if (isClear) 1.25.dp.toPx() else 1.dp.toPx())
                )
            }
        }
}

/**
 * Apple iOS 26 Clear Glass App Icon Squircle / Control Center Orb (matches `images_6a1407ce.jpg` & `images_192676b6.jpg`).
 * Reacts to `ActiveBeamThemeState.current` for live Theme ("iOS 26 Clear") and Color changes.
 */
@Composable
fun GlassLiquidIconOrb(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 14.dp,
    tintColor: Color = ElectricCyan,
    secondaryColor: Color = NeonTeal,
    content: @Composable BoxScope.() -> Unit
) {
    val activeTheme = ActiveBeamThemeState.current
    val isClear = activeTheme.isIos26ClearMode
    val dynamicPrimary = activeTheme.primaryAccent
    val dynamicSecondary = activeTheme.secondaryAccent
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .drawWithCache {
                val rPx = cornerRadius.toPx()
                val w = this.size.width
                val h = this.size.height

                // Smoky-clear iOS 26 translucent icon body with subtle tint refraction
                val squircleBody = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isClear) 0.44f else 0.30f),
                        dynamicPrimary.copy(alpha = 0.26f),
                        activeTheme.cardBaseColor.copy(alpha = if (isClear) 0.34f else 0.52f),
                        dynamicSecondary.copy(alpha = 0.22f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )

                // Top-left optical specular gloss
                val topGloss = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isClear) 0.40f else 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.22f, h * 0.18f),
                    radius = w * 0.72f
                )

                // Inner pillowed glass cushion rim
                val innerCushion = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isClear) 0.52f else 0.38f),
                        Color.Transparent,
                        Color.White.copy(alpha = if (isClear) 0.28f else 0.18f)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )

                // Crisp iOS 26 white specular outer rim (brightest at top-left & bottom-right corners)
                val squircleRim = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isClear) 0.96f else 0.88f),
                        Color.White.copy(alpha = 0.36f),
                        dynamicPrimary.copy(alpha = 0.40f),
                        Color.White.copy(alpha = if (isClear) 0.84f else 0.72f)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )

                val inset = 1.6.dp.toPx()
                val innerR = (rPx - inset).coerceAtLeast(2f)

                onDrawBehind {
                    drawRoundRect(
                        brush = squircleBody,
                        cornerRadius = CornerRadius(rPx, rPx)
                    )
                    drawCircle(
                        brush = topGloss,
                        radius = w * 0.72f,
                        center = Offset(w * 0.22f, h * 0.18f)
                    )
                    if (w > inset * 4) {
                        drawRoundRect(
                            brush = innerCushion,
                            topLeft = Offset(inset, inset),
                            size = Size(w - inset * 2f, h - inset * 2f),
                            cornerRadius = CornerRadius(innerR, innerR),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                    drawRoundRect(
                        brush = squircleRim,
                        cornerRadius = CornerRadius(rPx, rPx),
                        style = Stroke(width = if (isClear) 1.35.dp.toPx() else 1.15.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Concentric Segmented Ring Emblem from the Settings footer screenshot (`image_2fdad807.png`).
 */
@Composable
fun BeamDropSegmentedEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    primaryColor: Color = Color(0xFF2DD4BF),
    secondaryColor: Color = Color(0xFF38BDF8)
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val strokeOuter = w * 0.105f
        val strokeMid = w * 0.095f
        val brush = Brush.linearGradient(
            colors = listOf(primaryColor, secondaryColor),
            start = Offset.Zero,
            end = Offset(w, w)
        )

        // Outer 4 segmented arcs
        val outerInset = strokeOuter / 2f + 2f
        val outerSize = Size(w - outerInset * 2f, w - outerInset * 2f)
        val outerAngles = listOf(-70f to 52f, 20f to 52f, 110f to 52f, 200f to 52f)
        outerAngles.forEach { (startAngle, sweep) ->
            drawArc(
                brush = brush,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(outerInset, outerInset),
                size = outerSize,
                style = Stroke(width = strokeOuter, cap = StrokeCap.Round)
            )
        }

        // Middle 3 segmented arcs
        val midInset = w * 0.23f
        val midSize = Size(w - midInset * 2f, w - midInset * 2f)
        val midAngles = listOf(-45f to 75f, 75f to 75f, 195f to 75f)
        midAngles.forEach { (startAngle, sweep) ->
            drawArc(
                brush = brush,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(midInset, midInset),
                size = midSize,
                style = Stroke(width = strokeMid, cap = StrokeCap.Round)
            )
        }

        // Center glowing dot
        drawCircle(
            brush = brush,
            radius = w * 0.12f,
            center = Offset(w * 0.5f, w * 0.5f)
        )
    }
}

/**
 * Apple WWDC25 Liquid Glass Locate Hero Emblem (`podcasts.icon` / AirDrop Locate Squircle from the user's screenshot).
 * Renders the 3D pillowed royal-sapphire squircle with:
 * - `3_circle1`: Outer frosted milky-white translucent glass disc with 34° specular rim
 * - `2_circle2`: Middle frosted white glass C-ring with 34° specular rim
 * - `1_person`: Glowing milky-white 3D glass sphere core + rounded vertical frosted glass stem
 */
@Composable
fun WwdcLiquidGlassLocateEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 116.dp,
    cornerRadius: Dp = 30.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val rPx = cornerRadius.toPx()
                val center = Offset(w * 0.5f, h * 0.47f)

                val squircleGradient = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF60A5FA),
                        Color(0xFF2563EB),
                        Color(0xFF1D4ED8),
                        Color(0xFF172554)
                    ),
                    start = Offset(w * 0.15f, 0f),
                    end = Offset(w * 0.85f, h)
                )

                val topRightPillow = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFBAE6FD).copy(alpha = 0.65f),
                        Color(0xFF60A5FA).copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.78f, h * 0.22f),
                    radius = w * 0.55f
                )

                val innerCushionStroke = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f),
                        Color(0xFFBAE6FD).copy(alpha = 0.52f),
                        Color(0xFF1E40AF).copy(alpha = 0.25f),
                        Color(0xFF93C5FD).copy(alpha = 0.65f)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )

                // 3_circle1 (Outer frosted glass disc)
                val rOuter = w * 0.35f
                val outerDiscBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.58f),
                        Color(0xFFE0F2FE).copy(alpha = 0.32f),
                        Color(0xFF93C5FD).copy(alpha = 0.20f)
                    ),
                    start = Offset(center.x - rOuter, center.y - rOuter),
                    end = Offset(center.x + rOuter, center.y + rOuter)
                )

                // 2_circle2 (Middle frosted glass disc)
                val rMid = w * 0.22f
                val midDiscBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.74f),
                        Color(0xFFE0F2FE).copy(alpha = 0.46f),
                        Color(0xFFBAE6FD).copy(alpha = 0.30f)
                    ),
                    start = Offset(center.x - rMid, center.y - rMid),
                    end = Offset(center.x + rMid, center.y + rMid)
                )

                // 1_person (Central glowing white sphere + rounded frosted glass stem)
                val rCore = w * 0.095f
                val coreBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFF0F9FF),
                        Color(0xFFBAE6FD)
                    ),
                    center = Offset(center.x - rCore * 0.2f, center.y - rCore * 0.2f),
                    radius = rCore * 1.3f
                )

                val stemWidth = w * 0.165f
                val stemHeight = h * 0.28f
                val stemTop = center.y + rCore * 1.15f
                val stemLeft = center.x - stemWidth * 0.5f
                val stemBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        Color(0xFFE0F2FE).copy(alpha = 0.72f),
                        Color(0xFF93C5FD).copy(alpha = 0.54f)
                    ),
                    start = Offset(stemLeft, stemTop),
                    end = Offset(stemLeft + stemWidth, stemTop + stemHeight)
                )

                val outerRimBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFFBAE6FD).copy(alpha = 0.75f),
                        Color(0xFF3B82F6).copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.82f)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )

                val cushionInset = 4.dp.toPx()

                onDrawBehind {
                    // 1. Pillowed Sapphire Squircle Fill
                    drawRoundRect(
                        brush = squircleGradient,
                        cornerRadius = CornerRadius(rPx, rPx)
                    )
                    drawCircle(
                        brush = topRightPillow,
                        radius = w * 0.55f,
                        center = Offset(w * 0.78f, h * 0.22f)
                    )
                    // 2. Inner 3D Cushion Bevel
                    drawRoundRect(
                        brush = innerCushionStroke,
                        topLeft = Offset(cushionInset, cushionInset),
                        size = Size(w - cushionInset * 2f, h - cushionInset * 2f),
                        cornerRadius = CornerRadius(rPx - cushionInset, rPx - cushionInset),
                        style = Stroke(width = 5.dp.toPx())
                    )

                    // 3. Group 3_circle1: Outer Frosted Glass Disc + Specular Rim
                    drawCircle(
                        brush = outerDiscBrush,
                        radius = rOuter,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.78f),
                        radius = rOuter,
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx())
                    )

                    // 4. Group 2_circle2: Middle Frosted Glass Disc + Specular Rim
                    drawCircle(
                        brush = midDiscBrush,
                        radius = rMid,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.88f),
                        radius = rMid,
                        center = center,
                        style = Stroke(width = 1.8.dp.toPx())
                    )

                    // 5. Group 1_person: Central Glowing Glass Sphere + Frosted Capsule Stem
                    drawCircle(
                        brush = coreBrush,
                        radius = rCore,
                        center = center
                    )
                    drawRoundRect(
                        brush = stemBrush,
                        topLeft = Offset(stemLeft, stemTop),
                        size = Size(stemWidth, stemHeight),
                        cornerRadius = CornerRadius(stemWidth * 0.5f, stemWidth * 0.5f)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.90f),
                        topLeft = Offset(stemLeft, stemTop),
                        size = Size(stemWidth, stemHeight),
                        cornerRadius = CornerRadius(stemWidth * 0.5f, stemWidth * 0.5f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // 6. Outer Squircle Specular Rim
                    drawRoundRect(
                        brush = outerRimBrush,
                        cornerRadius = CornerRadius(rPx, rPx),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }
    )
}

/**
 * Top-Edge WWDC25 Glass Locate & Liquid Wave for Android.
 * Only runs its animation when visible (0% CPU when idle).
 */
@Composable
fun FluidAuroraRipple(
    intensity: Float,
    isContactLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val targetIntensity = if (isContactLocked) 1f else intensity.coerceIn(0f, 1f)
    val animatedIntensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "glass_liquid_top_intensity"
    )

    if (animatedIntensity <= 0.01f && !isContactLocked) {
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "top_liquid_wave_phase")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_rad"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawGlassLocateBeaconPulse(
            anchor = Offset(size.width * 0.5f, 0f),
            phase = wavePhase,
            intensity = animatedIntensity,
            maxRadius = size.width * 0.58f
        )
        drawTopGlassLiquidWave(
            phase = wavePhase,
            intensity = animatedIntensity,
            isContactLocked = isContactLocked
        )
    }
}

/**
 * Reusable modifier that renders a clean static WWDC25 Liquid Glass rim around interactive buttons.
 */
fun Modifier.fluidAuraRippleButton(
    enabled: Boolean = true,
    primaryColor: Color = ElectricCyan,
    secondaryColor: Color = NeonTeal,
    cornerRadius: Dp = 18.dp
): Modifier = composed {
    if (!enabled) return@composed this

    this.drawBehind {
        val radiusPx = cornerRadius.toPx()
        val expandPx = 3.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.42f),
                    primaryColor.copy(alpha = 0.30f),
                    secondaryColor.copy(alpha = 0.22f)
                )
            ),
            topLeft = Offset(-expandPx, -expandPx),
            size = Size(size.width + expandPx * 2f, size.height + expandPx * 2f),
            cornerRadius = CornerRadius(radiusPx + expandPx, radiusPx + expandPx),
            style = Stroke(width = 1.3.dp.toPx())
        )
    }
}

/**
 * Apple WWDC25 "Glass Locate" Directional Animation Overlay for Android (synchronized with Windows).
 * Combines:
 * 1. Concentric frosted white/ice-blue 3D Glass Locate lens discs (`3_circle1`, `2_circle2`, `1_core`)
 *    radiating from the target edge (`LEFT`, `RIGHT`, or `TOP`) with 34° specular rims.
 * 2. Smooth translucent Liquid Glass refraction waves along the matching screen edge.
 *
 * Only animates when `auraPathState.isActive` is true (0% idle CPU/battery usage).
 */
@Composable
fun AuraPathCanvasOverlay(
    auraPathState: AuraPathState,
    modifier: Modifier = Modifier
) {
    val targetAlpha = if (auraPathState.isActive) 1f else 0f
    val overlayAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "glass_liquid_overlay_alpha"
    )

    if (overlayAlpha <= 0.01f && !auraPathState.isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_flow_transition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_glass_wave_phase"
    )

    val direction = auraPathState.flowDirection.uppercase()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("aura_path_canvas")
    ) {
        when (direction) {
            "LEFT" -> {
                drawGlassLocateBeaconPulse(
                    anchor = Offset(0f, size.height * 0.48f),
                    phase = wavePhase,
                    intensity = overlayAlpha,
                    maxRadius = size.width * 0.62f
                )
                drawSideGlassLiquidWave(
                    isLeftEdge = true,
                    phase = wavePhase,
                    intensity = overlayAlpha
                )
            }
            "RIGHT" -> {
                drawGlassLocateBeaconPulse(
                    anchor = Offset(size.width, size.height * 0.48f),
                    phase = wavePhase,
                    intensity = overlayAlpha,
                    maxRadius = size.width * 0.62f
                )
                drawSideGlassLiquidWave(
                    isLeftEdge = false,
                    phase = wavePhase,
                    intensity = overlayAlpha
                )
            }
            else -> {
                drawGlassLocateBeaconPulse(
                    anchor = Offset(size.width * 0.5f, 0f),
                    phase = wavePhase,
                    intensity = overlayAlpha,
                    maxRadius = size.width * 0.68f
                )
                drawTopGlassLiquidWave(
                    phase = wavePhase,
                    intensity = overlayAlpha,
                    isContactLocked = true
                )
            }
        }
    }
}

/**
 * Apple WWDC25 "Glass Locate" NFC & Proximity Scan Animation Overlay.
 */
@Composable
fun NfcLiquidScanOverlay(
    isScanning: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scanAlpha by animateFloatAsState(
        targetValue = if (isScanning) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "nfc_scan_alpha"
    )

    if (scanAlpha <= 0.01f && !isScanning) return

    val infiniteTransition = rememberInfiniteTransition(label = "nfc_liquid_scan_transition")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nfc_scan_wave_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("nfc_scan_animation_overlay")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGlassLocateBeaconPulse(
                anchor = Offset(size.width * 0.5f, 0f),
                phase = scanProgress,
                intensity = scanAlpha,
                maxRadius = size.width * 0.82f
            )
            drawTopGlassLiquidWave(
                phase = scanProgress,
                intensity = scanAlpha * 0.9f,
                isContactLocked = true
            )
        }

        AnimatedVisibility(
            visible = isScanning,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 66.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .glassLiquidCard(
                        cornerRadius = 22.dp,
                        tintColor = ElectricCyan,
                        secondaryTint = NeonTeal,
                        baseColor = Color(0xFF0F2B70),
                        baseAlpha = 0.88f
                    )
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("nfc_scan_status_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    GlassLiquidIconOrb(
                        size = 36.dp,
                        cornerRadius = 11.dp,
                        tintColor = ElectricCyan,
                        secondaryColor = NeonTeal
                    ) {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = "NFC Scanning",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Glass Locate NFC Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Hold top of phone near another device to locate & beam",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("dismiss_nfc_scan_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close NFC Scan",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Draws the Apple WWDC25 "Glass Locate" Concentric Frosted Glass Discs (`3_circle1`, `2_circle2`, `1_core`)
 * expanding from the active edge anchor (`TOP`, `LEFT`, or `RIGHT`) with 34° white-ice specular rims.
 */
private fun DrawScope.drawGlassLocateBeaconPulse(
    anchor: Offset,
    phase: Float,
    intensity: Float,
    maxRadius: Float
) {
    val normProgress = (phase / (2f * PI.toFloat())).coerceIn(0f, 1f)

    // Ambient sapphire-cyan refraction aura around the locate anchor
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.36f * intensity),
                Color(0xFF7DD3FC).copy(alpha = 0.28f * intensity),
                Color(0xFF2563EB).copy(alpha = 0.18f * intensity),
                Color.Transparent
            ),
            center = anchor,
            radius = maxRadius * 0.95f
        ),
        radius = maxRadius * 0.95f,
        center = anchor
    )

    // 3 Concentric Frosted White-Ice Glass Locate Discs (matching 3_circle1, 2_circle2, 1_person)
    for (ringIdx in 0 until 3) {
        val ringProg = (normProgress + ringIdx * 0.33f) % 1f
        val radius = maxRadius * (0.14f + 0.78f * ringProg)
        val fade = (1f - ringProg) * (1f - ringProg * 0.45f) * intensity

        // Frosted milky-white translucent glass disc body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f * fade),
                    Color(0xFFE0F2FE).copy(alpha = 0.22f * fade),
                    Color.White.copy(alpha = 0.38f * fade),
                    Color.Transparent
                ),
                center = anchor,
                radius = radius
            ),
            radius = radius,
            center = anchor
        )

        // Crisp 34° Specular White-Ice Glass Rim
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f * fade),
                    Color(0xFFBAE6FD).copy(alpha = 0.70f * fade),
                    Color(0xFF60A5FA).copy(alpha = 0.45f * fade),
                    Color.White.copy(alpha = 0.85f * fade)
                ),
                start = Offset(anchor.x - radius, anchor.y - radius),
                end = Offset(anchor.x + radius, anchor.y + radius)
            ),
            radius = radius,
            center = anchor,
            style = Stroke(width = (2.4f - ringIdx * 0.4f).dp.toPx())
        )
    }

    // Central Glowing Milky-White Glass Core at the Locate Anchor
    val corePulse = 1f + 0.12f * sin(phase)
    val coreRadius = 22.dp.toPx() * corePulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f * intensity),
                Color(0xFFE0F2FE).copy(alpha = 0.72f * intensity),
                Color(0xFF38BDF8).copy(alpha = 0.30f * intensity),
                Color.Transparent
            ),
            center = anchor,
            radius = coreRadius * 1.8f
        ),
        radius = coreRadius * 1.8f,
        center = anchor
    )
}

/**
 * Draws a smooth, multi-layered Apple WWDC25 Liquid Glass fluid wave along the TOP edge of the screen.
 */
private fun DrawScope.drawTopGlassLiquidWave(
    phase: Float,
    intensity: Float,
    isContactLocked: Boolean
) {
    val w = size.width
    val maxDepth = (if (isContactLocked) 58.dp else 44.dp).toPx() * intensity.coerceIn(0.2f, 1f)
    if (maxDepth <= 1f) return

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f * intensity),
                Color(0xFF7DD3FC).copy(alpha = 0.24f * intensity),
                Color(0xFF2563EB).copy(alpha = 0.12f * intensity),
                Color.Transparent
            ),
            startY = 0f,
            endY = maxDepth * 2.0f
        ),
        size = Size(w, maxDepth * 2.0f)
    )

    for (layer in 0 until 3) {
        val layerPhase = phase + layer * 1.15f
        val baseHeight = maxDepth * (0.85f - layer * 0.18f)
        val amp = maxDepth * (0.24f - layer * 0.04f)
        val steps = 28
        val stepX = w / steps

        val fillPath = Path().apply {
            moveTo(0f, 0f)
            var prevX = 0f
            var prevY = baseHeight + sin(layerPhase) * amp
            lineTo(prevX, prevY)
            for (i in 1..steps) {
                val x = i * stepX
                val angle = (i.toFloat() / steps) * 2f * PI.toFloat() + layerPhase
                val y = baseHeight + sin(angle) * amp + cos(angle * 0.5f - layerPhase) * (amp * 0.45f)
                val midX = (prevX + x) * 0.5f
                val midY = (prevY + y) * 0.5f
                quadraticTo(prevX, prevY, midX, midY)
                prevX = x
                prevY = y
            }
            lineTo(w, prevY)
            lineTo(w, 0f)
            close()
        }

        val alphaScale = (1f - layer * 0.25f) * intensity
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.32f * alphaScale),
                    Color(0xFFBAE6FD).copy(alpha = 0.24f * alphaScale),
                    Color(0xFF3B82F6).copy(alpha = 0.12f * alphaScale),
                    Color.Transparent
                ),
                startY = 0f,
                endY = baseHeight + amp * 1.5f
            )
        )

        if (layer <= 1) {
            val rimPath = Path().apply {
                var prevX = 0f
                var prevY = baseHeight + sin(layerPhase) * amp
                moveTo(prevX, prevY)
                for (i in 1..steps) {
                    val x = i * stepX
                    val angle = (i.toFloat() / steps) * 2f * PI.toFloat() + layerPhase
                    val y = baseHeight + sin(angle) * amp + cos(angle * 0.5f - layerPhase) * (amp * 0.45f)
                    val midX = (prevX + x) * 0.5f
                    val midY = (prevY + y) * 0.5f
                    quadraticTo(prevX, prevY, midX, midY)
                    prevX = x
                    prevY = y
                }
                lineTo(w, prevY)
            }
            drawPath(
                path = rimPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFBAE6FD).copy(alpha = 0.72f * alphaScale),
                        Color.White.copy(alpha = 0.95f * alphaScale),
                        Color(0xFFBAE6FD).copy(alpha = 0.72f * alphaScale),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = if (layer == 0) 2.0.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Draws a smooth, multi-layered Apple WWDC25 Liquid Glass fluid wave along the LEFT or RIGHT vertical screen edge.
 */
private fun DrawScope.drawSideGlassLiquidWave(
    isLeftEdge: Boolean,
    phase: Float,
    intensity: Float
) {
    val w = size.width
    val h = size.height
    val maxReach = 118.dp.toPx() * intensity.coerceIn(0.25f, 1f)
    if (maxReach <= 1f) return

    val edgeX = if (isLeftEdge) 0f else w
    val dirSign = if (isLeftEdge) 1f else -1f

    drawRect(
        brush = Brush.horizontalGradient(
            colors = if (isLeftEdge) {
                listOf(
                    Color.White.copy(alpha = 0.34f * intensity),
                    Color(0xFF7DD3FC).copy(alpha = 0.26f * intensity),
                    Color(0xFF2563EB).copy(alpha = 0.12f * intensity),
                    Color.Transparent
                )
            } else {
                listOf(
                    Color.Transparent,
                    Color(0xFF2563EB).copy(alpha = 0.12f * intensity),
                    Color(0xFF7DD3FC).copy(alpha = 0.26f * intensity),
                    Color.White.copy(alpha = 0.34f * intensity)
                )
            },
            startX = if (isLeftEdge) 0f else w - maxReach * 2.1f,
            endX = if (isLeftEdge) maxReach * 2.1f else w
        ),
        topLeft = Offset(if (isLeftEdge) 0f else w - maxReach * 2.1f, 0f),
        size = Size(maxReach * 2.1f, h)
    )

    for (layer in 0 until 3) {
        val layerPhase = phase + layer * 1.2f
        val baseReach = maxReach * (0.82f - layer * 0.18f)
        val amp = maxReach * (0.26f - layer * 0.04f)
        val steps = 32
        val stepY = h / steps

        val fillPath = Path().apply {
            moveTo(edgeX, 0f)
            var prevY = 0f
            var prevX = edgeX + dirSign * (baseReach * 0.2f)
            lineTo(prevX, prevY)
            for (i in 1..steps) {
                val y = i * stepY
                val yFrac = i.toFloat() / steps
                val envelope = sin(yFrac * PI.toFloat()).coerceIn(0.15f, 1f)
                val angle = yFrac * 3f * PI.toFloat() - layerPhase
                val offset = (baseReach + sin(angle) * amp + cos(angle * 0.6f + layerPhase) * (amp * 0.4f)) * envelope
                val x = edgeX + dirSign * offset
                val midX = (prevX + x) * 0.5f
                val midY = (prevY + y) * 0.5f
                quadraticTo(prevX, prevY, midX, midY)
                prevX = x
                prevY = y
            }
            lineTo(edgeX, h)
            close()
        }

        val alphaScale = (1f - layer * 0.24f) * intensity
        drawPath(
            path = fillPath,
            brush = Brush.horizontalGradient(
                colors = if (isLeftEdge) {
                    listOf(
                        Color.White.copy(alpha = 0.36f * alphaScale),
                        Color(0xFFBAE6FD).copy(alpha = 0.26f * alphaScale),
                        Color(0xFF3B82F6).copy(alpha = 0.12f * alphaScale),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.Transparent,
                        Color(0xFF3B82F6).copy(alpha = 0.12f * alphaScale),
                        Color(0xFFBAE6FD).copy(alpha = 0.26f * alphaScale),
                        Color.White.copy(alpha = 0.36f * alphaScale)
                    )
                },
                startX = if (isLeftEdge) 0f else w - maxReach * 1.4f,
                endX = if (isLeftEdge) maxReach * 1.4f else w
            )
        )

        if (layer <= 1) {
            val rimPath = Path().apply {
                var prevY = 0f
                var prevX = edgeX + dirSign * (baseReach * 0.2f)
                moveTo(prevX, prevY)
                for (i in 1..steps) {
                    val y = i * stepY
                    val yFrac = i.toFloat() / steps
                    val envelope = sin(yFrac * PI.toFloat()).coerceIn(0.15f, 1f)
                    val angle = yFrac * 3f * PI.toFloat() - layerPhase
                    val offset = (baseReach + sin(angle) * amp + cos(angle * 0.6f + layerPhase) * (amp * 0.4f)) * envelope
                    val x = edgeX + dirSign * offset
                    val midX = (prevX + x) * 0.5f
                    val midY = (prevY + y) * 0.5f
                    quadraticTo(prevX, prevY, midX, midY)
                    prevX = x
                    prevY = y
                }
            }
            drawPath(
                path = rimPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFBAE6FD).copy(alpha = 0.72f * alphaScale),
                        Color.White.copy(alpha = 0.95f * alphaScale),
                        Color(0xFFBAE6FD).copy(alpha = 0.72f * alphaScale),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = if (layer == 0) 2.0.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Evaluates a point on a cubic Bezier curve at parameter t in [0, 1].
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
