package xyz.fortern.minehunt.storage

import org.intellij.lang.annotations.Language
import java.util.logging.Logger
import javax.sql.DataSource

class MysqlStorage(
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
                id             INT         NOT NULL AUTO_INCREMENT,
                uuid           CHAR(36)    NOT NULL,
                mode           VARCHAR(32) NOT NULL,
                start_time     BIGINT      NOT NULL,
                end_time       BIGINT      NOT NULL,
                duration       BIGINT      NOT NULL,
                finish_type    VARCHAR(32) NOT NULL,
                overworld_seed BIGINT      NOT NULL,
                seeds          LONGTEXT    NOT NULL,
                result         LONGTEXT    NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY game_record_uuid_uq (uuid),
                KEY game_record_mode_idx (mode),
                KEY game_record_finish_type_idx (finish_type),
                KEY game_record_duration_idx (duration),
                KEY game_record_start_time_idx (start_time)
            ) ENGINE = InnoDB;
        """

        @Language("SQL")
        private const val CREATE_MINEHUNT_RECORD = """
            CREATE TABLE IF NOT EXISTS $MINEHUNT_RECORD (
                game_id                 INT      NOT NULL,
                first_time_to_nether    BIGINT,
                first_time_to_the_end   BIGINT,
                first_player_to_nether  CHAR(36),
                first_player_to_the_end CHAR(36),
                PRIMARY KEY (game_id),
                CONSTRAINT minehunt_record_game_fk FOREIGN KEY (game_id)
                    REFERENCES $GAME_RECORD (id) ON DELETE CASCADE ON UPDATE CASCADE
            ) ENGINE = InnoDB;
        """

        @Language("SQL")
        private const val CREATE_PLAYER_IN_GAME = """
            CREATE TABLE IF NOT EXISTS $PLAYER_IN_GAME (
                id          INT      NOT NULL AUTO_INCREMENT,
                game_id     INT      NOT NULL,
                player_uuid CHAR(36) NOT NULL,
                player_rank INT      NOT NULL,
                details     LONGTEXT NOT NULL,
                PRIMARY KEY (id),
                KEY player_in_game_game_rank_idx (game_id, player_rank),
                KEY player_in_game_player_rank_idx (player_uuid, player_rank),
                KEY player_in_game_rank_idx (player_rank),
                CONSTRAINT player_in_game_game_fk FOREIGN KEY (game_id)
                    REFERENCES $GAME_RECORD (id) ON DELETE CASCADE ON UPDATE CASCADE
            ) ENGINE = InnoDB;
        """
    }
}
