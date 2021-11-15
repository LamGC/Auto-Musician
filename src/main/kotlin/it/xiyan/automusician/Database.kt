package it.xiyan.automusician

import mu.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.*
import java.sql.DriverManager

private val logger = KotlinLogging.logger {  }

val database = Database.Companion.connect(
    url = "jdbc:mysql://${Constants.serverProp.database.address}/${Constants.serverProp.database.databaseName}",
    user = Constants.serverProp.database.user,
    password = Constants.serverProp.database.password,
    generateSqlInUpperCase = true
)

fun initialDatabase() {
    DriverManager.getConnection(
        "jdbc:mysql://" +
                "${Constants.serverProp.database.address}/${Constants.serverProp.database.databaseName}?" +
                "user=${Constants.serverProp.database.user}&password=${Constants.serverProp.database.password}"
    ).use { connection ->
        val statement = connection.createStatement()
        val result = statement.executeUpdate("""
            CREATE TABLE IF NOT EXISTS ${NeteaseCloudUserPO.tableName} (
                uid BIGINT PRIMARY KEY NOT NULL,
                cookies TEXT NOT NULL,
                login_date DATE NOT NULL 
            )
        """.trimIndent()
        )

        if (result != 0) {
            logger.error { "Database initialization failed! (Code: $result)" }
        } else {
            logger.debug { "Database initialization completed." }
        }
    }
}

object NeteaseCloudUserPO: Table<NeteaseCloudUser>("musician_users") {
    // id 是 音乐工人 里面的唯一 id, 可以记录其他信息.
    val id = long("uid").primaryKey().bindTo { it.uid }
    val cookies = varchar("cookies").bindTo { it.cookies }
    val loginDate = datetime("login_date").bindTo { it.loginDate }
}

val Database.NeteaseCloudUserPO get() = this.sequenceOf(it.xiyan.automusician.NeteaseCloudUserPO)
