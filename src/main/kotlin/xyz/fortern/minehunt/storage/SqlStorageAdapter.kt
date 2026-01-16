package xyz.fortern.minehunt.storage

import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.util.logging.Logger
import javax.sql.DataSource

abstract class SqlStorageAdapter(
    protected val dataSource: DataSource,
    protected val logger: Logger,
) {
    abstract fun prepareSchema()

    abstract fun saveWholeGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>?): Int

    abstract fun deleteGameRecord(id: Int): Boolean

    abstract fun getGameRecordById(id: Int): GameRecord?
}
