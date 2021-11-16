package it.xiyan.automusician

import io.ktor.application.*
import io.ktor.html.respondHtml
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import mu.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger {  }

fun main(): Unit = runBlocking {
    logger.info { "The automatic musician is getting up..." }
    initialDatabase()
    embeddedServer(Netty, port = Constants.serverProp.httpPort, host = Constants.serverProp.bindAddress) {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Short.MAX_VALUE.toLong()
        }
        routing {
            get("/") {
                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }

            get("/qrlogin") {
                call.respondHtml(HttpStatusCode.OK, HTML::loginPage)
            }

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
                    outgoing.send(Frame.Text("""
                        {"confirm": false, "message": "Bad request parameters."}
                    """.trimIndent()))
                    close()
                    return@webSocket
                }

                if (!QrCodeLoginMonitor.hasLoginSession(webId)) {
                    logger.debug { "找不到指定的 Login Web Id. (id: $webId)" }
                    outgoing.send(Frame.Text("""
                        {"confirm": false, "message": "Login session not found."}
                    """.trimIndent()))
                    close()
                    return@webSocket
                }
                val job = QrCodeLoginMonitor.addListener(webId, this)

                outgoing.send(Frame.Text("""
                        {"confirm": true, "message": "Accepted, waiting for return."}
                    """.trimIndent()))
                job.join()
            }
        }
    }.start(wait = false)
    logger.info { "The automatic musician is awake! He's working now! " +
            "(Here: http://${Constants.serverProp.bindAddress}:${Constants.serverProp.httpPort})" }
}
