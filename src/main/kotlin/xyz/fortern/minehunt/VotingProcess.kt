package xyz.fortern.minehunt

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * 记录投票相关的数据。
 * 投票无法撤销。
 * 没有多线程保护。
 */
class VoteProcess(
    /**
     * plugin
     */
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

    /**
     * 投票统计
     */
    private var playerMap: MutableMap<UUID, Boolean> = HashMap()

    /**
     * 投下赞成票的人数
     */
    var pollingNum: Int = 0
        private set

    /**
     * 投票是否正在进行中
     */
    var running: Boolean = false
        private set

    /**
     * 用于投票倒计时的task
     */
    var countdownTask: BukkitTask? = null

    /**
     * 新的投票进程
     */
    fun newVote(players: List<Player>) {
        playerMap.clear()
        players.forEach {
            playerMap[it.uniqueId] = false
        }
        countdownTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, howtoCancel, time)
        running = true
    }

    /**
     * 玩家投下赞成票。
     *
     * 投票前应先判断投票进程是否开始。
     * 如未开始则应当先调用 [newVote]。
     *
     * 如果玩家不再 [playerMap] 中，则无法投票。
     *
     * @throws RuntimeException 投票未开始时调用会抛出异常
     */
    fun onPlayerVote(player: Player) {
        if (!running) {
            throw RuntimeException("投票未开始")
        }
        // 不能投票，或已经投票，直接返回
        if (!playerMap.contains(player.uniqueId) || playerMap[player.uniqueId] == true) {
            return
        }
        playerMap[player.uniqueId] = true
        pollingNum++
        onVote()

        // 票数是否足够？
        if (pollingNum * 1.0f / playerMap.size >= rate) {
            countdownTask?.cancel()
            countdownTask = null
            running = false
            howtoFinish()
        }
    }

    /**
     * 玩家能否投票
     */
    fun canVote(player: Player): Boolean {
        return playerMap.containsKey(player.uniqueId)
    }

    /**
     * 投票正在进行
     */
    fun isRunning(): Boolean = running

    /**
     * 投赞成票的玩家数量
     */
    fun pollingNum(): Int = pollingNum

    /**
     * 参与投票的玩家数量
     */
    fun playersNum(): Int = playerMap.size

    /**
     * 取消投票进程
     */
    fun cancel() {
        countdownTask?.cancel()
        countdownTask = null
        running = false
    }
}