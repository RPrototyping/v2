package com.example.shichiire.model

import kotlinx.datetime.Instant

/**
 * 約束全体の状態（ACTIVE 以外は結果が出たあと）と、1人分の結果（KEPT / BROKEN / MISMATCH）に使う。
 * MIXED は「2人とも」の約束で、2人の結果が分かれたとき（例：片方だけ破った）。
 */
enum class Status { ACTIVE, KEPT, BROKEN, MISMATCH, MIXED }

/**
 * 担保を出す人。約束の中身で決める。
 * - SELF：約束した本人だけ（例：「明日は遅刻しない」と本人が言う）
 * - PARTNER：相手だけ（例：「内定が出たら○○して」と本人が言い、○○するのは相手）
 * - BOTH：2人とも（例：「○○に遊びに行こう」のように、2人で守る約束）
 */
enum class Holder(val label: String) {
    SELF("自分だけ"),
    PARTNER("相手だけ"),
    BOTH("2人とも");

    val needsSelf: Boolean get() = this != PARTNER
    val needsPartner: Boolean get() = this != SELF
}

/**
 * 1人が申告する答え。担保を出している人ごとに「守ったか」を答える。true=守った / false=破った。
 * aboutSelf は約束した人について、aboutPartner は相手について（その人が担保を出していなければ null）。
 */
data class Answers(val aboutSelf: Boolean? = null, val aboutPartner: Boolean? = null) {
    /** 担保を出している人全員について答えているか */
    fun isComplete(holder: Holder): Boolean =
        (!holder.needsSelf || aboutSelf != null) && (!holder.needsPartner || aboutPartner != null)
}

/** 画面に出す状態（ACTIVE を担保・期限・申告の進み具合で分ける） */
enum class DisplayState(val label: String) {
    WAITING_COLLATERAL("担保待ち"),
    ACTIVE("進行中"),
    DUE("判定待ち"),
    WAITING_PARTNER("あと1人"), // どちらか1人が申告済み
    KEPT("守った"),
    BROKEN("破った"),
    MISMATCH("不一致"),
    MIXED("結果が分かれた"),
}

/**
 * 約束。
 * 担保の一言（selfCollateral / partnerCollateral）は、封をしたら画面に出さない。
 * 結果は担保を出した人ごとに決まる（selfOutcome / partnerOutcome）。
 * その人について2人の答えが「守った」でそろえば燃える、「破った」でそろえば公開、食い違えば永久封印。
 * 燃えた／永久封印の担保は空文字にして中身を残さない。
 *
 * 同期モードでは、相手の答えは結果が出るまで届かない（selfAnswers / partnerAnswers が null のまま）。
 * 申告したかどうかだけは selfReported / partnerReported でわかる。
 */
data class Promise(
    val id: String,
    val title: String,
    val partner: String,
    val due: Instant,
    val selfCollateral: String,
    val status: Status = Status.ACTIVE,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    /** 約束した人の名前（同期モード）。1台モードでは空 */
    val creator: String = "",
    val holder: Holder = Holder.SELF,
    val partnerCollateral: String = "",
    /** それぞれ担保を書いて封をしたか（中身が見えなくても、封をしたかどうかはわかる） */
    val selfSealed: Boolean = holder.needsSelf,
    val partnerSealed: Boolean = false,
    /** 約束した人の答え／相手の答え */
    val selfAnswers: Answers? = null,
    val partnerAnswers: Answers? = null,
    val selfReported: Boolean = selfAnswers != null,
    val partnerReported: Boolean = partnerAnswers != null,
    /** 約束した人の担保の結果／相手の担保の結果（担保を出していなければ null） */
    val selfOutcome: Status? = null,
    val partnerOutcome: Status? = null,
) {
    /** 必要な担保がそろっていて、申告できる状態か */
    val ready: Boolean
        get() = (!holder.needsSelf || selfSealed) && (!holder.needsPartner || partnerSealed)

    fun displayState(now: Instant): DisplayState = when (status) {
        Status.KEPT -> DisplayState.KEPT
        Status.BROKEN -> DisplayState.BROKEN
        Status.MISMATCH -> DisplayState.MISMATCH
        Status.MIXED -> DisplayState.MIXED
        Status.ACTIVE -> when {
            !ready -> DisplayState.WAITING_COLLATERAL
            selfReported || partnerReported -> DisplayState.WAITING_PARTNER
            now >= due -> DisplayState.DUE
            else -> DisplayState.ACTIVE
        }
    }

    /** me から見て、自分が約束した側か。me=null（1台モード）は常に約束した側として扱う */
    fun isCreator(me: String?): Boolean = me == null || me == creator

    /** me から見た相手の名前 */
    fun otherName(me: String?): String = if (isCreator(me)) partner else creator

    /** me が担保を出している（＝守る側の）約束か */
    fun isHolder(me: String?): Boolean = if (isCreator(me)) holder.needsSelf else holder.needsPartner

    /** me の担保の結果（担保を出していなければ null） */
    fun myOutcome(me: String?): Status? = when {
        !isHolder(me) -> null
        isCreator(me) -> selfOutcome
        else -> partnerOutcome
    }
}

/** 2人の答えから、担保を出した人ごとの結果と、約束全体の状態を決める */
data class Resolution(val selfOutcome: Status?, val partnerOutcome: Status?, val overall: Status)

fun resolve(holder: Holder, self: Answers, partner: Answers): Resolution {
    fun one(a: Boolean?, b: Boolean?): Status = when {
        a != b -> Status.MISMATCH
        a == true -> Status.KEPT
        else -> Status.BROKEN
    }
    val s = if (holder.needsSelf) one(self.aboutSelf, partner.aboutSelf) else null
    val p = if (holder.needsPartner) one(self.aboutPartner, partner.aboutPartner) else null
    val overall = listOfNotNull(s, p).distinct().singleOrNull() ?: Status.MIXED
    return Resolution(s, p, overall)
}

data class PartnerTrust(val name: String, val kept: Int, val judged: Int, val mismatch: Int)

/**
 * 信頼残高 ＝ 守った数 ÷ 判定済みの数。
 * 自分が担保を出した約束の「自分の結果」だけを数える（2人ともの約束で相手だけ破っても、自分は下がらない）。
 * 不一致は「守った」「破った」のどちらにも数えず、別に数える。
 */
data class TrustSummary(val kept: Int, val judged: Int, val mismatch: Int, val byPartner: List<PartnerTrust>) {
    val ratePercent: Int?
        get() = if (judged == 0) null else (kept * 100 + judged / 2) / judged
}

fun trustSummary(promises: List<Promise>, me: String? = null): TrustSummary {
    val mine = promises.mapNotNull { p -> p.myOutcome(me)?.let { p to it } }
    fun List<Pair<Promise, Status>>.judgedCount() = count { it.second == Status.KEPT || it.second == Status.BROKEN }
    fun List<Pair<Promise, Status>>.keptCount() = count { it.second == Status.KEPT }
    fun List<Pair<Promise, Status>>.mismatchCount() = count { it.second == Status.MISMATCH }
    val byPartner = mine.groupBy { it.first.otherName(me) }.map { (name, list) ->
        PartnerTrust(name, list.keptCount(), list.judgedCount(), list.mismatchCount())
    }
    return TrustSummary(mine.keptCount(), mine.judgedCount(), mine.mismatchCount(), byPartner)
}
