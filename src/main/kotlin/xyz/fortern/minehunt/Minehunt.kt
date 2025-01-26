package xyz.fortern.minehunt

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.command.MinehuntCommand
import xyz.fortern.minehunt.listener.GameListener

class Minehunt : JavaPlugin() {

    private lateinit var instance: Minehunt
    private lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        // 初始化
        this.instance = this
        this.adventure = BukkitAudiences.create(this)
        val console = Console(this, adventure)

        // 注册事件
        Bukkit.getPluginManager().registerEvents(GameListener(console, adventure), this)

        // 注册命令
        Bukkit.getPluginCommand("minehunt")!!.setExecutor(MinehuntCommand(console, adventure, this))
    }

    override fun onDisable() {
        this.adventure.close()
    }
}

