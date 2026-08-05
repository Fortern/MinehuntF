package xyz.fortern.minehunt.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.intellij.lang.annotations.Language
import xyz.fortern.minehunt.mode.manhunt.record.MinehuntRecord
import xyz.fortern.minehunt.record.FactionInfo
import xyz.fortern.minehunt.record.FinishType
import xyz.fortern.minehunt.record.GameDetails
import xyz.fortern.minehunt.record.GameMode
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * 对局记录关系型存储的统一接口。
 *
 * 实现只需要提供方言相关的建表语句；其余读写行为由该类统一保证。
 * 一条对局记录及其玩家记录始终在同一事务中写入。
 */
abstract class SqlStorageAdapter(
    protected val dataSource: DataSource,
    protected val logger: Logger,
) {
    companion object {
        @Language("PlainText")
        const val GAME_RECORD = "game_record"

        @Language("PlainText")
        const val MINEHUNT_RECORD = "minehunt_record"

        @Language("PlainText")
        const val PLAYER_IN_GAME = "player_in_game"

        @Language("SQL")
        private const val INSERT_INTO_GAME_RECORD = """
            INSERT INTO $GAME_RECORD (uuid, mode, start_time, end_time, duration, finish_type, overworld_seed, seeds, result)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        @Language("SQL")
        private const val INSERT_INTO_MINEHUNT_RECORD = """
            INSERT INTO $MINEHUNT_RECORD (first_time_to_nether, first_time_to_the_end, first_player_to_nether, first_player_to_the_end, game_id)
            VALUES (?, ?, ?, ?, ?);
        """

        @Language("SQL")
        private const val DELETE_GAME_RECORD = "DELETE FROM $GAME_RECORD WHERE id = ?;"

        @Language("SQL")
        private const val DELETE_MINEHUNT_RECORD = "DELETE FROM $MINEHUNT_RECORD WHERE game_id = ?;"

        @Language("SQL")
        private const val DELETE_PLAYER_RECORDS = "DELETE FROM $PLAYER_IN_GAME WHERE game_id = ?;"

        @Language("SQL")
        private const val SELECT_GAME_RECORD = """
            SELECT game.id,
                   game.uuid,
                   game.mode,
                   game.start_time,
                   game.end_time,
                   game.duration,
                   game.finish_type,
                   game.overworld_seed,
                   game.seeds,
                   game.result,
                   minehunt.game_id AS details_game_id,
                   minehunt.first_time_to_nether,
                   minehunt.first_time_to_the_end,
                   minehunt.first_player_to_nether,
                   minehunt.first_player_to_the_end
            FROM $GAME_RECORD AS game
            LEFT JOIN $MINEHUNT_RECORD AS minehunt ON minehunt.game_id = game.id
            WHERE game.id = ?;
        """

        private val gson = GsonBuilder().serializeNulls().create()
        private val playerDetailsGson = Gson()
        private val worldSeedsType = object : TypeToken<Map<String, Long>>() {}.type
        private val factionResultsType = object : TypeToken<List<FactionInfo>>() {}.type
    }

    /** 当前数据库方言的幂等建表、建索引语句。 */
    protected abstract val schemaStatements: List<String>

    /** 创建当前实现所需的表和索引；重复调用安全。 */
    @Throws(SQLException::class)
    fun prepareSchema() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                schemaStatements.forEach(statement::executeUpdate)
            }
        }
    }

    /** 在一个事务中插入最终对局及其玩家记录，返回生成的 ID，失败时返回 `0`。 */
    fun insertGameRecord(gameRecord: GameRecord, players: List<PlayerInGame>): Int {
        require(gameRecord.id == 0) { "A new game record must not already have a database ID" }
        val connection = try {
            dataSource.connection
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Could not connect to the database.", error)
            return 0
        }
        connection.use {
            return try {
                it.autoCommit = false
                val gameId = insertGameRow(gameRecord, it)
                insertGameModeDetails(gameRecord.details, gameId, it)
                insertPlayers(players, gameId, it)
                it.commit()
                gameId
            } catch (error: Throwable) {
                logger.log(Level.SEVERE, "执行数据库操作出错，正在回滚", error)
                rollback(it)
                0
            }
        }
    }

    /** 删除指定对局及其关联记录；仅当主记录存在并完成删除时返回 `true`。 */
    fun deleteGameRecord(id: Int): Boolean {
        val connection = try {
            dataSource.connection
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Could not connect to the database.", error)
            return false
        }
        connection.use {
            return try {
                it.autoCommit = false
                deleteByGameId(it, DELETE_PLAYER_RECORDS, id)
                deleteByGameId(it, DELETE_MINEHUNT_RECORD, id)
                val deleted = deleteByGameId(it, DELETE_GAME_RECORD, id) == 1
                it.commit()
                deleted
            } catch (error: Throwable) {
                logger.log(Level.SEVERE, "删除对局记录失败，正在回滚", error)
                rollback(it)
                false
            }
        }
    }

    /** 按数据库主键读取完整对局记录；记录不存在或内容无法恢复时返回 `null`。 */
    fun getGameRecordById(id: Int): GameRecord? {
        if (id <= 0) return null
        val connection = try {
            dataSource.connection
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Could not connect to the database.", error)
            return null
        }
        connection.use {
            return try {
                it.prepareStatement(SELECT_GAME_RECORD).use { statement ->
                    statement.setInt(1, id)
                    statement.executeQuery().use { result ->
                        if (result.next()) readGameRecord(result) else null
                    }
                }
            } catch (error: Throwable) {
                logger.log(Level.SEVERE, "读取对局记录失败: $id", error)
                null
            }
        }
    }

    private fun insertGameRow(gameRecord: GameRecord, connection: Connection): Int {
        connection.prepareStatement(INSERT_INTO_GAME_RECORD, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, gameRecord.uuid.toString())
            statement.setString(2, gameRecord.mode.toString())
            statement.setLong(3, gameRecord.startTime.toEpochMilli())
            statement.setLong(4, gameRecord.endTime.toEpochMilli())
            statement.setLong(5, gameRecord.duration.toMillis())
            statement.setString(6, gameRecord.finishType.toString())
            statement.setLong(7, gameRecord.overworldSeed)
            statement.setString(8, gson.toJson(gameRecord.worldSeeds))
            statement.setString(9, gson.toJson(gameRecord.result))
            statement.executeUpdate()
            return statement.generatedKeys.use { result ->
                check(result.next()) { "Database did not return a generated game ID" }
                result.getInt(1)
            }
        }
    }

    private fun insertGameModeDetails(gameDetails: GameDetails, gameId: Int, connection: Connection) {
        if (gameDetails !is MinehuntRecord) return
        connection.prepareStatement(INSERT_INTO_MINEHUNT_RECORD).use { statement ->
            gameDetails.firstTimeToNether?.let { statement.setLong(1, it.toEpochMilli()) }
                ?: statement.setNull(1, Types.BIGINT)
            gameDetails.firstTimeToTheEnd?.let { statement.setLong(2, it.toEpochMilli()) }
                ?: statement.setNull(2, Types.BIGINT)
            statement.setString(3, gameDetails.firstPlayerToNether?.toString())
            statement.setString(4, gameDetails.firstPlayerToTheEnd?.toString())
            statement.setInt(5, gameId)
            statement.executeUpdate()
        }
    }

    private fun insertPlayers(players: List<PlayerInGame>, gameId: Int, connection: Connection) {
        if (players.isEmpty()) return
        val sql = """
            INSERT INTO $PLAYER_IN_GAME (game_id, player_uuid, player_rank, details)
            VALUES (?, ?, ?, ?)
        """
        connection.prepareStatement(sql).use { statement ->
            players.forEach { player ->
                statement.setInt(1, gameId)
                statement.setString(2, player.player.toString())
                statement.setInt(3, player.rank)
                statement.setString(4, playerDetailsGson.toJson(player.details))
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun deleteByGameId(connection: Connection, sql: String, gameId: Int): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, gameId)
            statement.executeUpdate()
        }

    private fun readGameRecord(result: ResultSet): GameRecord {
        val mode = GameMode.valueOf(result.getString("mode"))
        val details = when (mode) {
            GameMode.MANHUNT -> {
                check(result.getObject("details_game_id") != null) {
                    "Missing Manhunt details for game ${result.getInt("id")}"
                }
                MinehuntRecord(
                    readNullableInstant(result, "first_time_to_nether"),
                    readNullableInstant(result, "first_time_to_the_end"),
                    result.getString("first_player_to_nether")?.let(UUID::fromString),
                    result.getString("first_player_to_the_end")?.let(UUID::fromString),
                )
            }

            GameMode.BINGO -> error("Bingo game details are not implemented")
        }
        val worldSeeds: Map<String, Long> = gson.fromJson(result.getString("seeds"), worldSeedsType)
        val factionResults: List<FactionInfo> = gson.fromJson(result.getString("result"), factionResultsType)
        return GameRecord(
            result.getInt("id"),
            UUID.fromString(result.getString("uuid")),
            mode,
            Instant.ofEpochMilli(result.getLong("start_time")),
            Instant.ofEpochMilli(result.getLong("end_time")),
            Duration.ofMillis(result.getLong("duration")),
            FinishType.valueOf(result.getString("finish_type")),
            factionResults,
            result.getLong("overworld_seed"),
            worldSeeds,
            details,
        )
    }

    private fun readNullableInstant(result: ResultSet, column: String): Instant? {
        val epochMillis = result.getLong(column)
        return if (result.wasNull()) null else Instant.ofEpochMilli(epochMillis)
    }

    private fun rollback(connection: Connection) {
        try {
            connection.rollback()
        } catch (error: SQLException) {
            logger.log(Level.SEVERE, "回滚失败", error)
        }
    }
}
