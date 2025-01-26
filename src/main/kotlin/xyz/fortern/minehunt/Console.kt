package xyz.fortern.minehunt

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team

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
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    val trackRunnerMap: Map<String, Int> = HashMap()
    
    /**
     * 用于遍历的速通者列表
     */
    val speedrunnerList: MutableList<String> = ArrayList()
    
    private var countdownTask: BukkitTask? = null
    
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
        
        return true
    }
    
    fun countdownToStart() {
        countdownTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Minehunt.instance(), object : Runnable {
            private var countdown = 6
            override fun run() {
                if (--countdown > 0) {
                    Bukkit.getOnlinePlayers().forEach {
                        val title = Title.title(
                            Component.text(countdown.toString(), NamedTextColor.DARK_PURPLE),
                            Component.text("开始倒计时", NamedTextColor.GRAY)
                        )
                        it.showTitle(title)
                    }
                } else {
                    countdownTask = null
                    start()
                }
            }
        }, 0, 20)
    }
    
    fun start() {
    
    }
    
    
    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}