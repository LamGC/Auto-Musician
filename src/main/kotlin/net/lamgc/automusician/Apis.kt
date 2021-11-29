package net.lamgc.automusician

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

fun Routing.api() {
    get("/api/login/createLoginSession") {
        call.respondText(ContentType.Application.Json, HttpStatusCode.OK) {
            QrCodeLoginMonitor.createWebLoginSession()
        }
    }

    webSocket("/api/login/check") {
        val webIdParam = call.request.queryParameters["id"]
        val webId: Int

        try {
            webId = webIdParam!!.toInt()
        } catch (e: Exception) {
            logger.error(e) { "解析参数时发生异常." }
            outgoing.send(
                Frame.Text("""
                        {"confirm": false, "message": "Bad request parameters."}
                    """.trimIndent()))
            close()
            return@webSocket
        }

        if (!QrCodeLoginMonitor.hasLoginSession(webId)) {
            logger.debug { "找不到指定的 Login Web Id. (id: $webId)" }
            outgoing.send(
                Frame.Text("""
                        {"confirm": false, "message": "Login session not found."}
                    """.trimIndent()))
            close()
            return@webSocket
        }
        val job = QrCodeLoginMonitor.addListener(webId, this)

        outgoing.send(
            Frame.Text("""
                        {"confirm": true, "message": "Accepted, waiting for return."}
                    """.trimIndent()))
        job.join()
    }
}
