package xyz.fortern.minehunt.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.intellij.lang.annotations.Language
import org.sqlite.SQLiteDataSource
import xyz.fortern.minehunt.mode.manhunt.record.MinehuntRecord
import xyz.fortern.minehunt.record.GameDetails
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
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
                uuid           TEXT    NOT NULL UNIQUE,
                mode           TEXT    NOT NULL,
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
            INSERT INTO $GAME_RECORD (uuid, mode, start_time, end_time, duration, finish_type, overworld_seed, seeds, result)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        @Language("SQL")
        private const val DELETE_GAME_RECORD = """
            DELETE FROM $GAME_RECORD WHERE id = ?;
        """

        @Language("SQL")
        private const val INSERT_INTO_MINEHUNT_RECORD = """
            INSERT INTO $MINEHUNT_RECORD (first_time_to_nether, first_time_to_the_end, first_player_to_nether, first_player_to_the_end, game_id)
            VALUES (?, ?, ?, ?, ?);
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
                listOf(CREATE_GAME_RECORD, CREATE_MINEHUNT_RECORD, CREATE_PLAYER_IN_GAME)
                    .flatMap { it.split(';') }
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .forEach(statement::executeUpdate)
            }
        }
    }

    // 或许需要一个合适的ORM框架

    override fun insertGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>): Int {
        require(gameRecord.id == 0) { "A new game record must not already have a database ID" }
        var realGameId = 0
        val connection = try {
            dataSource.connection
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Could not connect to the database.", e)
            return 0
        }
        connection.use {
            try {
                it.autoCommit = false
                // ======== insert into GameRecord start ========
                realGameId = insertGameRow(gameRecord, it)
                // ======== insert into GameRecord end ========

                // ======== insert into GameModeRecord start ========
                insertGameModeDetails(gameRecord.details, realGameId, connection)
                // ======== insert into GameModeRecord end ========

                // ======== insert into PlayerRecord start ========
                if (players.isNotEmpty()) {
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
                realGameId = 0
            }
        }
        return realGameId
    }

    /**
     * 向数据库中插入 gameRecord，返回主键
     */
    private fun insertGameRow(gameRecord: GameRecord, connection: Connection): Int {
        val realGameId: Int
        val statement1 = connection.prepareStatement(INSERT_INTO_GAME_RECORD, Statement.RETURN_GENERATED_KEYS)
        statement1.use {
            it.setString(1, gameRecord.uuid.toString())
            it.setString(2, gameRecord.mode.toString())
            it.setLong(3, gameRecord.startTime.toEpochMilli())
            it.setLong(4, gameRecord.endTime.toEpochMilli())
            it.setLong(5, gameRecord.duration.toMillis())
            it.setString(6, gameRecord.finishType.toString())
            it.setLong(7, gameRecord.overworldSeed)
            it.setString(8, gson.toJson(gameRecord.worldSeeds))
            it.setString(9, gson.toJson(gameRecord.result))
            it.executeUpdate()
            realGameId = it.generatedKeys.let { resultSet ->
                check(resultSet.next()) { "Database did not return a generated game ID" }
                resultSet.getInt(1)
            }
        }
        return realGameId
    }

    /**
     * 插入 gameDetails
     */
    @Throws(SQLException::class)
    private fun insertGameModeDetails(gameDetails: GameDetails, realGameId: Int, connection: Connection) {
        if (gameDetails is MinehuntRecord) {
            val statement = connection.prepareStatement(INSERT_INTO_MINEHUNT_RECORD)
            statement.use { statement ->
                gameDetails.firstTimeToNether.let {
                    if (it == null) {
                        statement.setNull(1, Types.BIGINT)
                    } else {
                        statement.setLong(1, it.toEpochMilli())
                    }
                }
                gameDetails.firstTimeToTheEnd.let {
                    if (it == null) {
                        statement.setNull(2, Types.BIGINT)
                    } else {
                        statement.setLong(2, it.toEpochMilli())
                    }
                }
                statement.setString(3, gameDetails.firstPlayerToNether?.toString())
                statement.setString(4, gameDetails.firstPlayerToTheEnd?.toString())
                statement.setInt(5, realGameId)
                statement.executeUpdate()
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
