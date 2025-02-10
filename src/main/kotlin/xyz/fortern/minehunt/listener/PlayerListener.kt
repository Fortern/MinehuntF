package xyz.fortern.minehunt.listener

import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import xyz.fortern.minehunt.Console
import xyz.fortern.minehunt.Console.GameStage

class PlayerListener(
    private val console: Console
) : Listener {
    
    /**
     * 玩家加入服务器时
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val stage = console.stage
        if (stage == GameStage.PREPARING && console.beginningCountdown == null) {
            // 在准备阶段，玩家设为冒险模式
            player.gameMode = GameMode.ADVENTURE
            // 重置队伍信息
            player.scoreboard.teams.forEach { it.removeEntry(player.name) }
            // 自动加入观察者队伍
            console.spectatorTeam.addEntry(player.name)
        } else {
            // 在倒计时或进行阶段，玩家自动加入各自的Team
            if (console.isSpeedrunner(player)) {
                console.speedrunnerTeam.addEntry(player.name)
            } else if (console.isHunter(player)) {
                console.hunterTeam.addEntry(player.name)
            } else {
                console.joinSpectator(player)
            }
        }
    }
    
    /**
     * 玩家退出服务器时的操作
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // 速通者退出时，从speedrunnerList中移除，避免指南针遍历
//        if (console.stage == GameStage.PROCESSING && console.speedrunnerSet.contains(player))
//            console.speedrunnerList.remove(player)
        
    }
    
    /**
     * 玩家丢弃物品时，阻止玩家丢弃猎人指南针，并将追踪目标切换到下一个
     */
    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        val itemStack = event.itemDrop.itemStack
        if (!console.isHunterCompass(itemStack)) return
        
        console.trackNextPlayer(event.player.name)
    }
    
    /**
     * 玩家想要移动时，在特定情况下阻止玩家移动
     */
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 暂且通过取消事件的方法阻止玩家移动
        if (console.stage != GameStage.PROCESSING) return
        
        val player = event.player
        // 猎人等待出生时，或等待复活时，阻止其移动
        if (console.isHunter(player) && (console.hunterSpawnCD != null || player.gameMode == GameMode.SPECTATOR))
            event.isCancelled = true
    }
    
    /**
     * 处理玩家死亡事件
     */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        console.handlePlayerDeath(player)
    }
    
}
