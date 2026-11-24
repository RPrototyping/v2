package com.example.shichiire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shichiire.model.Answers
import com.example.shichiire.model.DisplayState
import com.example.shichiire.model.Holder
import com.example.shichiire.model.Promise
import com.example.shichiire.model.PromiseStore
import com.example.shichiire.model.Status
import com.example.shichiire.time.formatShort
import com.example.shichiire.time.remainingText
import kotlinx.datetime.Instant

/** 申告の確認ダイアログに出す内容。byPartner=true なら相手の申告 */
private data class PendingReport(val byPartner: Boolean, val answers: Answers)

private fun word(kept: Boolean) = if (kept) "守った" else "破った"

/**
 * 見ている人から見た呼び名。
 * 同期モードでは自分を「あなた」、相手を名前で呼ぶ。
 * 1台モードでは端末を渡し合うので「あなた」は使わず、「約束した人」と相手の名前で呼ぶ。
 */
private class Names(p: Promise, me: String?) {
    val creator = when {
        me == null -> "約束した人"
        me == p.creator -> "あなた"
        else -> "${p.creator}さん"
    }
    val partner = if (me != null && me == p.partner) "あなた" else "${p.partner}さん"

    /** 担保を出している人（例：「あなた」「たけしさん」「2人」） */
    val holders = when (p.holder) {
        Holder.SELF -> creator
        Holder.PARTNER -> partner
        Holder.BOTH -> "2人"
    }
}

/** 担保を出している1人（約束した人か相手か） */
private data class Subject(val isCreator: Boolean, val name: String)

private fun subjects(p: Promise, names: Names): List<Subject> = buildList {
    if (p.holder.needsSelf) add(Subject(isCreator = true, name = names.creator))
    if (p.holder.needsPartner) add(Subject(isCreator = false, name = names.partner))
}

private fun Answers.about(s: Subject): Boolean? = if (s.isCreator) aboutSelf else aboutPartner

private fun Promise.outcomeOf(s: Subject): Status? = if (s.isCreator) selfOutcome else partnerOutcome

/** 例：1人分なら「守った」、2人分なら「たけしさん：守った・あなた：破った」 */
private fun summary(a: Answers, subjects: List<Subject>): String =
    subjects.singleOrNull()?.let { "「${a.about(it)?.let(::word) ?: "—"}」" }
        ?: subjects.joinToString("・") { s -> "${s.name}：${a.about(s)?.let(::word) ?: "—"}" }

