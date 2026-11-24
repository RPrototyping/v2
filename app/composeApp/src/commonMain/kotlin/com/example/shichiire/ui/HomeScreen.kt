package com.example.shichiire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shichiire.model.DisplayState
import com.example.shichiire.model.Promise
import com.example.shichiire.model.PromiseStore
import com.example.shichiire.model.trustSummary
import com.example.shichiire.time.formatShort
import kotlinx.datetime.Instant

@Composable
fun HomeScreen(
    store: PromiseStore,
    now: Instant,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val c = LocalAppColors.current
    var confirmReset by remember { mutableStateOf(false) }

    val order = mapOf(
        DisplayState.WAITING_PARTNER to 0, DisplayState.DUE to 0, DisplayState.WAITING_COLLATERAL to 1, DisplayState.ACTIVE to 1,
        DisplayState.BROKEN to 2, DisplayState.MIXED to 2, DisplayState.MISMATCH to 3, DisplayState.KEPT to 3,
    )
    val sorted = store.promises.sortedWith(compareBy<Promise>({ order.getValue(it.displayState(now)) }, { it.due }))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(APP_NAME, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("約束の担保", fontSize = 16.sp, color = c.sub, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                }
                Text(APP_TAGLINE, color = c.sub, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        item { ModeBar(store, onSwitchMode) }
        item { TrustCard(store) }
        item {
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("＋ 新しい約束", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
            }
        }
        items(sorted, key = { it.id }) { p -> PromiseCard(p, store.me, now, onClick = { onOpen(p.id) }) }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (store.me == null) {
                        "プロトタイプ：1台を2人で手渡しして使います。\n最初の5件はデモ用のサンプルです。\n記録はアプリを終了すると消えます。"
                    } else {
                        "プロトタイプ：約束はPCの中継サーバーにだけ置かれます。\nサーバーを止めると消えます。"
                    },
                    fontSize = 13.sp, color = c.sub, lineHeight = 19.sp,
                )
                TextButton(onClick = { confirmReset = true }) {
                    Text(if (store.me == null) "サンプルに戻す" else "サーバーの約束を全部消す", color = c.sub)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(if (store.me == null) "サンプルに戻しますか？" else "サーバーの約束を全部消しますか？") },
            text = { Text(if (store.me == null) "いまの約束はすべて消えます。" else "相手の端末からも、すべての約束が消えます。") },
            confirmButton = {
                TextButton(onClick = { store.reset(); confirmReset = false }) { Text(if (store.me == null) "戻す" else "消す") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("やめる") } },
        )
    }
}

@Composable
private fun TrustCard(store: PromiseStore) {
    val c = LocalAppColors.current
    val t = trustSummary(store.promises, store.me)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.ink).padding(16.dp),
    ) {
        // 1台モードでは端末を渡し合うので「あなた」ではなく「約束した人」と書く
        Text("${store.me?.let { "${it}さん" } ?: "約束した人"}の信頼残高", color = c.bg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("自分が担保を出した約束のうち、守れた割合", color = c.bg.copy(alpha = 0.7f), fontSize = 12.sp)
        val rate = t.ratePercent
        if (rate != null) {
            Text("$rate%", color = c.bg, fontSize = 44.sp, fontWeight = FontWeight.Black)
        } else {
            Text("まだ記録なし", color = c.bg, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (t.judged > 0 || t.mismatch > 0) {
            Text(
                "${t.judged}回中 ${t.kept}回 守った" + if (t.mismatch > 0) "（不一致 ${t.mismatch}回）" else "",
                color = c.bg.copy(alpha = 0.75f), fontSize = 13.sp,
            )
        }
        if (t.byPartner.isNotEmpty()) {
            Spacer(Modifier.padding(top = 6.dp))
            t.byPartner.forEach { pt ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pt.name, color = c.bg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(72.dp))
                    // 不一致しかない相手は判定済みが0件なので、バーは空にする
                    val fraction = if (pt.judged == 0) 0f else pt.kept.toFloat() / pt.judged
                    RateBar(fraction, track = Color.White.copy(alpha = 0.2f), fill = c.shu, modifier = Modifier.weight(1f))
                    Text(
                        "${pt.kept}/${pt.judged}" + if (pt.mismatch > 0) "・不一致${pt.mismatch}" else "",
                        color = c.bg, fontSize = 13.sp, textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 44.dp).padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromiseCard(p: Promise, me: String?, now: Instant, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.paper)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(p.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateTag(p.displayState(now))
            val who = if (p.isCreator(me)) "${p.partner}さんと" else "${p.creator}さんから"
            Text(
                "$who・${formatShort(p.due)}",
                fontSize = 13.sp, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
        }
    }
}

/** いまの使い方（1台／同期）と、切り替えボタン */
@Composable
private fun ModeBar(store: PromiseStore, onSwitchMode: () -> Unit) {
    val c = LocalAppColors.current
    val problem = store.problem
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (store.me == null) "1台モード（オフライン）" else "${store.me}さんとして同期中",
                fontSize = 13.sp, color = if (problem == null) c.sub else c.shu,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSwitchMode) { Text("切り替える", color = c.sub, fontSize = 13.sp) }
        }
        if (problem != null) ProblemBanner(problem)
    }
}
