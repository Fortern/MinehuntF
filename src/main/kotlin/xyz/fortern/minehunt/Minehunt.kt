package xyz.fortern.minehunt

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.command.MinehuntCommand
import xyz.fortern.minehunt.config.ConfigManager
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GameRecordService
import xyz.fortern.minehunt.listener.GameLifecycleListener
import xyz.fortern.minehunt.mode.manhunt.ManhuntGame
import xyz.fortern.minehunt.record.GameMode
import xyz.fortern.minehunt.storage.StorageManager

class Minehunt : JavaPlugin() {

    private lateinit var instance: Minehunt
    private lateinit var adventure: BukkitAudiences
    private lateinit var gameManager: GameManager
    private lateinit var gameRecords: GameRecordService

    override fun onEnable() {
        this.instance = this
        this.adventure = BukkitAudiences.create(this)

        // 处理配置
        this.saveDefaultConfig()
        val storageManager = StorageManager(this)
        val configManager = ConfigManager(this, storageManager)

        gameRecords = GameRecordService(storageManager, logger)
        gameManager = GameManager(this, adventure, gameRecords)
        gameManager.registerMode(GameMode.MANHUNT) {
            ManhuntGame(gameManager, this, adventure)
        }
        gameManager.selectMode(GameMode.MANHUNT)
        // 注册事件
        Bukkit.getPluginManager().registerEvents(GameLifecycleListener(gameManager, adventure), this)

        // 注册命令
        Bukkit.getPluginCommand("minehunt")!!.setExecutor(MinehuntCommand(gameManager, configManager, adventure, this))
    }

    override fun onDisable() {
        if (this::gameManager.isInitialized) gameManager.close()
        if (this::gameRecords.isInitialized) gameRecords.close()
        this.adventure.close()
    }
}
