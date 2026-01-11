package xyz.fortern.minehunt.util

import org.intellij.lang.annotations.Language
import xyz.fortern.minehunt.record.PlayerInMinehunt
import java.sql.DriverManager
import java.sql.SQLException

/**
 * 数据库工具类
 *
 * 考虑到与数据库交互极少，我们不使用连接池
 *
 * TODO 适配多种数据库
 */
class DB {
    companion object {
        private const val FILE = "record.db"
        private const val URL = "jdbc:sqlite:$FILE"
        @Language("PlainText")
        private const val PLAYER_RECORD = "player_record"
        @Language("PlainText")
        private const val PLAYER_IN_GAME = "player_in_game"

        @Language("SQL")
        private const val CREATE_GAME_RECORD_SQL = """
            CREATE TABLE $PLAYER_RECORD (
                id         INTEGER PRIMARY KEY NOT NULL UNIQUE,
                mode       TEXT    NOT NULL,
                start_time INTEGER NOT NULL,
                end_time   INTEGER NOT NULL,
                result     TEXT    NOT NULL
            );
        """

        @Language("SQL")
        private const val CREATE_PLAYER_IN_GAME_SQL = """
            CREATE TABLE $PLAYER_IN_GAME (
                
            );
        """

        fun init() {
            try {
                DriverManager.getConnection(URL).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeUpdate(CREATE_GAME_RECORD_SQL)

                    }
                }
            } catch (e: SQLException) {
                // if the error message is "out of memory",
                // it probably means no database file is found
                e.printStackTrace(System.err)
            }

        }

        fun insert(playerInMinehunt: PlayerInMinehunt): Int {
            try {
                DriverManager.getConnection(URL).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.queryTimeout = 30
                        val rs = statement.executeUpdate("insert into person values(2, 'yui')")
//                        while (rs.next()) {
//                            // read the result set
//                            println("name = " + rs.getString("name"))
//                            println("id = " + rs.getInt("id"))
//                        }
                    }
                }
            } catch (e: SQLException) {
                // if the error message is "out of memory",
                // it probably means no database file is found
                e.printStackTrace(System.err)
            }
            return 0
        }
    }

}
