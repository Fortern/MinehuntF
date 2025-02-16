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
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.rule.RuleItem
import java.util.concurrent.ConcurrentHashMap

/**
 * 游戏控制台
 */
class Console {
    
    var stage: GameStage = GameStage.PREPARING
        private set
    
    private var overworld = Bukkit.getWorld("world")!!
    
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
    private val speedrunnerSet: MutableSet<Player> = HashSet()
    
    /**
     * 猎人玩家集合
     */
    private val hunterSet: MutableSet<Player> = HashSet()
    
    /**
     * 旁观者玩家集合
     */
    private val spectatorSet: MutableSet<Player> = HashSet()
    
    /**
     * 终止游戏的投票统计
     */
    private val votingEndMap: MutableMap<String, Boolean> = HashMap()
    
    /**
     * 投票计数
     */
    private var votingCount: Int = 0
    
    /**
     * 速通者列表，用于指南针指向的遍历
     */
    private lateinit var speedrunnerList: List<Player>
    
    /**
     * 猎人持有的指南针指向的速通者在speedrunnerList中的index
     */
    private val trackRunnerMap: MutableMap<String, Int> = ConcurrentHashMap()
    
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
     * 猎人重生Task，保留这些引用方便在游戏结束时取消这些任务
     */
    private val hunterRespawnTasks: MutableMap<Player, BukkitTask> = HashMap()
    
    // bukkit task start
    
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
        // 初始化游戏规则
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
        
