package xyz.fortern.minehunt.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant

/** 将数据库未接收的完整对局记录保存为可人工恢复的本地 JSON 文件。 */
class RollbackStorage(private val directory: Path) {
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .registerTypeAdapter(
            Instant::class.java,
            JsonSerializer<Instant> { value, _, _ -> JsonPrimitive(value.toEpochMilli()) },
        )
        .registerTypeAdapter(
            Duration::class.java,
            JsonSerializer<Duration> { value, _, _ -> JsonPrimitive(value.toMillis()) },
        )
        .create()

    /** 使用同目录临时文件和原子替换，避免留下看似完整的半截记录。 */
    fun save(gameRecord: GameRecord, players: List<PlayerInGame>): Path {
        Files.createDirectories(directory)
        val fileName = "game-${gameRecord.uuid}.json"
        val destination = directory.resolve(fileName)
        if (Files.isRegularFile(destination)) return destination
        val temporary = directory.resolve(".$fileName.tmp")

        val root = JsonObject().apply {
            addProperty("schemaVersion", 2)
            addProperty("gameDetailsType", gameRecord.details.javaClass.name)
            add("game", gson.toJsonTree(gameRecord))
            add("players", gson.toJsonTree(players.map { player ->
                JsonObject().apply {
                    addProperty("player", player.player.toString())
                    addProperty("gameId", player.gameId)
                    addProperty("rank", player.rank)
                    addProperty("detailsType", player.details.javaClass.name)
                    add("details", gson.toJsonTree(player.details))
                }
            }))
        }

        try {
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { writer ->
                gson.toJson(root, writer)
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return destination
    }
}
