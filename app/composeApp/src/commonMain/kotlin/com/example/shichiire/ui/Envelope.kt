package com.example.shichiire.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class EnvelopeMode { Sealed, Open, Burning, Ash }

/** ふた（三角形） */
private val FlapShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

/** 封筒の前面（ポケット部分）。上辺がV字に切れている */
private val FrontShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width / 2f, size.height * 0.52f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private fun lerp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t

/**
 * 担保を入れた封筒。
 * Sealed：封をした状態 / Open：封が開いて担保が出てくる / Burning：下から燃える / Ash：燃えたあと
 * Sealed → Open に変わると、ふたが開いて手紙がせり上がるアニメーションが入る。
 */
@Composable
fun Envelope(
    mode: EnvelopeMode,
    letterLabel: String,
    letterText: String,
    modifier: Modifier = Modifier,
    letterFontSize: TextUnit = 19.sp,
    onBurnt: () -> Unit = {},
) {
    val c = LocalAppColors.current
    val currentOnBurnt by rememberUpdatedState(onBurnt)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = if (maxWidth < 400.dp) maxWidth else 400.dp
        val h = w * (2f / 3f)
        val headroom = h * 0.55f // 手紙がせり上がる分の余白

        if (mode == EnvelopeMode.Ash) {
            Ash(w, h, headroom, Modifier.align(Alignment.TopCenter))
            return@BoxWithConstraints
        }

        val isOpen = mode == EnvelopeMode.Open
        val flap by animateFloatAsState(
            targetValue = if (isOpen) 1f else 0f,
            animationSpec = tween(500, easing = FastOutSlowInEasing),
            label = "flap",
        )
        val rise by animateFloatAsState(
            targetValue = if (isOpen) 1f else 0f,
            animationSpec = tween(900, delayMillis = if (isOpen) 350 else 0, easing = FastOutSlowInEasing),
            label = "rise",
        )
        val burn = remember { Animatable(0f) }
        LaunchedEffect(mode) {
            if (mode == EnvelopeMode.Burning) {
                burn.snapTo(0f)
                burn.animateTo(1f, tween(1800, easing = LinearEasing))
                currentOnBurnt()
            } else {
                burn.snapTo(0f)
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = headroom)
                .width(w)
                .height(h)
                // 燃える：下から上へ消えていき、境目に炎の帯を描く
                .drawWithContent {
                    val p = burn.value
                    val visible = size.height * (1f - p)
                    if (p == 0f) {
                        // 燃えていないときは切り取らない（開いた手紙やふたは封筒の上にはみ出す）
                        drawContent()
                    } else {
                        clipRect(left = -size.width, top = -size.height, right = size.width * 2f, bottom = visible) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    if (p > 0f && p < 1f) {
                        val band = 56.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color(0xFFFFD25A),
                                0.7f to Color(0xFFFF7A2A),
                                1f to Color.Transparent,
                                startY = visible - band * 0.6f,
                                endY = visible + band * 0.4f,
                            ),
                            topLeft = Offset(-12f, visible - band * 0.6f),
                            size = Size(size.width + 24f, band),
                        )
                    }
                }
        ) {
            val flapModifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(h * 0.56f)
                .graphicsLayer {
                    rotationX = flap * 180f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 16f * density
                }
                .clip(FlapShape)
                .background(c.envFlap)

            // 奥
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(c.envDark))
            // 開いたふた（手紙より奥に描く）
            if (flap >= 0.5f) Box(flapModifier)
            // 手紙
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = lerp(h * 0.06f, -headroom * 0.9f, rise))
                    .width(w * 0.86f)
                    .height(h * 0.88f)
                    .shadow(2.dp, RoundedCornerShape(4.dp))
                    .background(c.letter, RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(letterLabel, fontSize = 12.sp, color = Color(0xFF8A837A))
                    Text(
                        letterText,
                        fontSize = letterFontSize,
                        lineHeight = letterFontSize * 1.4f,
                        fontWeight = FontWeight.Bold,
                        color = if (isOpen) Color(0xFF1D1B1A) else Color(0xFFBBBBBB),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            // 前面
            Box(Modifier.matchParentSize().clip(FrontShape).background(c.env))
            // 閉じたふた
            if (flap < 0.5f) Box(flapModifier)
            // 封蝋
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = h * 0.56f - 34.dp)
                    .size(68.dp)
                    .graphicsLayer {
                        val k = (1f - flap * 2f).coerceIn(0f, 1f)
                        alpha = k
                        scaleX = 0.4f + 0.6f * k
                        scaleY = 0.4f + 0.6f * k
                        rotationZ = (1f - k) * 40f
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0f to Color(0xFFFF7A5C),
                            0.55f to c.shu,
                            1f to Color(0xFF8F2312),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("質", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
        }
    }
}

/** 燃えたあとの灰 */
@Composable
private fun Ash(w: Dp, h: Dp, headroom: Dp, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(modifier.width(w).padding(top = headroom * 0.6f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "担保は燃えました。\n中身はもう誰にも読めません。",
            color = c.sub,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Canvas(Modifier.width(w).height(h * 0.45f)) {
            val sx = size.width / 400f
            val sy = size.height / 120f
            drawOval(c.ink.copy(alpha = 0.08f), topLeft = Offset(30f * sx, 104f * sy), size = Size(340f * sx, 16f * sy))
            val shards = listOf(
                Triple(110f, 96f, 26f), Triple(150f, 100f, 40f), Triple(200f, 92f, 34f), Triple(246f, 99f, 30f),
                Triple(284f, 102f, 20f), Triple(176f, 104f, 22f), Triple(226f, 106f, 18f),
            )
            shards.forEachIndexed { i, (x, y, r) ->
                val path = Path().apply {
                    moveTo((x - r) * sx, (y + 8f) * sy)
                    lineTo((x - r / 3f) * sx, (y - 6f + (i % 3) * 3f) * sy)
                    lineTo((x + r / 2f) * sx, y * sy)
                    lineTo((x + r) * sx, (y + 8f) * sy)
                    close()
                }
                drawPath(path, if (i % 2 == 0) Color(0xFF8A837A) else Color(0xFF6B6660), alpha = 0.8f)
            }
            drawCircle(Color(0xFF8F2312).copy(alpha = 0.55f), radius = 9f * sx, center = Offset(200f * sx, 98f * sy))
        }
    }
}
