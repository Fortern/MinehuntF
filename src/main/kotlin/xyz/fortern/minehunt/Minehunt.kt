package xyz.fortern.minehunt

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.command.MinehuntCommand
import xyz.fortern.minehunt.config.ConfigManager
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GameRecordService
import xyz.fortern.minehunt.listener.GameLifecycleListener
import xyz.fortern.minehunt.mode.bingo.BingoGame
import xyz.fortern.minehunt.mode.manhunt.ManhuntGame
import xyz.fortern.minehunt.record.GameMode
import xyz.fortern.minehunt.storage.StorageManager

class Minehunt : JavaPlugin() {

    private lateinit var instance: Minehunt
    private lateinit var gameManager: GameManager
    private lateinit var gameRecords: GameRecordService

    override fun onEnable() {
        this.instance = this
        // 处理配置
        this.saveDefaultConfig()
        val storageManager = StorageManager(this)
        val configManager = ConfigManager(this, storageManager)

        gameRecords = GameRecordService(storageManager, logger)
        gameManager = GameManager(this, gameRecords)
        gameManager.registerMode(GameMode.MANHUNT) {
            ManhuntGame(gameManager, this)
        }
        gameManager.registerMode(GameMode.BINGO) {
            BingoGame(gameManager, this)
        }
        gameManager.selectMode(GameMode.MANHUNT)
        // 注册事件
        Bukkit.getPluginManager().registerEvents(GameLifecycleListener(gameManager), this)

        // 注册命令
        Bukkit.getPluginCommand("game")!!.setExecutor(MinehuntCommand(gameManager, configManager, this))
    }

    override fun onDisable() {
        if (this::gameManager.isInitialized) gameManager.close()
        if (this::gameRecords.isInitialized) gameRecords.close()
    }
}
