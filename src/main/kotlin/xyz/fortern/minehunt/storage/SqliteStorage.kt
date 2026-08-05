package xyz.fortern.minehunt.storage

import org.intellij.lang.annotations.Language
import java.util.logging.Logger
import javax.sql.DataSource

class SqliteStorage(
    dataSource: DataSource,
    logger: Logger,
) : SqlStorageAdapter(dataSource, logger) {
    override val schemaStatements = listOf(
        CREATE_GAME_RECORD,
        CREATE_MODE_INDEX,
        CREATE_FINISH_TYPE_INDEX,
        CREATE_DURATION_INDEX,
        CREATE_START_TIME_INDEX,
        CREATE_MINEHUNT_RECORD,
        CREATE_MINEHUNT_GAME_ID_INDEX,
        CREATE_PLAYER_IN_GAME,
        CREATE_GAME_ID_RANK_INDEX,
        CREATE_PLAYER_UUID_RANK_INDEX,
        CREATE_RANK_INDEX,
    )

    companion object {
        @Language("SQL")
        private const val CREATE_GAME_RECORD = """
            CREATE TABLE IF NOT EXISTS game_record (
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
            )
        """

        private const val CREATE_MODE_INDEX =
            "CREATE INDEX IF NOT EXISTS mode_idx ON game_record (mode)"
        private const val CREATE_FINISH_TYPE_INDEX =
            "CREATE INDEX IF NOT EXISTS finish_type_idx ON game_record (finish_type)"
        private const val CREATE_DURATION_INDEX =
            "CREATE INDEX IF NOT EXISTS duration_idx ON game_record (duration)"
        private const val CREATE_START_TIME_INDEX =
            "CREATE INDEX IF NOT EXISTS start_time_idx ON game_record (start_time)"

        @Language("SQL")
        private const val CREATE_MINEHUNT_RECORD = """
            CREATE TABLE IF NOT EXISTS minehunt_record (
                game_id                 INTEGER REFERENCES game_record (id) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL UNIQUE,
                first_time_to_nether    INTEGER,
                first_time_to_the_end   INTEGER,
                first_player_to_nether  TEXT,
                first_player_to_the_end TEXT
            )
        """

        private const val CREATE_MINEHUNT_GAME_ID_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS game_id_idx ON minehunt_record (game_id)"

        @Language("SQL")
        private const val CREATE_PLAYER_IN_GAME = """
            CREATE TABLE IF NOT EXISTS player_in_game (
                id          INTEGER PRIMARY KEY AUTOINCREMENT UNIQUE,
                game_id     INTEGER REFERENCES game_record (id) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL,
                player_uuid TEXT    NOT NULL,
                player_rank INTEGER NOT NULL,
                details     TEXT    NOT NULL
            )
        """

        private const val CREATE_GAME_ID_RANK_INDEX =
            "CREATE INDEX IF NOT EXISTS game_id_rank_idx ON player_in_game (game_id, player_rank)"
        private const val CREATE_PLAYER_UUID_RANK_INDEX =
            "CREATE INDEX IF NOT EXISTS player_uuid_rank_idx ON player_in_game (player_uuid, player_rank)"
        private const val CREATE_RANK_INDEX =
            "CREATE INDEX IF NOT EXISTS rank_idx ON player_in_game (player_rank)"
    }
}
