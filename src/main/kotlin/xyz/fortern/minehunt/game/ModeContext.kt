package xyz.fortern.minehunt.game

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.java.JavaPlugin

/**
 * [GameManager] 提供给模式实例的受限运行环境。
 *
 * @property lobby 准备阶段成员关系的唯一数据源
 * @property records 负责按正确顺序异步保存初始和最终记录的服务
 */
class ModeContext internal constructor(
    val plugin: JavaPlugin,
    val adventure: BukkitAudiences,
    val lobby: Lobby,
    val records: GameRecordService,
    private val phaseProvider: () -> GamePhase,
    private val activeGameProvider: () -> ActiveGame?,
    private val finishGame: (GameOutcome) -> Unit,
) {
    /** 当前阶段的实时值，不是创建模式时的快照。 */
    val phase: GamePhase
        get() = phaseProvider()

    /** 当前正在运行或结束处理中的会话；尚未开局时为 `null`。 */
    val activeGame: ActiveGame?
        get() = activeGameProvider()

    /** 将模式判定的结果交回管理器，统一进入结束流程。 */
    fun finish(outcome: GameOutcome) = finishGame(outcome)
}

/** 每次选择模式时创建全新 [GameMode] 实例的工厂。 */
fun interface GameModeFactory {
    fun create(context: ModeContext): GameMode
}
