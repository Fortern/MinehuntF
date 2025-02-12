package xyz.fortern.minehunt

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.command.TestCommand
import xyz.fortern.minehunt.listener.PlayerListener

class Minehunt : JavaPlugin() {
    
    companion object {
        @JvmStatic
        private lateinit var instance: Minehunt
        
        @JvmStatic
        fun instance() = instance
    }
    
    override fun onEnable() {
        // Plugin startup logic
        
        // 初始化
        instance = this
        val console = Console()
        
        // 注册命令
        Bukkit.getPluginManager().registerEvents(PlayerListener(console), this)
        
        // 注册事件
        Bukkit.getPluginCommand("test")!!.setExecutor(TestCommand())
        
    }
    
    override fun onDisable() {
        // Plugin shutdown logic
    }
}

