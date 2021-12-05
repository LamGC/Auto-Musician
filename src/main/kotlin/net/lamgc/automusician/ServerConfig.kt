package net.lamgc.automusician

import com.google.gson.*
import mu.KotlinLogging
import net.lamgc.automusician.SslKeyStoreType.*
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.*

private val logger = KotlinLogging.logger { }

object Const {
    private const val PATH_SERVER_CONFIG = "./config.json"
    val FILE_SERVER_CONFIG: File
        get() = File(AppProperties.getProperty(PropertyNames.FILE_CONFIG, PATH_SERVER_CONFIG))
    val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .registerTypeAdapter(SslConfig::class.java, SslConfigAdapter)
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
    val ssl: SslConfig = SslConfig(),
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

data class SslConfig(
    val type: SslKeyStoreType = NONE,
    val port: Int = 8443,
    val key: SslKeyConfig? = null
)

interface SslKeyConfig {
    val privateKeyPassword: String?
}

data class JavaKeyStoreConfig(
    val keyStorePath: String,
    val keyStorePassword: String?,
    val keyEntryAlias: String,
    override val privateKeyPassword: String?
) : SslKeyConfig

data class PemKeyConfig(
    override val privateKeyPassword: String?,
    val fullChainPath: String,
    val privateKeyPath: String
) : SslKeyConfig

object SslConfigAdapter : JsonDeserializer<SslConfig> {
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): SslConfig {
        if (json !is JsonObject) {
            throw JsonParseException("Configuration item is not an json object.")
        } else if (!json.has("type")) {
            throw JsonParseException("Missing item 'type'.")
        }

        val typeStr = json.get("type").asString.trim().uppercase(Locale.getDefault())
        val type: SslKeyStoreType
        try {
            type = valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Invalid value (type='$typeStr')")
        }

        val sslKeyConfig: SslKeyConfig? = when (type) {
            NONE -> null
            JavaKeyStore -> context.deserialize(json.getAsJsonObject("key"), JavaKeyStoreConfig::class.java)
            PEM -> context.deserialize(json.getAsJsonObject("key"), PemKeyConfig::class.java)
        }

        val port = if (json.has("port")) {
            json.get("port").asInt
        } else {
            8443
        }

        return SslConfig(type, port, sslKeyConfig)
    }

}

enum class SslKeyStoreType {
    /**
     * 不启用 SSL.
     * <p> 如果确定不使用 SSL, 或者应用的前置代理会提供 SSL 的时候选择该项.
     */
    NONE,

    /**
     * 提供 JKS, 将直接从中获取密钥, 来提供 SSL 支持.
     */
    JavaKeyStore,

    /**
     * 提供正常的密钥文件, 服务端将自动转换成 JKS.
     */
    PEM
}
