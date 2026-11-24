package com.example.shichiire.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.shichiire.time.atLocal
import com.example.shichiire.time.currentInstant
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** 約束の置き場所。1台モード（LocalPromiseStore）と同期モード（RemotePromiseStore）がある */
interface PromiseStore {
    val promises: List<Promise>

    /** 同期モードでの自分の名前。1台モードでは null */
    val me: String?

    /** 同期がうまくいっていないときの説明。問題なければ null */
    val problem: String?

    fun find(id: String): Promise? = promises.firstOrNull { it.id == id }

    /** 約束を作る（自分が約束した側になる）。collateral は自分が担保を出さないときは空 */
    fun add(title: String, partner: String, due: Instant, holder: Holder, collateral: String): Promise

    /** 相手が担保を書いて封をする（holder が PARTNER / BOTH のとき） */
    fun sealPartnerCollateral(id: String, collateral: String)

    /** 封の送信中か（同期モードだけ。送れたと確かめるまで申告に進ませない） */
    fun isSealing(id: String): Boolean = false

    /** 申告の送信中か（同期モードだけ。受け付けられたと確かめるまで、申告前の画面のままにする） */
    fun isReporting(id: String): Boolean = false

    /** 1台モードで、相手が封をしたあと約束した人に端末を返すのを待っているか */
    fun awaitingHandBack(id: String): Boolean = false

    /** 1台モード：約束した人が端末を受け取った */
    fun confirmHandBack(id: String) {}

    /** 約束した本人の申告（担保を出した人ごとの答え）。一度決めたら変えられない */
    fun reportSelf(id: String, answers: Answers)

    /** 相手の申告（担保を出した人ごとの答え）。一度決めたら変えられない */
    fun reportPartner(id: String, answers: Answers)

    /** 1台モード：サンプルに戻す / 同期モード：サーバーの約束を全部消す */
    fun reset()
}

internal fun newPromiseId(): String = "p" + Random.nextLong(1L shl 40).toString(36)

/**
 * 1台モード。メモリ上だけで持つ（アプリを終了すると最初のサンプルに戻る）。
 * 1台を2人で手渡しして使うので、本人 → 相手 の順に申告する。
 */
class LocalPromiseStore : PromiseStore {
    override val promises: SnapshotStateList<Promise> = mutableStateListOf()
    override val me: String? = null
    override val problem: String? = null

    // 画面を開き直しても返却の確認を飛ばせないよう、画面ではなくここで覚えておく
    private val handBack = mutableStateSetOf<String>()

    override fun awaitingHandBack(id: String): Boolean = id in handBack

    override fun confirmHandBack(id: String) {
        handBack -= id
    }

    init {
        reset()
    }

    override fun add(title: String, partner: String, due: Instant, holder: Holder, collateral: String): Promise {
        val p = Promise(
            id = newPromiseId(),
            title = title,
            partner = partner,
            due = due,
            selfCollateral = if (holder.needsSelf) collateral else "",
            createdAt = currentInstant(),
            holder = holder,
        )
        promises.add(p)
        return p
    }

    override fun sealPartnerCollateral(id: String, collateral: String) {
        val i = promises.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = promises[i]
        if (p.status != Status.ACTIVE || !p.holder.needsPartner || p.partnerSealed) return
        promises[i] = p.copy(partnerCollateral = collateral, partnerSealed = true)
        handBack += id
    }

    override fun reportSelf(id: String, answers: Answers) {
        val i = promises.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = promises[i]
        if (p.status != Status.ACTIVE || !p.ready || p.selfReported || !answers.isComplete(p.holder)) return
        promises[i] = p.copy(selfAnswers = answers.onlyHolders(p.holder), selfReported = true)
    }

    /** 本人の申告のあとだけ受け付ける。2人の申告がそろった時点で、担保を出した人ごとに結果が決まる */
    override fun reportPartner(id: String, answers: Answers) {
        val i = promises.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = promises[i]
        val self = p.selfAnswers ?: return
        if (p.status != Status.ACTIVE || p.partnerReported || !answers.isComplete(p.holder)) return
        val mine = answers.onlyHolders(p.holder)
        val r = resolve(p.holder, self, mine)
        promises[i] = p.copy(
            partnerAnswers = mine,
            partnerReported = true,
            status = r.overall,
            selfOutcome = r.selfOutcome,
            partnerOutcome = r.partnerOutcome,
            // その人が破ったときだけ中身を残す。燃えた／永久封印の担保は誰にも読めないよう消す
            selfCollateral = if (r.selfOutcome == Status.BROKEN) p.selfCollateral else "",
            partnerCollateral = if (r.partnerOutcome == Status.BROKEN) p.partnerCollateral else "",
            resolvedAt = currentInstant(),
        )
    }

    override fun reset() {
        val now = currentInstant()
        handBack.clear()
        promises.clear()
        promises.addAll(
            listOf(
                Promise("s1", "明日の1限に遅刻しない", "たけし", atLocal(1, 9, base = now),
                    "小6までサンタを本気で信じてた", Status.ACTIVE, now),
                Promise("s2", "貸したマンガを今週中に返す", "そうた", now - 1.hours,
                    "カラオケの十八番がアニメのOP", Status.ACTIVE, now - 7.days),
                Promise("s3", "金曜までにレポートの下書きを送る", "みさき", atLocal(-3, 23, 59, base = now),
                    "", Status.KEPT, now - 10.days, now - 3.days,
                    selfAnswers = Answers(aboutSelf = true), partnerAnswers = Answers(aboutSelf = true), selfOutcome = Status.KEPT),
                Promise("s4", "今週はジムに2回行く", "たけし", atLocal(-6, 21, base = now),
                    "中学のころノートにポエムを書いてた", Status.BROKEN, now - 13.days, now - 6.days,
                    selfAnswers = Answers(aboutSelf = false), partnerAnswers = Answers(aboutSelf = false), selfOutcome = Status.BROKEN),
                Promise("s5", "週末に2人で水族館へ行く", "みさき", atLocal(4, 10, base = now),
                    "家では赤ちゃん言葉で猫に話しかけてる", Status.ACTIVE, now,
                    holder = Holder.BOTH, partnerCollateral = "目覚ましを7個かけても起きられない", partnerSealed = true),
            )
        )
    }
}

/** 担保を出していない人についての答えは捨てる（サーバーと同じ扱い） */
private fun Answers.onlyHolders(holder: Holder) =
    Answers(if (holder.needsSelf) aboutSelf else null, if (holder.needsPartner) aboutPartner else null)
