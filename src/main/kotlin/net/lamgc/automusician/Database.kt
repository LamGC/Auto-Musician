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
import java.sql.Connection
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

    val result = openDatabaseConnectionOrFailure(jdbcUrl) { connection ->
        val statement = connection.createStatement()
        val result = statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS ${NeteaseCloudUserPO.tableName} (
                uid BIGINT PRIMARY KEY NOT NULL,
                cookies TEXT NOT NULL,
                login_date DATETIME NOT NULL 
            )
        """.trimIndent()
        )

        if (result != 0) {
            logger.error { "Database initialization failed! (Code: $result)" }
        } else {
            logger.debug { "Database initialization completed." }
        }
    }

    if (!result) {
        exitProcess(1)
    }
}

fun openDatabaseConnectionOrFailure(jdbcUrl: String, block: (Connection) -> Unit): Boolean {
    val connection: Connection
    try {
        connection = DriverManager.getConnection(jdbcUrl)
    } catch (e: SQLException) {
        logger.error(e) { "Unable to connect to database: [${e.errorCode}] ${e.message}" }
        return false
    }
    connection.use {
        block(connection)
    }
    return true
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
