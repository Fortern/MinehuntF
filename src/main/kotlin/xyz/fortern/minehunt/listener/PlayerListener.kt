package xyz.fortern.minehunt.listener

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import xyz.fortern.minehunt.Console

class PlayerListener(
    private val console: Console
) : Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val stage = console.stage
        if (stage == Console.GameStage.PREPARING) {
            player.gameMode = GameMode.ADVENTURE
            console.spectatorSet.add(player)
            // 在开始倒计时阶段，所有玩家都是无敌状态
            if (console.countdownTask != null) {
                player.isInvulnerable = true
            }
        } else if (stage == Console.GameStage.PROCESSING && console.speedrunnerTeam.hasEntry(player.name)) {
            console.speedrunnerList.add(player)
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val stage = console.stage
        if (stage == Console.GameStage.PREPARING) {
            player.scoreboard.teams.forEach { it.removeEntry(player.name) }
        } else if (stage == Console.GameStage.PROCESSING && console.speedrunnerTeam.hasEntry(player.name)) {
            console.speedrunnerList.remove(player)
        }
        player.spigot().respawn()
        
    }
    
    @EventHandler
    fun onIn(event: PlayerInteractEvent) {
        val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("A")!!
        val size = team.entries.size
        
    }
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        player.spigot().respawn()
    }
}