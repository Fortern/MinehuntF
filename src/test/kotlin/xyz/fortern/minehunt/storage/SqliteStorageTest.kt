package xyz.fortern.minehunt.storage

import org.bukkit.ChatColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.sqlite.SQLiteDataSource
import xyz.fortern.minehunt.mode.manhunt.record.MinehuntRecord
import xyz.fortern.minehunt.mode.manhunt.record.PlayerInMinehunt
import xyz.fortern.minehunt.record.FactionInfo
import xyz.fortern.minehunt.record.FinishType
import xyz.fortern.minehunt.record.GameMode
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SqliteStorageTest {
    @Test
    fun `initial insert and final update keep one correctly mapped game record`() {
        val testDirectory = Path.of("target", "test-data")
        Files.createDirectories(testDirectory)
        val databaseFile = testDirectory.resolve("game-${UUID.randomUUID()}.db")
        val dataSource = SQLiteDataSource().also {
            it.url = "jdbc:sqlite:$databaseFile"
        }
        val storage = SqliteStorage(dataSource, Logger.getLogger("SqliteStorageTest"))
        storage.prepareSchema()

        val startedAt = Instant.fromEpochMilliseconds(1_000)
        val initial = GameRecord(
            0,
            GameMode.MANHUNT,
            startedAt,
            startedAt,
            0.seconds,
            FinishType.NULL,
            emptyList(),
            42L,
            mapOf("world" to 42L),
            MinehuntRecord.empty(),
        )
        val gameId = storage.saveWholeGameRecord(initial, null)
        assertTrue(gameId > 0)

        val playerId = UUID.randomUUID()
        val finished = GameRecord(
            gameId,
            GameMode.MANHUNT,
            startedAt,
            Instant.fromEpochMilliseconds(6_000),
            5.seconds,
            FinishType.FINISHED,
            listOf(FactionInfo("HUNTER", ChatColor.RED, 1, listOf(playerId))),
            42L,
            mapOf("world" to 42L),
            MinehuntRecord.empty(),
        )
        val player = PlayerInGame(
            playerId,
            gameId,
            1,
            PlayerInMinehunt(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap()),
        )
        assertEquals(gameId, storage.saveWholeGameRecord(finished, listOf(player)))

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT mode, overworld_seed, seeds, result FROM game_record").use { result ->
                    assertTrue(result.next())
                    assertEquals("MANHUNT", result.getString("mode"))
                    assertEquals(42L, result.getLong("overworld_seed"))
                    assertTrue(result.getString("seeds").contains("world"))
                    assertTrue(result.getString("result").contains("HUNTER"))
                    assertTrue(!result.next())
                }
                statement.executeQuery("SELECT COUNT(*) FROM minehunt_record WHERE game_id = $gameId").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM player_in_game WHERE game_id = $gameId").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }
}
