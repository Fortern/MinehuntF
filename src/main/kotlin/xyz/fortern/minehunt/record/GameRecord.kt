package xyz.fortern.minehunt.record

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * 代表一个游戏记录
 */
class GameRecord(
    /**
     * 游戏id
     */
    val id: Int,

    /**
     * 开始事件
     */
    val startTime: Instant,

    /**
     * 结束事件
     */
    val endTime: Instant,

    /**
     * 总时长（毫秒）
     */
    val totalTime: Duration,

    /**
     * 结束方式
     */
    val finishType: FinishType,

    /**
     * 阵营与排名
     */
    val result: List<FactionInfo>,

    /**
     * 特定模式的对局信息
     */
    val specificData: GameSpecificData?,
)
