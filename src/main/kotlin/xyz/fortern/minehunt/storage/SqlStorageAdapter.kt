package xyz.fortern.minehunt.storage

import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import javax.sql.DataSource

abstract class SqlStorageAdapter(
    protected val dataSource: DataSource,
) {
    abstract fun prepareSchema()

    abstract fun saveWholeGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>?): Int
}
