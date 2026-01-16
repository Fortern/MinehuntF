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
     * 游戏模式
     */
    val mode: GameMode,

    /**
     * 开始时间
     */
    val startTime: Instant,

    /**
     * 结束时间
     */
    val endTime: Instant,

    /**
     * 总时长（毫秒）
     */
    val duration: Duration,

    /**
     * 结束方式
     */
    val finishType: FinishType,

    /**
     * 阵营与排名
     */
    val result: List<FactionInfo>,

    /**
     * 主世界种子
     */
    val overworldSeed: Long,

    /**
     * 各个世界的种子
     */
    val worldSeeds: Map<String, Long>,

    /**
     * 特定模式的对局信息
     */
    val details: GameDetails,
)
