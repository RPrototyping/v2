package com.example.shichiire.server

import kotlinx.serialization.Serializable

// ---- 端末とやりとりする形（composeApp の net/Api.kt と同じ形にそろえる） ----

@Serializable
data class CreateRequest(
    val id: String,
    val title: String,
    val creator: String,
    val partner: String,
    val dueMs: Long,
    /** 約束した本人の担保。本人が担保を出さない（holder=PARTNER）ときは空 */
    val collateral: String,
    /** 担保を出す人：SELF / PARTNER / BOTH */
    val holder: String = "SELF",
)

/** 申告。担保を出した人ごとに「守ったか」を答える（その人が担保を出していなければ null） */
@Serializable
data class ReportRequest(val by: String, val aboutCreator: Boolean? = null, val aboutPartner: Boolean? = null)

/** 相手が担保を書いて封をする */
@Serializable
data class CollateralRequest(val by: String, val collateral: String)

/**
 * 端末に返す約束。見る人（me）ごとに中身を変える。
 * - selfCollateral / partnerCollateral：その人が「破った」で一致したときだけ入れる。それ以外は空文字
 * - 〜Says〜：自分の答えか、結果が出たあとだけ入れる（相手の答えを先に見せない）
 */
@Serializable
data class PromiseDto(
    val id: String,
    val title: String,
    val creator: String,
    val partner: String,
    val dueMs: Long,
    val createdAtMs: Long,
    val resolvedAtMs: Long? = null,
    val status: String,
    val holder: String = "SELF",
    val selfCollateral: String = "",
    val partnerCollateral: String = "",
    val selfSealed: Boolean = false,
    val partnerSealed: Boolean = false,
    val selfReported: Boolean = false,
    val partnerReported: Boolean = false,
    val creatorSaysCreator: Boolean? = null,
    val creatorSaysPartner: Boolean? = null,
    val partnerSaysCreator: Boolean? = null,
    val partnerSaysPartner: Boolean? = null,
    val creatorOutcome: String? = null,
    val partnerOutcome: String? = null,
)

enum class ReportResult { OK, NOT_FOUND, NOT_MEMBER, ALREADY, NOT_READY, INCOMPLETE }

/** 1人の答え。aboutCreator は約束した人について、aboutPartner は相手について */
private data class Ans(val aboutCreator: Boolean?, val aboutPartner: Boolean?)

enum class SealResult { OK, NOT_FOUND, NOT_PARTNER, NOT_NEEDED, ALREADY, INVALID }

private val HOLDERS = setOf("SELF", "PARTNER", "BOTH")
private fun needsSelf(holder: String) = holder != "PARTNER"
private fun needsPartner(holder: String) = holder != "SELF"

private class Rec(
    val id: String,
    val title: String,
    val creator: String,
    val partner: String,
    val dueMs: Long,
    val createdAtMs: Long,
    val holder: String,
    var selfCollateral: String,
    var partnerCollateral: String = "",
    var partnerSealed: Boolean = false,
    var status: String = "ACTIVE",
    var creatorAns: Ans? = null,
    var partnerAns: Ans? = null,
    var creatorOutcome: String? = null,
    var partnerOutcome: String? = null,
    var resolvedAtMs: Long? = null,
) {
    val selfSealed: Boolean get() = needsSelf(holder)
    val ready: Boolean get() = !needsPartner(holder) || partnerSealed
}

