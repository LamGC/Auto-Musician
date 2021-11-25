package it.xiyan.automusician

import com.cronutils.builder.CronBuilder
import com.cronutils.model.CronType
import com.cronutils.model.field.expression.FieldExpressionFactory.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

object AutoReceiveCloudBeans : NeteaseCloudUserTask() {
    override fun action(user: NeteaseCloudUser) {
        if (!NeteaseCloudMusician.isCreator(user.cookies)) {
            return
        }
        NeteaseCloudMusician.getTasks(user.cookies).filter { logger.debug { "Task: $it" };it.status == 20 }
            .forEach {
                NeteaseCloudMusician.receiveTaskReward(
                    cookie = user.cookies,
                    userMissionId = it.userMissionId!!,
                    period = it.period
                )
                logger.debug { "已自动领取用户 ${user.uid} 任务 \"${it.description}\" 的奖励." }
            }
    }
}

object MusicianSignIn : NeteaseCloudUserTask() {

    override fun action(user: NeteaseCloudUser) {
        if (!NeteaseCloudMusician.isCreator(user.cookies)) {
            return
        }
        NeteaseCloudMusician.signIn(user.cookies)

        logger.debug { "音乐人 ${user.uid} 签到完成." }
    }

}

fun initialTasks() {
    TaskManager.registerTask(
        MusicianSignIn, CronTrigger(
            CronBuilder.cron(CronType.SPRING.toDefinition())
                .withSecond(on(0))
                .withMinute(on(0))
                .withHour(on(8))
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
