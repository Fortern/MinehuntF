package xyz.fortern.minehunt.game

import java.util.UUID

/**
 * 准备阶段中的一名玩家。
 *
 * @property lastKnownName 玩家离线后仍可用于显示的最近名称
 * @property role 当前模式定义的稳定角色标识
 */
data class LobbyMember(
    val uniqueId: UUID,
    val lastKnownName: String,
    val role: String,
)

/**
 * 准备阶段成员和角色分配的唯一数据源。
 *
 * Bukkit 计分板队伍只负责展示，不应被用于反向推断大厅成员关系。
 */
class Lobby {
    private val members = LinkedHashMap<UUID, LobbyMember>()

    fun assign(uniqueId: UUID, playerName: String, role: String) {
        members[uniqueId] = LobbyMember(uniqueId, playerName, role)
    }

    fun remove(uniqueId: UUID): LobbyMember? = members.remove(uniqueId)

    fun member(uniqueId: UUID): LobbyMember? = members[uniqueId]

    fun members(role: String): List<LobbyMember> = members.values.filter { it.role == role }

    fun allMembers(): List<LobbyMember> = members.values.toList()

    fun clear() = members.clear()
}
