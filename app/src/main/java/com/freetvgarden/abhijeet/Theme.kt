package com.freetvgarden.abhijeet

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// ── Aesthetic Theme Colors ────────────────────────────────────────────────────
val IosBlue          = Color(0xFF30D158) // Primary Emerald Green Accent
val IosRed           = Color(0xFFFF453A) // Down / Error Red
val AestheticDark    = Color(0xFF0A0C10) // Pure Deep Midnight Background
val AestheticSurface = Color(0xFF12151E) // Matte Surface Dark
val AestheticCard    = Color(0xFF141824) // Card Surface
val AestheticBorder  = Color(0xFF222736) // Minimalist Subtle Border
val AestheticMuted   = Color(0xFF8E95A5) // Secondary Subtitle Text

/** Text helper with explicit color and typography */
@Composable
fun T(
    s: String, size: Int = 16, w: FontWeight = FontWeight.Medium, color: Color = Color.White,
    modifier: Modifier = Modifier, lines: Int = 1, align: TextAlign? = null
) = Text(
    s, modifier, color, size.sp, fontWeight = w, fontFamily = FontFamily.SansSerif,
    maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = align
)

/** Aesthetic Clean Dark Matte Background (Zero Gaudy Gradients) */
@Composable
fun TvBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(AestheticDark)) {
        content()
    }
}

