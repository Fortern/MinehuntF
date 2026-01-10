package xyz.fortern.minehunt.record

import org.bukkit.ChatColor
import java.util.*

/**
 * 代表游戏中的一个阵营
 */
class FactionInfo(
    /**
     * 阵营名称
     */
    val name: String,
    /**
     * 阵营颜色
     */
    val color: ChatColor,
    /**
     * 排名
     *
     * 0表示尚未进行排名或平局
     */
    val rank: Int,
    /**
     * 玩家列表
     */
    val players: List<UUID>,
)
