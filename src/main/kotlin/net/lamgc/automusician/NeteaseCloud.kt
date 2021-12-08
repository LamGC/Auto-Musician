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

private val logger = KotlinLogging.logger { }

interface NeteaseCloudUser : Entity<NeteaseCloudUser> {
    companion object : Entity.Factory<NeteaseCloudUser>()

    var uid: Long
    var cookies: String
    var loginDate: LocalDateTime
}

/**
 * 将 String 视为 Path 并构造 ApiUrl.
 *
 * 该方法会自动添加 time 参数用于阻止自动缓存数据.
 * @param cookie 请求所使用的 Cookie.
 * @return 返回构造好的 ApiUrl.
 */
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

/**
 * 适用于普通用户的网易云音乐 API.
 */
object NeteaseCloudMusic {

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
                        return@get Const.gson.fromJson(content!!, ApiResponseEntityMap::class.java)!!.data!!["unikey"]!!
                    }
                })
        )
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
                    val response = Const.gson.fromJson(content!!, ApiResponseEntityMap::class.java)!!
                    return@get LoginQrCode(response.data!!["qrurl"]!!, response.data["qrimg"]!!)
                }
            })
    }

    /**
     * 获取 QrCode 登录会话的结果.
     *
     */
    fun getQrCodeLoginResult(loginId: UUID): QrCodeLoginCheckResponse {
        return HttpUtils.get(url = "/login/qr/check?key=$loginId".toApiUrl(),
            action = { success: Boolean, _: HttpResponse?, content: String?, cause: Throwable? ->
                if (!success) {
                    throw cause!!
                } else {
                    Const.gson.fromJson(content!!, QrCodeLoginCheckResponse::class.java)!!
                }
            })
    }

    /**
     * 启动轮询来检查 QrCode 登录会话的登录结果.
     * @param id 网易云 QrCode 登录会话的 Id.
     * @param action 轮询结果操作. 当轮询得到结果时被执行,
     * action 将会重复调用直到登录会话结束(指 QrCode 过期, 或者登录成功.).
     */
    fun waitForQrCodeLoginResultAsync(
        id: UUID,
        action: (success: Boolean, code: Int, message: String, cookie: String?) -> Unit
    ): Job {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val counter = AtomicInteger()
            var errorCount = 0
            while (isActive) {
                logger.debug { "[$id] 第 ${counter.incrementAndGet()} 次轮询." }
                var apiResponse: QrCodeLoginCheckResponse
                try {
                    apiResponse = getQrCodeLoginResult(id)
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

    /**
     * 登出登录凭证.
     *
     * 登出后 Cookie 将失效.
     * @param cookie 要登出的登录凭证.
     * @return 登录成功返回 `true`.
     */
    fun logout(cookie: String): Boolean {
        return HttpUtils.get("/logout".toApiUrl(), cookie) { success, response, content, _ ->
            success && (response?.notError()
                ?: false) && Const.gson.fromJson(content!!, ApiResponseWithoutEntity::class.java)?.code == 200
        }
    }

    /**
     * 获取凭证所属的帐号信息.
     * @param cookie 登录 Cookie.
     * @return 返回用户帐号信息.
     */
    fun getUserAccount(cookie: String): NeteaseCloudUserAccountResponse {
        return HttpUtils.get("/user/account".toApiUrl(cookie))
        { success, response, content, cause ->
            if (success) {
                return@get Const.gson.fromJson(content!!, NeteaseCloudUserAccountResponse::class.java)!!
            } else {
                throw IOException("The HTTP request failed with a status code other than 200: ${response?.code}", cause)
            }
        }
    }

    /**
     * 使用登录凭证获取所属的用户 Id.
     * @param cookie 登录凭证, 与 userAccount 二选一.
     * @param userAccount 用户帐号信息, 与 cookie 二选一.
     */
    fun getUserId(
        cookie: String? = null,
        userAccount: NeteaseCloudUserAccountResponse = getUserAccount(cookie!!)
    ): Long {
        return (userAccount.account["id"]!! as Double).toLong()
    }

    /**
     * 使用登录凭证获取所属的用户昵称.
     * @param cookie 登录凭证, 与 userAccount 二选一.
     * @param userAccount 用户帐号信息, 与 cookie 二选一.
     */
    fun getUserName(
        cookie: String? = null,
        userAccount: NeteaseCloudUserAccountResponse = getUserAccount(cookie!!)
    ): String {
        return (userAccount.profile["nickname"]!! as String)
    }

}

/**
 * 专注于网易云音乐人的 API 集合.
 */
object NeteaseCloudMusician {

    /**
     * 获取音乐人任务列表.
     * @param cookie 登录凭证.
     * @return 返回音乐人任务列表.
     * @see MusicianTask
     */
    fun getTasks(cookie: String): List<MusicianTask> {
        return HttpUtils.get("/musician/tasks".toApiUrl(cookie), null) { success, _, content, cause ->
            if (!success) {
                throw cause!!
            }
            Const.gson.fromJson(content!!, MusicianTaskApiResponse::class.java)!!.data["list"]!!
        }
    }

    fun receiveTaskReward(cookie: String, userMissionId: Long, period: Int): Boolean {
        return HttpUtils.get(
            "/musician/cloudbean/obtain?id=$userMissionId&period=$period".toApiUrl(cookie),
            null
        ) { success, _, content, cause ->
            if (!success) {
                throw cause!!
            }

            val responseEntity = Const.gson.fromJson(content!!, ApiResponseEntityMap::class.java)!!
            responseEntity.code == 200 && responseEntity.message.contentEquals("success", true)
        }
    }

    /**
     * 网易云音乐人签到.
     *
     * 要求用户为创作者才能使用.
     *
     * @param cookie 登录凭证.
     */
    fun signIn(cookie: String): Boolean {
        return HttpUtils.get("/musician/sign".toApiUrl(cookie), null) { success, _, content, cause ->
            if (!success) {
                throw cause!!
            }

            val responseEntity = Const.gson.fromJson(content!!, ApiResponseEntity::class.java)!!
            responseEntity.code == 200 &&
                    responseEntity.message.contentEquals("success", true) &&
                    (responseEntity.data as Boolean)
        }
    }

    /**
     * 检查指定用户是否为创作者(可能不限于音乐人).
     * @param cookie 登录凭证, 与 userAccount 二选一.
     * @param userAccount 用户帐号信息, 与 cookie 二选一.
     * @return 如果是创作者, 返回 `true`.
     */
    fun isCreator(
        cookie: String? = null,
        userAccount: NeteaseCloudUserAccountResponse = NeteaseCloudMusic.getUserAccount(cookie!!)
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

/**
 * QrCode 登录结果.
 * @property code 状态 Id, 有以下状态：
 * - 800: 二维码过期
 * - 801: 等待扫码
 * - 802: 已扫码, 等待确认
 * - 803: 已确定, 登录成功.
 * @property message 接口返回消息.
 * @property cookie 登录成功时返回的登录凭证 Cookie, 当 `code == 803` 时本属性不为 `null`.
 */
data class QrCodeLoginCheckResponse(
    val code: Int,
    val message: String,
    val cookie: String?
)

/**
 * 二维码登录信息.
 * @property loginUrl 登录 Url, 需要通过网易云音乐 App 打开.
 * @property loginQrCodeBlob 二维码图片数据, 可直接用于 Html 的 img:src 属性.
 */
data class LoginQrCode(val loginUrl: String, val loginQrCodeBlob: String)

/**
 * 网易云帐号信息.
 * <p> 严重提醒：如果是数值型数据, 到 Map 中就是 Double 类型, 错误转换将导致 ClassCastException
 */
data class NeteaseCloudUserAccountResponse(
    val code: Int,
    val account: Map<String, Any?>,
    val profile: Map<String, Any?>
)

/**
 * 音乐人任务信息.
 */
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
