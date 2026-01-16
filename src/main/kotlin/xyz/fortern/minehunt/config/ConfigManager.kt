package xyz.fortern.minehunt.config

import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.storage.StorageManager
import xyz.fortern.minehunt.storage.StorageType

class ConfigManager(
    private val plugin: JavaPlugin,
    private val storageManager: StorageManager,
) {
    /**
     * 这个对象表达了整个配置文件。
     * 它是不可变的，每次重载都会生成一个新对象。
     */
    lateinit var configuration: GameConfiguration
        private set

    init {
        reload(true)
    }

    /**
     * 在插件加载时，同步地读取配置
     *
     * @param first true如果是插件加载时首次加载配置
     */
    fun reload(first: Boolean) {
        plugin.reloadConfig()
        val pluginConfig = plugin.config
        val storageConfig = pluginConfig.getConfigurationSection("storage")!!
        val storageType = StorageType.valueOf(storageConfig.getString("type")!!)
        val sqliteSection = storageConfig.getConfigurationSection("sqlite")!!
        val sqlite = Sqlite(
            sqliteSection.getInt("busy-timeout", 3000),
            sqliteSection.getString("file-name", "game.db")!!
        )
        val mysqlSection = storageConfig.getConfigurationSection("mysql")!!
        val mysql = Mysql(
            mysqlSection.getString("host")!!,
            mysqlSection.getInt("port"),
            mysqlSection.getString("username")!!,
            mysqlSection.getString("password")!!,
            mysqlSection.getString("database")!!,
        )
        val postgresqlSection = storageConfig.getConfigurationSection("postgres")!!
        val postgresql = Postgresql(
            postgresqlSection.getString("host")!!,
            postgresqlSection.getInt("port"),
            postgresqlSection.getString("username")!!,
            postgresqlSection.getString("password")!!,
            postgresqlSection.getString("database")!!,
            postgresqlSection.getString("schema")!!,
        )
        val storageConfiguration = StorageConfiguration(
            storageType,
            postgresql,
            mysql,
            sqlite,
        )
        val gameConfiguration = GameConfiguration(
            storageConfiguration
        )
        configuration = gameConfiguration
        // 数据库建表则是真正耗时的操作，为了减少复杂性，不允许重载数据源配置（只能通过重启生效）......
        if (first) {
            storageManager.reloadStorage(gameConfiguration.storage)
        }
    }
}
