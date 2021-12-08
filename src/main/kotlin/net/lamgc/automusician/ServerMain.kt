package net.lamgc.automusician

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File
import java.time.Duration
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger { }

/**
 * 初始化方法.
 */
fun initial(args: Array<String>) {
    processArguments(args)
    if (!Const.FILE_SERVER_CONFIG.exists()) {
        writeDefaultConfigFile()
        println(
            "The configuration file has been initialized. Please restart the server after configuration." +
                    "(Path: ${Const.FILE_SERVER_CONFIG.canonicalPath})"
        )
        exitProcess(1)
    }
    initialDatabase()
    initialTasks()
}

/**
 * 参数处理方法.
 */
fun processArguments(args: Array<String>) {
    if (args.isNotEmpty()) {
        val configFile = File(args[0])
        if (configFile.exists() and configFile.isFile) {
            val path = configFile.canonicalPath
            AppProperties.setProperty(PropertyNames.FILE_CONFIG.name, path)
            logger.info { "The specified profile path is `${path}`." }
        }
    }
}

/**
 * Ktor Server 模块安装方法.
 */
fun Application.modules() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(6)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Short.MAX_VALUE.toLong()
    }
}

/**
 * Ktor Server 路由方法.
 */
fun Application.router() {
    routing {
        pages()
        api()
    }
}

fun main(args: Array<String>): Unit = runBlocking {
    logger.info { "The automatic musician is getting up..." }
    logger.info { "Workdir: ${File(".").canonicalPath}" }
    initial(args)
    val serverEnvironment = applicationEngineEnvironment {
        connector {
            host = Const.config.bindAddress
            port = Const.config.httpPort
        }
        module(Application::modules)
        module(Application::router)
        sslConfig()
    }
    embeddedServer(Netty, serverEnvironment).start(wait = false)
    logger.info {
        "The automatic musician is awake! He's working now! " +
                "(Here: http://${Const.config.bindAddress}:${Const.config.httpPort})"
    }
}
