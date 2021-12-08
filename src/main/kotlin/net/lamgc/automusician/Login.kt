package net.lamgc.automusician

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.ktorm.entity.add
import org.ktorm.entity.update
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger { }

object QrCodeLoginMonitor {

    private val loginIdSet = HashcodeSet(ConcurrentHashMap<Int, UUID>(), 0)
    private val coroutineMap = ConcurrentHashMap<UUID, Job>()
    private val sessionMap = MultiValueMap<UUID, DefaultWebSocketSession>()

    /**
     * 创建 Web 登录会话.
     *
     * 不能直接将网易云登录 Id 交给前端! 这会让恶意分子通过 Id 截获登录 Cookie!
     * 所以这里通过将 UUID 转换成 HashCode, 并交给前端的方式规避前端直接获得登录 Id.
     * 虽然不排除会出现 HashCode 碰撞的可能性, 但在这种临时使用的场景中, 这种风险可以承受.
     *
     * @param loginId 网易云 QrCode 登录会话 Id, 如果不传入则自动创建新的 Id.
     */
    fun createWebLoginSession(loginId: UUID = NeteaseCloudMusic.createLoginQrCodeId()): String {
        val codeBlob = NeteaseCloudMusic.getLoginQrCode(loginId).loginQrCodeBlob
        val webLoginId = registerLoginId(loginId)
        return "{\"loginId\": $webLoginId,\"qrImg\": \"$codeBlob\"}"
    }

    /**
     * 将网易云 QrCode 登录会话 Id 注册到监视器中, 并返回前端使用的登录会话标识号.
     * @param loginId 网易云 QrCode 登录会话的 Id.
     * @return 返回提供给前端的登录会话标识号.
     */
    private fun registerLoginId(loginId: UUID): Int {
        loginIdSet.add(loginId)
        return HashcodeSet.getHash(loginId)
    }

    /**
     * 启动登录结果回报协程任务.
     *
     * 该任务将持续轮询 QrCode 登录结果, 并在出现状态更新的情况下将情况回报给前端, 给用户反馈状态.
     *
     * @param loginId 网易云 QrCode 登录会话的 Id.
     * @return 返回轮询回报协程任务的对象.
     */
    private fun startLoginResultReportCoroutine(loginId: UUID): Job {
        val job =
            NeteaseCloudMusic.waitForQrCodeLoginResultAsync(loginId) { success, code, message, cookie ->
                logger.debug { "[$loginId] 成功获取登录结果, 正在处理中..." }
                var repeatLogin = false
                var userId: Long? = null
                var userNick: String? = null
                var lastLogin: LocalDateTime? = null
                if (success) {
                    val cookies = handleCookies(cookie!!)
                    val userAccount = NeteaseCloudMusic.getUserAccount(cookie)
                    userId = NeteaseCloudMusic.getUserId(userAccount = userAccount)
                    repeatLogin = NeteaseCloudUserPO.hasUser(userId)
                    userNick = NeteaseCloudMusic.getUserName(userAccount = userAccount)
                    if (repeatLogin) {
                        lastLogin = NeteaseCloudUserPO.getUserById(userId)!!.loginDate
                    }
                    logger.info {
                        "用户 $userNick($userId) 登录成功, 正在录入数据库..." +
                                "(创作者: ${NeteaseCloudMusician.isCreator(userAccount = userAccount)})"
                    }
                    recordUserInfo(cookies)
                    logger.info { "用户 $userId 已录入数据库." }
                }

                val sessions = sessionMap[loginId]
                runBlocking(Dispatchers.IO) {
                    try {
                        logger.debug { "[$loginId] 正在将登录状况回报给客户端..." }
                        val responseBody = """
                            {
                              "success": $success,
                              "message": "$message",
                              "repeatLogin": $repeatLogin,
                              "userId": ${userId ?: -1},
                              "userName": "$userNick",
                              "lastLogin": ${lastLogin?.atZone(ZoneId.systemDefault())?.toInstant()?.epochSecond}
                            }
                        """.trimIndent()
                        for (session in sessions) {
                            logger.debug { "正在发送给 $session" }
                            if (!session.isActive) {
                                logger.debug { "会话已失效, 跳过发送." }
                                continue
                            }
                            logger.debug { "会话有效, 正在发送中..." }
                            session.outgoing.send(Frame.Text(responseBody))
                            logger.debug { "发送完成." }
                        }
                        logger.debug { "[$loginId] 回报完成." }
                    } finally {
                        if (code !in 801..802) {
                            loginIdSet.remove(loginId)
                            CoroutineScope(Dispatchers.IO).launch {
                                for (session in sessionMap[loginId]) {
                                    session.close()
                                }
                                sessionMap.remove(loginId)
                            }
                        }
                    }
                }
            }
        logger.debug { "[$loginId] 已启动回报协程." }
        return job
    }

    /**
     * 登记用户登录凭证.
     * @param cookie 用户在网易云的登录凭证.
     * @return 将凭证存入数据库后返回的, 对应的用户对象.
     */
    private fun recordUserInfo(cookie: String): NeteaseCloudUser {
        val userId = NeteaseCloudMusic.getUserId(cookie)
        val user = NeteaseCloudUser {
            uid = userId
            cookies = cookie
            loginDate = LocalDateTime.now()
        }
        val findUser = NeteaseCloudUserPO.getUserById(userId)
        if (findUser != null) {
            logger.debug { "用户 $userId 已存在, 正在更新登录凭证..." }
            logger.debug { "用户 $userId 正在销毁旧登录凭证..." }
            try {
                if (NeteaseCloudMusic.logout(findUser.cookies)) {
                    logger.debug { "用户 $userId 已成功销毁旧登录凭证." }
                } else {
                    logger.debug { "用户 $userId 销毁旧登录凭证失败, 凭证可能已失效." }
                }

            } catch (e: IOException) {
                logger.error(e) { "用户 ${findUser.uid} 登出旧凭证时发生异常." }
            }
            database.NeteaseCloudUserPO.update(user)
            logger.info { "用户 $userId 凭证已更新." }
        } else {
            logger.debug { "用户不存在, 正在添加到数据库..." }
            database.NeteaseCloudUserPO.add(user)
        }
        return user
    }

    /**
     * 处理 Cookies.
     * 通过登录状况接口会返回多余的 Cookie, 该方法将把不必要的 Cookie 删除, 只留下必要的 Cookie.
     * @return 返回修剪后的 Cookies.
     */
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

    /**
     * 检查监视器是否存在指定登录标识号的登录会话.
     * @param webId 登录标识号.
     * @return 如果存在登录会话, 返回 `true`.
     */
    fun hasLoginSession(webId: Int): Boolean {
        return try {
            loginIdSet.getByHash(webId)
            true
        } catch (e: NoSuchElementException) {
            false
        }
    }

    /**
     * 添加监听者.
     *
     * 前端将通过 WebSocket 监听 QrCode 登录结果, 本方法将 WebSocket 连接记录到对应登录会话的监听列表中.
     * @param webId 前端持有的登录会话标识号.
     * @param session 要求监听的 WebSocket 连接会话.
     * @return 返回对应登录回话的登录状况回报协程任务对象, 用于等待该任务.
     */
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




