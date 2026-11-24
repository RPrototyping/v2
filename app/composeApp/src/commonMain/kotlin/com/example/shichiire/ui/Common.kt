package com.example.shichiire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shichiire.model.DisplayState

/** 状態のタグ（担保待ち／進行中／判定待ち／あと1人／守った／破った／不一致／結果が分かれた） */
@Composable
fun StateTag(state: DisplayState, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val (bg, fg) = when (state) {
        DisplayState.ACTIVE, DisplayState.WAITING_COLLATERAL -> c.paper to c.ink
        DisplayState.DUE, DisplayState.WAITING_PARTNER -> c.ink to c.bg
        DisplayState.KEPT -> c.okSoft to c.ok
        DisplayState.BROKEN -> c.shuSoft to c.shu
        DisplayState.MISMATCH -> c.line to c.sub
        DisplayState.MIXED -> c.shuSoft to c.ink
    }
    Text(
        state.label,
        fontSize = 13.sp,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, if (state == DisplayState.ACTIVE || state == DisplayState.WAITING_COLLATERAL) c.line else Color.Transparent, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** 同期の問題を知らせる帯 */
@Composable
fun ProblemBanner(text: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Text(
        text, color = c.shu, fontSize = 13.sp,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.shuSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 大きいボタン（判定・封をする） */
@Composable
fun BigButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, contentColor: Color = Color.White) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** 割合のバー（0〜1） */
@Composable
fun RateBar(fraction: Float, track: Color, fill: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(fill))
    }
}
