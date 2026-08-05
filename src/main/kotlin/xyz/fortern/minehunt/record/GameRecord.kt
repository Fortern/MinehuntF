package xyz.fortern.minehunt.record

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 代表一个游戏记录
 */
class GameRecord(
    /**
     * 游戏id
     */
    val id: Int,

    /** 跨数据库记录与本地回退文件保持稳定的对局标识。 */
    val uuid: UUID,

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
