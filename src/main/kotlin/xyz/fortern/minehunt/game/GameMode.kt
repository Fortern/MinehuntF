package xyz.fortern.minehunt.game

import org.bukkit.entity.Player
import xyz.fortern.minehunt.record.GameMode as GameModeId
import xyz.fortern.minehunt.rule.RuleSet
import java.util.UUID

/**
 * 可选择游戏模式的运行时契约。
 *
 * 通用生命周期、投票和任务清理由 [GameManager] 负责；实现类只负责模式角色、规则、事件和结果。
 * 除非方法另有说明，回调都在服务器主线程执行。
 */
interface GameMode : AutoCloseable {
    /** 用于注册和持久化的稳定模式标识。 */
    val id: GameModeId

    /** 可供命令选择的稳定角色标识；应包含 [spectatorRole]。 */
    val roles: List<String>

    /** 新加入或未参加本局玩家使用的角色标识。 */
    val spectatorRole: String

    /** 当前模式的规则和值；规则只能在准备阶段由通用命令修改。 */
    val rules: RuleSet

    /** 可供 `/minehunt give` 请求的稳定物品标识。 */
    val specialItems: List<String>

    /** 判断角色是否应计入本局参赛者，而非观众。 */
    fun isParticipantRole(role: String): Boolean

    /**
     * 在准备阶段为玩家分配角色，并同步该模式的展示状态。
     *
     * @return 角色存在且分配成功时为 `true`
     */
    fun assignRole(player: Player, role: String): Boolean

    /** 从准备大厅及模式自己的展示状态中移除玩家。 */
    fun removeFromLobby(player: Player)

    /**
     * 校验当前大厅能否开局。
     *
     * @return 可以开始时为 `null`，否则返回面向命令发送者的失败原因
     */
    fun validateStart(): String?

    /** 返回倒计时结束时应固定到 [ActiveGame] 中的在线参赛者 UUID。 */
    fun participants(): Set<UUID>

    /**
     * 返回当前仍有资格参与终止投票的参赛者。
     *
     * 模式可在默认参赛者快照基础上排除已淘汰玩家。
     */
    fun stopVoters(activeGame: ActiveGame): Set<UUID> = activeGame.participants

    /** 初始化本局模式状态；模式任务必须注册到 [ActiveGame.tasks]。 */
    fun start(activeGame: ActiveGame)

    /** 处理已经确定的结果、恢复玩家状态并提交最终记录。 */
    fun finish(activeGame: ActiveGame, outcome: GameOutcome)

    /** 游戏进行中根据开局参赛者快照恢复重新上线玩家的身份。 */
    fun rejoin(player: Player)

    /**
     * 按模式物品标识尝试给予玩家特殊物品。
     *
     * @return 该物品标识由当前模式识别时为 `true`
     */
    fun giveSpecialItem(player: Player, item: String): Boolean

    /** 规则修改后同步计分板等模式展示。 */
    fun onRuleChanged(rule: xyz.fortern.minehunt.rule.RuleKey<*>) = Unit

    /** 释放模式实例持有的、作用域不属于单局会话的资源。 */
    override fun close() = Unit
}
