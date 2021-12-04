package net.lamgc.automusician

import mu.KotlinLogging
import org.ktorm.database.Database
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.schema.Table
import org.ktorm.schema.datetime
import org.ktorm.schema.long
import org.ktorm.schema.varchar
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger { }

val database
    get() = AppDatabase.database

object AppDatabase {
    val database = Database.Companion.connect(
        url = "jdbc:mysql://${Const.config.database.address}/${Const.config.database.databaseName}",
        user = Const.config.database.user,
        password = Const.config.database.password,
        generateSqlInUpperCase = true
    )
}

fun initialDatabase() {
    val jdbcUrl = "jdbc:mysql://" +
            "${Const.config.database.address}/${Const.config.database.databaseName}?" +
            "user=${Const.config.database.user}&password=${Const.config.database.password}"
    if (!checkDatabaseConnectivity(jdbcUrl)) {
        exitProcess(1)
    }

    DriverManager.getConnection(jdbcUrl).use { connection ->
        val statement = connection.createStatement()
        val result = statement.executeUpdate(
            """
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

fun checkDatabaseConnectivity(jdbcUrl: String): Boolean {
    return try {
        DriverManager.getConnection(jdbcUrl).use {
            it.close()
            true
        }
    } catch (e: SQLException) {
        logger.error { "Unable to connect to database: [${e.errorCode}] ${e.message}" }
        false
    }
}

object NeteaseCloudUserPO : Table<NeteaseCloudUser>("musician_users") {
    // id 是 音乐工人 里面的唯一 id, 可以记录其他信息.
    private val id = long("uid").primaryKey().bindTo { it.uid }
    val cookies = varchar("cookies").bindTo { it.cookies }
    val loginDate = datetime("login_date").bindTo { it.loginDate }

    fun getUserById(userId: Long): NeteaseCloudUser? = database.NeteaseCloudUserPO.find { it.id eq userId }

    fun hasUser(userId: Long): Boolean = getUserById(userId) != null
}

val Database.NeteaseCloudUserPO get() = this.sequenceOf(net.lamgc.automusician.NeteaseCloudUserPO)
