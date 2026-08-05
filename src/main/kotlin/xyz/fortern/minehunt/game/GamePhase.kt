package xyz.fortern.minehunt.game

/**
 * 单局游戏的通用生命周期阶段。
 *
 * 所有阶段变更都必须通过 [GameStateMachine]，避免无效跳转留下只初始化或只清理了一半的游戏。
 */
enum class GamePhase {
    /** 玩家选择角色和配置规则。 */
    LOBBY,

    /** 模式已通过开始校验，正在等待统一开局。 */
    COUNTDOWN,

    /** 当前模式正在处理游戏事件和胜负条件。 */
    RUNNING,

    /** 已停止游戏任务，正在生成并保存最终记录。 */
    SAVING,

    /** 最终记录已完成保存尝试，等待服务端重开或关闭。 */
    FINISHED,
}

/** 约束 [GamePhase] 只能按照已定义的生命周期顺序转换。 */
class GameStateMachine(initialPhase: GamePhase = GamePhase.LOBBY) {
    /** 当前生命周期阶段，只能由 [transitionTo] 修改。 */
    @Volatile
    var phase: GamePhase = initialPhase
        private set

    @Synchronized
    fun transitionTo(next: GamePhase) {
        require(next in allowedTransitions.getValue(phase)) {
            "Invalid game phase transition: $phase -> $next"
        }
        phase = next
    }

    companion object {
        private val allowedTransitions = mapOf(
            GamePhase.LOBBY to setOf(GamePhase.COUNTDOWN),
            GamePhase.COUNTDOWN to setOf(GamePhase.LOBBY, GamePhase.RUNNING),
            GamePhase.RUNNING to setOf(GamePhase.SAVING),
            GamePhase.SAVING to setOf(GamePhase.FINISHED),
            GamePhase.FINISHED to setOf(GamePhase.LOBBY),
        )
    }
}
