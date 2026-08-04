package xyz.fortern.minehunt.mode.manhunt

import org.bukkit.Material
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Blaze
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Enderman
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PiglinBarterEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GameOutcome
import xyz.fortern.minehunt.game.GamePhase
import xyz.fortern.minehunt.record.FinishType
import java.util.concurrent.ThreadLocalRandom

/**
 * 只属于 Manhunt 的 Bukkit 事件适配层。
 *
 * [GameManager] 只在 Manhunt 被选中期间注册该监听器；处理器仍会校验当前模式，
 * 避免模式切换边界上的事件串扰。
 */
class ManhuntListener(
    private val gameManager: GameManager,
) : Listener {

    /**
     * 猎人重生时给予追踪指南针
     */
    @EventHandler
    fun onPlayerSpawn(event: PlayerRespawnEvent) {
        val game = gameManager.currentMode as? ManhuntGame ?: return
        game.giveCompassIfNeed(event.player)
    }

    /**
     * 玩家丢弃物品时，阻止玩家丢弃猎人指南针，并将追踪目标切换到下一个
     */
    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        val game = gameManager.currentMode as? ManhuntGame ?: return
        val itemStack = event.itemDrop.itemStack
        if (!game.isHunterCompass(itemStack)) return

        game.trackNextPlayer(event.player)
        event.isCancelled = true
    }

    /**
     * 玩家想要移动时，在特定情况下阻止玩家移动
     */
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 暂且通过取消事件的方法阻止玩家移动
        if (gameManager.phase != GamePhase.RUNNING) return
        val game = gameManager.currentMode as? ManhuntGame ?: return

        val player = event.player
        // 猎人等待出生时，或等待复活时，阻止其移动
        if (game.getFaction(player) == ManhuntGame.Faction.HUNTER) {
            if (game.waitHunterSpawning(player) || game.isRespawning(player))
                event.isCancelled = true
        }
    }

    /**
     * 玩家想要传送时，在特定情况下阻止玩家传送
     */
    @EventHandler
    fun onHunterReadyTP(event: PlayerTeleportEvent) {
        val game = gameManager.currentMode as? ManhuntGame ?: return
        val player = event.player
        if (game.getFaction(player) == ManhuntGame.Faction.HUNTER
            && game.isRespawning(player)
            && event.cause != PlayerTeleportEvent.TeleportCause.PLUGIN
        ) {
            event.isCancelled = true
        }
    }

    /**
     * 处理玩家死亡事件
     */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val game = gameManager.currentMode as? ManhuntGame ?: return
        game.handlePlayerDeath(event.entity)
    }

    /**
     * 处理末影龙死亡以及增加速通战利品
     */
    @EventHandler
    fun onDragonDeath(event: EntityDeathEvent) {
        if (gameManager.phase != GamePhase.RUNNING) return
        val game = gameManager.currentMode as? ManhuntGame ?: return
        val entity = event.entity
        if (entity is EnderDragon) {
            gameManager.finish(GameOutcome(ManhuntGame.ROLE_SPEEDRUNNER, FinishType.FINISHED))
            return
        }
        // 是否给予更多速通相关的战利品
        if (!game.gameRules.getRuleValue(ManhuntRuleKeys.SPEEDRUN_LOOT_UP)) {
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
        if (gameManager.phase != GamePhase.RUNNING) return
        val game = gameManager.currentMode as? ManhuntGame ?: return
        if (!game.gameRules.getRuleValue(ManhuntRuleKeys.SPEEDRUN_LOOT_UP)) return

        if (ThreadLocalRandom.current().nextInt(10) < 3) {
            event.outcome.add(ItemStack(Material.ENDER_PEARL))
        }
    }

    /**
     * 监听传送门传送事件。改变维度时，记录一些信息。
     */
    @EventHandler
    fun onPlayerChangeWorld(event: PlayerPortalEvent) {
        if (gameManager.phase != GamePhase.RUNNING) return
        val game = gameManager.currentMode as? ManhuntGame ?: return

        // 我们用了Kotlin有了更装B的写法
        event.from.world?.let {
            game.recordLocAtPortal(event.player, event.from, event.to)
        }
    }

    /**
     * 处理玩家使用床
     */
    @EventHandler
    fun onPlayerBedEnterEvent(event: PlayerBedEnterEvent) {
        val game = gameManager.currentMode as? ManhuntGame ?: return
        if (gameManager.phase == GamePhase.RUNNING
            && !game.gameRules.getRuleValue(ManhuntRuleKeys.HUNTER_INTENTIONAL)
            && game.getFaction(event.player) == ManhuntGame.Faction.HUNTER
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
        if (event.hand == EquipmentSlot.OFF_HAND || gameManager.phase != GamePhase.RUNNING) return
        val game = gameManager.currentMode as? ManhuntGame ?: return
        val block = event.clickedBlock ?: return
        if (block.world == game.nether || block.type != Material.RESPAWN_ANCHOR) return
        if (!game.gameRules.getRuleValue(ManhuntRuleKeys.HUNTER_INTENTIONAL) && game.getFaction(event.player) == ManhuntGame.Faction.HUNTER) {
            event.setUseInteractedBlock(Event.Result.DENY)
        }
    }

    /**
     * arrow 射入实体事件
     */
    @EventHandler
    fun onArrow(event: ProjectileHitEvent) {
        val game = gameManager.currentMode as? ManhuntGame ?: return
        event.hitEntity ?: return
        val arrow = event.entity
        val shooter = arrow.shooter
        if (shooter == null || shooter !is Player || arrow !is AbstractArrow || arrow is Trident) return
        game.onPlayerArrowHit(shooter)
    }

}
