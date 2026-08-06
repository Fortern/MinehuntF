package xyz.fortern.minehunt.mode.bingo.record

import xyz.fortern.minehunt.record.PlayerDetails

/** Bingo 模式下单个参赛者所属队伍及其首次完成的目标。 */
class PlayerInBingo(
    val team: String,
    val completedTargets: List<String>,
) : PlayerDetails
