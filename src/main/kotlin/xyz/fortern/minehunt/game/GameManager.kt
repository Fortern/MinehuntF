package xyz.fortern.minehunt.game

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import xyz.fortern.minehunt.VoteProcess
import xyz.fortern.minehunt.record.FinishType
import java.time.Instant
import java.util.*
import java.util.logging.Level
import xyz.fortern.minehunt.record.GameMode as GameModeId

/**
 * 管理一台服务端进程中唯一一局游戏的通用生命周期。
 *
 * 该类是阶段转换、当前模式、通用投票和会话任务清理的协调入口；
 * 模式实现不应自行改变 [phase]。
 */
class GameManager(
    private val plugin: JavaPlugin,
    private val adventure: BukkitAudiences,
    private val records: GameRecordService,
) : AutoCloseable {
    private val state = GameStateMachine()
    private val factories = LinkedHashMap<GameModeId, () -> GameMode>()
    private var countdownTask: BukkitTask? = null
    private var remakeTask: BukkitTask? = null
    private var remakeScheduled = false

    /** 当前选中的模式实例；必须先注册并选择模式后才能读取。 */
    lateinit var currentMode: GameMode
        private set

    /** 实际开局时间；尚未开局时为 `null`。 */
    var startedAt: Instant? = null
        private set

    /** 进入结束阶段的时间；游戏尚未结束时为 `null`。 */
    var endedAt: Instant? = null
        private set

    /** 倒计时结束时固定的在线参赛者 UUID 快照。 */
    var participants: Set<UUID> = emptySet()
        private set

    /** 当前游戏阶段的只读视图。 */
    val phase: GamePhase
        get() = state.phase

    private val voteForStop: VoteProcess = VoteProcess(plugin, 30L * 20, 0.8f, {
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票完成--------", NamedTextColor.GOLD))
        }
        finish(GameOutcome(null, FinishType.STOPPED))
    }, {
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("投票结束，票数不足"))
        }
    }, {
        Bukkit.getOnlinePlayers().forEach {
            val votes = voteForStop.pollingNum()
            val players = voteForStop.playersNum()
            adventure.player(it).sendMessage(
                Component.text(
                    "投票终止游戏 ($votes/$players) (${String.format("%.2f%%", votes * 100.0 / players)})",
                    NamedTextColor.RED,
                )
            )
        }
    })

    private val voteForRemake: VoteProcess = VoteProcess(plugin, 30L * 20, 0.5f, {
        remakeScheduled = true
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票结束，5秒后游戏重开--------"))
        }
        remakeTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            remakeTask = null
            Bukkit.shutdown()
        }, 5 * 20L)
    }, {
        Bukkit.getOnlinePlayers().forEach {
            adventure.player(it).sendMessage(Component.text("--------投票结束，票数不足--------"))
        }
    }, {
        Bukkit.getOnlinePlayers().forEach {
            val votes = voteForRemake.pollingNum()
            val players = voteForRemake.playersNum()
            adventure.player(it).sendMessage(
                Component.text(
                    "投票重开游戏 ($votes/$players) (${String.format("%.2f%%", votes * 100.0 / players)})",
                    NamedTextColor.RED,
                )
            )
        }
    })

    /** 注册模式工厂；同一模式标识不能重复注册。 */
    fun registerMode(id: GameModeId, factory: () -> GameMode) {
        check(id !in factories) { "Game mode $id is already registered" }
        factories[id] = factory
    }

    /**
     * 在准备阶段创建并选中一个全新的模式实例。
     *
     * 切换时会关闭旧模式；新模式负责创建自己的大厅和角色状态。
     */
    fun selectMode(id: GameModeId) {
        check(phase == GamePhase.LOBBY) { "Game mode can only be selected in the lobby" }
        val factory = factories[id] ?: error("Game mode $id is not registered")
        deselectCurrentMode()
        currentMode = factory()
        plugin.server.pluginManager.registerEvents(currentMode.listener, plugin)
    }

    /** 注销旧模式监听器并释放模式资源。 */
    private fun deselectCurrentMode() {
        if (!this::currentMode.isInitialized) return
        HandlerList.unregisterAll(currentMode.listener)
        currentMode.cancelTasks()
        currentMode.close()
    }

    /**
     * 校验当前模式并尝试进入倒计时。
     *
     * @return 成功进入倒计时为 `null`，否则返回面向命令发送者的失败原因
     */
    fun tryStart(): String? {
        if (phase != GamePhase.LOBBY) return "现在不能开始游戏"
        if (voteForRemake.isRunning()) return "正在进行重开投票"
        val reason = currentMode.validateStart()
        if (reason != null) return reason

        state.transitionTo(GamePhase.COUNTDOWN)
        var countdown = 6
        countdownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (--countdown > 0) {
                Bukkit.getOnlinePlayers().forEach {
                    adventure.player(it).showTitle(
                        Title.title(
                            Component.text(countdown.toString(), NamedTextColor.DARK_PURPLE),
                            Component.text("开始倒计时", NamedTextColor.GRAY),
                            0,
                            20,
                            0,
                        )
                    )
                }
            } else {
                countdownTask?.cancel()
                countdownTask = null
                startNow()
            }
        }, 0, 20)
        return null
    }

    /** 取消尚未完成的开始倒计时并返回准备阶段。 */
    fun interruptCountdown() {
        if (phase != GamePhase.COUNTDOWN) return
        countdownTask?.cancel()
        countdownTask = null
        state.transitionTo(GamePhase.LOBBY)
    }

    private fun startNow() {
        check(phase == GamePhase.COUNTDOWN)
        startedAt = Instant.now()
        endedAt = null
        participants = currentMode.participants().toSet()
        try {
            currentMode.start()
            state.transitionTo(GamePhase.RUNNING)
        } catch (error: Throwable) {
            currentMode.cancelTasks()
            startedAt = null
            participants = emptySet()
            state.transitionTo(GamePhase.LOBBY)
            throw error
        }
    }

    /**
     * 结束正在运行的会话，并将清理和结果生成串行化。
     *
     * 非游戏进行阶段调用时不会产生效果。
     */
    @Synchronized
    fun finish(outcome: GameOutcome) {
        if (phase != GamePhase.RUNNING) return
        state.transitionTo(GamePhase.ENDING)
        endedAt = Instant.now()
        currentMode.cancelTasks()
        if (voteForStop.isRunning()) {
            voteForStop.cancel()
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it).sendMessage(Component.text("投票取消", NamedTextColor.RED))
            }
        }
        val completedRecord = try {
            currentMode.finish(outcome)
        } catch (error: Throwable) {
            plugin.logger.log(Level.SEVERE, "生成最终对局记录失败", error)
            state.transitionTo(GamePhase.SAVING)
            state.transitionTo(GamePhase.FINISHED)
            return
        }

        state.transitionTo(GamePhase.SAVING)
        try {
            records.save(completedRecord).whenComplete { _, _ -> finishSaving() }
        } catch (error: Throwable) {
            plugin.logger.log(Level.SEVERE, "启动对局记录保存任务失败", error)
            finishSaving()
        }
    }

    /** 保存成功、失败或超时回退结束后，都强制收敛到终止阶段。 */
    @Synchronized
    private fun finishSaving() {
        if (phase == GamePhase.SAVING) {
            state.transitionTo(GamePhase.FINISHED)
        }
    }

    /** 玩家在准备阶段选择身份 */
    fun assignRole(player: Player, role: String): Boolean {
        if (phase != GamePhase.LOBBY) return false
        return currentMode.assignRole(player, role)
    }

    /** 根据当前阶段为新玩家分配观众身份，或恢复本局既有身份。 */
    fun onPlayerJoin(player: Player) {
        when (phase) {
            GamePhase.LOBBY -> currentMode.assignRole(player, currentMode.spectatorRole)
            GamePhase.RUNNING -> currentMode.rejoin(player)
            else -> Unit
        }
    }

    /** 将玩家退出事件委托给当前模式。 */
    fun onPlayerQuit(player: Player) {
        currentMode.onPlayerQuit(player)
    }

    /**
     * 为终止本局投赞成票；第一票会按当前模式的有效参赛者名单创建表决。
     */
    fun voteForStop(player: Player) {
        val audience = adventure.player(player)
        if (phase != GamePhase.RUNNING) {
            audience.sendMessage(Component.text("只有游戏中才能投票", NamedTextColor.RED))
            return
        }
        val eligibleVoters = currentMode.stopVoters()
        if (player.uniqueId !in eligibleVoters) {
            audience.sendMessage(Component.text("只有游戏中的玩家才能投票", NamedTextColor.RED))
            return
        }
        if (!voteForStop.isRunning()) {
            val voters = eligibleVoters.mapNotNull(Bukkit::getPlayer)
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it).sendMessage(
                    Component.text("${player.name}发起了终止游戏的投票")
                        .appendNewline()
                        .append(Component.text("投票需达到的比例: ${String.format("%.2f%%", voteForStop.rate * 100)}"))
                        .appendNewline()
                        .append(Component.text("如果赞成请在${voteForStop.time / 20}秒内执行"))
                        .append(
                            Component.text(" /minehunt stop ", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand("/minehunt stop"))
                        )
                )
            }
            voteForStop.newVote(voters)
        }
        if (!voteForStop.canVote(player)) {
            audience.sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        voteForStop.onPlayerVote(player)
    }

    /**
     * 为关闭并重开服务端投赞成票；第一票会以当时全部在线玩家创建表决。
     */
    fun voteForRemake(player: Player) {
        val audience = adventure.player(player)
        if (phase == GamePhase.RUNNING || phase == GamePhase.COUNTDOWN || phase == GamePhase.ENDING || phase == GamePhase.SAVING) {
            audience.sendMessage(Component.text("游戏中不能重开", NamedTextColor.RED))
            return
        }
        if (remakeScheduled) {
            audience.sendMessage(Component.text("正在重开......"))
            return
        }
        if (!voteForRemake.isRunning()) {
            val voters = Bukkit.getOnlinePlayers().toList()
            if (voters.isEmpty()) return
            Bukkit.getOnlinePlayers().forEach {
                adventure.player(it).sendMessage(
                    Component.text("${player.name}发起了重开游戏的投票")
                        .appendNewline()
                        .append(Component.text("投票需达到的比例: ${String.format("%.2f%%", voteForRemake.rate * 100)}"))
                        .appendNewline()
                        .append(Component.text("如果赞成请在${voteForRemake.time / 20}秒内执行"))
                        .append(
                            Component.text(" /minehunt remake ", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand("/minehunt remake"))
                        )
                )
            }
            voteForRemake.newVote(voters)
        }
        if (!voteForRemake.canVote(player)) {
            audience.sendMessage(Component.text("你不在可投票的名单中", NamedTextColor.RED))
            return
        }
        voteForRemake.onPlayerVote(player)
    }

    override fun close() {
        countdownTask?.cancel()
        countdownTask = null
        remakeTask?.cancel()
        remakeTask = null
        if (voteForStop.isRunning()) voteForStop.cancel()
        if (voteForRemake.isRunning()) voteForRemake.cancel()
        deselectCurrentMode()
    }
}
