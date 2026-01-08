package xyz.fortern.minehunt.listener

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Blaze
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Enderman
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PiglinBarterEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.fortern.minehunt.Console
import xyz.fortern.minehunt.Console.GameStage
import xyz.fortern.minehunt.rule.RuleKey
import java.util.concurrent.ThreadLocalRandom

class GameListener(
    private val console: Console,
    private val adventure: BukkitAudiences,
) : Listener {

    /**
     * 玩家加入服务器时
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        adventure.player(event.player).sendMessage(Component.text("=====欢迎来到猎人游戏=====", NamedTextColor.GOLD))
        val player = event.player
        if (console.stage == GameStage.PREPARING) {
            // 在准备阶段，玩家设为冒险模式
            player.gameMode = GameMode.ADVENTURE
            // 自动加入观众阵营
            console.joinAudience(player)
        } else if (console.stage == GameStage.PROCESSING) {
            console.reJoinInGame(player)
        }
    }

    /**
     * 参与游戏的玩家在倒计时阶段退出，则中断倒计时
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (console.stage == GameStage.COUNTDOWN) {
            if (console.isHunter(player) || console.isSpeedrunner(player)) {
                console.interruptCountdownToStart()
            }
            // 将离开的玩家从team中移除
            player.scoreboard.teams.forEach { it.removeEntry(player.name) }
        }
    }

    /**
     * 猎人重生时给予追踪指南针
     */
    @EventHandler
    fun onPlayerSpawn(event: PlayerRespawnEvent) {
        console.giveCompassIfNeed(event.player)
    }

    /**
     * 玩家丢弃物品时，阻止玩家丢弃猎人指南针，并将追踪目标切换到下一个
     */
    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        val itemStack = event.itemDrop.itemStack
        if (!console.isHunterCompass(itemStack)) return

        console.trackNextPlayer(event.player)
        event.isCancelled = true
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
        if (console.isHunter(player)) {
            if (console.waitHunterSpawning(player) || console.isRespawning(player))
                event.isCancelled = true
        }
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
     * 处理末影龙死亡以及增加速通战利品
     */
    @EventHandler
    fun onDragonDeath(event: EntityDeathEvent) {
        if (console.stage != GameStage.PROCESSING) return
        val entity = event.entity
        if (entity is EnderDragon) {
            console.end("Speedrunner")
            return
        }
        // 是否给予更多速通相关的战利品
        if (!console.gameRules.getRuleValue(RuleKey.SPEEDRUN_LOOT_UP)) {
            return
        }
        if (entity is Blaze) {
            val drops = event.drops
            // 增加掉落烈焰棒的概率
            if (drops.isEmpty() && ThreadLocalRandom.current().nextBoolean()) {
                drops.add(ItemStack(Material.BLAZE_ROD))
            }
        } else if (entity is Enderman) {
            val drops = event.drops
            // 增加掉落末影珍珠的概率
            if (drops.isEmpty() && ThreadLocalRandom.current().nextBoolean()) {
                drops.add(ItemStack(Material.ENDER_PEARL))
            }
        }
    }

    /**
     * 增加猪灵交易获取末影珍珠的概率
     */
    @EventHandler
    fun onPiglinTrade(event: PiglinBarterEvent) {
        if (!console.gameRules.getRuleValue(RuleKey.SPEEDRUN_LOOT_UP)) return

        if (ThreadLocalRandom.current().nextInt(10) < 3) {
            event.outcome.add(ItemStack(Material.ENDER_PEARL))
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

    /**
     * 处理玩家使用床
     */
    @EventHandler
    fun onPlayerBedEnterEvent(event: PlayerBedEnterEvent) {
        if (console.stage == GameStage.PROCESSING
            && console.gameRules.getRuleValue(RuleKey.HUNTER_INTENTIONAL)
            && console.isHunter(event.player)
            && event.bedEnterResult == PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE
        ) {
            // ALLOW 玩家入睡。这似乎是唯一阻止床爆炸的方法。
            event.setUseBed(Event.Result.ALLOW)
        }
    }

    /**
     * 处理玩家使用重生锚
     */
    @EventHandler
    fun onItemUse(event: PlayerInteractEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND || console.stage != GameStage.PROCESSING) return
        val block = event.clickedBlock ?: return
        if (block.world == console.nether || block.type != Material.RESPAWN_ANCHOR) return
        if (!console.gameRules.getRuleValue(RuleKey.HUNTER_INTENTIONAL) && console.isHunter(event.player)) {
            event.setUseInteractedBlock(Event.Result.DENY)
        }
    }

}
