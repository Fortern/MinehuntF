package xyz.fortern.minehunt

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.Ticks
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.RenderType
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.rule.GameRules
import xyz.fortern.minehunt.rule.RuleKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 游戏控制台
 */
class Console {
    
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
    private val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    
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
     * 观察者队伍
     */
    val spectatorTeam: Team
    
    // 玩家退出游戏后会自动离开Team，所以我们维护自己的玩家集合
    
    /**
     * 速通者列表
     */
    private val speedrunnerSet: MutableSet<UUID> = HashSet()
    
    /**
     * 猎人玩家集合
     */
    private val hunterSet: MutableSet<UUID> = HashSet()
    
    /**
     * 旁观者玩家集合
     */
    private val spectatorSet: MutableSet<UUID> = HashSet()
    
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
     * 玩家离开主世界时最后的位置
     */
    private val playerLocInWorld: MutableMap<UUID, Location> = HashMap()
    
    /**
     * 玩家离开下界时最后的位置
     */
    private val playerLocInNether: MutableMap<UUID, Location> = HashMap()
    
    /**
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    private val trackRunnerMap: MutableMap<UUID, Int> = ConcurrentHashMap()
    
    /**
     * 猎人的指南针标记
     */
    private val compassFlag = "Hunter Compass"
    
    /**
     * 猎人指南针物品
     *
     * 出生与复活是唯一获取此物品的方法
     */
    private val hunterCompass: ItemStack = ItemStack(Material.COMPASS).apply {
        // 最大堆叠数设为1
        itemMeta.setMaxStackSize(1)
        // 设置名称
        itemMeta.displayName(Component.text(compassFlag, NamedTextColor.GOLD))
        // 设置Lore
        itemMeta.lore(
            listOf(
                // 第一个Lore用于标记这个指南针
                Component.text("compassFlag", NamedTextColor.GRAY),
                Component.text("右键使用或扔出以切换目标", NamedTextColor.GRAY)
            )
        )
        // 添加附魔：消失诅咒
        itemMeta.addEnchant(Enchantment.VANISHING_CURSE, 1, false)
    }
    
    // bukkit task start
    
    /**
     * 猎人重生Task，保留这些引用方便在游戏结束时取消这些任务
     */
    private val hunterRespawnTasks: MutableMap<Player, BukkitTask> = HashMap()
    
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
    
    companion object {
        @JvmStatic
        private lateinit var instance: Console
        
        @JvmStatic
        fun getInstance() = instance
    }
    
    init {
        instance = this
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
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        
        speedrunnerTeam = scoreboard.getTeam("speedrunner") ?: scoreboard.registerNewTeam("speedrunner")
        hunterTeam = scoreboard.getTeam("hunter") ?: scoreboard.registerNewTeam("hunter")
        spectatorTeam = scoreboard.getTeam("spectator") ?: scoreboard.registerNewTeam("spectator")
        
        speedrunnerTeam.let { t ->
            t.color(NamedTextColor.BLUE)
            t.prefix(Component.text("[速通者] ", NamedTextColor.BLUE))
            t.entries.forEach { speedrunnerTeam.removeEntries(it) }
        }
        hunterTeam.let { t ->
            t.color(NamedTextColor.RED)
            t.prefix(Component.text("[猎人] ", NamedTextColor.RED))
            t.entries.forEach { hunterTeam.removeEntries(it) }
        }
        spectatorTeam.let { t ->
            t.color(NamedTextColor.GRAY)
            t.prefix(Component.text("[观众] ", NamedTextColor.GRAY))
            t.entries.forEach { spectatorTeam.removeEntries(it) }
        }
    }
    
    /**
     * 判断玩家是否为猎人
     */
    fun isHunter(player: Player): Boolean = hunterSet.contains(player.uniqueId)
    
    /**
     * 判断是否为观察者
     */
    fun isSpectator(player: Player): Boolean = spectatorSet.contains(player.uniqueId)
    
    /**
     * 判断是否为速通者
     */
    fun isSpeedrunner(player: Player): Boolean = speedrunnerSet.contains(player.uniqueId)
    
    /**
     * 加入猎人阵营
     */
    fun joinHunter(player: Player) {
        if (stage == GameStage.PREPARING && beginningCountdown == null) {
            hunterTeam.addEntry(player.name)
            player.sendMessage(Component.text("你已加入[猎人]"))
        }
    }
    
