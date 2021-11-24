

package it.xiyan.automusician

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinition
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import mu.KotlinLogging
import org.ktorm.entity.forEach
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger { }

object TaskManager {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss:SSS")
    private val triggerMap: MutableMap<Task, TaskTrigger> = ConcurrentHashMap()
    private val lastFutureMap: MutableMap<Task, ScheduledFuture<*>> = ConcurrentHashMap()

    fun registerTask(task: Task, trigger: TaskTrigger) {
        triggerMap[task] = trigger
        val nextExecuteTime = scheduleTask(task)
        logger.debug {
            "已注册任务 $task, 下一次执行时间: " +
                    dateFormat.format(Date(System.currentTimeMillis() + nextExecuteTime))
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun unregisterTask(task: Task) {
        triggerMap.remove(task)
    }

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

private class TaskExecuteWrapper(val task: Task): Runnable {
    override fun run() {
        try {
            task.run()
        } finally {
            TaskManager.scheduleTask(task)
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

interface Task: Runnable
abstract class NeteaseCloudUserTask: Task {
    override fun run() {
        database.NeteaseCloudUserPO.forEach { action(it) }
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

class CronTrigger(cron: Cron): TaskTrigger {

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
