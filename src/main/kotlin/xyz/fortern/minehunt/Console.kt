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
    private val hunterTeam: Team
    
    /**
     * 观察者队伍
     */
    private val spectatorTeam: Team
    
    /**
     * 用于遍历的速通者列表
     */
    val speedrunnerList: MutableList<Player> = ArrayList()
    
    /**
     * 插件维护的猎人玩家集合
     */
    val hunterSet: MutableSet<Player> = HashSet()
    
    /**
     * 插件维护的旁观者玩家集合
     */
    val spectatorSet: MutableSet<Player> = HashSet()
    
    /**
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    val trackRunnerMap: Map<String, Int> = HashMap()
    
    var countdownTask: BukkitTask? = null
        private set
    
    companion object {
        @JvmStatic
        private lateinit var instance: Console
        
        @JvmStatic
        fun getInstance() = instance
    }
    
    init {
        instance = this
        // 设置游戏规则
        val world = Bukkit.getWorld("world")!!
        world.worldBorder.size = 32.0
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        world.setGameRule(GameRule.KEEP_INVENTORY, true)
        world.setGameRule(GameRule.SPAWN_RADIUS, 0)
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        world.difficulty = Difficulty.HARD
        
        // 配置队伍
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        
        speedrunnerTeam = scoreboard.getTeam("speedrunner") ?: scoreboard.registerNewTeam("speedrunner")
        hunterTeam = scoreboard.getTeam("hunter") ?: scoreboard.registerNewTeam("hunter")
        spectatorTeam = scoreboard.getTeam("spectator") ?: scoreboard.registerNewTeam("spectator")
        
        speedrunnerTeam.entries.forEach { speedrunnerTeam.removeEntries(it) }
        hunterTeam.entries.forEach { hunterTeam.removeEntries(it) }
        spectatorTeam.entries.forEach { spectatorTeam.removeEntries(it) }
    }
    
    fun tryStart(): Boolean {
        if (speedrunnerTeam.size == 0) return false
        if (Bukkit.getOnlinePlayers().count { it.isDead } > 0) return false
        countdownToStart()
        return true
    }
    
    fun countdownToStart() {
        // 进行开始前的倒计时
        countdownTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Minehunt.instance(), object : Runnable {
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
                    countdownTask = null
                    start()
                }
            }
        }, 0, 20)
    }
    
    /**
     * 开始游戏
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
        speedrunnerList.forEach { it.gameMode = GameMode.SURVIVAL }
        // 将猎人传送到世界底部
        hunterSet.forEach { it.teleport(Location(world, 0.0, -64.0, 0.0)) }
        
        val scheduler = Bukkit.getScheduler()
        val task = scheduler.runTaskTimerAsynchronously(Minehunt.instance(), Runnable {
            hunterSet.forEach {
                it.sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                it.gameMode = GameMode.SURVIVAL
                it.teleport(spawnLocation)
            }
        }, 0, RuleItem.HUNTER_READY_CD.value * 20L)
        
        
    }
    
    
    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}