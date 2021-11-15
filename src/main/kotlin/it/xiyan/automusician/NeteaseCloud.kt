package it.xiyan.automusician

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.apache.hc.core5.http.HttpResponse
import org.ktorm.entity.Entity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {  }

interface NeteaseCloudUser: Entity<NeteaseCloudUser> {
    companion object : Entity.Factory<NeteaseCloudUser>()

    var uid: Long
    var cookies: String
    var loginDate: LocalDateTime
}

internal fun String.toApiUrl(): String {
    val apiUrl = if (this.startsWith("/")) {
        if (Constants.serverProp.apiServer.endsWith("/")) {
            Constants.serverProp.apiServer + this.substring(1)
        } else {
            Constants.serverProp.apiServer + this
        }
    } else {
        if (Constants.serverProp.apiServer.endsWith("/")) {
            Constants.serverProp.apiServer + this
        } else {
            "${Constants.serverProp.apiServer}/$this"
        }
    }

    return if (apiUrl.indexOf("?") == -1) {
        apiUrl + "?time=${System.currentTimeMillis()}"
    } else {
        apiUrl + "&time=${System.currentTimeMillis()}"
    }

}

object NeteaseCloud {
    private val adapter = Constants.moshi.adapter(ApiResponse::class.java)!!

    /**
     * 创建登录用 QR 码的 Id.
     * <p> Id 用于跟踪登录情况, 而不是用于登录二维码内容.
     * @return 返回新 Qr 码 Id, 注意这不是用于 QR 码链接的 Id.
     */
    fun createLoginQrCodeId(): UUID {
        return UUID.fromString(HttpUtils.get(url = "/login/qr/key".toApiUrl(),
            action = { success: Boolean, _: HttpResponse?, content: String?, cause: Throwable? ->
                logger.debug { "Response: $content" }
                if (!success) {
                    throw cause!!
                } else {
                    return@get adapter.fromJson(content!!)!!.data["unikey"]!!
                }
            }))
    }

    /**
     * 通过唯一的 Qr 登录 Id 获取对应的 Qr 登录码.
     * @return 返回用于网页的 Blob 数据文本 (可直接嵌入 img 标签的 src 来使用).
     */
    fun getLoginQrCode(id: UUID): LoginQrCode {
        return HttpUtils.get(url = "/login/qr/create?key=$id&qrimg=true".toApiUrl(),
            action = { success: Boolean, _: HttpResponse?, content: String?, cause: Throwable? ->
                if (!success) {
                    throw cause!!
                } else {
                    val response = adapter.fromJson(content!!)!!
                    return@get LoginQrCode(response.data["qrurl"]!!, response.data["qrimg"]!!)
                }
            })
    }

    fun waitForQrCodeLoginResultAsync(id: UUID, action: (success: Boolean, code: Int, message: String, cookie: String?) -> Unit): Job {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val qrAdapter = Constants.moshi.adapter(QrCodeLoginCheckResponse::class.java)!!
            val counter = AtomicInteger()
            while (isActive) {
                logger.debug { "[$id] 第 ${counter.incrementAndGet()} 次轮询." }
                val apiResponse = HttpUtils.get(url = "/login/qr/check?key=$id".toApiUrl(),
                    action = { success: Boolean, _: HttpResponse?, content: String?, cause: Throwable? ->
                        if (!success) {
                            throw cause!!
                        } else {
                            qrAdapter.fromJson(content!!)!!
                        }
                    })
                logger.debug { "[$id] 轮询结果: $apiResponse" }
                if (apiResponse.code != 801) {
                    // 将实际操作切换回默认线程池
                    withContext(Dispatchers.Default) {
                        action(apiResponse.code == 803, apiResponse.code, apiResponse.message, apiResponse.cookie)
                    }
                    if (apiResponse.code !in 801..802) {
                        break
                    }
                }
                delay(5000)
            }
        }
        return job
    }

    fun logout(cookie: String) {
        HttpUtils.get("/logout".toApiUrl(), cookie) { _, _, _, _ ->
        }
    }

    private val accountAdapter = Constants.moshi.adapter(NeteaseCloudUserAccount::class.java)

    private fun getUserAccount(cookie: String): NeteaseCloudUserAccount {
        return HttpUtils.get("/user/account?cookie=${URLEncoder.encode(cookie, StandardCharsets.UTF_8)}".toApiUrl()) { success, response, content, cause ->
            return@get accountAdapter.nonNull().fromJson(content!!)!!
        }
    }

    fun getUserId(cookie: String): Long {
        return (getUserAccount(cookie).account["id"]!! as Double).toLong()
    }

    fun getUserName(cookie: String): String {
        return (getUserAccount(cookie).profile["nickname"]!! as String)
    }

}

data class ApiResponse(val code: Int, val data: Map<String, String>)

data class QrCodeLoginCheckResponse(val code: Int, val message: String, val cookie: String?)

data class LoginQrCode(val loginUrl: String, val loginQrCodeBlob: String)

data class NeteaseCloudUserAccount(val code: Int, val account: Map<String, Any?>, val profile: Map<String, Any?>)
