package xyz.fortern.minehunt.config

import xyz.fortern.minehunt.storage.StorageType

class GameConfiguration(
    val storage: StorageConfiguration
)

class StorageConfiguration(
    val storageType: StorageType,
    val postgresql: Postgresql,
    val mysql: Mysql,
    val sqlite: Sqlite
)

class Postgresql(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val database: String,
    val schema: String,
)

class Mysql(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val database: String,
)

class Sqlite(
    val busyTimeout: Int,
    val fileName: String,
)