@Composable
fun DetailScreen(store: PromiseStore, id: String, now: Instant, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val p = store.find(id)
    var pending by remember(id) { mutableStateOf<PendingReport?>(null) }
    // 1台モードで端末を相手に渡したか。画面を開き直したら必ず「渡してください」から始める
    var handedOver by remember(id) { mutableStateOf(false) }
    // この画面を開いている間に2人とも「守った」で決まったときだけ燃える演出を流す
    // （同期モードでは相手の申告がサーバーから届いた瞬間に決まるので、状態の変化で判断する）
    var burning by remember(id) { mutableStateOf(false) }
    var lastStatus by remember(id) { mutableStateOf(p?.status) }
    LaunchedEffect(p?.status) {
        if (lastStatus == Status.ACTIVE && p?.status == Status.KEPT) burning = true
        lastStatus = p?.status
    }
    val me = store.me

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 一覧へ", color = c.sub) }
        store.problem?.let { ProblemBanner(it, Modifier.padding(bottom = 8.dp)) }
        if (p == null) {
            Text(
                if (me == null) "この約束は見つかりません。"
                else "この約束は見つかりません。\nサーバーに届かなかった可能性があります。\n一覧に戻って、もう一度作ってください。",
                color = c.sub, lineHeight = 22.sp,
            )
        } else {
            val state = p.displayState(now)
            val isCreator = p.isCreator(me)
            val other = p.otherName(me)
            val names = Names(p, me)
            val subs = subjects(p, names)

            Text(p.title, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StateTag(state)
                Text(
                    (if (isCreator) "${p.partner}さんと" else "${p.creator}さんから") + "・期限 ${formatShort(p.due)}",
                    color = c.sub, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
            }
            Text(
                "担保を出す人：${names.holders}",
                color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
            )

            val broken = subs.filter { p.outcomeOf(it) == Status.BROKEN }
            val mode = when {
                p.status == Status.KEPT && burning -> EnvelopeMode.Burning
                p.status == Status.KEPT -> EnvelopeMode.Ash
                broken.isNotEmpty() -> EnvelopeMode.Open
                else -> EnvelopeMode.Sealed // 担保待ち・進行中・申告中・不一致（永久封印）
            }
            val letter = openedLetter(p, broken)
            Envelope(
                mode = mode,
                letterLabel = if (mode == EnvelopeMode.Open) letter.label else "担保",
                letterText = if (mode == EnvelopeMode.Open) letter.text else "封印中",
                modifier = Modifier.padding(vertical = 8.dp),
                onBurnt = { burning = false },
            )

            Column(Modifier.padding(top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    DisplayState.WAITING_COLLATERAL -> CollateralSection(
                        p = p, me = me, handedOver = handedOver,
                        onHandOver = { handedOver = true },
                        sealing = store.isSealing(p.id),
                        onSeal = { text ->
                            store.sealPartnerCollateral(p.id, text)
                            handedOver = false
                        },
                    )
                    DisplayState.ACTIVE, DisplayState.DUE, DisplayState.WAITING_PARTNER -> {
                        val left = remainingText(p.due, now)
                        Column {
                            Text(if (left != null) "期限まで" else "期限を過ぎました", color = c.sub, fontSize = 14.sp)
                            Text(if (left != null) "あと $left" else "判定待ち", fontSize = 32.sp, fontWeight = FontWeight.Black)
                        }
                        Text("担保は封印されています。\n書いた本人も、まだ誰も読めません。", lineHeight = 24.sp)
                        // 1台モードで相手が封をした直後は、約束した人に端末を返すまで申告を出さない
                        if (me == null && store.awaitingHandBack(p.id)) {
                            InfoCard(
                                title = "約束した人に端末を返してください",
                                body = "${p.partner}さんの担保に封をしました。\nここからは、約束した人が操作します。",
                            )
                            BigButton(
                                "約束した人が受け取った", c.ink, contentColor = c.bg,
                                onClick = { store.confirmHandBack(p.id) }, modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (me == null) {
                            LocalReportSection(
                                p = p, subs = subs,
                                handedOver = handedOver,
                                onHandOver = { handedOver = true },
                                onReport = { pending = it },
                            )
                        } else {
                            SyncReportSection(
                                p = p, subs = subs, isCreator = isCreator, other = other,
                                sending = store.isReporting(p.id), onReport = { pending = it },
                            )
                        }
                    }
                    DisplayState.KEPT, DisplayState.BROKEN, DisplayState.MISMATCH, DisplayState.MIXED ->
                        ResultSection(p = p, me = me, subs = subs, other = other)
                }
            }
        }
    }

    val r = pending
    if (p != null && r != null) {
        ReportDialog(
            p = p, me = me, subs = subjects(p, Names(p, me)), report = r,
            onConfirm = {
                if (r.byPartner) store.reportPartner(p.id, r.answers) else {
                    store.reportSelf(p.id, r.answers)
                    handedOver = false
                }
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

private data class OpenedLetter(val label: String, val text: String)

/**
 * 破った人の担保が封筒から出てくる手紙。
 * 手紙の大きさは決まっているので、2人分のときは見出しだけにして、全文は下の結果に出す
 */
private fun openedLetter(p: Promise, broken: List<Subject>): OpenedLetter = when (broken.size) {
    0 -> OpenedLetter("担保", "")
    1 -> {
        val s = broken.single()
        OpenedLetter("${s.name}の担保 ― 公開されました", if (s.isCreator) p.selfCollateral else p.partnerCollateral)
    }
    else -> OpenedLetter("2人の担保 ― 公開されました", "2人分の担保が\n開きました。\n↓ 下に全文")
}

/** 結果。約束全体の見出しと、担保を出した人ごとの結果 */
@Composable
private fun ResultSection(p: Promise, me: String?, subs: List<Subject>, other: String) {
    val c = LocalAppColors.current
    val (title, color) = when (p.status) {
        Status.KEPT -> "約束は守られました" to c.ok
        Status.BROKEN -> "約束は破られました" to c.shu
        Status.MISMATCH -> "申告が一致しませんでした" to c.sub
        else -> "結果が分かれました" to c.ink
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
        subs.forEach { s -> PersonResult(p, s) }
    }
    if (p.myOutcome(me) == Status.BROKEN) Text("次の約束で、信頼を取り戻しましょう。", color = c.sub, fontSize = 14.sp)
    if (subs.any { p.outcomeOf(it) == Status.MISMATCH }) {
        Text(
            if (me == null) "答えが食い違いました。\nこの2人では、今後約束しない方がいいかもしれません。"
            else "答えが食い違いました。\n${other}さんとは、今後約束しない方がいいかもしれません。",
            color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
    }
}

/** 1人分の結果（守った → 燃えた／破った → 公開／食い違い → 永久封印） */
@Composable
private fun PersonResult(p: Promise, s: Subject) {
    val c = LocalAppColors.current
    val collateral = if (s.isCreator) p.selfCollateral else p.partnerCollateral
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${s.name}の担保", color = c.sub, fontSize = 13.sp)
        when (p.outcomeOf(s)) {
            Status.KEPT -> Text("守った → 燃えて消えました", color = c.ok, fontWeight = FontWeight.Bold)
            Status.BROKEN -> {
                Text("破った → 公開されました", color = c.shu, fontWeight = FontWeight.Bold)
                Text(
                    collateral,
                    color = Color(0xFF1D1B1A), fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(c.letter).border(1.dp, c.line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Status.MISMATCH -> {
                Text("答えが食い違った → 永久に封印", color = c.sub, fontWeight = FontWeight.Bold)
                val a = p.selfAnswers?.about(s)
                val b = p.partnerAnswers?.about(s)
                Text(
                    "約束した人：${a?.let(::word) ?: "—"}／${p.partner}さん：${b?.let(::word) ?: "—"}",
                    color = c.sub, fontSize = 13.sp,
                )
            }
            else -> Text("—", color = c.sub)
        }
    }
}

/** 申告の確認。取り消せないことと、人ごとの結果のパターンを短い行に分けて見せる */
@Composable
private fun ReportDialog(
    p: Promise,
    me: String?,
    subs: List<Subject>,
    report: PendingReport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val other = p.otherName(me)
    val single = subs.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (single != null) "「${report.answers.about(single)?.let(::word)}」で申告" else "この内容で申告",
                fontSize = 22.sp, fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("申告は取り消せません。", fontWeight = FontWeight.Bold, color = c.ink, fontSize = 16.sp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    subs.forEach { s ->
                        val kept = report.answers.about(s) == true
                        DialogCase(
                            condition = "${s.name}：${word(kept)}",
                            result = "一致すれば、担保は" + if (kept) "燃える" else "公開",
                            color = if (kept) c.ok else c.shu,
                        )
                    }
                }
                Text("答えが食い違った人の担保は、\n永久に封印されます。", color = c.ink, fontSize = 14.sp, lineHeight = 20.sp)
                Text(
                    when {
                        me != null -> "あなたの答えは、結果が出るまで\n${other}さんには見えません。"
                        !report.byPartner -> "このあと${p.partner}さんに端末を渡します。\n答えは結果が出るまで見せません。"
                        else -> "このあと結果が出ます。"
                    },
                    color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("申告する", color = c.ink, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる", color = c.sub) } },
    )
}

/** 確認ダイアログの「〜：守った → 〜」の1行 */
@Composable
private fun DialogCase(condition: String, result: String, color: Color) {
    val c = LocalAppColors.current
    Column {
        Text(condition, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("→ $result", color = color, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * 申告の入力。担保を出した人ごとに「守った／破った」を選ぶ。
 * 1人分だけなら、ボタンを押すとすぐ確認に進む。2人分なら、両方選んでから「申告する」。
 */
@Composable
private fun AnswerForm(p: Promise, subs: List<Subject>, onSubmit: (Answers) -> Unit) {
    val c = LocalAppColors.current
    val single = subs.singleOrNull()
    if (single != null) {
        Text("${single.name}は約束を守った？", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("守った", c.ok, onClick = { onSubmit(single.answer(true)) }, modifier = Modifier.weight(1f))
            BigButton("破った", c.shu, onClick = { onSubmit(single.answer(false)) }, modifier = Modifier.weight(1f))
        }
        return
    }
    var aboutSelf by remember(p.id) { mutableStateOf<Boolean?>(null) }
    var aboutPartner by remember(p.id) { mutableStateOf<Boolean?>(null) }
    Text("それぞれ、約束を守った？", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    subs.forEach { s ->
        val value = if (s.isCreator) aboutSelf else aboutPartner
        val set: (Boolean) -> Unit = { if (s.isCreator) aboutSelf = it else aboutPartner = it }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${s.name}は？", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceButton("守った", selected = value == true, color = c.ok, onClick = { set(true) }, modifier = Modifier.weight(1f))
                ChoiceButton("破った", selected = value == false, color = c.shu, onClick = { set(false) }, modifier = Modifier.weight(1f))
            }
        }
    }
    val ready = aboutSelf != null && aboutPartner != null
    BigButton(
        if (ready) "申告する" else "2人分えらんでください",
        if (ready) c.ink else c.line,
        contentColor = if (ready) c.bg else c.sub,
        onClick = { if (ready) onSubmit(Answers(aboutSelf, aboutPartner)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Subject.answer(kept: Boolean) = if (isCreator) Answers(aboutSelf = kept) else Answers(aboutPartner = kept)

/** 選ぶボタン（選んだら塗りつぶし） */
@Composable
private fun ChoiceButton(text: String, selected: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        BigButton(text, color, onClick = onClick, modifier = modifier)
    } else {
        OutlinedButton(
            onClick = onClick, shape = RoundedCornerShape(12.dp),
            modifier = modifier.heightIn(min = 56.dp),
        ) { Text(text, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

/** 相手の担保がまだのとき。相手が担保を書いて封をする */
@Composable
private fun CollateralSection(
    p: Promise,
    me: String?,
    handedOver: Boolean,
    onHandOver: () -> Unit,
    sealing: Boolean,
    onSeal: (String) -> Unit,
) {
    val c = LocalAppColors.current
    when {
        // 同期モードで約束した側：相手を待つ
        me != null && p.isCreator(me) -> InfoCard(
            title = "${p.partner}さんの担保を待っています",
            body = "${p.partner}さんが担保を書いて封をしたら、\n約束が始まります。",
        )
        // 1台モード：まず端末を渡す
        me == null && !handedOver -> {
            InfoCard(
                title = "${p.partner}さんに端末を渡してください",
                body = "この約束は${p.partner}さん" + (if (p.holder == Holder.BOTH) "も" else "が") +
                    "担保を出します。\n${p.partner}さんに担保を書いてもらいましょう。",
            )
            BigButton("${p.partner}さんとして担保を書く", c.ink, contentColor = c.bg, onClick = onHandOver, modifier = Modifier.fillMaxWidth())
        }
        else -> CollateralForm(
            title = if (me == null) "${p.partner}さんの担保" else "あなたの担保を書く",
            sealing = sealing,
            onSeal = onSeal,
        )
    }
}

@Composable
private fun CollateralForm(title: String, sealing: Boolean, onSeal: (String) -> Unit) {
    val c = LocalAppColors.current
    var text by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text(
        "「破ったら相手に見られてもいいけど、\nできれば見られたくない一言」にしましょう。",
        color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
    )
    OutlinedTextField(
        value = text, onValueChange = { if (it.length <= 40) text = it },
        label = { Text("担保の一言（40字まで）") }, placeholder = { Text("例：小6までサンタを本気で信じてた") },
        modifier = Modifier.fillMaxWidth(),
    )
    AgreeRow(agreed = agreed, onToggle = { agreed = !agreed })
    error?.let { Text(it, color = c.shu, fontSize = 14.sp) }
    BigButton(
        if (sealing) "送っています…" else "封をする", c.ink, contentColor = c.bg,
        onClick = {
            if (sealing) return@BigButton
            val t = text.trim()
            error = when {
                t.isEmpty() -> "担保の一言を書いてください。"
                !agreed -> "公開への同意にチェックしてください。"
                else -> null
            }
            if (error == null) onSeal(t)
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("封をすると、担保は自分にも表示されなくなります。", color = c.sub, fontSize = 13.sp)
}

/** 公開への同意のチェック */
@Composable
fun AgreeRow(agreed: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.Top) {
        Checkbox(checked = agreed, onCheckedChange = { onToggle() })
        Text(
            "約束を破ったら、この一言が相手に公開されることに同意します。\n他人の秘密や悪口は書きません。",
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** 同期モードの申告。自分の申告だけを出し、相手の答えは結果が出るまで見せない */
@Composable
private fun SyncReportSection(
    p: Promise,
    subs: List<Subject>,
    isCreator: Boolean,
    other: String,
    sending: Boolean,
    onReport: (PendingReport) -> Unit,
) {
    val c = LocalAppColors.current
    val myAnswers = if (isCreator) p.selfAnswers else p.partnerAnswers
    val reported = if (isCreator) p.selfReported else p.partnerReported
    val otherReported = if (isCreator) p.partnerReported else p.selfReported
    if (!reported) {
        Text(
            "${other}さんも、自分の端末で申告します。\n2人の答えがそろうと結果が出ます。" +
                if (otherReported) "\n（${other}さんはもう申告しました）" else "",
            color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        // 送信中もフォームは残す（失敗したとき、選んだ答えのままやり直せる）
        if (sending) InfoCard(title = "申告を送っています…", body = "サーバーが受け付けたら、次に進みます。")
        AnswerForm(p, subs, onSubmit = { a -> if (!sending) onReport(PendingReport(byPartner = !isCreator, answers = a)) })
    } else {
        InfoCard(
            title = "${other}さんの申告を待っています",
            body = "あなたの申告：" + (myAnswers?.let { summary(it, subs) } ?: "申告済み") +
                "\n結果が出るまで、${other}さんには見えません。",
        )
    }
}

/**
 * 1台モードの申告。1台を2人で手渡しして使う。
 * 本人が申告 → 「相手に渡してください」→ 相手が申告、の順。相手には本人の答えを見せない。
 */
@Composable
private fun LocalReportSection(
    p: Promise,
    subs: List<Subject>,
    handedOver: Boolean,
    onHandOver: () -> Unit,
    onReport: (PendingReport) -> Unit,
) {
    val c = LocalAppColors.current
    when {
        !p.selfReported -> {
            Text("約束した人の申告", fontSize = 15.sp, color = c.sub, fontWeight = FontWeight.Bold)
            Text(
                "まず約束した人が、次に${p.partner}さんが申告します。\n2人の答えがそろうと結果が出ます。",
                color = c.sub, fontSize = 14.sp, lineHeight = 20.sp,
            )
            AnswerForm(p, subs, onSubmit = { a -> onReport(PendingReport(byPartner = false, answers = a)) })
        }
        !handedOver -> {
            InfoCard(
                title = "${p.partner}さんに端末を渡してください",
                body = "約束した人の申告は終わりました。\n答えは結果が出るまで${p.partner}さんには見せません。",
            )
            BigButton("${p.partner}さんとして申告する", c.ink, contentColor = c.bg, onClick = onHandOver, modifier = Modifier.fillMaxWidth())
        }
        else -> {
            Text("${p.partner}さんの申告", fontSize = 15.sp, color = c.sub, fontWeight = FontWeight.Bold)
            AnswerForm(p, subs, onSubmit = { a -> onReport(PendingReport(byPartner = true, answers = a)) })
        }
    }
}

/** 囲み付きのお知らせ */
@Composable
private fun InfoCard(title: String, body: String) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.paper)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black)
        Text(body, color = c.sub, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
