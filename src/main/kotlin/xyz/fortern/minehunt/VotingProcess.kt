package xyz.fortern.minehunt

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * 将纯投票状态与 Bukkit 超时任务组合成一次限时赞成票。
 *
 * 投票名单在 [newVote] 时固定；每位名单内玩家只能投一票，投票无法撤销。
 * 所有公开方法都应在服务器主线程调用。
 */
class VoteProcess(
    private val plugin: JavaPlugin,
    /**
     * 初始倒计时
     */
    var time: Long,

    /**
     * 比例达到多少来完成投票
     */
    val rate: Float,

    /**
     * 投票达成票数时完成时执行的操作
     */
    private val howtoFinish: () -> Unit,

    /**
     * 投票计时结束尚未达成票数时的操作
     */
    private val howtoCancel: () -> Unit,

    /**
     * 当一位玩家投票时执行的操作
     */
    private val onVote: () -> Unit
) {
    private val ballot = VoteBallot(rate)

    /**
     * 用于投票倒计时的task
     */
    var countdownTask: BukkitTask? = null

    /**
     * 新的投票进程
     *
     * @param players 参与此次投票的玩家列表
     */
    fun newVote(players: List<Player>) {
        ballot.start(players.map(Player::getUniqueId))
        // Bukkit callbacks may send messages or change game state, so the timeout
        // must run on the server thread as well.
        countdownTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!ballot.running) return@Runnable
            finishRunningVote()
            howtoCancel()
        }, time)
    }

    /**
     * 玩家投下赞成票。
     *
     * 投票前应先判断投票进程是否开始。
     * 如未开始则应当先调用 [newVote]。
     *
     * 如果玩家不在此次投票名单中，则无法投票。
     *
     * @throws RuntimeException 投票未开始时调用会抛出异常
     */
    fun onPlayerVote(player: Player) {
        if (!ballot.running) {
            throw RuntimeException("投票未开始")
        }
        when (ballot.vote(player.uniqueId)) {
            VoteResult.REJECTED -> Unit
            VoteResult.ACCEPTED -> onVote()
            VoteResult.PASSED -> {
                onVote()
                finishRunningVote()
                howtoFinish()
            }
        }
    }

    /**
     * 玩家能否投票
     */
    fun canVote(player: Player): Boolean {
        return ballot.canVote(player.uniqueId)
    }

    /**
     * 投票正在进行
     */
    fun isRunning(): Boolean = ballot.running

    /**
     * 投赞成票的玩家数量
     */
    fun pollingNum(): Int = ballot.votes

    /**
     * 参与投票的玩家数量
     */
    fun playersNum(): Int = ballot.players

    /**
     * 取消投票进程
     */
    fun cancel() {
        finishRunningVote()
    }

    private fun finishRunningVote() {
        countdownTask?.cancel()
        countdownTask = null
        ballot.cancel()
    }
}

/**
 * 投票行为产生的结果
 */
internal enum class VoteResult {
    /**
     * 投票被拒绝，比如投票者无权投票
     */
    REJECTED,

    /**
     * 投票被接受，但最终结果尚未确定
     */
    ACCEPTED,

    /**
     * 投票被接受，且该票决定了最终结果
     */
    PASSED,
}

/** 不依赖 Bukkit 的单次投票状态，便于独立校验名单、去重和通过比例。 */
internal class VoteBallot(private val requiredRate: Float) {
    private val eligiblePlayers = LinkedHashSet<UUID>()
    private val acceptedPlayers = LinkedHashSet<UUID>()

    /** 是否已有一轮可以继续接收投票的表决。 */
    var running: Boolean = false
        private set

    /** 当前有效赞成票数。 */
    val votes: Int
        get() = acceptedPlayers.size

    /** 本轮固定的可投票玩家数。 */
    val players: Int
        get() = eligiblePlayers.size

    init {
        require(requiredRate > 0.0f && requiredRate <= 1.0f) { "投票比例必须在 (0, 1] 范围内" }
    }

    fun start(players: Collection<UUID>) {
        check(!running) { "已有投票正在进行" }
        require(players.isNotEmpty()) { "投票参与者不能为空" }
        eligiblePlayers.clear()
        eligiblePlayers.addAll(players)
        acceptedPlayers.clear()
        running = true
    }

    fun canVote(player: UUID): Boolean = player in eligiblePlayers

    fun vote(player: UUID): VoteResult {
        check(running) { "投票未开始" }
        if (player !in eligiblePlayers || !acceptedPlayers.add(player)) return VoteResult.REJECTED
        if (votes.toFloat() / players >= requiredRate) {
            running = false
            return VoteResult.PASSED
        }
        return VoteResult.ACCEPTED
    }

    fun cancel() {
        running = false
    }
}