        speedrunnerTeam.entries.forEach { speedrunnerTeam.removeEntries(it) }
        hunterTeam.entries.forEach { hunterTeam.removeEntries(it) }
        spectatorTeam.entries.forEach { spectatorTeam.removeEntries(it) }
    }
    
    /**
     * 判断玩家是否为猎人
     */
    fun isHunter(player: Player) = hunterSet.contains(player)
    
    /**
     * 判断是否为观察者
     */
    fun isSpectator(player: Player) = spectatorSet.contains(player)
    
    /**
     * 判断是否为速通者
     */
    fun isSpeedrunner(player: Player) = speedrunnerSet.contains(player)
    
    /**
     * 加入猎人阵营
     */
    fun joinHunter(player: Player) {
        speedrunnerSet.remove(player)
        spectatorSet.remove(player)
        hunterSet.add(player)
    }
    
    /**
     * 加入速通者阵营
     */
    fun joinSpeedrunner(player: Player) {
        hunterSet.remove(player)
        spectatorSet.remove(player)
        speedrunnerSet.add(player)
    }
    
    /**
     * 加入观察者阵营
     */
    fun joinSpectator(player: Player) {
        hunterSet.remove(player)
        speedrunnerSet.remove(player)
        spectatorSet.add(player)
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
    private fun start() {
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
            trackRunnerMap[it.name] = 0
        }
        
        // 创建指南针更新任务
        val compassTask = object : BukkitRunnable() {
            override fun run() {
                hunterSet.forEach {
                    if (!it.isOnline) return@forEach
                    val i = (trackRunnerMap[it.name] ?: return@forEach) % speedrunnerList.size
                    val speedrunner = speedrunnerList[i]
                    val items = it.inventory.all(hunterCompass)
                    var flag = false
                    items.forEach { (k, v) ->
                        val lore = v.lore()
                        // 避免玩家身上有多个猎人指南针
                        if (flag) {
                            it.inventory.clear(k)
                        }
                        if (!lore.isNullOrEmpty() && lore[0].equals(compassFlag)) {
                            flag = true
                            // 让指南针指向某一个猎人
                            val meta = v.itemMeta as CompassMeta
                            meta.isLodestoneTracked = false
                            meta.lodestone = speedrunner.location
                        }
                    }
                }
            }
        }
        
        // 猎人出生倒计时Task
        hunterSpawnCD = Bukkit.getScheduler().runTaskLater(Minehunt.instance(), Runnable {
            // 猎人设置初始状态
            hunterSet.forEach {
                it.sendMessage(Component.text("你已到达出生点", NamedTextColor.RED))
                it.gameMode = GameMode.SURVIVAL
                it.teleport(spawnLocation)
                it.inventory.addItem(hunterCompass)
            }
            
            // 自动更新指南针任务开始运行
            compassRefreshTask = compassTask.runTaskTimer(Minehunt.instance(), 0, 20)
            
            // 通知速通者
            speedrunnerSet.forEach { it.sendMessage(Component.text("猎人开始追杀", NamedTextColor.RED)) }
        }, RuleItem.HUNTER_READY_CD.value * 20L)
        
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
        val name = player.name
        if (voteTask == null) {
            // 投票发起
            voteTask = Bukkit.getScheduler().runTaskLater(Minehunt.instance(), Runnable {
                voteTask = null
                votingEndMap.clear()
                votingCount = 0
            }, 60)
            // 统计参与投票的玩家
            speedrunnerSet.forEach {
                // 生存模式的速通者统计进来
                if (it.isOnline && it.gameMode == GameMode.SURVIVAL) {
                    votingEndMap[it.name] = false
                }
            }
            hunterSet.forEach {
                if (it.isOnline) {
                    votingEndMap[it.name] = false
                }
            }
            Bukkit.getOnlinePlayers().forEach {
                it.sendMessage(Component.text("${name}发起了终止游戏的投，如果赞成请在60秒内执行 /minehunt stop"))
            }
        }
        // 玩家投票
        if (!votingEndMap.containsKey(name)) {
            player.sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        votingEndMap[name] = true
        votingCount++
        player.sendMessage(Component.text("voting (${votingCount}/${votingEndMap.size})", NamedTextColor.RED))
        if (votingCount != votingEndMap.size) {
            return
        }
        // 投票完成，游戏结束
        Bukkit.getOnlinePlayers().forEach {
            it.sendMessage(Component.text("投票完成，游戏结束", NamedTextColor.GOLD))
        }
        end()
    }
    
    /**
     * 结束处理
     */
    private fun end() {
        // TODO 延迟传送至出生点
        
        // 取消剩余的复活任务
        val iterator = hunterRespawnTasks.iterator()
        while (iterator.hasNext()) {
            iterator.next().value.cancel()
            iterator.remove()
        }
        // 所有人传送至出生点，设为生存模式
        Bukkit.getOnlinePlayers().forEach {
            it.gameMode = GameMode.SURVIVAL
            it.teleport(overworld.spawnLocation)
        }
        stage = GameStage.OVER
    }
    
    /**
     * 判断物品是否为猎人指南针
     */
    fun isHunterCompass(itemStack: ItemStack) = hunterCompass == itemStack
    
    /**
     * 让该玩家所追踪的目标切换到下一个
     */
    fun trackNextPlayer(playerName: String) {
        if (stage != GameStage.PROCESSING) return
        val i = trackRunnerMap[playerName] ?: return
        if (speedrunnerList.isEmpty()) return
        
        var j = i
        while (true) {
            j++
            j %= speedrunnerList.size
            val speedrunner = speedrunnerList[i]
            if (speedrunner.gameMode == GameMode.SURVIVAL && speedrunner.isOnline || i == j) {
                trackRunnerMap[playerName] = j
                break
            }
        }
    }
    
    /**
     * 处理玩家死亡
     */
    fun handlePlayerDeath(player: Player) {
        if (isHunter(player)) {
            // 猎人置为旁观者模式
            player.gameMode = GameMode.SPECTATOR
        } else if (isSpeedrunner(player)) {
            // 猎人置为旁观者模式
            player.gameMode = GameMode.SPECTATOR
            val task = Bukkit.getScheduler().runTaskLater(Minehunt.instance(), Runnable {
                player.gameMode = GameMode.SURVIVAL
                hunterRespawnTasks.remove(player)
            }, 20)
            hunterRespawnTasks[player] = task
        }
    }
    
    /**
     * 游戏阶段
     */
    enum class GameStage {
        PREPARING, PROCESSING, OVER
    }
}
