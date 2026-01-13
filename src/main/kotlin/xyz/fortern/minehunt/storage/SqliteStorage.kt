package xyz.fortern.minehunt.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.intellij.lang.annotations.Language
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.MinehuntRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.sql.SQLException
import java.sql.Types
import javax.sql.DataSource

class SqliteStorage(dataSource: DataSource) : SqlStorageAdapter(dataSource) {
    companion object {
        @Language("PlainText")
        private const val GAME_RECORD = "game_record"

        @Language("PlainText")
        private const val MINEHUNT_RECORD = "minehunt_record"

        @Language("PlainText")
        private const val PLAYER_IN_GAME = "player_in_game"

        @Language("SQL")
        private const val CREATE_GAME_RECORD = """
            CREATE TABLE $GAME_RECORD (
                id          INTEGER PRIMARY KEY NOT NULL UNIQUE,
                mode        INTEGER NOT NULL,
                start_time  INTEGER NOT NULL,
                end_time    INTEGER NOT NULL,
                duration    INTEGER NOT NULL,
                finish_type TEXT    NOT NULL,
                result      TEXT    NOT NULL
            );
            CREATE INDEX IF NOT EXISTS mode_idx ON $GAME_RECORD (mode);
            CREATE INDEX IF NOT EXISTS finish_type_idx ON $GAME_RECORD (finish_type);
            CREATE INDEX IF NOT EXISTS duration_idx ON $GAME_RECORD (duration);
            CREATE INDEX IF NOT EXISTS start_time_idx ON $GAME_RECORD (start_time);
        """

        @Language("SQL")
        private const val CREATE_MINEHUNT_RECORD = """
            CREATE TABLE IF NOT EXISTS $MINEHUNT_RECORD (
                game_id                 INTEGER REFERENCES $GAME_RECORD (id) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL,
                first_time_to_nether    INTEGER,
                first_time_to_the_end   INTEGER,
                first_player_to_nether  TEXT,
                first_player_to_the_end TEXT
            );
            CREATE INDEX IF NOT EXISTS $MINEHUNT_RECORD ON minehunt_record (game_id);
        """

        @Language("SQL")
        private const val CREATE_PLAYER_IN_GAME = """
            CREATE TABLE $PLAYER_IN_GAME (
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
            INSERT INTO $GAME_RECORD (mode, start_time, end_time, duration, finish_type, result)
            VALUES (?, ?, ?, ?, ?, ?);
        """

        @Language("SQL")
        private const val UPDATE_GAME_RECORD = """
            UPDATE $GAME_RECORD SET mode = ?, start_time = ?, end_time = ?, duration = ?, result = ?
            WHERE id = ?;
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
        private const val UPDATE_MINEHUNT_RECORD = """
            UPDATE $MINEHUNT_RECORD SET first_time_to_nether = ?, first_time_to_the_end = ?, first_player_to_nether = ?, first_player_to_the_end = ?
            where game_id = ?;
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

    @Throws(SQLException::class)
    override fun saveWholeGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>?): Int {
        var realGameId: Int
        val insert = gameRecord.id == 0
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            val gameId = gameRecord.id
            // ======== insert into / update GameRecord start ========
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
                if (!insert) {
                    it.setInt(7, gameId)
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
            // ======== insert into / update GameRecord end ========

            // ======== insert into / update GameModeRecord start ========
            val gameDetails = gameRecord.specificData
            if (gameDetails is MinehuntRecord) {
                val statement2 = if (gameId == 0) {
                    connection.prepareStatement(INSERT_INTO_MINEHUNT_RECORD)
                } else {
                    connection.prepareStatement(UPDATE_MINEHUNT_RECORD)
                }
                statement2.use { statement ->
                    gameDetails.firstTimeToNether.let {
                        if (it == null)
                            statement.setNull(1, Types.BIGINT)
                        else
                            statement.setLong(1, it.toEpochMilliseconds())
                    }
                    gameDetails.firstTimeToTheEnd.let {
                        if (it == null)
                            statement.setNull(2, Types.BIGINT)
                        else
                            statement.setLong(2, it.toEpochMilliseconds())
                    }
                    statement.setString(3, gameDetails.firstPlayerToNether?.toString())
                    statement.setString(4, gameDetails.firstTimeToTheEnd?.toString())
                    statement.setInt(5, realGameId)
                }
            }
            // ======== insert into / update GameModeRecord end ========

            // ======== insert into PlayerRecord start ========
            if (!players.isNullOrEmpty()) {
                connection.prepareStatement(INSERT_INTO_PLAYER_IN_GAME).use {
                    players.forEach { playerInGame ->
                        it.setInt(1, realGameId)
                        it.setString(2, playerInGame.player.toString())
                        it.setInt(3, playerInGame.rank)
                        it.setString(4, Gson().toJson(playerInGame.details))
                        it.addBatch()
                    }
                    it.executeBatch()
                }
            }
            // ======== insert into PlayerRecord end ========
            connection.commit()
        }
        return realGameId
    }
}