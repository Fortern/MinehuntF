package xyz.fortern.minehunt.game

import xyz.fortern.minehunt.record.GameMode
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.time.Instant

/**
 * 已通过开始校验的一局游戏会话。
 *
 * @property mode 创建本局时选中的模式标识，整局保持不变
 * @property startedAt 实际开始游戏的时间，不包含大厅和倒计时阶段
 * @property participants 开局时在线参赛者的 UUID 快照，重连不会改变该集合
 * @property tasks 只属于本局的任务作用域，游戏结束时由 [GameManager] 统一关闭
 */
class ActiveGame(
    val mode: GameMode,
    val startedAt: Instant,
    val participants: Set<UUID>,
    val tasks: GameTaskScope,
) {
    /**
     * 持久化后的对局 ID。
     *
     * `0` 表示初始记录尚未写入或写入失败；最终写入成功后可能再次更新。
     */
    @Volatile
    var recordId: Int = 0
        internal set

    /** 本局进入结束阶段的时间；游戏仍在进行时为 `null`。 */
    var endedAt: Instant? = null
        internal set

    /**
     * 初始记录写入的完成信号，用于保证最终记录在取得对局 ID 后再写入。
     */
    internal val initialRecordId = CompletableFuture<Int>()
}
