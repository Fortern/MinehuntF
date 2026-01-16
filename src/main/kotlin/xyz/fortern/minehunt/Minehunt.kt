package xyz.fortern.minehunt

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.command.MinehuntCommand
import xyz.fortern.minehunt.config.ConfigManager
import xyz.fortern.minehunt.listener.GameListener
import xyz.fortern.minehunt.storage.StorageManager

class Minehunt : JavaPlugin() {

    private lateinit var instance: Minehunt
    private lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        this.instance = this
        this.adventure = BukkitAudiences.create(this)

        // 处理配置
        this.saveDefaultConfig()
        val storageManager = StorageManager(this)
        val configManager = ConfigManager(this, storageManager)

        // 创建控制台
        val console = Console(this, adventure, storageManager)

        // 注册事件
        Bukkit.getPluginManager().registerEvents(GameListener(console, adventure), this)

        // 注册命令
        Bukkit.getPluginCommand("minehunt")!!.setExecutor(MinehuntCommand(console, configManager, adventure, this))
    }

    override fun onDisable() {
        this.adventure.close()
    }
}
