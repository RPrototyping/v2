package com.example.shichiire.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.shichiire.net.CollateralRequest
import com.example.shichiire.net.CreateRequest
import com.example.shichiire.net.PromiseDto
import com.example.shichiire.net.ReportRequest
import com.example.shichiire.net.createHttpClient
import com.example.shichiire.time.currentInstant
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

/**
 * 同期モード。同じWi-Fiにある中継サーバーと、1秒ごとにやりとりする。
 *
 * - 操作（作成・申告）は先に画面へ反映し、裏でサーバーへ送る
 * - 送信と取得は Mutex で1本ずつ流す
 * - 送信待ちの操作があるとき、または取得の途中で自分の操作が入ったときは、その取得結果は古いので捨てる
 *   （画面が一瞬もとに戻るのを防ぐ）。送信が終わったら取り直す
 * - scope は画面（メインスレッド）のものを使う前提。状態の読み書きはメインスレッドだけで行う
 * - 取得が一時的に遅れるだけで警告を出さないよう、3回続けて失敗したときだけ「つながりません」を出す
 */
class RemotePromiseStore(
    private val baseUrl: String,
    override val me: String,
    private val scope: CoroutineScope,
) : PromiseStore {
    override val promises: SnapshotStateList<Promise> = mutableStateListOf()
    // つながらない（取得の失敗）と、送れなかった（送信の失敗）を分けて持つ。
    // 送信の失敗は次の取得が成功しても消えないよう、数秒だけ出し続ける
    private var connectionProblem: String? by mutableStateOf(null)
    private var sendProblem: String? by mutableStateOf(null)
    override val problem: String? get() = sendProblem ?: connectionProblem

    private val client = createHttpClient()
    private val mutex = Mutex()
    private var localVersion = 0
    private var pendingSends = 0
    private var consecutiveFailures = 0
    // 封の送信中の約束。サーバーで封が確かめられるまで「担保待ち」のまま、入力を残しておく
    private val sealing = mutableStateSetOf<String>()

    override fun isSealing(id: String): Boolean = id in sealing

    // 申告の送信中の約束。サーバーで受け付けられた取得結果が反映されるまで、画面は申告前のまま（選んだ答えも残る）
    private val reporting = mutableStateSetOf<String>()

    override fun isReporting(id: String): Boolean = id in reporting

    /** 1秒ごとにサーバーから取り直す。画面を離れるときに止める */
    fun startPolling(): Job = scope.launch {
        while (isActive) {
            refresh()
            delay(1_000)
        }
    }

    fun close() = client.close()

    private suspend fun refresh() {
        val version = localVersion
        try {
            val list: List<PromiseDto> = mutex.withLock {
                client.get("$baseUrl/promises") { parameter("me", me) }.body()
            }
            // 取得できた時点で「つながっている」。画面に反映するかとは別に、警告を解く
            consecutiveFailures = 0
            connectionProblem = null
            if (version != localVersion || pendingSends > 0) return
            replaceAll(list.map { it.toPromise() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Shichiire: refresh failed: $e") // 原因を追えるようにログへ出す（Android は logcat の System.out）
            consecutiveFailures++
            if (consecutiveFailures >= FAILURES_BEFORE_WARNING) {
                connectionProblem = "サーバーにつながりません。\nPCのサーバーとWi-Fiを確認してください。"
            }
        }
    }

    /** 中身が変わったものだけ差し替える（一覧のちらつきを防ぐ） */
    private fun replaceAll(next: List<Promise>) {
        // 封の送信中の約束は、サーバーで封が確かめられた（または進行中でなくなった）結果が反映されたときに外す
        sealing.toList().forEach { id ->
            val p = next.firstOrNull { it.id == id }
            if (p == null || p.partnerSealed || p.status != Status.ACTIVE) sealing -= id
        }
        reporting.toList().forEach { id ->
            val p = next.firstOrNull { it.id == id }
            val mine = p != null && (if (p.isCreator(me)) p.selfReported else p.partnerReported)
            if (p == null || mine || p.status != Status.ACTIVE) reporting -= id
        }
        if (next == promises.toList()) return
        promises.clear()
        promises.addAll(next)
    }

    /** onFinished(ok)：送信が成功したか。キャンセルされたときも false で必ず呼ぶ */
    private fun send(onFinished: (ok: Boolean) -> Unit = {}, request: suspend () -> HttpResponse) {
        localVersion++
        pendingSends++
        // UNDISPATCHED：開始前にキャンセルされても finally で pendingSends を必ず戻すため、すぐ try に入る
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var ok = false
            try {
                val r = try {
                    mutex.withLock { request() }
                } finally {
                    pendingSends--
                }
                ok = r.status.isSuccess()
                if (!ok) showSendProblem("送信できませんでした。\n${r.bodyAsText()}")
            } catch (e: CancellationException) {
                onFinished(false)
                throw e
            } catch (e: Exception) {
                println("Shichiire: send failed: $e")
                showSendProblem("送信できませんでした。\nPCのサーバーとWi-Fiを確認してください。")
            }
            onFinished(ok)
            refresh()
        }
    }

    private fun showSendProblem(message: String) {
        sendProblem = message
        scope.launch {
            delay(5_000)
            if (sendProblem == message) sendProblem = null
        }
    }

    override fun add(title: String, partner: String, due: Instant, holder: Holder, collateral: String): Promise {
        val p = Promise(
            id = newPromiseId(),
            title = title,
            partner = partner,
            due = due,
            selfCollateral = "", // 担保の中身は端末に残さない。サーバーだけが持つ
            createdAt = currentInstant(),
            creator = me,
            holder = holder,
        )
        promises.add(p)
        send {
            client.post("$baseUrl/promises") {
                contentType(ContentType.Application.Json)
                setBody(CreateRequest(p.id, title, me, partner, due.toEpochMilliseconds(), collateral, holder.name))
            }
        }
        return p
    }

    override fun sealPartnerCollateral(id: String, collateral: String) {
        val i = promises.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = promises[i]
        if (p.status != Status.ACTIVE || p.isCreator(me) || !p.holder.needsPartner || p.partnerSealed) return
        if (id in sealing) return
        // 画面は先に進めない。サーバーで封が確かめられた取得結果（partnerSealed）が反映されたら進む（replaceAll で外す）
        // 送信に失敗したときだけ、すぐに外して入力をやり直せるようにする
        sealing += id
        send(onFinished = { ok -> if (!ok) sealing -= id }) {
            client.post("$baseUrl/promises/$id/collateral") {
                contentType(ContentType.Application.Json)
                setBody(CollateralRequest(me, collateral))
            }
        }
    }

    override fun reportSelf(id: String, answers: Answers) = report(id, answers)

    override fun reportPartner(id: String, answers: Answers) = report(id, answers)

    /** 自分が約束した側か相手側かは、サーバーが名前で判断する */
    private fun report(id: String, answers: Answers) {
        val i = promises.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = promises[i]
        if (p.status != Status.ACTIVE || !p.ready || !answers.isComplete(p.holder)) return
        if (if (p.isCreator(me)) p.selfReported else p.partnerReported) return
        if (id in reporting) return
        // 画面は先に進めない。サーバーが受け付けた取得結果が反映されたら進む（replaceAll で外す）
        // 送信に失敗したときだけ、すぐに外して申告をやり直せるようにする
        reporting += id
        send(onFinished = { ok -> if (!ok) reporting -= id }) {
            client.post("$baseUrl/promises/$id/report") {
                contentType(ContentType.Application.Json)
                setBody(ReportRequest(me, answers.aboutSelf, answers.aboutPartner))
            }
        }
    }

    override fun reset() {
        promises.clear()
        send { client.post("$baseUrl/reset") }
    }

    private companion object {
        const val FAILURES_BEFORE_WARNING = 3
    }
}

private fun PromiseDto.toPromise() = Promise(
    id = id,
    title = title,
    partner = partner,
    due = Instant.fromEpochMilliseconds(dueMs),
    selfCollateral = selfCollateral,
    status = status.toStatus() ?: Status.ACTIVE,
    createdAt = Instant.fromEpochMilliseconds(createdAtMs),
    resolvedAt = resolvedAtMs?.let { Instant.fromEpochMilliseconds(it) },
    creator = creator,
    selfReported = selfReported,
    partnerReported = partnerReported,
    selfAnswers = if (creatorSaysCreator != null || creatorSaysPartner != null) Answers(creatorSaysCreator, creatorSaysPartner) else null,
    partnerAnswers = if (partnerSaysCreator != null || partnerSaysPartner != null) Answers(partnerSaysCreator, partnerSaysPartner) else null,
    selfOutcome = creatorOutcome?.toStatus(),
    partnerOutcome = partnerOutcome?.toStatus(),
    holder = runCatching { Holder.valueOf(holder) }.getOrDefault(Holder.SELF),
    partnerCollateral = partnerCollateral,
    selfSealed = selfSealed,
    partnerSealed = partnerSealed,
)

private fun String.toStatus(): Status? = runCatching { Status.valueOf(this) }.getOrNull()
