package xyz.fortern.minehunt.game

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

/**
 * 跟踪同一生命周期内创建的 Bukkit 任务，并在关闭时统一取消。
 *
 * 作用域关闭后再调度任务会立即取消该任务并抛出异常，避免任务逃逸到下一局游戏。
 */
class GameTaskScope(private val plugin: Plugin) : AutoCloseable {
    private val tasks = ConcurrentHashMap.newKeySet<BukkitTask>()

    @Volatile
    private var closed = false

    /** 在服务器主线程延迟指定 tick 数执行一次。 */
    fun runLater(delayTicks: Long, action: () -> Unit): BukkitTask =
        register(plugin.server.scheduler.runTaskLater(plugin, Runnable(action), delayTicks))

    /** 在服务器主线程按指定 tick 周期重复执行。 */
    fun runTimer(delayTicks: Long, periodTicks: Long, action: () -> Unit): BukkitTask =
        register(plugin.server.scheduler.runTaskTimer(plugin, Runnable(action), delayTicks, periodTicks))

    /** 在线程外执行不访问 Bukkit API 的工作。 */
    fun runAsync(action: () -> Unit): BukkitTask =
        register(plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable(action)))

    /** 提前取消并移除某个已注册任务；传入 `null` 时不执行操作。 */
    @Synchronized
    fun cancel(task: BukkitTask?) {
        if (task == null) return
        task.cancel()
        tasks.remove(task)
    }

    @Synchronized
    private fun register(task: BukkitTask): BukkitTask {
        if (closed) {
            task.cancel()
            error("Task scope is already closed")
        }
        tasks.add(task)
        return task
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        tasks.forEach(BukkitTask::cancel)
        tasks.clear()
    }
}
