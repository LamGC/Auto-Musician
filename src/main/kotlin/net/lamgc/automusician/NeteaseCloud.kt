package net.lamgc.automusician

import kotlinx.coroutines.*
import mu.KotlinLogging
import org.apache.hc.core5.http.HttpResponse
import org.ktorm.entity.Entity
import java.io.IOException
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

internal fun String.toApiUrl(cookie: String? = null): String {
    val apiUrl = if (this.startsWith("/")) {
        if (Const.config.apiServer.endsWith("/")) {
            Const.config.apiServer + this.substring(1)
        } else {
            Const.config.apiServer + this
        }
    } else {
        if (Const.config.apiServer.endsWith("/")) {
            Const.config.apiServer + this
        } else {
            "${Const.config.apiServer}/$this"
        }
    }

    return if (apiUrl.indexOf("?") == -1) {
        apiUrl + "?time=${System.currentTimeMillis()}"
    } else {
        apiUrl + "&time=${System.currentTimeMillis()}"
    } + if (cookie != null) "&cookie=${URLEncoder.encode(cookie, StandardCharsets.UTF_8)}" else ""
}

private val apiResponseEntityMapAdapter = Const.moshi.adapter(ApiResponseEntityMap::class.java)!!
private val apiResponseWithoutEntityAdapter = Const.moshi.adapter(ApiResponseWithoutEntity::class.java)!!
private val qrAdapter = Const.moshi.adapter(QrCodeLoginCheckResponse::class.java)!!

object NeteaseCloud {

