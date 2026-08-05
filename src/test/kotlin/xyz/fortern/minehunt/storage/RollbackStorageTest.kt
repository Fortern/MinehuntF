package xyz.fortern.minehunt.storage

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.fortern.minehunt.mode.manhunt.record.MinehuntRecord
import xyz.fortern.minehunt.mode.manhunt.record.PlayerInMinehunt
import xyz.fortern.minehunt.record.FinishType
import xyz.fortern.minehunt.record.GameMode
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RollbackStorageTest {
    @Test
    fun `complete record is atomically written as recoverable json`() {
        val fallbackDirectory = Path.of("target", "test-data", "rollback-${UUID.randomUUID()}")
        val storage = RollbackStorage(fallbackDirectory)
        val startedAt = Instant.ofEpochMilli(1_000)
        val netherAt = Instant.ofEpochMilli(1_500)
        val gameUuid = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val game = GameRecord(
            0,
            gameUuid,
            GameMode.MANHUNT,
            startedAt,
            Instant.ofEpochMilli(2_000),
            Duration.ofSeconds(1),
            FinishType.FINISHED,
            emptyList(),
            42L,
            mapOf("world" to 42L),
            MinehuntRecord(netherAt, null, playerId, null),
        )
        val player = PlayerInGame(
            playerId,
            0,
            1,
            PlayerInMinehunt(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap()),
        )

        val file = storage.save(game, listOf(player))

        assertTrue(Files.isRegularFile(file))
        assertEquals("game-$gameUuid.json", file.fileName.toString())
        assertFalse(Files.list(fallbackDirectory).use { files -> files.anyMatch { it.fileName.toString().endsWith(".tmp") } })
        val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonObject }
        assertEquals(2, json.get("schemaVersion").asInt)
        assertEquals(gameUuid.toString(), json.getAsJsonObject("game").get("uuid").asString)
        assertEquals("MANHUNT", json.getAsJsonObject("game").get("mode").asString)
        assertEquals(1_500L, json.getAsJsonObject("game").getAsJsonObject("details").get("firstTimeToNether").asLong)
        assertEquals(playerId.toString(), json.getAsJsonArray("players")[0].asJsonObject.get("player").asString)
    }
}
