package net.lamgc.automusician

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.time.Duration
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {  }

fun initial() {
    if (!Constants.FILE_SERVER_PROPERTIES.exists()) {
        initialConfigFile()
        println(
            "The configuration file has been initialized. Please restart the server after configuration." +
                    "(Path: ${Constants.FILE_SERVER_PROPERTIES.canonicalPath})"
        )
        exitProcess(1)
    }
    initialDatabase()
    initialTasks()
}

fun Application.modules() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(6)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Short.MAX_VALUE.toLong()
    }
}

fun main(): Unit = runBlocking {
    logger.info { "The automatic musician is getting up..." }
    initial()
    embeddedServer(Netty, port = Constants.serverProp.httpPort, host = Constants.serverProp.bindAddress) {
        modules()
        routing {
            pages()
            api()
        }
    }.start(wait = false)
    logger.info { "The automatic musician is awake! He's working now! " +
            "(Here: http://${Constants.serverProp.bindAddress}:${Constants.serverProp.httpPort})" }
}
