package xyz.fortern.minehunt

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.rule.RuleItem
import java.util.concurrent.ConcurrentHashMap

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
     * 速通者列表，用于指南针指向的遍历
     */
    lateinit var speedrunnerList: List<Player>
    
    /**
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    val trackRunnerMap: MutableMap<String, Int> = ConcurrentHashMap()
    
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
    
    /**
     * 游戏开始前的倒计时任务
     */
    var beginningCountdown: BukkitTask? = null
        private set
    
    /**
     * 猎人出生倒计时
     */
    var hunterSpawnCD: BukkitTask? = null
        private set
    
    /**
     * 指南针刷新任务
     */
    private var compassRefreshTask: BukkitTask? = null
    
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
        // TODO 如何阻止玩家玩家移动？设置速度为0或取消move事件
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
        if (speedrunnerSet.isEmpty()) throw RuntimeException("No Speedrunner")
        
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
        
        // 速通者更改为生存模式，并加入speedrunnerList
        speedrunnerSet.forEach { it.gameMode = GameMode.SURVIVAL }
        speedrunnerList = speedrunnerSet.toList()
        
        // 将猎人传送到世界底部，且指南针开始有所指向
        hunterSet.forEach {
            it.teleport(Location(world, 0.0, -64.0, 0.0))
            trackRunnerMap.put(it.name, 0)
        }
        
        // 猎人出生倒计时Task
        hunterSpawnCD = Bukkit.getScheduler().runTaskTimerAsynchronously(Minehunt.instance(), Runnable {
            hunterSet.forEach {
                it.sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                it.gameMode = GameMode.SURVIVAL
                it.teleport(spawnLocation)
                it.inventory.addItem(hunterCompass)
            }
            speedrunnerSet.forEach { it.sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED)) }
            // 指南针开始追踪
            compassRefreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Minehunt.instance(), Runnable {
                // TODO 如何取得玩家身上的指南针？
                hunterSet.forEach {
                    if (it.isOnline) {
                        var i = (trackRunnerMap[it.name] ?: return@forEach) % speedrunnerList.size
                        val speedrunner = speedrunnerList[i]
                        val location = speedrunner.location
                        val items = it.inventory.all(hunterCompass)
                        items.forEach { k, v ->
                            val lore = v.lore()
                            if (lore != null && lore.isNotEmpty() && lore[0].equals(compassFlag)) {
                            
                            }
                        }
                        
                    }
                }
                
            }, 0, 20)
            
        }, RuleItem.HUNTER_READY_CD.value * 20L, 0)
        
        // TODO 如何不再阻止玩家移动？
        
    }
    
    /**
     * 判断物品是否为猎人指南针
     */
    fun isHunterCompass(itemStack: ItemStack) = hunterCompass == itemStack
    
    /**
     * 让该玩家所追踪的目标切换倒下一个
     */
    fun trackNextPlayer(playerName: String) {
        var i = trackRunnerMap[playerName] ?: return
        i++
        trackRunnerMap[playerName] = i % speedrunnerList.size
    }
    
    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}