/**
 * Aesthetic Minimalist Focus Surface:
 * Clean matte surface that scales slightly with subtle emerald glow on focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    solidOnFocus: Boolean = false,
    focusScale: Float = 1.05f,
    base: Float = 0.85f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        if (focused) focusScale else 1f,
        spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "scale"
    )
    Box(
        modifier
            .zIndex(if (focused) 2f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 16.dp else 0.dp, shape, ambientColor = IosBlue, spotColor = IosBlue)
            .clip(shape)
            .background(
                if (focused && solidOnFocus) IosBlue
                else if (focused) Color(0xFF1B2130)
                else AestheticSurface.copy(alpha = base)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) IosBlue else AestheticBorder,
                shape
            )
            .combinedClickable(interactionSource = src, indication = null, onLongClick = onLongClick, onClick = onClick)
    ) { this.content(focused) }
}

@Composable
fun RoundBtn(modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Glass(modifier.size(60.dp), CircleShape, solidOnFocus = true, focusScale = 1.1f, onClick = onClick) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            icon(if (f) Color.Black else Color.White)
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier.size(56.dp)) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Canvas(modifier) {
        drawArc(
            IosBlue, a, 270f, false, topLeft = Offset(4f, 4f),
            size = Size(size.width - 8f, size.height - 8f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ── Complete Vector Icon System (No Emojis) ───────────────────────────────────
enum class Ico {
    Tv,
    Radio,
    Webcam,
    HeartFilled,
    HeartOutline,
    Search,
    Settings,
    Refresh,
    Filter,
    Sort,
    Close,
    Check,
    Play,
    Pause,
    Prev,
    Next,
    Plus,
    Minus,
    History,
    Globe,
    Language,
    Category,
    Sliders
}

@Composable
fun TvIcon(k: Ico, tint: Color, modifier: Modifier = Modifier.size(22.dp)) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val strokeWidth = 2.dp.toPx()
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun tri(x0: Float, x1: Float, flip: Boolean = false) = Path().apply {
        if (!flip) { moveTo(x0, 0f); lineTo(x1, h / 2); lineTo(x0, h) }
        else { moveTo(x1, 0f); lineTo(x0, h / 2); lineTo(x1, h) }
        close()
    }

    when (k) {
        Ico.Tv -> {
            // Television screen
            drawRoundRect(tint, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.58f), CornerRadius(4.dp.toPx()), style = stroke)
            // Stand neck & base
            drawLine(tint, Offset(w * 0.5f, h * 0.76f), Offset(w * 0.5f, h * 0.90f), strokeWidth)
            drawLine(tint, Offset(w * 0.32f, h * 0.90f), Offset(w * 0.68f, h * 0.90f), strokeWidth)
            // Left & Right Antennas
            drawLine(tint, Offset(w * 0.44f, h * 0.18f), Offset(w * 0.28f, h * 0.04f), strokeWidth)
            drawLine(tint, Offset(w * 0.56f, h * 0.18f), Offset(w * 0.72f, h * 0.04f), strokeWidth)
        }

        Ico.Radio -> {
            // Radio body
            drawRoundRect(tint, Offset(w * 0.08f, h * 0.28f), Size(w * 0.84f, h * 0.58f), CornerRadius(4.dp.toPx()), style = stroke)
            // Speaker
            drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.35f, h * 0.57f), style = stroke)
            drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.35f, h * 0.57f))
            // Tuning lines
            drawLine(tint, Offset(w * 0.62f, h * 0.46f), Offset(w * 0.82f, h * 0.46f), strokeWidth)
            drawLine(tint, Offset(w * 0.62f, h * 0.66f), Offset(w * 0.82f, h * 0.66f), strokeWidth)
            // Antenna
            drawLine(tint, Offset(w * 0.22f, h * 0.28f), Offset(w * 0.78f, h * 0.06f), strokeWidth)
        }

        Ico.Webcam -> {
            // Camera body
            drawRoundRect(tint, Offset(w * 0.1f, h * 0.24f), Size(w * 0.56f, h * 0.52f), CornerRadius(4.dp.toPx()), style = stroke)
            // Lens triangle
            val lens = Path().apply {
                moveTo(w * 0.66f, h * 0.38f)
                lineTo(w * 0.90f, h * 0.24f)
                lineTo(w * 0.90f, h * 0.76f)
                lineTo(w * 0.66f, h * 0.62f)
                close()
            }
            drawPath(lens, tint)
            // Center circle
            drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.38f, h * 0.50f), style = stroke)
        }

        Ico.HeartFilled -> {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.86f)
                cubicTo(w * 0.12f, h * 0.56f, 0f, h * 0.34f, 0f, h * 0.20f)
                cubicTo(0f, h * 0.06f, w * 0.16f, 0f, w * 0.34f, 0f)
                cubicTo(w * 0.44f, 0f, w * 0.5f, h * 0.08f, w * 0.5f, h * 0.14f)
                cubicTo(w * 0.5f, h * 0.08f, w * 0.56f, 0f, w * 0.66f, 0f)
                cubicTo(w * 0.84f, 0f, w, h * 0.06f, w, h * 0.20f)
                cubicTo(w, h * 0.34f, w * 0.88f, h * 0.56f, w * 0.5f, h * 0.86f)
                close()
            }
            drawPath(p, tint)
        }

        Ico.HeartOutline -> {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.86f)
                cubicTo(w * 0.12f, h * 0.56f, 0f, h * 0.34f, 0f, h * 0.20f)
                cubicTo(0f, h * 0.06f, w * 0.16f, 0f, w * 0.34f, 0f)
                cubicTo(w * 0.44f, 0f, w * 0.5f, h * 0.08f, w * 0.5f, h * 0.14f)
                cubicTo(w * 0.5f, h * 0.08f, w * 0.56f, 0f, w * 0.66f, 0f)
                cubicTo(w * 0.84f, 0f, w, h * 0.06f, w, h * 0.20f)
                cubicTo(w, h * 0.34f, w * 0.88f, h * 0.56f, w * 0.5f, h * 0.86f)
                close()
            }
            drawPath(p, tint, style = stroke)
        }

        Ico.Search -> {
            drawCircle(tint, radius = w * 0.30f, center = Offset(w * 0.42f, h * 0.42f), style = stroke)
            drawLine(tint, Offset(w * 0.64f, h * 0.64f), Offset(w * 0.88f, h * 0.88f), strokeWidth = strokeWidth * 1.3f, cap = StrokeCap.Round)
        }

        Ico.Settings -> {
            // Gear: outer ring with 6 teeth notches and inner hole
            drawCircle(tint, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            // 8 notches around the gear
            val r1 = w * 0.30f; val r2 = w * 0.44f
            for (angle in 0 until 360 step 45) {
                val rad = Math.toRadians(angle.toDouble()).toFloat()
                val cos = Math.cos(rad.toDouble()).toFloat()
                val sin = Math.sin(rad.toDouble()).toFloat()
                drawLine(
                    tint,
                    Offset(w * 0.5f + r1 * cos, h * 0.5f + r1 * sin),
                    Offset(w * 0.5f + r2 * cos, h * 0.5f + r2 * sin),
                    strokeWidth = strokeWidth * 1.2f,
                    cap = StrokeCap.Round
                )
            }
        }

        Ico.Refresh -> {
            drawArc(tint, 45f, 270f, false, topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.68f), style = stroke)
            // Arrowhead at top
            val arrow = Path().apply {
                moveTo(w * 0.68f, h * 0.04f)
                lineTo(w * 0.84f, h * 0.20f)
                lineTo(w * 0.56f, h * 0.20f)
                close()
            }
            drawPath(arrow, tint)
        }

        Ico.Filter, Ico.Sliders -> {
            // 3 slider lines with indicator dots
            drawLine(tint, Offset(w * 0.12f, h * 0.26f), Offset(w * 0.88f, h * 0.26f), strokeWidth)
            drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.36f, h * 0.26f))

            drawLine(tint, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.88f, h * 0.50f), strokeWidth)
            drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.68f, h * 0.50f))

            drawLine(tint, Offset(w * 0.12f, h * 0.74f), Offset(w * 0.88f, h * 0.74f), strokeWidth)
            drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.42f, h * 0.74f))
        }

        Ico.Sort -> {
            // Up arrow
            drawLine(tint, Offset(w * 0.32f, h * 0.80f), Offset(w * 0.32f, h * 0.20f), strokeWidth)
            val up = Path().apply {
                moveTo(w * 0.18f, h * 0.34f)
                lineTo(w * 0.32f, h * 0.18f)
                lineTo(w * 0.46f, h * 0.34f)
            }
            drawPath(up, tint, style = stroke)

            // Down arrow
            drawLine(tint, Offset(w * 0.68f, h * 0.20f), Offset(w * 0.68f, h * 0.80f), strokeWidth)
            val down = Path().apply {
                moveTo(w * 0.54f, h * 0.66f)
                lineTo(w * 0.68f, h * 0.82f)
                lineTo(w * 0.82f, h * 0.66f)
            }
            drawPath(down, tint, style = stroke)
        }

        Ico.Close -> {
            drawLine(tint, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), strokeWidth = strokeWidth * 1.2f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), strokeWidth = strokeWidth * 1.2f, cap = StrokeCap.Round)
        }

        Ico.Check -> {
            val check = Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.40f, h * 0.74f)
                lineTo(w * 0.82f, h * 0.26f)
            }
            drawPath(check, tint, style = Stroke(width = strokeWidth * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        Ico.Play -> drawPath(tri(w * .18f, w * .90f), tint)

        Ico.Pause -> {
            drawRoundRect(tint, Offset(w * .18f, h * 0.12f), Size(w * .24f, h * 0.76f), CornerRadius(3.dp.toPx()))
            drawRoundRect(tint, Offset(w * .58f, h * 0.12f), Size(w * .24f, h * 0.76f), CornerRadius(3.dp.toPx()))
        }

        Ico.Next -> {
            drawPath(tri(w * 0.1f, w * .70f), tint)
            drawRoundRect(tint, Offset(w * .76f, h * 0.12f), Size(w * .14f, h * 0.76f), CornerRadius(2.dp.toPx()))
        }

        Ico.Prev -> {
            drawPath(tri(w * 0.30f, w * 0.90f, true), tint)
            drawRoundRect(tint, Offset(w * .10f, h * 0.12f), Size(w * .14f, h * 0.76f), CornerRadius(2.dp.toPx()))
        }

        Ico.Plus -> {
            drawLine(tint, Offset(w * 0.16f, h * 0.5f), Offset(w * 0.84f, h * 0.5f), strokeWidth = strokeWidth * 1.3f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.84f), strokeWidth = strokeWidth * 1.3f, cap = StrokeCap.Round)
        }

        Ico.Minus -> {
            drawLine(tint, Offset(w * 0.16f, h * 0.5f), Offset(w * 0.84f, h * 0.5f), strokeWidth = strokeWidth * 1.3f, cap = StrokeCap.Round)
        }

        Ico.History -> {
            drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.26f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }

        Ico.Globe -> {
            drawCircle(tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
            drawLine(tint, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), strokeWidth)
            drawOval(tint, Offset(w * 0.28f, h * 0.12f), Size(w * 0.44f, h * 0.76f), style = stroke)
        }

        Ico.Language -> {
            drawRoundRect(tint, Offset(w * 0.12f, h * 0.16f), Size(w * 0.76f, h * 0.56f), CornerRadius(4.dp.toPx()), style = stroke)
            // Speech tail
            val tail = Path().apply {
                moveTo(w * 0.28f, h * 0.72f)
                lineTo(w * 0.28f, h * 0.90f)
                lineTo(w * 0.44f, h * 0.72f)
                close()
            }
            drawPath(tail, tint)
            drawLine(tint, Offset(w * 0.26f, h * 0.36f), Offset(w * 0.74f, h * 0.36f), strokeWidth)
            drawLine(tint, Offset(w * 0.26f, h * 0.52f), Offset(w * 0.60f, h * 0.52f), strokeWidth)
        }

        Ico.Category -> {
            // 4 rounded tiles
            val s = w * 0.32f
            drawRoundRect(tint, Offset(w * 0.12f, h * 0.12f), Size(s, s), CornerRadius(2.dp.toPx()))
            drawRoundRect(tint, Offset(w * 0.56f, h * 0.12f), Size(s, s), CornerRadius(2.dp.toPx()))
            drawRoundRect(tint, Offset(w * 0.12f, h * 0.56f), Size(s, s), CornerRadius(2.dp.toPx()))
            drawRoundRect(tint, Offset(w * 0.56f, h * 0.56f), Size(s, s), CornerRadius(2.dp.toPx()))
        }
    }
}
