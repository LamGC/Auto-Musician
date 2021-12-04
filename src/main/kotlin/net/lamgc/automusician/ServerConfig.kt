package net.lamgc.automusician

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

private val logger = KotlinLogging.logger {  }

object Const {
    private const val PATH_SERVER_CONFIG = "./config.json"
    val FILE_SERVER_CONFIG: File
        get() = File(AppProperties.getProperty(PropertyNames.FILE_CONFIG, PATH_SERVER_CONFIG))
    val gson: Gson = GsonBuilder()
        .generateNonExecutableJson()
        .serializeNulls()
        .setPrettyPrinting()
        .create()
    val config = loadServerConfig()
}

fun loadServerConfig(configFile: File = Const.FILE_SERVER_CONFIG): ServerConfig {
    return if (!configFile.exists()) {
        logger.warn { "The configuration file does not exist. Run the server with the default configuration." }
        ServerConfig.DEFAULT
    } else {
        try {
            val properties = (Const.gson.fromJson(configFile.readText(StandardCharsets.UTF_8), ServerConfig::class.java)
                ?: ServerConfig.DEFAULT)
            if (properties == ServerConfig.DEFAULT) {
                logger.warn { "The configuration file is empty. Use the default configuration file to run the server." }
            } else {
                logger.info {
                    "Successfully loaded configuration file. " +
                            "(Path: ${configFile.canonicalPath})"
                }
            }
            properties
        } catch (e: IOException) {
            logger.error(e) { "An error occurred while loading the configuration file." }
            ServerConfig.DEFAULT
        }
    }
}

val AppProperties = Properties()
fun Properties.getProperty(property: PropertyNames, default: String): String =
    this.getProperty(property.name, default)

enum class PropertyNames {
    FILE_CONFIG
}

fun writeDefaultConfigFile(configFile: File = Const.FILE_SERVER_CONFIG) {
    configFile.writeText(Const.gson.toJson(ServerConfig.DEFAULT), StandardCharsets.UTF_8)
}

data class ServerConfig(
    val apiServer: String = "https://netease-cloud-music-api-binaryify.vercel.app/",
    val bindAddress: String = "0.0.0.0",
    val httpPort: Int = 8080,
    val database: DatabaseConnectConfig = DatabaseConnectConfig(),
    val httpProxy: HttpProxy = HttpProxy()
) {
    companion object {
        val DEFAULT = ServerConfig()
    }
}

data class DatabaseConnectConfig(
    val address: String = "localhost",
    val databaseName: String = "auto_musician",
    val user: String? = "root",
    val password: String? = ""
)

data class HttpProxy(val enable: Boolean = false, val host: String = "127.0.0.1", val port: Int = 1080)
