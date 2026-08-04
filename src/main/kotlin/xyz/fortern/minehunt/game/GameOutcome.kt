package xyz.fortern.minehunt.game

import xyz.fortern.minehunt.record.FinishType

/**
 * 模式提交给 [GameManager] 的结束结果。
 *
 * @property winnerRole 获胜方在当前模式中的稳定角色标识；终止或无胜者时为 `null`
 * @property finishType 正常完成、主动终止等与模式无关的结束类型
 */
data class GameOutcome(
    val winnerRole: String?,
    val finishType: FinishType,
)