    /**
     * 加入速通者阵营
     */
    fun joinSpeedrunner(player: Player) {
        if (stage == GameStage.PREPARING && beginningCountdown == null) {
            speedrunnerTeam.addEntry(player.name)
            player.sendMessage(Component.text("你已加入[速通者]"))
        }
    }
    
    /**
     * 加入观察者阵营
     */
    fun joinSpectator(player: Player) {
        if (stage == GameStage.PREPARING && beginningCountdown == null) {
            spectatorTeam.addEntry(player.name)
            player.sendMessage(Component.text("你已加入[观众]"))
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
        beginningCountdown = Bukkit.getScheduler().runTaskTimer(Minehunt.instance(), object : Runnable {
            private var countdown = 6
            override fun run() {
                if (--countdown > 0) {
                    // 倒计时期间，每秒显示一次标题
                    Bukkit.getOnlinePlayers().forEach {
                        val title = Title.title(
                            Component.text(countdown.toString(), NamedTextColor.DARK_PURPLE),
                            Component.text("开始倒计时", NamedTextColor.GRAY),
                            Title.Times.times(Ticks.duration(5), Ticks.duration(20), Ticks.duration(5))
                        )
                        it.showTitle(title)
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
        val world = Bukkit.getWorld("world")!!
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true)
        world.setGameRule(GameRule.DO_MOB_SPAWNING, true)
        world.setGameRule(GameRule.KEEP_INVENTORY, false)
        world.setGameRule(GameRule.SPAWN_RADIUS, 10)
        world.difficulty = Difficulty.HARD
        overworld.worldBorder.size = 9999999.0
        
        val spawnLocation = world.spawnLocation
        
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
                it.teleport(Location(world, 0.0, -64.0, 0.0))
                trackRunnerMap[it.uniqueId] = 0
            }
        }
        
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
        hunterSpawnCD = Bukkit.getScheduler().runTaskLater(Minehunt.instance(), Runnable {
            // 猎人设置初始状态
            hunterSet.forEach {
                val player = Bukkit.getPlayer(it) ?: return@forEach
                player.sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                player.gameMode = GameMode.SURVIVAL
                player.teleport(spawnLocation)
                player.inventory.addItem(hunterCompass)
            }
            
            // “自动更新指南针”任务开始运行
            compassRefreshTask = compassTask.runTaskTimer(Minehunt.instance(), 0, 20)
            
            // 通知速通者
            speedrunnerSet.forEach {
                Bukkit.getPlayer(it)?.sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED))
            }
        }, gameRules.getRuleValue(RuleKey.HUNTER_READY_CD) * 20L)
        
        stage = GameStage.PROCESSING
    }
    
    /**
     * 投票结束游戏
     */
    fun voteForStop(player: Player) {
        if (stage != GameStage.PROCESSING) {
            player.sendMessage(Component.text("只有游戏中才能投票", NamedTextColor.RED))
            return
        }
        if (!isHunter(player) && !isSpeedrunner(player)) {
            player.sendMessage(Component.text("只有游戏中的玩家才能投票"))
            return
        }
        if (voteTask == null) {
            // 投票发起
            voteTask = Bukkit.getScheduler().runTaskLater(Minehunt.instance(), Runnable {
                voteTask = null
                votingEndMap.clear()
                votingCount = 0
            }, 60)
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
                it.sendMessage(Component.text("${player.name}发起了终止游戏的投，如果赞成请在60秒内执行 /minehunt stop"))
            }
        }
        // 玩家投票
        if (!votingEndMap.containsKey(player.uniqueId)) {
            player.sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        votingEndMap[player.uniqueId] = true
        votingCount++
        player.sendMessage(Component.text("voting (${votingCount}/${votingEndMap.size})", NamedTextColor.RED))
        if (votingCount != votingEndMap.size) {
            return
        }
        // 投票完成，游戏结束
        Bukkit.getOnlinePlayers().forEach {
            it.sendMessage(Component.text("--------投票完成--------", NamedTextColor.GOLD))
        }
        end(null)
    }
    
    /**
     * 结束处理
     */
    fun end(winner: String?) {
        stage = GameStage.OVER
        // 所有人设为生存模式
        Bukkit.getOnlinePlayers().forEach {
            it.sendMessage(Component.text("--------游戏结束--------", NamedTextColor.GREEN))
            if (winner != null) {
                it.sendMessage(Component.text("获胜者：$winner", NamedTextColor.GOLD))
            } else {
                it.sendMessage(Component.text("没有赢家", NamedTextColor.GOLD))
            }
            it.gameMode = GameMode.SURVIVAL
        }
        // 取消剩余的复活任务
        hunterRespawnTasks.forEach {
            it.value.cancel()
        }
        hunterRespawnTasks.clear()
    }
    
    /**
     * 判断物品是否为猎人指南针
     */
    fun isHunterCompass(itemStack: ItemStack) = hunterCompass == itemStack
    
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
                val location = speedrunner.location
                // hunter操作指南针时立即刷新位置
                refreshCompassTrack(hunter, speedrunner)
                break
            }
        }
    }
    
    /**
     * 使hunter的指南针指向此时speedrunner的位置
     */
    private fun refreshCompassTrack(hunter: Player, speedrunner: Player) {
        val items = hunter.inventory.all(hunterCompass)
        var flag = false
        items.forEach inner@{ (k, v) ->
            // 避免玩家身上有多个猎人指南针
            if (flag) {
                hunter.inventory.clear(k)
                return@inner
            }
            val lore = v.lore()
            if (!lore.isNullOrEmpty() && lore[0].equals(compassFlag)) {
                flag = true
                // 让指南针指向某一个猎人
                val meta = v.itemMeta as CompassMeta
                meta.isLodestoneTracked = false
                meta.lodestone = speedrunner.location
            }
        }
    }
    
    /**
     * 处理玩家死亡
     */
    fun handlePlayerDeath(player: Player) {
        if (stage != GameStage.PROCESSING) return
        
        Bukkit.getPlayer(player.uniqueId)
        if (isSpeedrunner(player)) {
            // 速通者置为旁观者模式，加入淘汰名单
            player.gameMode = GameMode.SPECTATOR
            outPlayers.add(player.uniqueId)
            // 给淘汰的玩家名字上加删除线
            val playerListName = player.playerListName()
            playerListName.style(Style.style(TextDecoration.STRIKETHROUGH))
            player.playerListName(playerListName)
            // 如给所有hunter都淘汰，则游戏结束
            if (outPlayers.size == speedrunnerSet.size) {
                end("hunter")
            }
        } else if (isHunter(player)) {
            // 猎人置为旁观者模式，稍后复活
            player.gameMode = GameMode.SPECTATOR
            val task = Bukkit.getScheduler().runTaskLater(Minehunt.instance(), Runnable {
                player.gameMode = GameMode.SURVIVAL
                hunterRespawnTasks.remove(player)
            }, 20)
            hunterRespawnTasks[player] = task
        }
    }
    
    private fun initScoreboard() {
        //清除旧的计分板信息
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.getObjective("rule-list")?.unregister()
        scoreboard.getObjective("players")?.unregister()
        
        scoreboard.registerNewObjective("players", Criteria.HEALTH, null).let {
            it.displaySlot = DisplaySlot.PLAYER_LIST
            it.renderType = RenderType.HEARTS
        }
        
        //设置新的计分板信息
        val ruleListObjective = scoreboard.registerNewObjective(
            "rule-list",
            Criteria.DUMMY,
            Component.text("游戏规则", NamedTextColor.DARK_AQUA)
        )
        val rules = gameRules.getAllRules()
        rules.onEachIndexed { i, entry ->
            val entryId = entry.key.name
            val score = ruleListObjective.getScore(entryId)
                .apply { customName(Component.text(entry.key.info, NamedTextColor.GOLD)) }
            score.score = rules.size - i
            val teamForOneRule = scoreboard.registerNewTeam(entryId)
            teamForOneRule.addEntry(entryId)
            teamForOneRule.suffix(
                Component.text(": ").append(Component.text(entry.value.toString(), NamedTextColor.GREEN))
            )
        }
        ruleListObjective.displaySlot = DisplaySlot.SIDEBAR
    }
    
    /**
     * 更新规则时刷新对应规则项的后缀
     */
    fun refreshEntry(ruleKey: RuleKey<*>) {
        val teamForOneRule = scoreboard.getTeam(ruleKey.name) ?: return
        teamForOneRule.suffix(
            Component.text(": ").append(
                Component.text(gameRules.getRuleValue(ruleKey).toString(), NamedTextColor.GREEN)
            )
        )
    }
    
    /**
     * 记录玩家进入传送门时的位置
     */
    fun recordLocAtPortal(player: Player, location: Location) {
        val world = location.world
        if (world.uid == overworld.uid) {
            playerLocInWorld[player.uniqueId] = location
        } else if (world.uid == nether.uid) {
            playerLocInNether[player.uniqueId] = location
        }
    }
    
    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}
