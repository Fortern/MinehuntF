package xyz.fortern.minehunt

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.rule.GameRules
import xyz.fortern.minehunt.rule.RuleKey
import xyz.fortern.minehunt.util.serialize
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 游戏控制台
 */
class Console(
    private val plugin: JavaPlugin,
    private val adventure: BukkitAudiences,
) {

    /**
     * 全部的游戏规则
     */
    val gameRules = GameRules()

    /**
     * 游戏阶段
     */
    var stage: GameStage = GameStage.PREPARING
        private set

    /**
     * 计分板
     */
    private val scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard

    /**
     * 主世界
     */
    private val overworld = Bukkit.getWorld("world")!!

    /**
     * 下界
     */
    private val nether = Bukkit.getWorld("world_nether")!!

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
     * 观众玩家集合
     */
    private val audienceSet: MutableSet<UUID> = HashSet()

    /**
     * 淘汰玩家集合
     */
    private val outPlayers: MutableSet<UUID> = HashSet()

    /**
     * 终止游戏的投票统计
     */
    private val votingEndMap: MutableMap<UUID, Boolean> = HashMap()

    /**
     * 投票计数
     */
    private var votingCount: Int = 0

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

        itemMeta.setDisplayName(Component.text("Hunter Compass", NamedTextColor.GOLD).serialize())
        // 设置Lore
        itemMeta.lore = listOf(
            // 第一个Lore用于标记这个指南针
            Component.text(compassFlag, NamedTextColor.GRAY).serialize(),
            Component.text("右键使用或扔出以切换目标", NamedTextColor.GRAY).serialize()
        )
        // 添加附魔：消失诅咒
        itemMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, false)
        this.itemMeta = itemMeta
    }

    // bukkit task start

    /**
     * 猎人重生Task，保留这些引用方便在游戏结束时取消这些任务
     */
    private val hunterRespawnTasks: MutableMap<UUID, BukkitTask> = HashMap()

    /**
     * 游戏开始前的倒计时任务
     */
    var beginningCountdown: BukkitTask? = null
        private set

    /**
     * 猎人出生倒计时
     */
    private var hunterSpawnCD: BukkitTask? = null

    /**
     * 指南针刷新任务
     */
    private var compassRefreshTask: BukkitTask? = null

    /**
     * 投票倒计时
     */
    private var voteTask: BukkitTask? = null

    // bukkit task end

    init {
        initScoreboard()
        // 初始化 Minecraft 游戏规则
        overworld.worldBorder.size = 32.0
        overworld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        overworld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        overworld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        overworld.setGameRule(GameRule.KEEP_INVENTORY, true)
        overworld.setGameRule(GameRule.SPAWN_RADIUS, 0)
        overworld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        overworld.difficulty = Difficulty.HARD

        // 初始化队伍
        val scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard

        speedrunnerTeam = scoreboard.getTeam("speedrunner") ?: scoreboard.registerNewTeam("speedrunner")
        hunterTeam = scoreboard.getTeam("hunter") ?: scoreboard.registerNewTeam("hunter")
        audienceTeam = scoreboard.getTeam("spectator") ?: scoreboard.registerNewTeam("spectator")

        speedrunnerTeam.let { t ->
            t.color = ChatColor.BLUE
//            t.prefix = Component.text("[速通者] ", NamedTextColor.BLUE).serialize()
            t.prefix = "[速通者] "
            t.entries.forEach { speedrunnerTeam.removeEntry(it) }
        }
        hunterTeam.let { t ->
            t.color = ChatColor.RED
//            t.prefix = Component.text("[猎人] ", NamedTextColor.RED).serialize()
            t.prefix = "[猎人] "
            t.entries.forEach { hunterTeam.removeEntry(it) }
        }
        audienceTeam.let { t ->
            t.color = ChatColor.GRAY
//            t.prefix = Component.text("[观众] ", NamedTextColor.GRAY).serialize()
            t.prefix = "[观众] "
            t.entries.forEach { audienceTeam.removeEntry(it) }
        }
    }

    /**
     * 判断玩家是否为猎人
     */
    fun isHunter(player: Player): Boolean = hunterTeam.hasEntry(player.name)

    /**
     * 判断是否为观众
     */
    fun isSpectator(player: Player): Boolean = audienceTeam.hasEntry(player.name)

    /**
     * 判断是否为速通者
     */
    fun isSpeedrunner(player: Player): Boolean = speedrunnerTeam.hasEntry(player.name)

    /**
     * 加入猎人阵营
     */
    fun joinHunter(player: Player) {
        if (stage == GameStage.PREPARING && beginningCountdown == null) {
            hunterTeam.addEntry(player.name)
            adventure.player(player).sendMessage(Component.text("你已加入[猎人]"))
        }
    }

    /**
     * 加入速通者阵营
     */
    fun joinSpeedrunner(player: Player) {
        if (stage == GameStage.PREPARING && beginningCountdown == null) {
            speedrunnerTeam.addEntry(player.name)
            adventure.player(player).sendMessage(Component.text("你已加入[速通者]"))
        }
    }

    /**
     * 加入观众阵营
     */
    fun joinAudience(player: Player) {
        if (stage == GameStage.PREPARING) {
            audienceTeam.addEntry(player.name)
            adventure.player(player).sendMessage(Component.text("你已加入[观众]"))
        }
    }

    /**
     * 尝试开始游戏。如果满足条件，则返回空字符串，否则返回原因描述
     */
    fun tryStart(): String {
        if (speedrunnerTeam.size == 0) return "速通者需要至少一位玩家"

        // 以后可能有其他需要判断的情况

        countdownToStart()
        return ""
    }

    /**
     * 游戏开始前的倒计时
     */
    private fun countdownToStart() {
        beginningCountdown = Bukkit.getScheduler().runTaskTimer(plugin, object : Runnable {
            private var countdown = 6
            override fun run() {
                if (--countdown > 0) {
                    // 倒计时期间，每秒显示一次标题
                    Bukkit.getOnlinePlayers().forEach {
                        adventure.player(it).showTitle(
                            Title.title(
                                Component.text(countdown.toString(), NamedTextColor.DARK_PURPLE),
                                Component.text("开始倒计时", NamedTextColor.GRAY),
                                5,
                                20,
                                5
                            )
                        )
                    }
                } else {
                    // 倒计时结束后开始游戏
                    beginningCountdown!!.cancel()
                    beginningCountdown = null
                    start()
                }
            }
        }, 0, 20)
    }

    fun interruptCountdownToStart() {
        beginningCountdown?.cancel()
        beginningCountdown = null
    }

    /**
     * 开始游戏
     *
     * 游戏阶段由 PREPARING 变为 PROCESSING
     */
    private fun start() {
        // 速通者更改为生存模式，并加入speedrunnerList
        speedrunnerTeam.entries.forEach { entry ->
            Bukkit.getPlayer(entry)?.let { speedrunnerSet.add(it.uniqueId) }
        }
        if (speedrunnerSet.isEmpty()) throw RuntimeException("No Speedrunner")

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
        }

        // 速通者更改为生存模式，并加入speedrunnerList
        speedrunnerTeam.entries.forEach { entry ->
            Bukkit.getPlayer(entry)?.let { it.gameMode = GameMode.SURVIVAL }
        }
        speedrunnerList = speedrunnerSet.toList()

        // 将猎人传送到世界底部，且指南针开始有所指向
        hunterTeam.entries.forEach { entry ->
            Bukkit.getPlayer(entry)?.let {
                hunterSet.add(it.uniqueId)
                it.teleport(Location(overworld, 0.0, -64.0, 0.0))
                trackRunnerMap[it.uniqueId] = 0
            }
        }

        val pvp = gameRules.getRuleValue(RuleKey.FRIENDLY_FIRE)
        speedrunnerTeam.setAllowFriendlyFire(pvp)
        hunterTeam.setAllowFriendlyFire(pvp)

        // 创建指南针更新任务
        val compassTask = object : BukkitRunnable() {
            override fun run() {
                hunterSet.forEach {
                    val hunter = Bukkit.getPlayer(it) ?: return@forEach
                    val i = (trackRunnerMap[it] ?: return@forEach) % speedrunnerList.size
                    // hunter 正在追踪的 speedrunner
                    // 这意味着如果速通者掉线，指南针将指向掉线时的位置
                    val speedrunner = Bukkit.getPlayer(speedrunnerList[i]) ?: return@forEach
                    refreshCompassTrack(hunter, speedrunner)
                }
            }
        }

        // 猎人出生倒计时Task
        hunterSpawnCD = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // 猎人设置初始状态
            hunterSet.forEach {
                val player = Bukkit.getPlayer(it) ?: return@forEach
                adventure.player(player).sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                player.gameMode = GameMode.SURVIVAL
                player.teleport(spawnLocation)
                player.inventory.addItem(hunterCompass)
            }

            // “自动更新指南针”任务开始运行
            compassRefreshTask = compassTask.runTaskTimer(plugin, 0, 20)

            // 通知速通者
            speedrunnerSet.forEach {
                adventure.player(it).sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED))
            }
            hunterSpawnCD = null
        }, gameRules.getRuleValue(RuleKey.HUNTER_READY_CD) * 20L)

        scoreboard.getObjective("rule-list")!!.displaySlot = null
        stage = GameStage.PROCESSING
    }

    /**
     * 投票结束游戏
     */
    fun voteForStop(player: Player) {
        if (stage != GameStage.PROCESSING) {
            adventure.player(player).sendMessage(Component.text("只有游戏中才能投票", NamedTextColor.RED))
            return
        }
        if (!isHunter(player) && !isSpeedrunner(player)) {
            adventure.player(player).sendMessage(Component.text("只有游戏中的玩家才能投票"))
            return
        }
        if (voteTask == null) {
            // 投票发起
            voteTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                voteTask = null
                votingEndMap.clear()
                votingCount = 0
                Bukkit.getOnlinePlayers().forEach {
                    adventure.player(it).sendMessage(Component.text("票数不足，游戏继续。"))
                }
            }, 60 * 20L)
            // 统计参与投票的玩家
            speedrunnerSet.forEach {
                Bukkit.getPlayer(it) ?: return@forEach
                // 生存模式的速通者统计进来
                if (!outPlayers.contains(it)) {
                    votingEndMap[it] = false
                }
            }
            hunterSet.forEach {
                Bukkit.getPlayer(it) ?: return@forEach
                votingEndMap[it] = false
            }
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it)
                    .sendMessage(Component.text("${player.name}发起了终止游戏的投，如果赞成请在60秒内执行 /minehunt stop"))
            }
        }
        // 玩家投票
        if (!votingEndMap.containsKey(player.uniqueId)) {
            adventure.player(player).sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        votingEndMap[player.uniqueId] = true
        votingCount++
        adventure.player(player)
            .sendMessage(Component.text("Voting (${votingCount}/${votingEndMap.size})", NamedTextColor.RED))
        if (votingCount != votingEndMap.size) {
            return
        }
        // 投票完成，游戏结束
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票完成--------", NamedTextColor.GOLD))
        }
        end(null)
    }

    /**
     * 结束处理
     */
    fun end(winner: String?) {
        if (stage != GameStage.PROCESSING) return

        stage = GameStage.OVER
        hunterSpawnCD?.cancel()
        hunterSpawnCD = null
        compassRefreshTask?.cancel()
        compassRefreshTask = null
        // 所有人设为生存模式
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------游戏结束--------", NamedTextColor.GREEN))
            if (winner != null) {
                adventure.player(it).sendMessage(Component.text("获胜者：$winner", NamedTextColor.GOLD))
            } else {
                adventure.player(it).sendMessage(Component.text("没有赢家", NamedTextColor.GOLD))
            }
            it.gameMode = GameMode.SURVIVAL
        }
        // 取消剩余的复活任务
        hunterRespawnTasks.forEach {
            it.value.cancel()
        }
        hunterRespawnTasks.clear()
        // 有仇的报仇
        speedrunnerTeam.setAllowFriendlyFire(true)
        hunterTeam.setAllowFriendlyFire(true)
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
        if (stage != GameStage.PROCESSING) return
        val i = trackRunnerMap[hunter.uniqueId] ?: return
        if (speedrunnerList.isEmpty()) return

        var j = i
        while (true) {
            j++
            j %= speedrunnerList.size
            if (i == j) {
                // 极端情况，所有速通者都掉线了
                break
            }
            val uuid = speedrunnerList[j]
            val speedrunner = Bukkit.getPlayer(uuid)
            if (!outPlayers.contains(uuid) && speedrunner != null) {
                trackRunnerMap[hunter.uniqueId] = j
                // hunter操作指南针时立即刷新位置
                refreshCompassTrack(hunter, speedrunner)
                adventure.player(hunter).sendActionBar(Component.text("指向 ${speedrunner.name}"))
                break
            }
        }
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
        if (stage != GameStage.PROCESSING) return

        val uuid = player.uniqueId
        if (isSpeedrunner(player)) {
            // 速通者置为旁观者模式，加入淘汰名单
            player.gameMode = GameMode.SPECTATOR
            outPlayers.add(uuid)
            // 如给所有hunter都淘汰，则游戏结束
            if (outPlayers.size == speedrunnerSet.size) {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { end("Hunter") }, 0)
            }
        } else if (isHunter(player)) {
            // 猎人置为旁观者模式，稍后复活
            player.gameMode = GameMode.SPECTATOR
            adventure.player(player).sendMessage(Component.text("等待重生"))
            hunterRespawnTasks[uuid] = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                player.gameMode = GameMode.SURVIVAL
                hunterRespawnTasks.remove(uuid)
            }, gameRules.getRuleValue(RuleKey.HUNTER_RESPAWN_CD) * 20L)
        }
    }

    private fun initScoreboard() {
        // TODO 将使用更好的计分板API

        //清除旧的计分板信息
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.getObjective("rule-list")?.unregister()

        //设置新的计分板信息
        val displayComponent = Component.text("游戏规则", NamedTextColor.DARK_AQUA)
        val ruleListObjective = scoreboard.registerNewObjective(
            "rule-list",
            Criteria.DUMMY,
            displayComponent.content()
        )
        val rules = gameRules.getAllRules()
        rules.onEachIndexed { i, entry ->
            val ruleInfoComponent = Component.text(entry.key.info, NamedTextColor.GOLD)
            val stupidSpigotEntry = ruleInfoComponent.content()
            val score = ruleListObjective.getScore(stupidSpigotEntry)
            score.score = rules.size - i

            val teamForOneRule = scoreboard.registerNewTeam(stupidSpigotEntry)
            teamForOneRule.addEntry(stupidSpigotEntry)
//            teamForOneRule.suffix =
//                Component.text(": ").append(Component.text(entry.value.toString(), NamedTextColor.GREEN)).serialize()
            teamForOneRule.suffix = ": " + entry.value.toString()
        }
        ruleListObjective.displaySlot = DisplaySlot.SIDEBAR
    }

    /**
     * 更新规则时刷新对应规则项的后缀
     */
    fun refreshEntry(ruleKey: RuleKey<*>) {
//        val teamForOneRule = scoreboard.getTeam(ruleKey.name) ?: return
        val teamForOneRule = scoreboard.getTeam(ruleKey.info) ?: return
//        teamForOneRule.suffix = Component.text(": ")
//            .append(Component.text(gameRules.getRuleValue(ruleKey).toString(), NamedTextColor.GREEN))
//            .serialize()
        teamForOneRule.suffix = ": " + gameRules.getRuleValue(ruleKey).toString()
    }

    /**
     * 记录玩家进入传送门时的位置
     */
    fun recordLocAtPortal(player: Player, location: Location) {
        val world = location.world!!
        if (world.uid == overworld.uid) {
            playerLocInWorld[player.uniqueId] = location
        } else if (world.uid == nether.uid) {
            playerLocInNether[player.uniqueId] = location
        }
    }

    /**
     * 给予猎人追踪指南针
     */
    fun giveCompassIfNeed(player: Player) {
        if (stage == GameStage.PROCESSING && isHunter(player)) {
            val items = player.inventory.all(Material.COMPASS)
            var have = false
            for (item in items) {
                val lore = item.value.itemMeta!!.lore
                if (lore.isNullOrEmpty()) continue
                val loreContent = lore[0]
                if (loreContent.contentEquals(compassFlag)) {
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
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}
