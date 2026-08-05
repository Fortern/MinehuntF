package xyz.fortern.minehunt.game

import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import xyz.fortern.minehunt.storage.StorageManager
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/** 一次最终持久化所需的完整普通数据快照。 */
data class CompletedGameRecord(
    val game: GameRecord,
    val players: List<PlayerInGame>,
)

/**
 * 在独立线程中插入最终记录；数据库失败或超时后立即回退到本地文件。
 *
 * 传入的 [CompletedGameRecord] 必须已在服务器主线程中构造完成，本类不会访问 Bukkit API。
 */
class GameRecordService internal constructor(
    private val logger: Logger,
    private val saveTimeout: Duration,
    private val executor: ExecutorService,
    private val databaseSave: (CompletedGameRecord) -> Int,
    private val localSave: (CompletedGameRecord) -> Path,
) : AutoCloseable {
    private val databaseTasks = ConcurrentHashMap.newKeySet<CompletableFuture<Int>>()

    @Volatile
    private var closed = false

    constructor(storageManager: StorageManager, logger: Logger) : this(
        logger,
        Duration.ofSeconds(5),
        Executors.newSingleThreadExecutor { action ->
            Thread(action, "minehunt-game-record-save").apply { isDaemon = true }
        },
        { record -> storageManager.insertGameRecord(record.game, record.players) },
        { record -> storageManager.saveToLocalFile(record.game, record.players) },
    )

    init {
        require(!saveTimeout.isZero && !saveTimeout.isNegative) { "Save timeout must be positive" }
    }

    /**
     * 启动一次且仅一次数据库插入。
     *
     * 返回的 future 总会正常完成，使生命周期无论保存结果如何都能离开 SAVING 阶段。
     */
    fun save(record: CompletedGameRecord): CompletableFuture<Unit> {
        val databaseTask: CompletableFuture<Int>
        synchronized(this) {
            if (closed) {
                return CompletableFuture.completedFuture(
                    saveLocally(record, CancellationException("Game record service is closed"))
                )
            }
            databaseTask = try {
                CompletableFuture.supplyAsync({ databaseSave(record) }, executor)
            } catch (error: Throwable) {
                return CompletableFuture.completedFuture(saveLocally(record, error))
            }
            databaseTasks.add(databaseTask)
        }

        return databaseTask
            .orTimeout(saveTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .handle { databaseId, error ->
                try {
                    if (error == null && databaseId != null && databaseId > 0) {
                        Unit
                    } else {
                        saveLocally(
                            record,
                            unwrap(error) ?: IllegalStateException("Database insert returned no generated ID"),
                        )
                    }
                } finally {
                    databaseTasks.remove(databaseTask)
                }
            }
    }

    private fun saveLocally(record: CompletedGameRecord, databaseError: Throwable) {
        logger.log(Level.WARNING, "数据库保存对局记录失败，正在回退到本地文件", databaseError)
        try {
            val file = localSave(record)
            logger.log(Level.WARNING, "对局记录已回退保存到本地文件: {0}", file)
        } catch (localError: Throwable) {
            logger.log(Level.SEVERE, "数据库与本地文件均无法保存对局记录", localError)
        }
    }

    private fun unwrap(error: Throwable?): Throwable? =
        if (error is CompletionException && error.cause != null) error.cause else error

    /** 插件关闭时让尚未完成的数据库任务立即走本地回退，并停止保存线程。 */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        databaseTasks.toList().forEach {
            it.completeExceptionally(CancellationException("Plugin is shutting down"))
        }
        executor.shutdownNow()
    }

}
