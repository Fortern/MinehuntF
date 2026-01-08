package xyz.fortern.minehunt

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
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
    val overworld = Bukkit.getWorld("world")!!

    /**
     * 下界
     */
    val nether = Bukkit.getWorld("world_nether")!!

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

    // bukkit task end

    /**
     * 为终止游戏而发起的投票进程
     */
    private val voteForStop: VoteProcess = VoteProcess(plugin, 30L * 20, 0.8f, {
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票完成--------", NamedTextColor.GOLD))
        }
        end(null)
    }, {
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("投票结束，票数不足"))
        }
    }, {
        // 通知所有玩家投票进程
        Bukkit.getOnlinePlayers().forEach {
            val pollingNum = voteForStop.pollingNum()
            val playersNum = voteForStop.playersNum()
            adventure.player(it).sendMessage(
                Component.text(
                    "投票终止游戏 (${pollingNum}/${playersNum}) (${String.format("%.2f%%", pollingNum * 100.0 / playersNum)})",
                    NamedTextColor.RED
                )
            )
        }
    })

    /**
     * 为重开游戏而发起的投票进程
     */
    private val voteForRemake: VoteProcess = VoteProcess(plugin, 30L * 20, 0.5f, {
        stage = GameStage.REMAKE
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票结束，5秒后游戏重开--------"))
            // 重开本质上是停止服务器，由外部程序控制如何启动新游戏
        }
        // 5 秒后重开
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            Bukkit.shutdown()
        }, 5 * 20L)
    }, {
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票结束，票数不足--------"))
        }
    }, {
        // 通知所有玩家投票进程
        Bukkit.getOnlinePlayers().forEach {
            val pollingNum = voteForRemake.pollingNum()
            val playersNum = voteForRemake.playersNum()
            adventure.player(it).sendMessage(
                Component.text(
                    "投票重开游戏 (${pollingNum}/${playersNum}) (${String.format("%.2f%%", pollingNum * 100.0 / playersNum)})",
                    NamedTextColor.RED
                )
            )
        }
    })

    init {
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

        speedrunnerTeam = scoreboard.getTeam("speedrunner") ?: scoreboard.registerNewTeam("speedrunner")
        hunterTeam = scoreboard.getTeam("hunter") ?: scoreboard.registerNewTeam("hunter")
        audienceTeam = scoreboard.getTeam("spectator") ?: scoreboard.registerNewTeam("spectator")

        speedrunnerTeam.let { t ->
            t.color = ChatColor.BLUE
            t.prefix = "[速通者]"
            t.entries.forEach { speedrunnerTeam.removeEntry(it) }
        }
        hunterTeam.let { t ->
            t.color = ChatColor.RED
            t.prefix = "[猎人]"
            t.entries.forEach { hunterTeam.removeEntry(it) }
        }
        audienceTeam.let { t ->
            t.color = ChatColor.GRAY
            t.prefix = "[观众]"
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
     * 重进游戏
     */
    fun reJoinInGame(player: Player) {
        if (stage != GameStage.PROCESSING) {
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

    /**
     * 尝试开始游戏。如果满足条件，则返回空字符串，否则返回原因描述
     */
    fun tryStart(): String {
        if (speedrunnerTeam.size == 0) return "速通者需要至少一位玩家"
        if (voteForRemake.isRunning()) return "正在进行重开投票"
        // 以后可能有其他需要判断的情况
        countdownToStart()
        return ""
    }

    /**
     * 游戏开始前的倒计时
     */
    private fun countdownToStart() {
        stage = GameStage.COUNTDOWN
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
                                0,
                                20,
                                0
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
        stage = GameStage.PREPARING
    }

    /**
     * 开始游戏
     *
     * 游戏阶段由 COUNTDOWN 变为 PROCESSING
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
                it.teleport(Location(overworld, 0.0, overworld.minHeight - 2.0, 0.0))
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
            compassRefreshTask = compassTask.runTaskTimer(plugin, 0, 5)

            // 通知速通者
            speedrunnerSet.forEach {
                adventure.player(it).sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED))
            }
            hunterSpawnCD = null
        }, gameRules.getRuleValue(RuleKey.HUNTER_READY_CD) * 20L)

        scoreboard.getObjective("rule-list")!!.displaySlot = null
        Bukkit.getOnlinePlayers().forEach { player ->
            adventure.player(player).sendMessage(Component.text("--------游戏开始-------", NamedTextColor.GREEN))
        }
        stage = GameStage.PROCESSING
    }

    /**
     * 投票结束游戏
     */
    fun voteForStop(player: Player) {
        val audience = adventure.player(player)
        if (stage != GameStage.PROCESSING) {
            audience.sendMessage(Component.text("只有游戏中才能投票", NamedTextColor.RED))
            return
        }
        if (!isHunter(player) && !isSpeedrunner(player)) {
            audience.sendMessage(Component.text("只有游戏中的玩家才能投票"))
            return
        }
        // 新投票，统计参与投票的玩家，并通知所有玩家
        if (!voteForStop.isRunning()) {
            val players: MutableList<UUID> = mutableListOf()
            speedrunnerSet.forEach {
                Bukkit.getPlayer(it) ?: return@forEach
                // 生存模式的速通者统计进来
                if (!outPlayers.contains(it)) {
                    players.add(it)
                }
            }
            hunterSet.forEach {
                // 所有的猎人统计进来
                Bukkit.getPlayer(it) ?: return@forEach
                players.add(it)
            }
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it).sendMessage(
                    Component.text("${player.name}发起了终止游戏的投票")
                        .append(Component.newline())
                        .append(Component.text("投票需达到的比例: ${String.format("%.2f%%", voteForStop.rate * 100)}"))
                        .append(Component.newline())
                        .append(Component.text("如果赞成请在${voteForStop.time / 20}秒内执行"))
                        .append(
                            Component.text(" /minehunt stop ", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand("/minehunt stop"))
                        )
                        .append(Component.text("(可点击执行)", NamedTextColor.WHITE))
                )
            }
            voteForStop.newVote(Bukkit.getOnlinePlayers().toList())
        }
        // 玩家投票
        if (!voteForStop.canVote(player)) {
            audience.sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        voteForStop.onPlayerVote(player)
    }

    /**
     * 投票重开游戏
     */
    fun voteForRemake(player: Player) {
        val audience = adventure.player(player)
        if (stage == GameStage.PROCESSING || beginningCountdown != null) {
            audience.sendMessage(Component.text("游戏中不能重开", NamedTextColor.RED))
            return
        }
        if (stage == GameStage.REMAKE) {
            audience.sendMessage(Component.text("正在重开......"))
            return
        }
        // 新投票，统计参与投票的玩家，并通知所有玩家
        if (!voteForRemake.isRunning()) {
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it).sendMessage(
                    Component.text("${player.name}发起了重开游戏的投票")
                        .append(Component.newline())
                        .append(Component.text("投票需达到的比例: ${String.format("%.2f%%", voteForRemake.rate * 100.0)}"))
                        .append(Component.newline())
                        .append(Component.text("如果赞成请在${voteForRemake.time / 20}秒内执行"))
                        .append(
                            Component.text(" /minehunt remake ", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand("/minehunt remake"))
                        )
                        .append(Component.text("(可点击执行)", NamedTextColor.WHITE))
                )
            }
            voteForRemake.newVote(Bukkit.getOnlinePlayers().toList())
        }
        // 玩家进行投票
        if (!voteForRemake.canVote(player)) {
            audience.sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        voteForRemake.onPlayerVote(player)
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
        // 取消剩余的复活任务
        hunterRespawnTasks.forEach {
            it.value.cancel()
        }
        hunterRespawnTasks.clear()
        // 取消投票进程
        if (voteForStop.isRunning()) {
            voteForStop.cancel()
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it).sendMessage(Component.text("投票取消", NamedTextColor.RED))
            }
        }
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
        val ruleListObjective = scoreboard.registerNewObjective(
            "rule-list",
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
    fun refreshEntry(ruleKey: RuleKey<*>) {
//        val teamForOneRule = scoreboard.getTeam(ruleKey.name) ?: return
        val teamForOneRule = scoreboard.getTeam("${ChatColor.GOLD}${ruleKey.displayName}") ?: return
        teamForOneRule.suffix = ": ${ChatColor.GREEN}${gameRules.getRuleValue(ruleKey)}"
    }

    /**
     * 记录玩家进入传送门时的位置
     */
    fun recordLocAtPortal(player: Player, from: Location) {
        val world = from.world!!
        if (world.uid == overworld.uid) {
            playerLocInWorld[player.uniqueId] = from
        } else if (world.uid == nether.uid) {
            playerLocInNether[player.uniqueId] = from
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
     * 玩家是否正在重生
     */
    fun isRespawning(player: Player): Boolean {
        return hunterRespawnTasks.containsKey(player.uniqueId)
    }

    /**
     * 猎人是否在等待出生
     */
    fun waitHunterSpawning(player: Player): Boolean {
        return hunterSpawnCD != null
    }

    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, COUNTDOWN, PROCESSING, OVER, REMAKE
    }
}
