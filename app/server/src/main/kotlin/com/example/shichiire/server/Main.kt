package com.example.shichiire.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 信じてMe! の中継サーバー（デモ用）。
 * データはメモリ上だけ。サーバーを止めると消える。
 * 担保の中身はサーバーだけが持ち、「破った」で一致するまでどの端末にも送らない。
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8787
    println("信じてMe! 中継サーバーを起動します（ポート $port）")
    println("スマホの接続画面には、次のどれかを入力してください：")
    lanAddresses().forEach { println("  $it:$port") }
    println("（Androidエミュレータからは 10.0.2.2:$port）")
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module(store: ServerStore = ServerStore()) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }

        get("/promises") {
            val me = call.request.queryParameters["me"]?.trim().orEmpty()
            if (me.isEmpty()) return@get call.respond(HttpStatusCode.BadRequest, "me が必要です")
            call.respond(store.visibleTo(me))
        }

        post("/promises") {
            val req = runCatching { call.receive<CreateRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "形式が正しくありません")
            when (val r = store.create(req)) {
                null -> call.respond(HttpStatusCode.Created, "ok")
                else -> call.respond(HttpStatusCode.BadRequest, r)
            }
        }

        post("/promises/{id}/report") {
            val id = call.parameters["id"].orEmpty()
            val req = runCatching { call.receive<ReportRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "形式が正しくありません")
            when (store.report(id, req.by.trim(), req.aboutCreator, req.aboutPartner)) {
                ReportResult.OK -> call.respond(HttpStatusCode.OK, "ok")
                ReportResult.NOT_FOUND -> call.respond(HttpStatusCode.NotFound, "約束が見つかりません")
                ReportResult.NOT_MEMBER -> call.respond(HttpStatusCode.Forbidden, "この約束の当事者ではありません")
                ReportResult.ALREADY -> call.respond(HttpStatusCode.Conflict, "もう申告済みです")
                ReportResult.NOT_READY -> call.respond(HttpStatusCode.Conflict, "まだ担保がそろっていません")
                ReportResult.INCOMPLETE -> call.respond(HttpStatusCode.BadRequest, "担保を出した人全員について答えてください")
            }
        }

        post("/promises/{id}/collateral") {
            val id = call.parameters["id"].orEmpty()
            val req = runCatching { call.receive<CollateralRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "形式が正しくありません")
            when (store.sealPartner(id, req.by.trim(), req.collateral)) {
                SealResult.OK -> call.respond(HttpStatusCode.OK, "ok")
                SealResult.NOT_FOUND -> call.respond(HttpStatusCode.NotFound, "約束が見つかりません")
                SealResult.NOT_PARTNER -> call.respond(HttpStatusCode.Forbidden, "この約束の相手ではありません")
                SealResult.NOT_NEEDED -> call.respond(HttpStatusCode.Conflict, "この約束では相手の担保はいりません")
                SealResult.ALREADY -> call.respond(HttpStatusCode.Conflict, "もう封をしています")
                SealResult.INVALID -> call.respond(HttpStatusCode.BadRequest, "担保は1〜40字で書いてください")
            }
        }

        // リハーサル用：全部消す
        post("/reset") {
            store.clear()
            call.respond(HttpStatusCode.OK, "ok")
        }
    }
}

private fun lanAddresses(): List<String> =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .map { it.hostAddress }
