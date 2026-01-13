package xyz.fortern.minehunt.record

import java.util.*

class PlayerInGame(
    /**
     * 玩家的UUID
     */
    val player: UUID,

    /**
     * 所在对局ID
     */
    val gameId: Int,

    /**
     * 游戏排名
     */
    val rank: Int,

    /**
     * 详细的玩家数据
     */
    val details: PlayerDetails,
)
