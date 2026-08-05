package xyz.fortern.minehunt.mode.manhunt

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.apache.commons.lang3.time.DurationFormatUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.Statistic
import org.bukkit.World
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Blaze
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Enderman
import org.bukkit.entity.EntityType
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
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.game.CompletedGameRecord
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GameOutcome
import xyz.fortern.minehunt.game.GamePhase
import xyz.fortern.minehunt.game.Lobby
import xyz.fortern.minehunt.mode.manhunt.record.MinehuntRecord
import xyz.fortern.minehunt.mode.manhunt.record.PlayerInMinehunt
import xyz.fortern.minehunt.record.FactionInfo
import xyz.fortern.minehunt.record.FinishType
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import xyz.fortern.minehunt.rule.RuleKey
import xyz.fortern.minehunt.util.foodStats
import xyz.fortern.minehunt.util.oreStats
import xyz.fortern.minehunt.util.toolStats
import xyz.fortern.minehunt.util.weaponStats
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import xyz.fortern.minehunt.game.GameMode as RuntimeGameMode
import xyz.fortern.minehunt.record.GameMode as GameModeId

/**
 * Manhunt 模式的运行时实现。
 *
 * 该类维护猎人追踪、速通者淘汰、模式规则和统计记录；阶段转换、投票及任务清理由
 * [xyz.fortern.minehunt.game.GameManager] 负责。
 */
