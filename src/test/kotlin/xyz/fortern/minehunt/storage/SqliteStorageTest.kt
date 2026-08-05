package xyz.fortern.minehunt.storage

import org.bukkit.ChatColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

class SqliteStorageTest {
    @Test
    fun `complete record can be inserted loaded and deleted`() {
        val testDirectory = Path.of("target", "test-data")
        Files.createDirectories(testDirectory)
        val databaseFile = testDirectory.resolve("game-${UUID.randomUUID()}.db")
        val dataSource = SQLiteDataSource().also {
            it.url = "jdbc:sqlite:$databaseFile"
        }
        val storage = SqliteStorage(dataSource, Logger.getLogger("SqliteStorageTest"))
        storage.prepareSchema()

        val startedAt = Instant.ofEpochMilli(1_000)
        val firstNetherAt = Instant.ofEpochMilli(2_000)
        val gameUuid = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val finished = GameRecord(
            0,
            gameUuid,
            GameMode.MANHUNT,
            startedAt,
            Instant.ofEpochMilli(6_000),
            Duration.ofSeconds(5),
            FinishType.FINISHED,
            listOf(FactionInfo("HUNTER", ChatColor.RED, 1, listOf(playerId))),
            42L,
            mapOf("world" to 42L),
            MinehuntRecord(firstNetherAt, null, playerId, null),
        )
        val player = PlayerInGame(
            playerId,
            0,
            1,
            PlayerInMinehunt(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap()),
        )
        val gameId = storage.insertGameRecord(finished, listOf(player))
        assertTrue(gameId > 0)

        val loaded = checkNotNull(storage.getGameRecordById(gameId))
        assertEquals(gameId, loaded.id)
        assertEquals(gameUuid, loaded.uuid)
        assertEquals(GameMode.MANHUNT, loaded.mode)
        assertEquals(startedAt, loaded.startTime)
        assertEquals(Instant.ofEpochMilli(6_000), loaded.endTime)
        assertEquals(Duration.ofSeconds(5), loaded.duration)
        assertEquals(FinishType.FINISHED, loaded.finishType)
        assertEquals(42L, loaded.overworldSeed)
        assertEquals(mapOf("world" to 42L), loaded.worldSeeds)
        assertEquals(1, loaded.result.size)
        assertEquals("HUNTER", loaded.result.single().name)
        assertEquals(ChatColor.RED, loaded.result.single().color)
        assertEquals(listOf(playerId), loaded.result.single().players)
        val details = loaded.details as MinehuntRecord
        assertEquals(firstNetherAt, details.firstTimeToNether)
        assertNull(details.firstTimeToTheEnd)
        assertEquals(playerId, details.firstPlayerToNether)
        assertNull(details.firstPlayerToTheEnd)

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT uuid, mode, overworld_seed, seeds, result FROM game_record").use { result ->
                    assertTrue(result.next())
                    assertEquals(gameUuid.toString(), result.getString("uuid"))
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

        assertTrue(storage.deleteGameRecord(gameId))
        assertNull(storage.getGameRecordById(gameId))
        assertFalse(storage.deleteGameRecord(gameId))

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                listOf("game_record", "minehunt_record", "player_in_game").forEach { table ->
                    statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                        assertTrue(result.next())
                        assertEquals(0, result.getInt(1))
                    }
                }
            }
        }
    }
}
