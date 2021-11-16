package it.xiyan.automusician

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.ktorm.entity.add
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {  }

object QrCodeLoginMonitor {

    private val loginIdSet = HashcodeSet(ConcurrentHashMap<Int, UUID>(), 0)
    private val coroutineMap = ConcurrentHashMap<UUID, Job>()
    private val sessionMap = MultiValueMap<UUID, DefaultWebSocketSession>()

    fun createWebLoginSession(loginId: UUID = NeteaseCloud.createLoginQrCodeId()): String {
        val codeBlob = NeteaseCloud.getLoginQrCode(loginId).loginQrCodeBlob
        val webLoginId = registerLoginId(loginId)
        return "{\"loginId\": $webLoginId,\"qrImg\": \"$codeBlob\"}"
    }

    private fun registerLoginId(id: UUID): Int {
        loginIdSet.add(id)
        return HashcodeSet.getHash(id)
    }

    private fun startLoginResultReportCoroutine(id: UUID): Job {
        val job =
            NeteaseCloud.waitForQrCodeLoginResultAsync(id) { success, code, message, cookie ->
                try {
                    logger.debug { "[$id] 成功获取登录结果, 正在处理中..." }
                    var repeatLogin = false
                    var userId: Long? = null
                    var userNick: String? = null
                    if (success) {
                        val cookies = handleCookies(cookie!!)
                        logger.debug { "登录成功, 正在录入数据库..." }
                        userId = NeteaseCloud.getUserId(cookies)
                        repeatLogin = NeteaseCloudUserPO.hasUser(userId)
                        userNick = NeteaseCloud.getUserName(cookies)
                        recordUserInfo(cookies)
                    }

                    val sessions = sessionMap[id]
                    CoroutineScope(Dispatchers.IO).launch {
                        logger.debug { "[$id] 正在将登录状况回报给客户端..." }
                        val responseBody = """
                            {
                              "success": $success,
                              "message": "$message",
                              "repeatLogin": $repeatLogin,
                              "userId": ${userId ?: -1},
                              "userName": "$userNick"
                            }
                        """.trimIndent()
                        for (session in sessions) {
                            // FIXME(LamGC, 2021.11.15): 浏览器那侧的登录回报还是会在收到消息后掉线，需要检查一下原因。
                            logger.debug { "正在发送给 $session" }
                            if (!session.isActive) {
                                logger.debug { "会话已失效, 跳过发送." }
                                continue
                            }
                            session.outgoing.send(Frame.Text(responseBody))
                        }
                        logger.debug { "[$id] 回报完成." }
                    }
                } finally {
                    if (code !in 801..802) {
                        loginIdSet.remove(id)
                        CoroutineScope(Dispatchers.IO).launch {
                            for (session in sessionMap[id]) {
                                session.close()
                            }
                            sessionMap.remove(id)
                        }
                    }
                }
            }
        logger.debug { "[$id] 已启动回报协程." }
        return job
    }

    private fun recordUserInfo(cookie: String): NeteaseCloudUser {
        val userId = NeteaseCloud.getUserId(cookie)
        val findUser = NeteaseCloudUserPO.getUserById(userId)
        if (findUser != null) {
            logger.debug { "用户已存在, 跳过添加." }
            return findUser
        }

        val user = NeteaseCloudUser {
            uid = userId
            cookies = cookie
            loginDate = LocalDateTime.now()
        }
        logger.debug { "用户不存在, 正在添加到数据库..." }
        database.NeteaseCloudUserPO.add(user)
        return user
    }

    private fun handleCookies(cookies: String): String {
        val newCookies = StringBuilder()
        val antiRepeat = HashSet<String>()
        cookies.split(";").filter {
            val ck = it.trim()
            ck.startsWith("MUSIC_U")
        }.forEach {
            if (!antiRepeat.contains(it)) {
                newCookies.append(it).append(';')
                antiRepeat.add(it)
            }
        }
        return newCookies.toString()
    }

    fun hasLoginSession(id: Int): Boolean {
        return try {
            loginIdSet.getByHash(id)
            true
        } catch (e: NoSuchElementException) {
            false
        }
    }

    @Suppress("ReplacePutWithAssignment")
    fun addListener(webId: Int, session: DefaultWebSocketSession): Job {
        val loginId = loginIdSet.getByHash(webId)
        if (sessionMap.isEmpty(loginId)) {
            coroutineMap[loginId] = startLoginResultReportCoroutine(loginId)
        }
        sessionMap.put(loginId, session)
        return coroutineMap[loginId]!!
    }

}




