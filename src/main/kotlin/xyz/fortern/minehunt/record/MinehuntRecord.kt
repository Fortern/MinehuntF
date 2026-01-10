package xyz.fortern.minehunt.record

import java.util.*
import kotlin.time.Instant

/**
 * 猎人模式对局详情
 */
class MinehuntRecord(
    /**
     * 首次进入下界的时间
     */
    val firstTimeToNether: Instant?,

    /**
     * 首次进入末地的时间
     */
    val firstTimeToTheEnd: Instant?,

    /**
     * 第一个进入下界的玩家
     */
    val firstPlayerToNether: UUID?,

    /**
     * 第一个进入末地的玩家
     */
    val firstPlayerToTheEnd: UUID?,
) : GameSpecificData
