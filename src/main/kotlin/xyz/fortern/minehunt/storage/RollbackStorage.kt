package xyz.fortern.minehunt.storage

import org.sqlite.SQLiteDataSource
import xyz.fortern.minehunt.record.PlayerInGame

/**
 * 当数据源不可用时，会临时使用这个本地SQLite存储
 */
class RollbackStorage(dataSource: SQLiteDataSource) {
    fun save(playerInGame: PlayerInGame) {

    }
}