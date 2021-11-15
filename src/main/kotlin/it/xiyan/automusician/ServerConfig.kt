package it.xiyan.automusician

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {  }

object Constants {
    val FILE_SERVER_PROPERTIES = File("./config.json")
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    val serverProp = loadServerProperties()
}

fun loadServerProperties(): ServerProperties {
    return if (!Constants.FILE_SERVER_PROPERTIES.exists()) {
        logger.warn { "The configuration file does not exist. Run the server with the default configuration." }
        ServerProperties.DEFAULT
    } else {
        val adapter = Constants.moshi.adapter(ServerProperties::class.javaObjectType)
        try {
            val properties = (adapter.fromJson(Constants.FILE_SERVER_PROPERTIES.readText(StandardCharsets.UTF_8)) ?: ServerProperties.DEFAULT)
            if (properties == ServerProperties.DEFAULT) {
                logger.warn { "The configuration file is empty. Use the default configuration file to run the server." }
            } else {
                logger.info { "Successfully loaded configuration file. " +
                        "(Path: ${Constants.FILE_SERVER_PROPERTIES.canonicalPath})" }
            }
            properties
        } catch (e: IOException) {
            logger.error(e) { "An error occurred while loading the configuration file." }
            ServerProperties.DEFAULT
        }
    }
}

data class ServerProperties(
    val apiServer: String = "https://netease-cloud-music-api-binaryify.vercel.app/",
    val bindAddress: String = "0.0.0.0",
    val httpPort: Int = 8080,
    val database: DatabaseConnectConfig = DatabaseConnectConfig()
) {
    companion object {
        val DEFAULT = ServerProperties()
    }
}

data class DatabaseConnectConfig(
    val address: String = "localhost",
    val databaseName: String = "auto_musician",
    val user: String? = "root",
    val password: String? = ""
)
