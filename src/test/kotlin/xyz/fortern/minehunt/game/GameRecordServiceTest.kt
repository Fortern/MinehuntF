package xyz.fortern.minehunt.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.fortern.minehunt.mode.manhunt.record.MinehuntRecord
import xyz.fortern.minehunt.record.FinishType
import xyz.fortern.minehunt.record.GameMode
import xyz.fortern.minehunt.record.GameRecord
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class GameRecordServiceTest {
    @Test
    fun `successful database insert runs once without local fallback`() {
        val databaseCalls = AtomicInteger()
        val localCalled = AtomicBoolean()
        val service = service(
            databaseSave = {
                databaseCalls.incrementAndGet()
                42
            },
            localSave = {
                localCalled.set(true)
                Path.of("unused.json")
            },
        )

        service.use { it.save(completedRecord()).get(2, TimeUnit.SECONDS) }

        assertEquals(1, databaseCalls.get())
        assertFalse(localCalled.get())
    }

    @Test
    fun `database failure falls back to local file`() {
        val fallbackFile = Path.of("fallback.json")
        var savedFile: Path? = null
        val service = service(
            databaseSave = { throw IllegalStateException("database unavailable") },
            localSave = {
                savedFile = fallbackFile
                fallbackFile
            },
        )

        service.use { it.save(completedRecord()).get(2, TimeUnit.SECONDS) }

        assertEquals(fallbackFile, savedFile)
    }

    @Test
    fun `database timeout falls back without waiting for blocked insert`() {
        val databaseStarted = CountDownLatch(1)
        val releaseDatabase = CountDownLatch(1)
        val fallbackCalled = AtomicBoolean()
        val service = service(
            timeout = Duration.ofMillis(100),
            databaseSave = {
                databaseStarted.countDown()
                releaseDatabase.await()
                42
            },
            localSave = {
                fallbackCalled.set(true)
                Path.of("timeout.json")
            },
        )

        try {
            val future = service.save(completedRecord())
            assertTrue(databaseStarted.await(1, TimeUnit.SECONDS))

            future.get(2, TimeUnit.SECONDS)

            assertTrue(fallbackCalled.get())
        } finally {
            releaseDatabase.countDown()
            service.close()
        }
    }

    @Test
    fun `local fallback failure still completes save future`() {
        val service = service(
            databaseSave = { throw IllegalStateException("database unavailable") },
            localSave = { throw IllegalStateException("disk unavailable") },
        )

        service.use { it.save(completedRecord()).get(2, TimeUnit.SECONDS) }
    }

    private fun service(
        timeout: Duration = Duration.ofSeconds(1),
        databaseSave: (CompletedGameRecord) -> Int,
        localSave: (CompletedGameRecord) -> Path,
    ): GameRecordService = GameRecordService(
        Logger.getAnonymousLogger().apply {
            level = Level.OFF
            useParentHandlers = false
        },
        timeout,
        Executors.newSingleThreadExecutor(),
        databaseSave,
        localSave,
    )

    private fun completedRecord(): CompletedGameRecord {
        val startedAt = Instant.ofEpochMilli(1_000)
        return CompletedGameRecord(
            GameRecord(
                0,
                UUID.randomUUID(),
                GameMode.MANHUNT,
                startedAt,
                Instant.ofEpochMilli(2_000),
                Duration.ofSeconds(1),
                FinishType.FINISHED,
                emptyList(),
                42L,
                mapOf("world" to 42L),
                MinehuntRecord.empty(),
            ),
            emptyList(),
        )
    }
}
