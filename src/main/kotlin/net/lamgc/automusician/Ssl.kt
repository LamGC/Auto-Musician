package net.lamgc.automusician

import io.ktor.server.engine.*
import mu.KotlinLogging
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.util.io.pem.PemReader
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory


private val logger = KotlinLogging.logger { }

object KeyUtils {
    /**
     * 创建新的 KeyStore.
     *
     * @param type KeyStore 类型, 默认为 `JavaKeyStore`.
     * @return 返回指定类型的 KeyStore 对象.
     */
    fun createKeyStore(type: String = "JKS"): KeyStore {
        val keyStore = KeyStore.getInstance(type)
        keyStore.load(null, null)
        return keyStore
    }

    /**
     * 从文件中加载 KeyStore.
     * @param file KeyStore 文件.
     * @param password KeyStore 密码.
     * @return 如果成功, 返回加载到的 KeyStore 对象.
     */
    fun loadKeyStore(file: File, password: String): KeyStore = KeyStore.getInstance(file, password.toCharArray())

    /**
     * 将 Ssl 密钥对加入到 KeyStore.
     * @param ks KeyStore 对象.
     * @param privateKey Ssl 私钥.
     * @param fullChain Ssl 证书链.
     * @param alias Ssl 密钥对在 KeyStore 中的别名.
     * @param password Ssl 私钥密码.
     */
    fun putSslKeyToKeyStore(
        ks: KeyStore,
        privateKey: PrivateKey,
        fullChain: Array<out Certificate>,
        alias: String = "tlsKeyEntry",
        password: String
    ) {
        ks.setKeyEntry(alias, privateKey, password.toCharArray(), fullChain)
    }

    /**
     * 从文件加载 PKCS#1 私钥.
     * @param keyFile 按 PKCS#1 规范, 以 PEM 格式存储的私钥文件.
     * @param password 私钥密码.
     * @return 返回私钥对象.
     */
    fun loadPrivateKeyFromFile(keyFile: File, password: String? = null): PrivateKey {
        if (!keyFile.exists() || !keyFile.isFile) {
            throw IOException("The file does not exist or the path specified is not a file.")
        }
        keyFile.reader(StandardCharsets.UTF_8).use {
            val privateKeyInfo: PrivateKeyInfo = when (val obj = PEMParser(it).readObject()) {
                is PEMKeyPair -> {
                    obj.privateKeyInfo
                }
                is PKCS8EncryptedPrivateKeyInfo -> {
                    val provider = JceOpenSSLPKCS8DecryptorProviderBuilder().build(password!!.toCharArray())
                    obj.decryptPrivateKeyInfo(provider)
                }
                else -> {
                    throw IllegalStateException("Unsupported key type: ${obj::class.java}")
                }
            }
            return privateKeyInfoToPrivateKey(privateKeyInfo)
        }
    }

    /**
     * 将 PrivateKeyInfo 转换成 PrivateKey.
     * @param info 要转换的 PrivateKeyInfo.
     * @return 从 PrivateKeyInfo 转换出来的 PrivateKey 对象.
     */
    private fun privateKeyInfoToPrivateKey(info: PrivateKeyInfo): PrivateKey = JcaPEMKeyConverter().getPrivateKey(info)

    /**
     * 从 PEM 文件中加载证书链.
     * @param certChainFile 证书链文件.
     * @return 返回证书链数组, 证书在数组中存在先后顺序,
     * 顺序为: `Site-Cert(First, 0), Intermediate-Cert ..., CA Root Cert(Last, length - 1)`
     */
    fun loadCertificateChain(certChainFile: File): Array<Certificate> {
        if (!certChainFile.exists() || !certChainFile.isFile) {
            throw IOException("The file does not exist or the path specified is not a file.")
        }
        val certList = ArrayList<Certificate>()
        val certFactory = CertificateFactory.getInstance("X.509")
        certChainFile.bufferedReader(StandardCharsets.UTF_8).use {
            val pemReader = PemReader(it)
            var obj = pemReader.readPemObject()
            while (obj != null) {
                certList.add(certFactory.generateCertificate(obj.content.inputStream()))
                obj = pemReader.readPemObject()
            }
        }
        logger.debug { "Certificate chain length: ${certList.size}" }
        return certList.toArray(emptyArray())
    }
}

/**
 * Ktor Server 的 SSL 配置方法.
 */
fun ApplicationEngineEnvironmentBuilder.sslConfig() {
    val config = Const.config.ssl
    var enableSsl = false
    when {
        config.type == SslKeyStoreType.JavaKeyStore && config.key is JavaKeyStoreConfig -> {
            val keyStoreFile = File(config.key.keyStorePath)
            sslConnector(
                keyStore = KeyUtils.loadKeyStore(keyStoreFile, config.key.keyStorePassword!!),
                keyStorePassword = { config.key.keyStorePassword.toCharArray() },
                keyAlias = config.key.keyEntryAlias,
                privateKeyPassword = { config.key.privateKeyPassword?.toCharArray() ?: "".toCharArray() }
            ) {
                port = config.port
                keyStorePath = keyStoreFile
            }
            enableSsl = true
        }

        config.type == SslKeyStoreType.PEM && config.key is PemKeyConfig -> {
            val keyEntryAlias = "sslKeyEntry"
            val memKeyStore = loadPemKeyToMemKeyStore(
                File(config.key.fullChainPath),
                File(config.key.privateKeyPath),
                config.key.privateKeyPassword ?: "",
                keyEntryAlias
            )
            sslConnector(
                keyStore = memKeyStore,
                keyStorePassword = { "".toCharArray() },
                keyAlias = keyEntryAlias,
                privateKeyPassword = { config.key.privateKeyPassword?.toCharArray() ?: "".toCharArray() }
            ) {
                port = config.port
            }
            enableSsl = true
        }
    }
    logger.info { if (enableSsl) "SSL enabled, port: ${config.port}" else "SSL disabled." }
}

/**
 * 将 PEM 证书加载倒临时 KeyStore 对象中.
 *
 * 由于 Ktor 只能加载 KeyStore 密钥库中的 Ssl 密钥对,
 * 所以使用这个方法来将常见的 PEM 证书转换成 Ktor 可用的 KeyStore.
 *
 * @param fullChainFile Ssl 证书链文件.
 * @param privateKeyFile Ssl 私钥文件.
 * @param privateKeyPassword Ssl 私钥密码.
 * @param keyEntryAlias Ssl 密钥对在临时 KeyStore 密钥库中的别名.
 */
fun loadPemKeyToMemKeyStore(
    fullChainFile: File,
    privateKeyFile: File,
    privateKeyPassword: String = "",
    keyEntryAlias: String
): KeyStore {
    val keyStore = KeyUtils.createKeyStore()
    val fullChain = KeyUtils.loadCertificateChain(fullChainFile)
    val privateKey = KeyUtils.loadPrivateKeyFromFile(privateKeyFile, privateKeyPassword)
    KeyUtils.putSslKeyToKeyStore(keyStore, privateKey, fullChain, keyEntryAlias, privateKeyPassword)
    return keyStore
}