class ManhuntGame(
    private val gameManager: GameManager,
    private val plugin: JavaPlugin,
    private val adventure: BukkitAudiences,
) : RuntimeGameMode {

    /** Manhunt 准备阶段的成员和角色状态。 */
    private val lobby = Lobby()

    override val id = GameModeId.MANHUNT
    override val listener = ManhuntListener()
    override val roles = listOf(ROLE_HUNTER, ROLE_SPEEDRUNNER, ROLE_AUDIENCE)
    override val spectatorRole = ROLE_AUDIENCE
    override val rules = ManhuntRules()
    override val specialItems = listOf(SPECIAL_ITEM_COMPASS)

    val gameRules: ManhuntRules
        get() = rules

    // =========== 游戏流程 start ===========

    /**
     * 首次进入下界的时间
     */
    var firstTimeInNether: Instant? = null
        private set

    /**
     * 首次进入末地的时间
     */
    var firstTimeInTheEnd: Instant? = null
        private set

    /**
     * 首个进入下界的玩家
     */
    var firstPlayerInNether: Player? = null
        private set

    /**
     * 首个进入末地的玩家
     */
    var firstPlayerInTheEnd: Player? = null
        private set

    // =========== 游戏流程 end ===========

    // =========== 游戏内数据 start ===========
    /**
     * 全部的游戏规则
     */
    /**
     * 计分板
     */
    private val scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard

    /**
     * 主世界
     */
    val overworld: World

    /**
     * 下界
     */
    val nether: World

    /**
     * 末地
     */
    val theEnd: World

    /**
     * 世界种子
     */
    val worldSeeds: Map<String, Long>

    /**
     * 速通者队伍
     */
    val speedrunnerTeam: Team

    /**
     * 猎人队伍
     */
    val hunterTeam: Team

    /**
     * 观众队伍
     */
    val audienceTeam: Team

    // 我们维护自己的玩家集合

    /**
     * 速通者列表
     */
    private val speedrunnerSet: MutableSet<UUID> = HashSet()

    /**
     * 猎人玩家集合
     */
    private val hunterSet: MutableSet<UUID> = HashSet()

    /**
     * 淘汰玩家集合
     */
    private val outPlayers: MutableSet<UUID> = HashSet()

    /**
     * 速通者列表，用于指南针指向的遍历
     */
    private lateinit var speedrunnerList: List<UUID>

    /**
     * 速通者离开主世界时最后的位置
     */
    private val playerLocInWorld: MutableMap<UUID, Location> = HashMap()

    /**
     * 速通者离开下界时最后的位置
     */
    private val playerLocInNether: MutableMap<UUID, Location> = HashMap()

    /**
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    private val trackRunnerMap: MutableMap<UUID, Int> = ConcurrentHashMap()

    /**
     * 猎人的指南针标记
     */
    private val compassFlag = "compassFlag"

    /**
     * 猎人指南针物品
     *
     * 出生与复活是唯一获取此物品的方法
     */
    private val hunterCompass: ItemStack = ItemStack(Material.COMPASS).apply {
        // 最大堆叠数设为1
        val itemMeta = this.itemMeta!!
        itemMeta.setMaxStackSize(1)
        // 设置名称
        itemMeta.setDisplayName("${ChatColor.GOLD}Hunter Compass")
        // 设置Lore
        itemMeta.lore = listOf(
            // 第一个Lore用于标记这个指南针
            "${ChatColor.GRAY}${compassFlag}",
            "${ChatColor.GRAY}右键使用或扔出以切换目标",
        )
        // 添加附魔：消失诅咒
        itemMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, false)
        this.itemMeta = itemMeta
    }

    /**
     * 玩家箭矢命中次数
     */
    private val arrowHits: MutableMap<UUID, Int> = HashMap()

    // =========== 游戏内数据 end ===========

    // =========== Bukkit Task start ===========

    /**
     * 猎人重生Task，保留这些引用方便在游戏结束时取消这些任务
     */
    private val hunterRespawnTasks: MutableMap<UUID, BukkitTask> = HashMap()

    /**
     * 猎人出生倒计时
     */
    private var hunterSpawnCD: BukkitTask? = null

    /**
     * 指南针刷新任务
     */
    private var compassRefreshTask: BukkitTask? = null

    /** 延迟到当前 Bukkit 事件结束后执行的游戏结束任务。 */
    private var finishTask: BukkitTask? = null

    // bukkit task end

    companion object {
        private const val RULE_LIST = "rule-list"
        private const val GAME_RESULT = "game-result"
        const val ROLE_HUNTER = "hunter"
        const val ROLE_SPEEDRUNNER = "speedrunner"
        const val ROLE_AUDIENCE = "audience"
        const val SPECIAL_ITEM_COMPASS = "compass"
    }

    init {
        val worlds = Bukkit.getWorlds()
        overworld = worlds[0]
        val overworldName = overworld.name
        var netherTmp: World? = null
        var theEndTmp: World? = null
        val seeds = HashMap<String, Long>()
        worlds.forEach {
            if (it.name == "${overworldName}_nether") {
                netherTmp = it
            } else if (it.name == "${overworldName}_the_end") {
                theEndTmp = it
            }
            seeds[it.name] = it.seed

        }
        if (netherTmp == null || theEndTmp == null) {
            throw RuntimeException("需要[下界]与[末地]维度才能进行游戏")
        }
        nether = netherTmp
        theEnd = theEndTmp
        worldSeeds = seeds

        // 初始化计分板
        initScoreboard()

        // 初始化 Minecraft 游戏规则
        overworld.worldBorder.size = 32.0
        overworld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        overworld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        overworld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        overworld.setGameRule(GameRule.SPAWN_RADIUS, 0)
        overworld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        overworld.difficulty = Difficulty.HARD

        // 初始化队伍
        val scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard

        scoreboard.getTeam(Faction.SPEEDRUN.name)?.unregister()
        speedrunnerTeam = scoreboard.registerNewTeam(Faction.SPEEDRUN.name).also {
            it.color = ChatColor.BLUE
            it.prefix = "[速通者]"
        }
        scoreboard.getTeam(Faction.HUNTER.name)?.unregister()
        hunterTeam = scoreboard.registerNewTeam(Faction.HUNTER.name).also {
            it.color = ChatColor.RED
            it.prefix = "[猎人]"
        }
        scoreboard.getTeam("AUDIENCE")?.unregister()
        audienceTeam = scoreboard.registerNewTeam("AUDIENCE").also {
            it.color = ChatColor.GRAY
            it.prefix = "[观众]"
        }
    }

    /**
     * 获取玩家所在的阵营
     */
    fun getFaction(player: OfflinePlayer): Faction? {
        return if (gameManager.phase == GamePhase.RUNNING || gameManager.phase == GamePhase.ENDING || gameManager.phase == GamePhase.SAVING || gameManager.phase == GamePhase.FINISHED) {
            if (speedrunnerSet.contains(player.uniqueId)) {
                Faction.SPEEDRUN
            } else if (hunterSet.contains(player.uniqueId)) {
                Faction.HUNTER
            } else {
                null
            }
        } else {
            when (lobby.member(player.uniqueId)?.role) {
                ROLE_SPEEDRUNNER -> Faction.SPEEDRUN
                ROLE_HUNTER -> Faction.HUNTER
                else -> null
            }
        }
    }

    private fun isParticipantRole(role: String): Boolean = role == ROLE_HUNTER || role == ROLE_SPEEDRUNNER

    override fun assignRole(player: Player, role: String): Boolean {
        if (gameManager.phase != GamePhase.LOBBY || role !in roles) return false
        lobby.assign(player.uniqueId, player.name, role)
        hunterTeam.removeEntry(player.name)
        speedrunnerTeam.removeEntry(player.name)
        audienceTeam.removeEntry(player.name)
        when (role) {
            ROLE_HUNTER -> {
                hunterTeam.addEntry(player.name)
                adventure.player(player).sendMessage(Component.text("你已加入${hunterTeam.color}[猎人]"))
            }

            ROLE_SPEEDRUNNER -> {
                speedrunnerTeam.addEntry(player.name)
                adventure.player(player).sendMessage(Component.text("你已加入${speedrunnerTeam.color}[速通者]"))
            }

            ROLE_AUDIENCE -> {
                audienceTeam.addEntry(player.name)
                adventure.player(player).sendMessage(Component.text("你已加入${audienceTeam.color}[观众]"))
            }
        }
        return true
    }

    private fun removeFromLobby(player: Player) {
        lobby.remove(player.uniqueId)
        hunterTeam.removeEntry(player.name)
        speedrunnerTeam.removeEntry(player.name)
        audienceTeam.removeEntry(player.name)
    }

    override fun onPlayerQuit(player: Player) {
        if (gameManager.phase != GamePhase.COUNTDOWN) return
        val role = lobby.member(player.uniqueId)?.role ?: return
        if (isParticipantRole(role)) gameManager.interruptCountdown()
        removeFromLobby(player)
    }

    /**
     * 重进游戏
     */
    override fun rejoin(player: Player) {
        if (gameManager.phase != GamePhase.RUNNING) {
            return
        }
        if (hunterSet.contains(player.uniqueId)) {
            hunterTeam.addEntry(player.name)
            adventure.player(player).sendMessage(Component.text("你已加入${hunterTeam.color}[猎人]"))
            // 如果猎人在死亡后离开了游戏，导致没有切回生存模式，这里再检测一次
            if (!hunterRespawnTasks.containsKey(player.uniqueId) && player.gameMode != GameMode.SURVIVAL) {
                player.gameMode = GameMode.SURVIVAL
            }
        } else if (speedrunnerSet.contains(player.uniqueId)) {
            speedrunnerTeam.addEntry(player.name)
            adventure.player(player).sendMessage(Component.text("你已加入${speedrunnerTeam.color}[速通者]"))
        } else {
            audienceTeam.addEntry(player.name)
            adventure.player(player).sendMessage(Component.text("你已加入${audienceTeam.color}[观众]"))
            player.gameMode = GameMode.SPECTATOR
        }
    }

    override fun validateStart(): String? {
        val onlineSpeedrunners = lobby.members(ROLE_SPEEDRUNNER).count { Bukkit.getPlayer(it.uniqueId) != null }
        return if (onlineSpeedrunners == 0) "速通者需要至少一位玩家" else null
    }

    override fun participants(): Set<UUID> =
        lobby.allMembers()
            .filter { isParticipantRole(it.role) && Bukkit.getPlayer(it.uniqueId) != null }
            .mapTo(LinkedHashSet()) { it.uniqueId }

    override fun stopVoters(): Set<UUID> = gameManager.participants - outPlayers

    /**
     * 开始游戏
     *
     * 游戏阶段由 COUNTDOWN 变为 PROCESSING
     */
    override fun start() {
        // 初始化模式相关的数据
        firstTimeInNether = null
        firstTimeInTheEnd = null
        firstPlayerInNether = null
        firstPlayerInTheEnd = null
        speedrunnerSet.clear()
        hunterSet.clear()
        outPlayers.clear()
        trackRunnerMap.clear()
        playerLocInWorld.clear()
        playerLocInNether.clear()
        arrowHits.clear()

        // 修改游戏规则
        overworld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        overworld.setGameRule(GameRule.DO_WEATHER_CYCLE, true)
        overworld.setGameRule(GameRule.DO_MOB_SPAWNING, true)
        overworld.setGameRule(GameRule.KEEP_INVENTORY, false)
        overworld.setGameRule(GameRule.SPAWN_RADIUS, 10)
        overworld.difficulty = Difficulty.HARD
        this.overworld.worldBorder.size = 9999999.0

        val spawnLocation = overworld.spawnLocation

        // 重置所有玩家状态
        Bukkit.getOnlinePlayers().forEach {
            it.gameMode = GameMode.SPECTATOR
            it.health = 20.0
            it.inventory.clear()
            it.saturation = 20.0f
            it.foodLevel = 20
            it.level = 0
            it.teleport(spawnLocation)
            // TODO Spigot没有重置统计信息的API，将不可避免地使用反射
            // 考虑到游戏开局是冒险模式，先手动重置击杀类数据
            it.setStatistic(Statistic.ENTITY_KILLED_BY, EntityType.PLAYER, 0)
            it.setStatistic(Statistic.KILL_ENTITY, EntityType.PLAYER, 0)
            it.setStatistic(Statistic.USE_ITEM, Material.BOW, 0)
            it.setStatistic(Statistic.USE_ITEM, Material.CROSSBOW, 0)
        }

        // 固定speedrunnerSet和speedrunnerList，速通者状态修改
        lobby.members(ROLE_SPEEDRUNNER).forEach { member ->
            Bukkit.getPlayer(member.uniqueId)?.let {
                speedrunnerSet.add(it.uniqueId)
                it.gameMode = GameMode.SURVIVAL
            }
        }
        if (speedrunnerSet.isEmpty()) throw RuntimeException("No Speedrunner")
        speedrunnerList = speedrunnerSet.toList()

        // 固定hunterSet，将猎人传送到世界底部，且指南针开始有所指向
        lobby.members(ROLE_HUNTER).forEach { member ->
            Bukkit.getPlayer(member.uniqueId)?.let {
                hunterSet.add(it.uniqueId)
                it.teleport(Location(overworld, 0.0, overworld.minHeight - 2.0, 0.0))
                trackRunnerMap[it.uniqueId] = 0
            }
        }

        val pvp = gameRules.getRuleValue(ManhuntRuleKeys.FRIENDLY_FIRE)
        speedrunnerTeam.setAllowFriendlyFire(pvp)
        hunterTeam.setAllowFriendlyFire(pvp)

        val refreshCompasses = {
            hunterSet.forEach {
                val hunter = Bukkit.getPlayer(it) ?: return@forEach
                val i = (trackRunnerMap[it] ?: return@forEach) % speedrunnerList.size
                // This means an offline speedrunner remains at their last known target.
                val speedrunner = Bukkit.getPlayer(speedrunnerList[i]) ?: return@forEach
                refreshCompassTrack(hunter, speedrunner)
            }
        }

        // 猎人出生倒计时Task
        hunterSpawnCD = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // 猎人设置初始状态
            hunterSet.forEach {
                val player = Bukkit.getPlayer(it) ?: return@forEach
                adventure.player(player).sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                player.gameMode = GameMode.SURVIVAL
                player.teleport(spawnLocation)
                player.inventory.addItem(hunterCompass)
            }

            // “自动更新指南针”任务开始运行
            compassRefreshTask = plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable(refreshCompasses),
                0,
                5,
            )

            // 通知速通者
            speedrunnerSet.forEach {
                adventure.player(it).sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED))
            }
            hunterSpawnCD = null
        }, gameRules.getRuleValue(ManhuntRuleKeys.HUNTER_READY_CD) * 20L)

        scoreboard.getObjective("rule-list")!!.displaySlot = null
        Bukkit.getOnlinePlayers().forEach { player ->
            adventure.player(player).sendMessage(Component.text("--------游戏开始-------", NamedTextColor.GREEN))
        }
    }

    /**
     * 结束处理
     */
    override fun finish(outcome: GameOutcome): CompletedGameRecord {
        val winner = outcome.winnerRole?.let(Faction::fromRole)
        val finishType = outcome.finishType
        val startTime = checkNotNull(gameManager.startedAt) { "Game start time is not initialized" }
        val endTime = gameManager.endedAt ?: Instant.now()

        hunterSpawnCD = null
        compassRefreshTask = null
        hunterRespawnTasks.clear()
        // 所有人设为生存模式
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------游戏结束--------", NamedTextColor.GREEN))
            if (winner != null) {
                adventure.player(it).sendMessage(Component.text("获胜者：${winner.displayName}", NamedTextColor.GOLD))
            } else {
                adventure.player(it).sendMessage(Component.text("没有赢家", NamedTextColor.GOLD))
            }
            it.gameMode = GameMode.SURVIVAL
        }
        // 有仇的报仇
        speedrunnerTeam.setAllowFriendlyFire(true)
        hunterTeam.setAllowFriendlyFire(true)

        // 猎人阵营信息
        val factionInfo1 = FactionInfo(
            Faction.HUNTER.name,
            hunterTeam.color,
            if (winner == null) 0 else if (winner == Faction.HUNTER) 1 else 2,
            hunterSet.toList()
        )

        // 速通者阵营信息
        val factionInfo2 = FactionInfo(
            Faction.SPEEDRUN.name,
            speedrunnerTeam.color,
            if (winner == null) 0 else if (winner == Faction.SPEEDRUN) 1 else 2,
            speedrunnerSet.toList()
        )
        val modeDetails = MinehuntRecord(
            firstTimeInNether,
            firstTimeInTheEnd,
            firstPlayerInNether?.uniqueId,
            firstPlayerInTheEnd?.uniqueId,
        )

        val gameRecord = GameRecord(
            0,
            UUID.randomUUID(),
            GameModeId.MANHUNT,
            startTime,
            endTime,
            Duration.between(startTime, endTime),
            finishType,
            listOf(factionInfo1, factionInfo2).sortedBy { it.rank },
            worldSeeds[overworld.name]!!,
            worldSeeds,
            modeDetails,
        )

        // 计分板显示
        overScoreboard(gameRecord, winner)

        val resultInfo = Component.text()
            .append(Component.text("=====对局信息=====", NamedTextColor.GREEN))
            .appendNewline()
            .append(Component.text("对局ID: 保存中"))
            .appendNewline()
            .append(
                Component.text(
                    "开始时间: ${startTime.atZone(ZoneId.systemDefault()).format(formatter)}"
                )
            )
            .appendNewline()
            .append(Component.text("持续时长: ${DurationFormatUtils.formatDurationHMS(gameRecord.duration.seconds * 1000L)}"))
            .appendNewline()
            .append(Component.text("胜者: ${winner?.displayName}"))

        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(resultInfo)
        }

        // 玩家在该模式下的数据
        val playerRecords = (hunterSet + speedrunnerSet).map { uuid ->
            val player = Bukkit.getOfflinePlayer(uuid)
            // 工具类
            val toolsTmpMap = toolStats.associateWith { player.getStatistic(Statistic.USE_ITEM, it) }.filter { (_, n) -> n > 0 }
            // 武器类
            val weaponsTmpMap = weaponStats.associateWith { player.getStatistic(Statistic.USE_ITEM, it) }.filter { (_, n) -> n > 0 }
            // 食物类
            val foodTmpMap = foodStats.associateWith { player.getStatistic(Statistic.USE_ITEM, it) }.filter { (_, n) -> n > 0 }
            // 矿石类
            val oreTmpMap = oreStats.associateWith { player.getStatistic(Statistic.MINE_BLOCK, it) }.filter { (_, n) -> n > 0 }

            // 生物类
            val killEntity = mutableMapOf<EntityType, Int>()
            val killedByEntity = mutableMapOf<EntityType, Int>()
            EntityType.entries.forEach {
                if (it == EntityType.UNKNOWN) {
                    return@forEach
                }
                var n: Int = player.getStatistic(Statistic.KILL_ENTITY, it)
                if (n > 0) {
                    killEntity[it] = n
                }
                n = player.getStatistic(Statistic.ENTITY_KILLED_BY, it)
                if (n > 0) {
                    killedByEntity[it] = n
                }
            }
            val playerInfo = Component.text()
                .append(Component.text("=====你的数据=====", NamedTextColor.GREEN))
                .appendNewline()
                .append(Component.text("----击杀生物----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(
                    Component.text().also { text ->
                        if (killEntity.isEmpty()) {
                            text.append(Component.text("No data.")).appendNewline()
                            return@also
                        }
                        killEntity.forEach { (type, n) ->
                            text.append(Component.translatable(type.translationKey)).append(Component.text(": $n")).appendNewline()
                        }
                    }
                )
                .append(Component.text("----被生物击杀----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(
                    Component.text().also { text ->
                        if (killedByEntity.isEmpty()) {
                            text.append(Component.text("No data.")).appendNewline()
                            return@also
                        }
                        killedByEntity.forEach { (type, n) ->
                            text.append(Component.translatable(type.translationKey)).append(Component.text(": $n")).appendNewline()
                        }
                    }
                )
                .append(Component.text("----工具使用----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(
                    Component.text().also { text ->
                        if (toolsTmpMap.isEmpty()) {
                            text.append(Component.text("No data.")).appendNewline()
                            return@also
                        }
                        toolsTmpMap.forEach { (type, n) ->
                            text.append(Component.translatable(type.translationKey)).append(Component.text(": $n"))
                                .appendNewline()
                        }
                    }
                )
                .append(Component.text("----武器使用----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(
                    Component.text().also { text ->
                        if (weaponsTmpMap.isEmpty()) {
                            text.append(Component.text("No data.")).appendNewline()
                            return@also
                        }
                        weaponsTmpMap.forEach { (type, n) ->
                            text.append(Component.translatable(type.translationKey)).append(Component.text(": $n")).appendNewline()
                        }
                        val shootTimes = weaponsTmpMap.getOrDefault(Material.BOW, 0) + weaponsTmpMap.getOrDefault(Material.CROSSBOW, 0)
                        if (shootTimes == 0) {
                            return@also
                        }
                        val hitRate = String.format("%.2f%%", arrowHits.getOrDefault(uuid, 0) * 100.0 / shootTimes)
                        text.append(Component.text("箭矢命中率: $hitRate")).appendNewline()
                    }
                )
                .append(Component.text("----食物食用----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(
                    Component.text().also { text ->
                        if (foodTmpMap.isEmpty()) {
                            text.append(Component.text("No data.")).appendNewline()
                            return@also
                        }
                        foodTmpMap.forEach { (type, n) ->
                            text.append(Component.translatable(type.translationKey)).append(Component.text(": $n")).appendNewline()
                        }
                    }.build()
                )
                .append(Component.text("----矿石开采----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(
                    Component.text().also { text ->
                        if (oreTmpMap.isEmpty()) {
                            text.append(Component.text("No data.")).appendNewline()
                            return@also
                        }
                        oreTmpMap.forEach { (type, n) ->
                            text.append(Component.translatable(type.translationKey)).append(Component.text(": $n")).appendNewline()
                        }
                    }.build()
                )
                .append(Component.text("----End.----", NamedTextColor.YELLOW))
            adventure.player(uuid).sendMessage(playerInfo)
            val rank = if (winner == null) 0 else if (getFaction(player) == winner) 1 else 2
            PlayerInGame(
                player.uniqueId,
                0,
                rank,
                PlayerInMinehunt(
                    killEntity.mapKeys { it.key.key.toString() },
                    killedByEntity.mapKeys { it.key.key.toString() },
                    foodTmpMap.mapKeys { it.key.key.toString() },
                    toolsTmpMap.mapKeys { it.key.key.toString() },
                    weaponsTmpMap.mapKeys { it.key.key.toString() },
                    oreTmpMap.mapKeys { it.key.key.toString() })
            )
        }
        return CompletedGameRecord(gameRecord, playerRecords)
    }

    /**
     * 判断物品是否为猎人指南针
     */
    fun isHunterCompass(itemStack: ItemStack): Boolean {
        val lore = itemStack.itemMeta!!.lore
        if (lore.isNullOrEmpty()) return false
        val loreContent = lore[0]
        return loreContent.contains(compassFlag)
    }

    /**
     * 让该玩家所追踪的目标切换到下一个
     */
    fun trackNextPlayer(hunter: Player) {
        if (gameManager.phase != GamePhase.RUNNING) return
        val i = trackRunnerMap[hunter.uniqueId] ?: return
        if (speedrunnerList.isEmpty()) return

        var nextTrackRunner: OfflinePlayer?

        var j = i
        while (true) {
            j++
            j %= speedrunnerList.size
            if (i == j) {
                // 极端情况，所有速通者都掉线了，或者只有1个速通者
                // 则追踪到一开始追踪到的那个人
                nextTrackRunner = Bukkit.getOfflinePlayer(speedrunnerList[j])
                break
            }
            val uuid = speedrunnerList[j]
            nextTrackRunner = Bukkit.getOfflinePlayer(uuid)
            if (!outPlayers.contains(uuid) && nextTrackRunner.isOnline) {
                // 有符合条件的下一个目标
                break
            }
        }

        trackRunnerMap[hunter.uniqueId] = j
        // hunter操作指南针时立即刷新位置
        if (nextTrackRunner.isOnline && !outPlayers.contains(nextTrackRunner.uniqueId)) {
            refreshCompassTrack(hunter, nextTrackRunner.player!!)
        }
        adventure.player(hunter).sendActionBar(Component.text("指向 ${nextTrackRunner.name}"))
    }

    /**
     * 使hunter的指南针指向此时speedrunner的位置
     */
    private fun refreshCompassTrack(hunter: Player, speedrunner: Player) {
        val items = hunter.inventory.all(Material.COMPASS)
        items.firstNotNullOfOrNull { (_, itemStack) ->
            val lore = itemStack.itemMeta!!.lore
            if (lore.isNullOrEmpty()) return@firstNotNullOfOrNull

            val loreContent = lore[0]
            if (loreContent.contains(compassFlag)) {
                // 让指南针指向某一个猎人
                val meta = itemStack.itemMeta as CompassMeta
                meta.isLodestoneTracked = false
                if (hunter.world.uid == speedrunner.world.uid) {
                    meta.lodestone = speedrunner.location
                } else if (hunter.world.uid == overworld.uid) {
                    meta.lodestone = playerLocInWorld[speedrunner.uniqueId]
                } else if (hunter.world.uid == nether.uid) {
                    meta.lodestone = playerLocInNether[speedrunner.uniqueId]
                } else {
                    meta.lodestone = null
                }
                itemStack.itemMeta = meta
                itemStack.amount = 1
            }
        }
    }

    /**
     * 处理玩家死亡
     */
    fun handlePlayerDeath(player: Player) {
        if (gameManager.phase != GamePhase.RUNNING) return

        val uuid = player.uniqueId
        val faction = getFaction(player)
        if (faction == Faction.SPEEDRUN) {
            // 速通者置为旁观者模式，加入淘汰名单
            player.gameMode = GameMode.SPECTATOR
            outPlayers.add(uuid)
            // 如给所有hunter都淘汰，则游戏结束
            if (outPlayers.size == speedrunnerSet.size) {
                finishTask?.cancel()
                finishTask = plugin.server.scheduler.runTask(plugin, Runnable {
                    finishTask = null
                    gameManager.finish(GameOutcome(ROLE_HUNTER, FinishType.FINISHED))
                })
            }
        } else if (faction == Faction.HUNTER) {
            // 猎人置为旁观者模式，稍后复活
            player.gameMode = GameMode.SPECTATOR
            adventure.player(player).sendMessage(Component.text("等待重生"))
            hunterRespawnTasks.remove(uuid)?.cancel()
            hunterRespawnTasks[uuid] = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                player.gameMode = GameMode.SURVIVAL
                hunterRespawnTasks.remove(uuid)
            }, gameRules.getRuleValue(ManhuntRuleKeys.HUNTER_RESPAWN_CD) * 20L)
        }
    }

    private fun initScoreboard() {
        // TODO 将使用更好的计分板API

        //清除旧的计分板信息
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.getObjective(RULE_LIST)?.unregister()

        //设置新的计分板信息
        val ruleListObjective = scoreboard.registerNewObjective(
            RULE_LIST,
            Criteria.DUMMY,
            "${ChatColor.DARK_AQUA}游戏规则"
        )
        val rules = gameRules.getAllRules()
        rules.onEachIndexed { i, entry ->
            val stupidSpigotEntry = "${ChatColor.GOLD}${entry.key.displayName}"
            val score = ruleListObjective.getScore(stupidSpigotEntry)
            score.score = rules.size - i

            val teamForOneRule = scoreboard.registerNewTeam(stupidSpigotEntry)
            teamForOneRule.addEntry(stupidSpigotEntry)
            teamForOneRule.suffix = ": ${ChatColor.GREEN}${entry.value}"
        }
        ruleListObjective.displaySlot = DisplaySlot.SIDEBAR
    }

    /**
     * 更新规则时刷新对应规则项的后缀
     */
    override fun onRuleChanged(rule: RuleKey<*>) = refreshEntry(rule)

    private fun refreshEntry(ruleKey: RuleKey<*>) {
//        val teamForOneRule = scoreboard.getTeam(ruleKey.name) ?: return
        val teamForOneRule = scoreboard.getTeam("${ChatColor.GOLD}${ruleKey.displayName}") ?: return
        teamForOneRule.suffix = ": ${ChatColor.GREEN}${gameRules.getRuleValue(ruleKey)}"
    }

    private fun overScoreboard(gameRecord: GameRecord, winner: Faction?) {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.getObjective(GAME_RESULT)?.unregister()
        val objective = scoreboard.registerNewObjective(
            GAME_RESULT,
            Criteria.DUMMY,
            "${ChatColor.DARK_AQUA}猎人模式 Game Over"
        )
        objective.getScore("${ChatColor.YELLOW}====基本信息====").score = 15
        objective.getScore(if (gameRecord.id == 0) "对局ID: 保存中" else "对局ID: ${gameRecord.id}").score = 14
        objective.getScore("开始时间: ${gameRecord.startTime.atZone(ZoneId.systemDefault()).format(formatter)}").score = 13
        objective.getScore("持续时长: ${DurationFormatUtils.formatDurationHMS(gameRecord.duration.toSeconds() * 1000L)}").score = 12
        objective.getScore("胜者: ${winner?.displayName}").score = 11
        val specificData = gameRecord.details as MinehuntRecord
        objective.getScore("${ChatColor.YELLOW}====对局阶段====").score = 10
        val time1 = specificData.firstTimeToNether
        val duration1 = if (time1 == null) gameRecord.duration else Duration.between(gameRecord.startTime, time1)
        objective.getScore("阶段一•主世界：${DurationFormatUtils.formatDurationHMS(duration1.toSeconds() * 1000L)}").score = 9
        objective.getScore("首个进入下界的玩家：${firstPlayerInNether?.name}").score = 8
        val time2 = specificData.firstTimeToTheEnd
        val duration2 =
            if (time1 == null) Duration.ZERO else if (time2 != null) Duration.between(time1, time2) else Duration.between(time1, gameRecord.endTime)
        objective.getScore("阶段二•下界：${DurationFormatUtils.formatDurationHMS(duration2.seconds * 1000L)}").score = 7
        objective.getScore("首个进入末地的玩家：${firstPlayerInTheEnd?.name}").score = 6
        val duration3 = if (time2 == null) Duration.ZERO else Duration.between(time2, gameRecord.endTime)
        objective.getScore("阶段三•末地：${DurationFormatUtils.formatDurationHMS(duration3.seconds * 1000L)}").score = 5
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    /**
     * 记录玩家进入传送门时的位置
     */
    fun recordLocAtPortal(player: Player, from: Location, to: Location?) {
        val world = from.world!!
        if (world.uid == overworld.uid) {
            playerLocInWorld[player.uniqueId] = from
        } else if (world.uid == nether.uid) {
            playerLocInNether[player.uniqueId] = from
        }
        if (to != null) {
            val toWorld = to.world!!
            if (toWorld == nether) {
                if (firstPlayerInNether == null) {
                    firstPlayerInNether = player
                    firstTimeInNether = Instant.now()
                }
            } else if (toWorld == theEnd) {
                if (firstPlayerInTheEnd == null) {
                    firstPlayerInTheEnd = player
                    firstTimeInTheEnd = Instant.now()
                }
            }
        }
    }

    /**
     * 给予猎人追踪指南针
     */
    fun giveCompassIfNeed(player: Player) {
        if (gameManager.phase == GamePhase.RUNNING && getFaction(player) == Faction.HUNTER) {
            val items = player.inventory.all(Material.COMPASS)
            var have = false
            for ((_, item) in items) {
                val lore = item.itemMeta!!.lore
                if (lore.isNullOrEmpty()) continue
                val loreContent = lore[0]
                if (loreContent.contains(compassFlag)) {
                    have = true
                    break
                }
            }
            if (!have) {
                player.inventory.addItem(hunterCompass)
            }
        }
    }

    /**
     * 玩家箭矢命中实体时进行记录
     */
    fun onPlayerArrowHit(shooter: Player) {
        if (gameManager.phase != GamePhase.RUNNING) return
        val faction = getFaction(shooter)
        if (faction == Faction.SPEEDRUN || faction == Faction.HUNTER) {
            val uniqueId = shooter.uniqueId
            arrowHits[uniqueId] = arrowHits.getOrDefault(uniqueId, 0) + 1
        }
    }

    /**
     * 玩家是否正在重生
     */
    fun isRespawning(player: Player): Boolean {
        return hunterRespawnTasks.containsKey(player.uniqueId)
    }

    /**
     * 猎人是否在等待出生
     */
    fun waitHunterSpawning(): Boolean {
        return hunterSpawnCD != null
    }

    /**
     * 猎人游戏阵营
     */
    enum class Faction(val role: String, val displayName: String) {
        HUNTER(ROLE_HUNTER, "Hunter"),
        SPEEDRUN(ROLE_SPEEDRUNNER, "Speedrunner");

        companion object {
            fun fromRole(role: String): Faction = entries.firstOrNull { it.role == role }
                ?: error("Unknown Manhunt winner role: $role")
        }
    }

    override fun giveSpecialItem(player: Player, item: String): Boolean {
        if (item != SPECIAL_ITEM_COMPASS) return false
        giveCompassIfNeed(player)
        return true
    }

    override fun cancelTasks() {
        hunterSpawnCD?.cancel()
        hunterSpawnCD = null
        compassRefreshTask?.cancel()
        compassRefreshTask = null
        finishTask?.cancel()
        finishTask = null
        hunterRespawnTasks.values.forEach(BukkitTask::cancel)
        hunterRespawnTasks.clear()
    }

    override fun close() {
        listOf(speedrunnerTeam, hunterTeam, audienceTeam).forEach { team ->
            runCatching(team::unregister)
        }
    }

    /**
     * 只属于 Manhunt 的 Bukkit 事件适配层。
     *
     * [GameManager] 只在 Manhunt 被选中期间注册该监听器，因此处理器可以直接访问外部模式实例，
     * 无需再次查询和转换当前模式。
     */
    inner class ManhuntListener : Listener {

        /**
         * 猎人重生时给予追踪指南针
         */
        @EventHandler
        fun onPlayerSpawn(event: PlayerRespawnEvent) {
            giveCompassIfNeed(event.player)
        }

        /**
         * 玩家丢弃物品时，阻止玩家丢弃猎人指南针，并将追踪目标切换到下一个
         */
        @EventHandler
        fun onDropItem(event: PlayerDropItemEvent) {
            val itemStack = event.itemDrop.itemStack
            if (!isHunterCompass(itemStack)) return

            trackNextPlayer(event.player)
            event.isCancelled = true
        }

        /**
         * 玩家想要移动时，在特定情况下阻止玩家移动
         */
        @EventHandler
        fun onPlayerMove(event: PlayerMoveEvent) {
            // 暂且通过取消事件的方法阻止玩家移动
            if (gameManager.phase != GamePhase.RUNNING) return

            val player = event.player
            // 猎人等待出生时，或等待复活时，阻止其移动
            if (getFaction(player) == Faction.HUNTER) {
                if (waitHunterSpawning() || isRespawning(player))
                    event.isCancelled = true
            }
        }

        /**
         * 玩家想要传送时，在特定情况下阻止玩家传送
         */
        @EventHandler
        fun onHunterReadyTP(event: PlayerTeleportEvent) {
            val player = event.player
            if (getFaction(player) == Faction.HUNTER
                && isRespawning(player)
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
            handlePlayerDeath(event.entity)
        }

        /**
         * 处理末影龙死亡以及增加速通战利品
         */
        @EventHandler
        fun onDragonDeath(event: EntityDeathEvent) {
            if (gameManager.phase != GamePhase.RUNNING) return
            val entity = event.entity
            if (entity is EnderDragon) {
                gameManager.finish(GameOutcome(ROLE_SPEEDRUNNER, FinishType.FINISHED))
                return
            }
            // 是否给予更多速通相关的战利品
            if (!gameRules.getRuleValue(ManhuntRuleKeys.SPEEDRUN_LOOT_UP)) {
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
            if (!gameRules.getRuleValue(ManhuntRuleKeys.SPEEDRUN_LOOT_UP)) return

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

            // 我们用了Kotlin有了更装B的写法
            event.from.world?.let {
                recordLocAtPortal(event.player, event.from, event.to)
            }
        }

        /**
         * 处理玩家使用床
         */
        @EventHandler
        fun onPlayerBedEnterEvent(event: PlayerBedEnterEvent) {
            if (gameManager.phase == GamePhase.RUNNING
                && !gameRules.getRuleValue(ManhuntRuleKeys.HUNTER_INTENTIONAL)
                && getFaction(event.player) == Faction.HUNTER
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
            val block = event.clickedBlock ?: return
            if (block.world == nether || block.type != Material.RESPAWN_ANCHOR) return
            if (!gameRules.getRuleValue(ManhuntRuleKeys.HUNTER_INTENTIONAL) && getFaction(event.player) == Faction.HUNTER) {
                event.setUseInteractedBlock(Event.Result.DENY)
            }
        }

        /**
         * arrow 射入实体事件
         */
        @EventHandler
        fun onArrow(event: ProjectileHitEvent) {
            event.hitEntity ?: return
            val arrow = event.entity
            val shooter = arrow.shooter
            if (shooter == null || shooter !is Player || arrow !is AbstractArrow || arrow is Trident) return
            onPlayerArrowHit(shooter)
        }
    }
}

val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
