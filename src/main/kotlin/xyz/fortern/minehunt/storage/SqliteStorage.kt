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
        CREATE_MINEHUNT_RECORD,
        CREATE_PLAYER_IN_GAME,
    )

    companion object {
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
                player_rank INTEGER NOT NULL,
                details     TEXT    NOT NULL
            );
            CREATE INDEX IF NOT EXISTS game_id_rank_idx ON $PLAYER_IN_GAME (game_id, player_rank);
            CREATE INDEX IF NOT EXISTS player_uuid_rank_idx ON $PLAYER_IN_GAME (player_uuid, player_rank);
            CREATE INDEX IF NOT EXISTS rank_idx ON $PLAYER_IN_GAME (player_rank);
        """
    }
}
