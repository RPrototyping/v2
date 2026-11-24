package com.example.shichiire.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---- 中継サーバーとやりとりする形（server の ServerStore.kt と同じ形にそろえる） ----

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
    /** 約束した人の答え（自分の分か、結果が出たあとだけ入る） */
    val creatorSaysCreator: Boolean? = null,
    val creatorSaysPartner: Boolean? = null,
    /** 相手の答え（自分の分か、結果が出たあとだけ入る） */
    val partnerSaysCreator: Boolean? = null,
    val partnerSaysPartner: Boolean? = null,
    /** 担保を出した人ごとの結果（KEPT / BROKEN / MISMATCH） */
    val creatorOutcome: String? = null,
    val partnerOutcome: String? = null,
)

/** エンジンは各OSのもの（Android=OkHttp / iOS=Darwin）が自動で選ばれる */
fun createHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        // エミュレータや混んだWi-Fiでは1回の通信が数秒かかることがあるので、少し長めにとる
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 8_000
    }
}

/** 入力されたアドレスを http://host:port の形にそろえる。ポート省略時は 8787 */
fun normalizeServerUrl(input: String): String? {
    var s = input.trim().trimEnd('/')
    if (s.isEmpty()) return null
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
    val hostPart = s.substringAfter("://")
    if (hostPart.isEmpty() || hostPart.contains('/')) return null
    if (!hostPart.contains(':')) s = "$s:8787"
    return s
}

/** サーバーにつながるか確かめる */
suspend fun checkServer(baseUrl: String): Boolean {
    val client = createHttpClient()
    return try {
        val r = client.get("$baseUrl/health")
        r.status.isSuccess() && r.bodyAsText() == "ok"
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    } finally {
        client.close()
    }
}
