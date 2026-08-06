package xyz.fortern.minehunt.mode.bingo.record

import xyz.fortern.minehunt.record.GameDetails
import java.util.UUID

/** 一次目标完成的不可变记录。 */
data class BingoClaimRecord(
    val team: String,
    val target: String,
    val slot: Int,
    val player: UUID,
    val elapsedMillis: Long,
)

/** 正常结束时形成的获胜连线；平局时可以同时存在两队的连线。 */
data class BingoWinningLineRecord(
    val team: String,
    val slots: List<Int>,
)

/** Bingo 对局专属详情。 */
class BingoRecord(
    val cardSeed: Long,
    val targets: List<String>,
    val claims: List<BingoClaimRecord>,
    val winningLines: List<BingoWinningLineRecord>,
) : GameDetails
