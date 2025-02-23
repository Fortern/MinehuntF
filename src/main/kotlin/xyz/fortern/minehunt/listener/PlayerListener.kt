package xyz.fortern.minehunt.listener

import org.bukkit.GameMode
import org.bukkit.entity.EnderDragon
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
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
            // 自动加入观察者队伍
            console.spectatorTeam.addPlayer(player)
        }
    }
    
    /**
     * 参与游戏的玩家在倒计时阶段退出，则中断倒计时
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val stage = console.stage
        if (stage == GameStage.PREPARING) {
            if (console.beginningCountdown != null && (console.isHunter(player) || console.isSpeedrunner(player))) {
                console.interruptCountdownToStart()
            }
            // 将离开的玩家从team中移除
            player.scoreboard.teams.forEach { it.removePlayer(player) }
        }
        
    }
    
    /**
     * 玩家丢弃物品时，阻止玩家丢弃猎人指南针，并将追踪目标切换到下一个
     */
    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        val itemStack = event.itemDrop.itemStack
        if (!console.isHunterCompass(itemStack)) return
        
        console.trackNextPlayer(event.player)
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
        if (console.isHunter(player) && player.gameMode == GameMode.SPECTATOR)
            event.isCancelled = true
    }
    
    /**
     * 玩家想要传送时，在特定情况下阻止玩家传送
     */
    @EventHandler
    fun onHunterReadyTP(event: PlayerTeleportEvent) {
        val player = event.player
        if (console.isHunter(player) && player.gameMode == GameMode.SPECTATOR && event.cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.isCancelled = true
        }
    }
    
    /**
     * 处理玩家死亡事件
     */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        console.handlePlayerDeath(event.entity)
    }
    
    /**
     * 末影龙死亡事件
     */
    @EventHandler
    fun onDragonDeath(event: EntityDeathEvent) {
        if (event.entity is EnderDragon && console.stage == GameStage.PROCESSING) {
            console.end("speedrunner")
        }
    }
    
    /**
     * 监听传送门传送事件。改变维度时，记录最后的位置。
     */
    @EventHandler
    fun onPlayerChangeWorld(event: PlayerPortalEvent) {
        if (console.stage != GameStage.PROCESSING) return
        
        // 我们用了Kotlin有了更装B的写法
        event.from.world?.let {
            console.recordLocAtPortal(event.player, event.from)
        }
    }
    
}