    /**
     * 创建登录用 QR 码的 Id.
     * <p> Id 用于跟踪登录情况, 而不是用于登录二维码内容.
     * @return 返回新 Qr 码 Id, 注意这不是用于 QR 码链接的 Id.
     */
    fun createLoginQrCodeId(): UUID {
        return UUID.fromString(
            HttpUtils.get(
                url = "/login/qr/key".toApiUrl(),
            action = { success: Boolean, _: HttpResponse?, content: String?, cause: Throwable? ->
                logger.debug { "Response: $content" }
                if (!success) {
                    throw cause!!
                } else {
                    return@get apiResponseEntityMapAdapter.fromJson(content!!)!!.data!!["unikey"]!!
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
                    val response = apiResponseEntityMapAdapter.fromJson(content!!)!!
                    return@get LoginQrCode(response.data!!["qrurl"]!!, response.data["qrimg"]!!)
                }
            })
    }

    fun waitForQrCodeLoginResultAsync(id: UUID, action: (success: Boolean, code: Int, message: String, cookie: String?) -> Unit): Job {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val counter = AtomicInteger()
            var errorCount = 0
            while (isActive) {
                logger.debug { "[$id] 第 ${counter.incrementAndGet()} 次轮询." }
                var apiResponse: QrCodeLoginCheckResponse
                try {
                    apiResponse = HttpUtils.get(url = "/login/qr/check?key=$id".toApiUrl(),
                        action = { success: Boolean, _: HttpResponse?, content: String?, cause: Throwable? ->
                            if (!success) {
                                throw cause!!
                            } else {
                                qrAdapter.fromJson(content!!)!!
                            }
                        })
                } catch (e: IOException) {
                    logger.error(e) { "轮询扫码结果时发生异常." }
                    if (++errorCount > 5) {
                        logger.warn { "[$id] 由于错误次数过多, 轮询终止." }
                    }
                    continue
                }
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

    fun logout(cookie: String): Boolean {
        return HttpUtils.get("/logout".toApiUrl(), cookie) { success, response, content, _ ->
            success && (response?.notError()
                ?: false) && apiResponseWithoutEntityAdapter.fromJson(content!!)?.code == 200
        }
    }

    private val accountAdapter = Const.moshi.adapter(NeteaseCloudUserAccount::class.java)

    fun getUserAccount(cookie: String): NeteaseCloudUserAccount {
        return HttpUtils.get("/user/account".toApiUrl(cookie))
        { success, response, content, _ ->
            if (success) {
                return@get accountAdapter.nonNull().fromJson(content!!)!!
            } else {
                throw IOException("The HTTP request failed with a status code other than 200: ${response?.code}")
            }
        }
    }

    fun getUserId(cookie: String? = null, userAccount: NeteaseCloudUserAccount = getUserAccount(cookie!!)): Long {
        return (userAccount.account["id"]!! as Double).toLong()
    }

    fun getUserName(cookie: String? = null, userAccount: NeteaseCloudUserAccount = getUserAccount(cookie!!)): String {
        return (userAccount.profile["nickname"]!! as String)
    }

}

object NeteaseCloudMusician {

    private val taskAdapter = Const.moshi.adapter(MusicianTaskApiResponse::class.java)
    private val apiResponseEntityAdapter = Const.moshi.adapter(ApiResponseEntity::class.java)

    fun getTasks(cookie: String): List<MusicianTask> {
        return HttpUtils.get("/musician/tasks".toApiUrl(cookie), null) { success, _, content, cause ->
            if (!success) {
                throw cause!!
            }
            taskAdapter.fromJson(content!!)!!.data["list"]!!
        }
    }

    fun receiveTaskReward(cookie: String, userMissionId: Long, period: Int): Boolean {
        return HttpUtils.get("/musician/cloudbean/obtain?id=$userMissionId&period=$period".toApiUrl(cookie), null) {
                success, _, content, cause ->
            if (!success) {
                throw cause!!
            }

            val responseEntity = apiResponseEntityMapAdapter.fromJson(content!!)!!
            responseEntity.code == 200 && responseEntity.message.contentEquals("success", true)
        }
    }

    fun signIn(cookie: String): Boolean {
        return HttpUtils.get("/musician/sign".toApiUrl(cookie), null) {
                success, _, content, cause ->
            if (!success) {
                throw cause!!
            }

            val responseEntity = apiResponseEntityAdapter.fromJson(content!!)!!
            responseEntity.code == 200 &&
                    responseEntity.message.contentEquals("success", true) &&
                    (responseEntity.data as Boolean)
        }
    }

    fun isCreator(
        cookie: String? = null,
        userAccount: NeteaseCloudUserAccount = NeteaseCloud.getUserAccount(cookie!!)
    ): Boolean {
        val type = (userAccount.profile["djStatus"] as Double).toInt()
        return type != 0
    }

}

data class ApiResponseEntityMap(val code: Int, val message: String?, val data: Map<String, String>?)

data class ApiResponseEntity(val code: Int, val message: String?, val data: Any?)

data class ApiResponseWithoutEntity(val code: Int, val message: String?)

data class MusicianTaskApiResponse(
    val code: Int,
    val message: String,
    val data: Map<String, List<MusicianTask>>
)

data class QrCodeLoginCheckResponse(val code: Int, val message: String, val cookie: String?)

data class LoginQrCode(val loginUrl: String, val loginQrCodeBlob: String)

/**
 * 网易云帐号信息.
 * <p> 严重提醒：如果是数值型数据, 到 Map 中就是 Double 类型, 错误转换将导致 ClassCastException
 */
data class NeteaseCloudUserAccount(val code: Int, val account: Map<String, Any?>, val profile: Map<String, Any?>)

data class MusicianTask(
    val userMissionId: Long?,
    val missionId: Long,
    val userId: Long,
    val missionEntityId: Long,
    val rewardId: Long,
    val progressRate: Int,
    val description: String,
    val type: Int,
    val tag: Int,
    val actionType: Int,
    val platform: Int,
    val status: Int,
    val button: String,
    val sortValue: Int,
    val startTime: Long,
    val endTime: Long,
    val extendInfo: String,
    val updateTime: Long?,
    val period: Int,
    val targetCount: Int,
    val rewardWorth: String,
    val rewardType: Int,
    val needToReceive: Int?
)
