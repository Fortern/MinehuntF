package xyz.fortern.minehunt.listener

import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
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
            if (console.speedrunnerSet.contains(player)) {
                console.speedrunnerTeam.addEntry(player.name)
            } else if (console.hunterSet.contains(player)) {
                console.hunterTeam.addEntry(player.name)
            } else {
                console.spectatorSet.add(player)
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
        if (console.stage == GameStage.PROCESSING && console.speedrunnerSet.contains(player))
            console.speedrunnerList.remove(player)
        
    }
    
    
}
