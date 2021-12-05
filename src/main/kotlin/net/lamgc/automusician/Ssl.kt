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
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.crypto.EncryptedPrivateKeyInfo


private val logger = KotlinLogging.logger { }

object KeyUtils {

    private val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")

    fun createKeyStore(type: String = "JKS"): KeyStore {
        val keyStore = KeyStore.getInstance(type)
        keyStore.load(null, null)
        return keyStore
    }

    fun loadKeyStore(file: File, password: String): KeyStore = KeyStore.getInstance(file, password.toCharArray())
    fun putSslKeyToKeyStore(
        ks: KeyStore,
        privateKey: PrivateKey,
        fullChain: Array<out Certificate>,
        alias: String = "tlsKeyEntry",
        password: String
    ) {
        ks.setKeyEntry(alias, privateKey, password.toCharArray(), fullChain)
    }

    private fun isEncodedAsEncryptedPrivateKeyInfo(keyData: ByteArray): Boolean {
        // From JavaKeyStore.java:320
        return try {
            EncryptedPrivateKeyInfo(keyData)
            true
        } catch (ioe: IOException) {
            false
        }
    }

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

    private fun privateKeyInfoToPrivateKey(info: PrivateKeyInfo): PrivateKey = JcaPEMKeyConverter().getPrivateKey(info)

    fun loadCertificateChain(certChainFile: File): Array<Certificate> {
        if (!certChainFile.exists() || !certChainFile.isFile) {
            throw IOException("The file does not exist or the path specified is not a file.")
        }
        val certList = ArrayList<Certificate>()
        val certFactory = CertificateFactory.getInstance("X.509")
        certChainFile.bufferedReader(StandardCharsets.UTF_8).use {
            val pemReader = PemReader(it)
            val obj = pemReader.readPemObject()
            certList.add(certFactory.generateCertificate(obj.content.inputStream()))
        }
        return certList.toArray(emptyArray())
    }
}

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
