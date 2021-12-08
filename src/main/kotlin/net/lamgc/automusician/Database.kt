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

/**
 * Auto-Musician 全局使用的 SQL 数据库 ORM 对象.
 *
 * 实际指向了 `AppDatabase.database`
 */
val database
    get() = AppDatabase.database

/**
 * 通过 AppDatabase, 我们可以推迟 Ktorm 建立数据库连接的时机, 方便 initialDatabase 优先初始化数据库.
 */
private object AppDatabase {
    val database = Database.Companion.connect(
        url = "jdbc:mysql://${Const.config.database.address}/${Const.config.database.databaseName}",
        user = Const.config.database.user,
        password = Const.config.database.password,
        generateSqlInUpperCase = true
    )
}

/**
 * 数据库初始化方法.
 */
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

/**
 * 打开数据库连接, 否则失败返回.
 *
 * 当成功建立数据库连接时, block 将会被执行, 这么做不仅能检查数据库连通性, 还能进行初始化操作, 一举两得.
 *
 * @param jdbcUrl Jdbc 连接 Url.
 * @param block 当 Jdbc 成功打开数据库连接后的操作.
 * @return 如果连通性检查失败, block 将不会执行, 并返回 `false`.
 */
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

/**
 * 网易云用户持久化对象.
 */
object NeteaseCloudUserPO : Table<NeteaseCloudUser>("musician_users") {
    private val id = long("uid").primaryKey().bindTo { it.uid }
    val cookies = varchar("cookies").bindTo { it.cookies }
    val loginDate = datetime("login_date").bindTo { it.loginDate }

    /**
     * 通过用户 Id 获取用户对象.
     * @param userId 用户 Id.
     * @return 如果存在该用户, 返回用户对象, 否则返回 `null`.
     */
    fun getUserById(userId: Long): NeteaseCloudUser? = database.NeteaseCloudUserPO.find { it.id eq userId }

    /**
     * 检查给定 Id 所属的用户是否已登录在数据库中.
     *
     * 该方法等价于 `getUserById(userId) != null`
     *
     * @param userId 待检查的用户 Id.
     * @return 如果存在, 返回 `true`.
     */
    fun hasUser(userId: Long): Boolean = getUserById(userId) != null
}

val Database.NeteaseCloudUserPO get() = this.sequenceOf(net.lamgc.automusician.NeteaseCloudUserPO)
