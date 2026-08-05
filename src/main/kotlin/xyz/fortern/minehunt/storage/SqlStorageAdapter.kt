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

    /** 在一个事务中插入最终对局及其玩家记录，返回生成的 ID，失败时返回 `0`。 */
    abstract fun insertGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>): Int

    /** 删除指定对局及其关联记录；仅当主记录存在并完成删除时返回 `true`。 */
    abstract fun deleteGameRecord(id: Int): Boolean

    /** 按数据库主键读取完整对局记录；记录不存在或内容无法恢复时返回 `null`。 */
    abstract fun getGameRecordById(id: Int): GameRecord?
}
