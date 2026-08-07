package xyz.fortern.minehunt.game

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * 将纯投票状态与 Bukkit 超时任务组合成一次限时赞成票。
 *
 * 每位名单内玩家只能投一票，投票无法撤销。
 * 所有公开方法都应在服务器主线程调用。
 */
class VoteProcess(
    private val plugin: JavaPlugin,
    /**
     * 初始倒计时
     */
    val time: Long,

    /**
     * 比例达到多少来完成投票
     */
    val rate: Float,

    /**
     * 新抽票流程创建时要执行的操作
     */
    private val onStart: (Player) -> Unit,

    /**
     * 投票达成票数时完成时执行的操作
     */
    private val onFinish: () -> Unit,

    /**
     * 投票计时结束尚未达成票数时的操作
     */
    private val onCancel: () -> Unit,

    /**
     * 当一位玩家投票被接受时的操作
     */
    private val onVoteAccepted: (Player) -> Unit,

    /**
     * 当一位玩家投票被拒绝时的操作
     */
    private val onVoteRejected: (Player) -> Unit,

    /**
     * 当玩家重复投票时的操作
     */
    private val onVoteDuplicated: (Player) -> Unit,
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
    private fun newVote(players: Collection<UUID>) {
        ballot.start(players)
        // Bukkit callbacks may send messages or change game state, so the timeout
        // must run on the server thread as well.
        countdownTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!ballot.running) return@Runnable
            finishRunningVote()
            onCancel()
        }, time)
    }

    /**
     * [player] 投下赞成票。
     *
     * 投票前会先判断投票进程是否开始。若未开始，使用[stopVoters]固定可投票玩家列表。
     *
     * 如果玩家不在此次投票名单中，则无法投票。
     */
    fun onPlayerVote(player: Player, stopVoters: Set<UUID>) {
        if (!ballot.running) {
            if (ballot.canVote(player.uniqueId)) {
                // 没有投票资格的人，不能发起投票
                onVoteRejected(player)
                return
            }
            newVote(stopVoters)
            onStart(player)
        }
        when (ballot.vote(player.uniqueId)) {
            VoteResult.REJECTED -> onVoteRejected(player)
            VoteResult.DUPLICATED -> onVoteDuplicated(player)
            VoteResult.ACCEPTED -> onVoteAccepted(player)
            VoteResult.PASSED -> {
                onVoteAccepted(player)
                finishRunningVote()
                onFinish()
            }
        }
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
     * 投票重复
     */
    DUPLICATED,

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
        if (!canVote(player)) return VoteResult.REJECTED
        if (!acceptedPlayers.add(player)) return VoteResult.DUPLICATED
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
