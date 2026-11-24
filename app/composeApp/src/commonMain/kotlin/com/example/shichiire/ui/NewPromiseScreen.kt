package com.example.shichiire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shichiire.model.Holder
import com.example.shichiire.model.PromiseStore
import com.example.shichiire.time.currentInstant
import com.example.shichiire.time.duePresets
import com.example.shichiire.time.formatShort
import kotlinx.datetime.Instant

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewPromiseScreen(store: PromiseStore, now: Instant, onCreated: (String) -> Unit, onCancel: () -> Unit) {
    val c = LocalAppColors.current
    var title by remember { mutableStateOf("") }
    var partner by remember { mutableStateOf("") }
    var collateral by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var preset by remember { mutableIntStateOf(3) } // 明日 21:00
    var holder by remember { mutableStateOf(Holder.SELF) }
    var error by remember { mutableStateOf<String?>(null) }

    val due = duePresets[preset].resolve(now)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onCancel) { Text("← やめる", color = c.sub) }
        Text("新しい約束", fontSize = 28.sp, fontWeight = FontWeight.Black)

        OutlinedTextField(
            value = title, onValueChange = { if (it.length <= 40) title = it },
            label = { Text("約束すること") }, placeholder = { Text("例：明日の1限に遅刻しない") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = partner, onValueChange = { if (it.length <= 12) partner = it },
            label = { Text("約束する相手") },
            placeholder = { Text(if (store.me == null) "例：たけし" else "相手が端末で入れた名前（例：たけし）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("期限：${formatShort(due)}", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                duePresets.forEachIndexed { i, d ->
                    FilterChip(selected = preset == i, onClick = { preset = i }, label = { Text(d.label) })
                }
            }
        }

        val who = partner.trim().ifBlank { "相手" }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("担保を出す人", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Holder.entries.forEach { h ->
                    FilterChip(selected = holder == h, onClick = { holder = h }, label = { Text(h.label) })
                }
            }
            Text(
                when (holder) {
                    Holder.SELF -> "あなたが守る約束。担保はあなたが書きます。\n例：「明日は遅刻しない」"
                    Holder.PARTNER -> "${who}さんが守る約束。\n担保は${who}さんが書きます。\n例：「内定が出たら焼肉おごって」"
                    Holder.BOTH -> "2人で守る約束。\n担保は2人とも書きます。\n例：「週末に水族館へ行こう」"
                },
                color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }

        if (holder.needsSelf) {
            Text(
                "担保は「破ったら相手に見られてもいいけど、\nできれば見られたくない一言」にしましょう。",
                color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
            )
            OutlinedTextField(
                value = collateral, onValueChange = { if (it.length <= 40) collateral = it },
                label = { Text("あなたの担保（40字まで）") }, placeholder = { Text("例：小6までサンタを本気で信じてた") },
                modifier = Modifier.fillMaxWidth(),
            )

            // 入力中だけ、担保がどう見えるかをプレビューする
            Envelope(
                mode = EnvelopeMode.Open,
                letterLabel = "担保 ― ${who}さんに公開されたら",
                letterText = collateral.ifBlank { "ここに担保の一言が入ります" },
            )

            AgreeRow(agreed = agreed, onToggle = { agreed = !agreed })
        }
        if (holder.needsPartner) {
            Text(
                "${who}さんが担保を書いて封をすると、\n約束が始まります。",
                color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }

        error?.let { Text(it, color = c.shu, fontSize = 14.sp) }

        BigButton(
            when {
                holder.needsSelf -> "封をする"
                store.me == null -> "約束を作る"
                else -> "約束を送る"
            },
            c.ink, contentColor = c.bg,
            onClick = {
                val t = title.trim()
                val pa = partner.trim()
                val col = collateral.trim()
                val d = duePresets[preset].resolve(currentInstant())
                error = when {
                    t.isEmpty() -> "約束することを書いてください。"
                    pa.isEmpty() -> "約束する相手を書いてください。"
                    pa == store.me -> "自分とは約束できません。相手の名前を書いてください。"
                    d <= currentInstant() -> "期限は今より後にしてください。"
                    holder.needsSelf && col.isEmpty() -> "あなたの担保を書いてください。"
                    holder.needsSelf && !agreed -> "公開への同意にチェックしてください。"
                    else -> null
                }
                if (error == null) onCreated(store.add(t, pa, d, holder, if (holder.needsSelf) col else "").id)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (holder.needsSelf) "封をすると、担保は自分にも表示されなくなります。" else if (store.me == null) "このあと${who}さんに端末を渡して、\n担保を書いてもらいます。" else "送ると、${who}さんの端末に届きます。",
            color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 32.dp),
        )
    }
}
