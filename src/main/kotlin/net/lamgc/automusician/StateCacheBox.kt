package net.lamgc.automusician

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KotlinLogging
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger { }
private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss:SSS")
private val keyExpireExecution = Executors.newScheduledThreadPool(
    2,
    ThreadFactoryBuilder()
        .setNameFormat("pool-CacheExpire-%d")
        .build()
)

/**
 * 状态缓冲盒.
 *
 * 按需要创建记录器, 记录状态.
 */
object StateCacheBox {

    private val cacheDB: DB = DBMaker
        .fileDB("./cache.db")
        .fileChannelEnable()
        .closeOnJvmShutdown()
        .make()

    fun getExecutionRecorder(
        task: Task,
        expireTime: Long = -1,
        expireTimeProvider: (() -> Long)?,
        extraFlag: String? = null
    ): ExecutionRecorder {
        val expireTimestamp = if (expireTimeProvider != null) {
            expireTimeProvider()
        } else {
            expireTime
        }

        val name = if (extraFlag?.isEmpty() != false) {
            "TASK_EXECUTION::`${task.getTaskId()}`"
        } else {
            "TASK_EXECUTION::`${task.getTaskId()}`-`$extraFlag`"
        }

        val mapMaker = cacheDB.hashMap(
            name,
            Serializer.STRING, Serializer.DATE
        )

        if (expireTimestamp > 0) {
            mapMaker.expireAfterCreate(expireTimestamp)
            mapMaker.expireExecutor(keyExpireExecution)
            logger.debug {
                "HashMap 将在 ${dateFormat.format(Date(System.currentTimeMillis() + expireTimestamp))}" +
                        " 过期.($expireTimestamp 毫秒)"
            }
        }
        return ExecutionRecorder(mapMaker.createOrOpen())
    }

}

class ExecutionRecorder(private val map: MutableMap<String, Date>) {

    fun setExecuted(obj: OperatedObject) {
        map[obj.objectIdentity()] = Date()
        logger.debug { "已设置对象 `${obj.objectIdentity()}` 任务执行成功." }
    }

    fun isExecuted(obj: OperatedObject): Boolean = map[obj.objectIdentity()] != null

    fun notExecuted(obj: OperatedObject): Boolean = !isExecuted(obj)

}

interface OperatedObject {

    fun objectIdentity(): String

}

enum class OnceExpire : () -> Long {

    /**
     * 取到第二天同一时刻的所需毫秒数.
     */
    DAILY {
        override fun invoke(): Long = 86400000
    },

    /**
     * 到第二天凌晨.
     *
     * 与 [DAILY] 不一样, 即使在任意时刻获取第二天时间, 都返回到第二天凌晨所需的毫秒数.
     */
    DAILY_ROUND {
        override fun invoke(): Long {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1)

            calendar.set(Calendar.HOUR, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis - System.currentTimeMillis()
        }
    }

    ;


}