/** 約束の置き場所。リクエストは並行に来るので、操作はすべて lock の中で行う */
class ServerStore(private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val recs = LinkedHashMap<String, Rec>()

    /** 作成。問題があればその理由を返す */
    fun create(r: CreateRequest): String? = synchronized(lock) {
        val creator = r.creator.trim()
        val partner = r.partner.trim()
        val collateral = r.collateral.trim()
        when {
            !ID.matches(r.id) -> return "id の形式が正しくありません"
            recs.containsKey(r.id) -> return "同じ id の約束があります"
            r.holder !in HOLDERS -> return "担保を出す人の指定が正しくありません"
            r.title.isBlank() || r.title.length > 40 -> return "約束することは1〜40字で書いてください"
            creator.isEmpty() || creator.length > 12 -> return "名前は1〜12字で書いてください"
            partner.isEmpty() || partner.length > 12 -> return "相手の名前は1〜12字で書いてください"
            creator == partner -> return "自分とは約束できません"
            needsSelf(r.holder) && !validCollateral(collateral) -> return "担保は1〜40字で書いてください"
            // 端末とPCの時計のずれを1分まで許す
            r.dueMs < clock() - CLOCK_SKEW_MS -> return "期限は今より後にしてください"
        }
        recs[r.id] = Rec(
            r.id, r.title.trim(), creator, partner, r.dueMs, clock(), r.holder,
            selfCollateral = if (needsSelf(r.holder)) collateral else "",
        )
        null
    }

    /** 相手が担保を書いて封をする */
    fun sealPartner(id: String, by: String, collateral: String): SealResult = synchronized(lock) {
        val rec = recs[id] ?: return SealResult.NOT_FOUND
        val text = collateral.trim()
        when {
            by != rec.partner -> SealResult.NOT_PARTNER
            !needsPartner(rec.holder) -> SealResult.NOT_NEEDED
            rec.partnerSealed || rec.status != "ACTIVE" -> SealResult.ALREADY
            !validCollateral(text) -> SealResult.INVALID
            else -> {
                rec.partnerCollateral = text
                rec.partnerSealed = true
                SealResult.OK
            }
        }
    }

    /**
     * 申告。担保がそろってから受け付ける。担保を出した人全員について答えが必要。
     * 2人の申告がそろったら、担保を出した人ごとに結果を決める
     */
    fun report(id: String, by: String, aboutCreator: Boolean?, aboutPartner: Boolean?): ReportResult = synchronized(lock) {
        val rec = recs[id] ?: return ReportResult.NOT_FOUND
        if (by != rec.creator && by != rec.partner) return ReportResult.NOT_MEMBER
        if (rec.status != "ACTIVE") return ReportResult.ALREADY
        if (!rec.ready) return ReportResult.NOT_READY
        val needC = needsSelf(rec.holder)
        val needP = needsPartner(rec.holder)
        if ((needC && aboutCreator == null) || (needP && aboutPartner == null)) return ReportResult.INCOMPLETE
        // 担保を出していない人についての答えは捨てる
        val ans = Ans(if (needC) aboutCreator else null, if (needP) aboutPartner else null)
        if (by == rec.creator) {
            if (rec.creatorAns != null) return ReportResult.ALREADY
            rec.creatorAns = ans
        } else {
            if (rec.partnerAns != null) return ReportResult.ALREADY
            rec.partnerAns = ans
        }
        val a = rec.creatorAns
        val b = rec.partnerAns
        if (a != null && b != null) {
            fun one(x: Boolean?, y: Boolean?) = when {
                x != y -> "MISMATCH"
                x == true -> "KEPT"
                else -> "BROKEN"
            }
            rec.creatorOutcome = if (needC) one(a.aboutCreator, b.aboutCreator) else null
            rec.partnerOutcome = if (needP) one(a.aboutPartner, b.aboutPartner) else null
            rec.status = listOfNotNull(rec.creatorOutcome, rec.partnerOutcome).distinct().singleOrNull() ?: "MIXED"
            // 燃えた／永久封印の担保は、中身をサーバーからも消す。破った人の担保だけ残す
            if (rec.creatorOutcome != "BROKEN") rec.selfCollateral = ""
            if (rec.partnerOutcome != "BROKEN") rec.partnerCollateral = ""
            rec.resolvedAtMs = clock()
        }
        ReportResult.OK
    }

    fun visibleTo(me: String): List<PromiseDto> = synchronized(lock) {
        recs.values.filter { it.creator == me || it.partner == me }.map { it.toDto(me) }
    }

    fun clear() = synchronized(lock) { recs.clear() }

    private fun validCollateral(text: String) = text.isNotEmpty() && text.length <= 40

    private fun Rec.toDto(me: String): PromiseDto {
        val resolved = status != "ACTIVE"
        val showCreatorAns = resolved || me == creator
        val showPartnerAns = resolved || me == partner
        return PromiseDto(
            id = id, title = title, creator = creator, partner = partner,
            dueMs = dueMs, createdAtMs = createdAtMs, resolvedAtMs = resolvedAtMs,
            status = status,
            holder = holder,
            selfCollateral = if (creatorOutcome == "BROKEN") selfCollateral else "",
            partnerCollateral = if (partnerOutcome == "BROKEN") partnerCollateral else "",
            selfSealed = selfSealed,
            partnerSealed = partnerSealed,
            selfReported = creatorAns != null,
            partnerReported = partnerAns != null,
            creatorSaysCreator = if (showCreatorAns) creatorAns?.aboutCreator else null,
            creatorSaysPartner = if (showCreatorAns) creatorAns?.aboutPartner else null,
            partnerSaysCreator = if (showPartnerAns) partnerAns?.aboutCreator else null,
            partnerSaysPartner = if (showPartnerAns) partnerAns?.aboutPartner else null,
            creatorOutcome = creatorOutcome,
            partnerOutcome = partnerOutcome,
        )
    }

    private companion object {
        val ID = Regex("[a-z0-9]{1,32}")
        const val CLOCK_SKEW_MS = 60_000L
    }
}
