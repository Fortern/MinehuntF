package xyz.fortern.minehunt.storage

import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * 对局记录关系型存储的统一接口。
 *
 * 实现需要保证一条对局记录及其玩家记录在同一事务中写入。
 */
abstract class SqlStorageAdapter(
    protected val dataSource: DataSource,
    protected val logger: Logger,
) {
    /** 创建当前实现所需的表和索引；重复调用必须安全。 */
    abstract fun prepareSchema()

    /**
     * 插入或更新完整对局记录。
     *
     * [players] 为 `null` 时只保存开局快照；非 `null` 时同时保存最终玩家记录。
     * 返回持久化后的对局 ID，失败时返回 `0`。
     */
    abstract fun saveWholeGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>?): Int

    abstract fun deleteGameRecord(id: Int): Boolean

    abstract fun getGameRecordById(id: Int): GameRecord?
}
