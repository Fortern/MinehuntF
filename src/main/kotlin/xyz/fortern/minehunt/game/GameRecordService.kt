package xyz.fortern.minehunt.game

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import xyz.fortern.minehunt.storage.StorageManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * 一次最终持久化所需的完整数据。
 *
 * @property game 对局级记录
 * @property players 本局所有参赛者的模式专属记录
 */
data class CompletedGameRecord(
    val game: GameRecord,
    val players: List<PlayerInGame>,
)

/**
 * 在不阻塞服务器主线程的情况下，顺序执行初始记录插入和最终记录更新。
 */
class GameRecordService(
    private val plugin: JavaPlugin,
    private val storageManager: StorageManager,
) : AutoCloseable {
    private val tasks = ConcurrentHashMap.newKeySet<BukkitTask>()

    @Volatile
    private var closed = false

    /**
     * 异步写入开局快照，并返回用于发布生成 ID 的 future。
     */
    fun saveInitial(record: GameRecord): CompletableFuture<Int> {
        val recordId = CompletableFuture<Int>()
        if (!runAsync {
            val id = try {
                storageManager.saveWholeGameRecord(record, null)
            } catch (error: Throwable) {
                plugin.logger.log(Level.SEVERE, "保存初始对局记录失败", error)
                0
            }
            recordId.complete(id)
        }) {
            recordId.complete(0)
        }
        return recordId
    }

    /**
     * 等待初始写入完成后异步构造并保存最终记录。
     *
     * [recordFactory] 在线程外执行，只能使用调用前捕获的普通数据，不得在其中访问 Bukkit API。
     * 初始写入失败时会以 `0` 作为 ID，让存储层尝试直接插入最终记录。
     */
    fun saveFinal(initialRecordId: CompletableFuture<Int>, recordFactory: (Int) -> CompletedGameRecord) {
        initialRecordId.whenComplete { initialId, error ->
            val usableId = if (error == null) initialId else 0
            runAsync {
                try {
                    val completed = recordFactory(usableId)
                    storageManager.saveWholeGameRecord(completed.game, completed.players)
                } catch (saveError: Throwable) {
                    plugin.logger.log(Level.SEVERE, "保存最终对局记录失败", saveError)
                }
            }
        }
    }

    @Synchronized
    private fun runAsync(action: () -> Unit): Boolean {
        if (closed) return false
        return try {
            tasks.add(plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable(action)))
            true
        } catch (error: IllegalStateException) {
            false
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        tasks.forEach(BukkitTask::cancel)
        tasks.clear()
    }
}
