package xyz.fortern.minehunt.listener

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GamePhase

/** 将模式无关的玩家加入、退出事件转交给 [GameManager]。 */
class GameLifecycleListener(
    private val gameManager: GameManager,
    private val adventure: BukkitAudiences,
) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        adventure.player(event.player).sendMessage(Component.text("=====欢迎来到 Minehunt=====", NamedTextColor.GOLD))
        if (gameManager.phase == GamePhase.LOBBY) {
            event.player.gameMode = GameMode.ADVENTURE
        }
        gameManager.onPlayerJoin(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        gameManager.onPlayerQuit(event.player)
    }
}
