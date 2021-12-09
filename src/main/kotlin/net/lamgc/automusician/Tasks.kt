package net.lamgc.automusician

import com.cronutils.builder.CronBuilder
import com.cronutils.model.CronType
import com.cronutils.model.field.expression.FieldExpressionFactory.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

/**
 * 任务 - 云豆自动领取.
 */
object AutoReceiveCloudBeans : NeteaseCloudUserTask() {
    override fun action(user: NeteaseCloudUser) {
        if (!NeteaseCloudMusician.isCreator(user.cookies)) {
            return
        }
        NeteaseCloudMusician.getTasks(user.cookies).filter { it.status == 20 }
            .forEach {
                NeteaseCloudMusician.receiveTaskReward(
                    cookie = user.cookies,
                    userMissionId = it.userMissionId!!,
                    period = it.period
                )
                logger.info { "已自动领取用户 ${user.uid} 任务 \"${it.description}\" 的奖励." }
            }
    }
}

/**
 * 任务 - 音乐人自动签到.
 */
object MusicianSignIn : NeteaseCloudUserTask() {

    override fun action(user: NeteaseCloudUser) {
        if (!NeteaseCloudMusician.isCreator(user.cookies)) {
            return
        }
        if (NeteaseCloudMusician.signIn(user.cookies)) {
            logger.info { "音乐人 ${user.uid} 签到完成." }
        } else {
            logger.warn { "音乐人 ${user.uid} 签到失败." }
        }

    }

}

/**
 * 任务初始化方法.
 */
fun initialTasks() {
    TaskManager.registerTask(
        MusicianSignIn, CronTrigger(
            CronBuilder.cron(CronType.SPRING.toDefinition())
                .withSecond(on(0))
                .withMinute(on(0))
                .withHour(every(4))
                .withDoM(always())
                .withMonth(always())
                .instance()
        )

    )
    TaskManager.registerTask(
        AutoReceiveCloudBeans, CronTrigger(
            CronBuilder.cron(CronType.SPRING.toDefinition())
                .withSecond(on(0))
                .withMinute(on(0))
                .withHour(every(3))
                .withDoM(always())
                .withMonth(always())
                .instance()
        )
    )
}
