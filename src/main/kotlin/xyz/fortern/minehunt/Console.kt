package xyz.fortern.minehunt

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.rule.RuleItem

/**
 * 游戏控制台
 */
class Console {
    
    var stage: GameStage = GameStage.PREPARING
    
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
    val speedrunnerSet: MutableSet<Player> = HashSet()
    
    /**
     * 猎人玩家集合
     */
    val hunterSet: MutableSet<Player> = HashSet()
    
    /**
     * 旁观者玩家集合
     */
    val spectatorSet: MutableSet<Player> = HashSet()
    
    /**
     * 存活中的速通者列表，用于指南针指向的遍历
     */
    val speedrunnerList: MutableList<Player> = ArrayList()
    
    /**
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    val trackRunnerMap: Map<String, Int> = HashMap()
    
    /**
     * 游戏开始前的倒计时任务
     */
    var beginningCountdown: BukkitTask? = null
        private set
    
    /**
     * 猎人出生倒计时
     */
    var hunterSpawnCD: BukkitTask? = null
    
    companion object {
        @JvmStatic
        private lateinit var instance: Console
        
        @JvmStatic
        fun getInstance() = instance
    }
    
    init {
        instance = this
        // 初始化游戏规则
        val world = Bukkit.getWorld("world")!!
        world.worldBorder.size = 32.0
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        world.setGameRule(GameRule.KEEP_INVENTORY, true)
        world.setGameRule(GameRule.SPAWN_RADIUS, 0)
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        world.difficulty = Difficulty.HARD
        
        // 初始化队伍
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        
        speedrunnerTeam = scoreboard.getTeam("speedrunner") ?: scoreboard.registerNewTeam("speedrunner")
        hunterTeam = scoreboard.getTeam("hunter") ?: scoreboard.registerNewTeam("hunter")
        spectatorTeam = scoreboard.getTeam("spectator") ?: scoreboard.registerNewTeam("spectator")
        
        speedrunnerTeam.entries.forEach { speedrunnerTeam.removeEntries(it) }
        hunterTeam.entries.forEach { hunterTeam.removeEntries(it) }
        spectatorTeam.entries.forEach { spectatorTeam.removeEntries(it) }
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
    fun countdownToStart() {
        beginningCountdown = Bukkit.getScheduler().runTaskTimerAsynchronously(Minehunt.instance(), object : Runnable {
            private var countdown = 6
            override fun run() {
                if (--countdown > 0) {
                    // 倒计时期间，每秒显示一次标题
                    Bukkit.getOnlinePlayers().forEach {
                        val title = Title.title(
                            Component.text(countdown.toString(), NamedTextColor.DARK_PURPLE),
                            Component.text("开始倒计时", NamedTextColor.GRAY)
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
    
    /**
     * 开始游戏
     *
     * 游戏阶段由 PREPARING 变为 PROCESSING
     */
    fun start() {
        // 修改游戏规则
        val world = Bukkit.getWorld("world")!!
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true)
        world.setGameRule(GameRule.DO_MOB_SPAWNING, true)
        world.setGameRule(GameRule.KEEP_INVENTORY, false)
        world.setGameRule(GameRule.SPAWN_RADIUS, 10)
        world.difficulty = Difficulty.HARD
        
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
        
        // 通过Team遍历玩家很麻烦
        
        // 速通者更改为生存模式
        speedrunnerSet.forEach { it.gameMode = GameMode.SURVIVAL }
        // 将猎人传送到世界底部
        hunterSet.forEach { it.teleport(Location(world, 0.0, -64.0, 0.0)) }
        
        // 猎人出生倒计时Task
        hunterSpawnCD = Bukkit.getScheduler().runTaskTimerAsynchronously(Minehunt.instance(), Runnable {
            hunterSet.forEach {
                it.sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                it.gameMode = GameMode.SURVIVAL
                it.teleport(spawnLocation)
                // TODO 给予猎人指南针
                
            }
            speedrunnerSet.forEach { it.sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED)) }
        }, RuleItem.HUNTER_READY_CD.value * 20L, 0)
        
        
    }
    
    
    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}