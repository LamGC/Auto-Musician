package net.lamgc.automusician

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import mu.KotlinLogging
import org.ktorm.entity.toList
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger { }
private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss:SSS")

/**
 * 任务管理器.
 */
object TaskManager {
    private val triggerMap: MutableMap<Task, TaskTrigger> = ConcurrentHashMap()
    private val lastFutureMap: MutableMap<Task, ScheduledFuture<*>> = ConcurrentHashMap()

    /**
     * 注册任务.
     *
     * @param task 任务对象.
     * @param trigger 任务触发器.
     */
    fun registerTask(task: Task, trigger: TaskTrigger) {
        triggerMap[task] = trigger
        val nextExecuteTime = scheduleTask(task)
        logger.info {
            "已注册任务 $task, 下一次执行时间: " +
                    dateFormat.format(Date(System.currentTimeMillis() + nextExecuteTime))
        }
    }

    /**
     * 注销任务.
     *
     * 注销之后将不再执行任务.
     * @param task 任务对象.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun unregisterTask(task: Task) {
        triggerMap.remove(task)
    }

    /**
     * 重新设定任务定时器.
     *
     * 该方法为内部方法, 不要调用它!
     */
    internal fun scheduleTask(task: Task): Long {
        val trigger = triggerMap[task]!!
        val nextExecuteTime = trigger.nextExecuteTime()
        if (nextExecuteTime < 0) {
            unregisterTask(task)
            return nextExecuteTime
        }
        lastFutureMap[task] = ScheduledTaskExecutor.execute(task, nextExecuteTime)
        return nextExecuteTime
    }

}

/**
 * 任务执行包装器.
 *
 * 通过包装器可以将重设定时的操作包装起来, 任务实现类无需处理该问题.
 */
private class TaskExecuteWrapper(val task: Task) : Runnable {
    override fun run() {
        try {
            task.run()
        } finally {
            val nextExecuteTime = TaskManager.scheduleTask(task)
            logger.debug {
                "任务 ${task::class.java} 下一次执行时间: " +
                        dateFormat.format(Date(System.currentTimeMillis() + nextExecuteTime))
            }
        }
    }

}

object ScheduledTaskExecutor {
    private val threadPoolExecutor: ScheduledExecutorService =
        Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())

    fun execute(task: Task, delay: Long): ScheduledFuture<*> {
        return threadPoolExecutor.schedule(TaskExecuteWrapper(task), delay, TimeUnit.MILLISECONDS)
    }

}

interface Task : Runnable {
    /**
     * 获取任务执行器 Id.
     * @return 获取该任务执行器的 Id, 不同的任务执行器 Id 不同.
     */
    fun getTaskId(): String = this::class.java.name
}

abstract class NeteaseCloudUserTask(private val filter: (NeteaseCloudUser) -> Boolean = { true }) : Task {

    override fun run() {
        database.NeteaseCloudUserPO.toList().filter(filter).forEach { action(it) }
    }

    abstract fun action(user: NeteaseCloudUser)
}

abstract class OnceNeteaseCloudUserTask(
    private val filter: (NeteaseCloudUser) -> Boolean = { true },
    private val onceExpire: (() -> Long)
) : Task {

    final override fun run() {
        val recorder = StateCacheBox.getExecutionRecorder(this, expireTimeProvider = onceExpire)
        database.NeteaseCloudUserPO.toList()
            .filter(recorder::notExecuted)
            .filter(filter)
            .forEach {
                action(it)
                recorder.setExecuted(it)
            }
    }

    abstract fun action(user: NeteaseCloudUser)
}

interface TaskTrigger {

    /**
     * 当前时间距离下一次执行的时间.
     * @return 如果不再执行, 返回 -1.
     */
    fun nextExecuteTime(): Long

}

class CronTrigger(cron: Cron) : TaskTrigger {

    constructor(cronExpression: String, cronDefinition: CronDefinition) :
            this(CronParser(cronDefinition).parse(cronExpression))

    private val executeTime: ExecutionTime = ExecutionTime.forCron(cron)

    init {
        cron.validate()
    }

    override fun nextExecuteTime(): Long {
        val now: ZonedDateTime = ZonedDateTime.now()
        val timeToNextExecution = executeTime.timeToNextExecution(now).get()
        return timeToNextExecution.toMillis()
    }

}

fun CronType.toDefinition(): CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(this)
