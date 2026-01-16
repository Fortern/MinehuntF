package xyz.fortern.minehunt.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.intellij.lang.annotations.Language
import org.sqlite.SQLiteDataSource
import xyz.fortern.minehunt.record.GameDetails
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.MinehuntRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.sql.Connection
import java.sql.SQLException
import java.sql.Types
import java.util.logging.Level
import java.util.logging.Logger

class SqliteStorage(
    dataSource: SQLiteDataSource,
    logger: Logger
) : SqlStorageAdapter(dataSource, logger) {
    companion object {
        @Language("PlainText")
        private const val GAME_RECORD = "game_record"

        @Language("PlainText")
        private const val MINEHUNT_RECORD = "minehunt_record"

        @Language("PlainText")
        private const val PLAYER_IN_GAME = "player_in_game"

        @Language("SQL")
        private const val CREATE_GAME_RECORD = """
            CREATE TABLE IF NOT EXISTS $GAME_RECORD (
                id             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE,
                mode           INTEGER NOT NULL,
                start_time     INTEGER NOT NULL,
                end_time       INTEGER NOT NULL,
                duration       INTEGER NOT NULL,
                finish_type    TEXT    NOT NULL,
                overworld_seed INTEGER NOT NULL,
                seeds          TEXT    NOT NULL,
                result         TEXT    NOT NULL
            );
            CREATE INDEX IF NOT EXISTS mode_idx ON $GAME_RECORD (mode);
            CREATE INDEX IF NOT EXISTS finish_type_idx ON $GAME_RECORD (finish_type);
            CREATE INDEX IF NOT EXISTS duration_idx ON $GAME_RECORD (duration);
            CREATE INDEX IF NOT EXISTS start_time_idx ON $GAME_RECORD (start_time);
        """

        @Language("SQL")
        private const val CREATE_MINEHUNT_RECORD = """
            CREATE TABLE IF NOT EXISTS $MINEHUNT_RECORD (
                game_id                 INTEGER REFERENCES $GAME_RECORD (id) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL UNIQUE,
                first_time_to_nether    INTEGER,
                first_time_to_the_end   INTEGER,
                first_player_to_nether  TEXT,
                first_player_to_the_end TEXT
            );
            CREATE UNIQUE INDEX IF NOT EXISTS game_id_idx ON $MINEHUNT_RECORD (game_id);
        """

        @Language("SQL")
        private const val CREATE_PLAYER_IN_GAME = """
            CREATE TABLE IF NOT EXISTS $PLAYER_IN_GAME (
                id          INTEGER PRIMARY KEY AUTOINCREMENT UNIQUE,
                game_id     INTEGER REFERENCES $GAME_RECORD (id) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL,
                player_uuid TEXT    NOT NULL,
                rank        INTEGER NOT NULL,
                details     TEXT    NOT NULL
            );
            CREATE INDEX IF NOT EXISTS game_id_rank_idx ON $PLAYER_IN_GAME (game_id, rank);
            CREATE INDEX IF NOT EXISTS player_uuid_rank_idx ON $PLAYER_IN_GAME (player_uuid,rank);
            CREATE INDEX IF NOT EXISTS rank_idx ON $PLAYER_IN_GAME (rank);
        """

        @Language("SQL")
        private const val INSERT_INTO_GAME_RECORD = """
            INSERT INTO $GAME_RECORD (mode, start_time, end_time, duration, finish_type, overworld_seed, seeds, result)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """

        @Language("SQL")
        private const val UPDATE_GAME_RECORD = """
            UPDATE $GAME_RECORD SET mode = ?, start_time = ?, end_time = ?, duration = ?, finish_type = ?, overworld_seed = ?, seeds = ?, result = ?
            WHERE id = ?;
        """

        @Language("SQL")
        private const val DELETE_GAME_RECORD = """
            DELETE FROM $GAME_RECORD WHERE id = ?;
        """

        @Language("SQL")
        private const val UPSERT_MINEHUNT_RECORD = """
            INSERT INTO $MINEHUNT_RECORD (first_time_to_nether, first_time_to_the_end, first_player_to_nether, first_player_to_the_end, game_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (game_id)
            DO UPDATE SET first_time_to_nether = ?, first_time_to_the_end = ?, first_player_to_nether = ?, first_player_to_the_end = ?
            WHERE game_id = ?;
            ;
        """

        @Language("SQL")
        private const val INSERT_INTO_PLAYER_IN_GAME = """
            INSERT INTO $PLAYER_IN_GAME (game_id, player_uuid, rank, details)
            VALUES (?, ?, ?, ?);
        """

        private val gson = GsonBuilder().serializeNulls().create()
    }

    @Throws(SQLException::class)
    override fun prepareSchema() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(CREATE_GAME_RECORD)
                statement.executeUpdate(CREATE_MINEHUNT_RECORD)
                statement.executeUpdate(CREATE_PLAYER_IN_GAME)
            }
        }
    }

    // 或许需要一个合适的ORM框架

    override fun saveWholeGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>?): Int {
        var realGameId = gameRecord.id
        val connection = try {
            dataSource.connection
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Could not connect to the database.", e)
            return 0
        }
        connection.use {
            try {
                it.autoCommit = false
                // ======== insert into / update GameRecord start ========
                realGameId = upsertGameRecord(gameRecord, it)
                // ======== insert into / update GameRecord end ========

                // ======== insert into / update GameModeRecord start ========
                upsertGameModeDetails(gameRecord.details, realGameId, connection)
                // ======== insert into / update GameModeRecord end ========

                // ======== insert into PlayerRecord start ========
                if (!players.isNullOrEmpty()) {
                    it.prepareStatement(INSERT_INTO_PLAYER_IN_GAME).use { statement ->
                        players.forEach { playerInGame ->
                            statement.setInt(1, realGameId)
                            statement.setString(2, playerInGame.player.toString())
                            statement.setInt(3, playerInGame.rank)
                            statement.setString(4, Gson().toJson(playerInGame.details))
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
                // ======== insert into PlayerRecord end ========
                it.commit()
            } catch (e: Throwable) {
                logger.log(Level.SEVERE, "执行数据库操作出错，正在回滚", e)
                try {
                    it.rollback()
                } catch (e: SQLException) {
                    logger.log(Level.SEVERE, "回滚失败", e)
                }
            }
        }
        return realGameId
    }

    /**
     * 向数据库中插入 gameRecord，返回主键
     */
    private fun upsertGameRecord(gameRecord: GameRecord, connection: Connection): Int {
        val insert = gameRecord.id == 0
        val realGameId: Int
        val statement1 = if (insert) {
            connection.prepareStatement(INSERT_INTO_GAME_RECORD)
        } else {
            connection.prepareStatement(UPDATE_GAME_RECORD)
        }
        statement1.use {
            it.setString(1, gameRecord.mode.toString())
            it.setLong(2, gameRecord.startTime.toEpochMilliseconds())
            it.setLong(3, gameRecord.endTime.toEpochMilliseconds())
            it.setLong(4, gameRecord.duration.inWholeMilliseconds)
            it.setString(5, gameRecord.finishType.toString())
            it.setString(6, gson.toJson(gameRecord.result))
            it.setLong(7, gameRecord.overworldSeed)
            it.setString(8, gson.toJson(gameRecord.worldSeeds))
            if (!insert) {
                it.setInt(9, gameRecord.id)
            }
            it.executeUpdate()
            realGameId = if (insert) {
                it.generatedKeys.let { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
            } else {
                gameRecord.id
            }
        }
        return realGameId
    }

    /**
     * 插入或更新 gameDetails
     */
    @Throws(SQLException::class)
    private fun upsertGameModeDetails(gameDetails: GameDetails, realGameId: Int, connection: Connection) {
        if (gameDetails is MinehuntRecord) {
            val statement = connection.prepareStatement(UPSERT_MINEHUNT_RECORD)
            statement.use { statement ->
                gameDetails.firstTimeToNether.let {
                    if (it == null) {
                        statement.setNull(1, Types.BIGINT)
                        statement.setNull(1 + 5, Types.BIGINT)
                    } else {
                        statement.setLong(1, it.toEpochMilliseconds())
                        statement.setLong(1 + 5, it.toEpochMilliseconds())
                    }
                }
                gameDetails.firstTimeToTheEnd.let {
                    if (it == null) {
                        statement.setNull(2, Types.BIGINT)
                        statement.setNull(2 + 5, Types.BIGINT)
                    } else {
                        statement.setLong(2, it.toEpochMilliseconds())
                        statement.setLong(2 + 5, it.toEpochMilliseconds())
                    }
                }
                var playerUUID = gameDetails.firstPlayerToNether?.toString()
                statement.setString(3, playerUUID)
                statement.setString(3 + 5, playerUUID)
                playerUUID = gameDetails.firstPlayerToTheEnd?.toString()
                statement.setString(4, playerUUID)
                statement.setString(4 + 5, playerUUID)
                statement.setInt(5, realGameId)
                statement.setInt(5 + 5, realGameId)
            }
        }
    }

    override fun deleteGameRecord(id: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun getGameRecordById(id: Int): GameRecord? {
        TODO("Not yet implemented")
    }
}