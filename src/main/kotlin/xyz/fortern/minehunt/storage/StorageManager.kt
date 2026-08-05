package xyz.fortern.minehunt.storage

import com.mysql.cj.jdbc.MysqlDataSource
import org.bukkit.plugin.java.JavaPlugin
import org.sqlite.SQLiteDataSource
import xyz.fortern.minehunt.config.StorageConfiguration
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import xyz.fortern.minehunt.storage.StorageType.*
import java.io.File
import java.nio.file.Path
import java.util.logging.Level

/** 根据配置持有当前对局记录存储，并向游戏层隐藏具体数据库实现。 */
class StorageManager(
    private val plugin: JavaPlugin,
) {
    /**
     * 当前正在使用的数据库存储
     */
    private lateinit var sqlStorageAdapter: SqlStorageAdapter

    private val rollbackStorage = RollbackStorage(
        plugin.dataFolder.toPath().resolve("unsaved-game-records")
    )

    /**
     * 重新创建配置对象，并初始化数据库架构
     */
    fun reloadStorage(storageConfiguration: StorageConfiguration) {
        plugin.logger.log(Level.INFO, "Reloading storage config")
        val adapter = when (storageConfiguration.storageType) {
            SQLITE -> {
                val sqlite = storageConfiguration.sqlite
                val dataSource = SQLiteDataSource().also {
                    it.config.busyTimeout = sqlite.busyTimeout
                    it.url = "jdbc:sqlite:${File(plugin.dataFolder, sqlite.fileName).path}"
                }
                SqliteStorage(dataSource, plugin.logger)
            }

            MYSQL -> {
                val mysql = storageConfiguration.mysql
                MysqlDataSource().also {
                    it.setUrl("jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}")
                    it.user = mysql.username
                    it.password = mysql.password
                }
                TODO()
            }

            POSTGRES -> {
                val postgresql = storageConfiguration.postgresql
                MysqlDataSource().also {
                    it.setUrl("jdbc:mysql://${postgresql.host}:${postgresql.port}/${postgresql.database}?currentSchema=${postgresql.schema}")
                    it.user = postgresql.username
                    it.password = postgresql.password
                }
                TODO()
            }
        }
        // 初始化数据库表（网络IO耗时操作）
        adapter.prepareSchema()
        sqlStorageAdapter = adapter
        plugin.logger.log(Level.INFO, "SqlStorageAdapter changed")
    }

    fun adapterReady(): Boolean {
        return this::sqlStorageAdapter.isInitialized
    }

    /** 仅在游戏结束时调用数据库，一次性插入完整记录。 */
    fun insertGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>): Int =
        sqlStorageAdapter.insertGameRecord(gameRecord, players)

    /** 数据库失败或超时时，将完整记录写入插件数据目录中的独立 JSON 文件。 */
    fun saveToLocalFile(gameRecord: GameRecord, players: List<PlayerInGame>): Path =
        rollbackStorage.save(gameRecord, players)
}
