package xyz.fortern.minehunt.game

import org.bukkit.entity.Player
import org.bukkit.event.Listener
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

    /** 只在当前模式被选中期间注册的 Bukkit 事件监听器。 */
    val listener: Listener

    /** 可供命令选择的稳定角色标识；应包含 [spectatorRole]。 */
    val roles: List<String>

    /** 新加入或未参加本局玩家使用的角色标识。 */
    val spectatorRole: String

    /** 当前模式的规则和值；规则只能在准备阶段由通用命令修改。 */
    val rules: RuleSet

    /** 可供 `/minehunt give` 请求的稳定物品标识。 */
    val specialItems: List<String>

    /**
     * 在准备阶段为玩家分配角色，并同步该模式的展示状态。
     *
     * @return 角色存在且分配成功时为 `true`
     */
    fun assignRole(player: Player, role: String): Boolean

    /** 根据当前阶段处理玩家退出，并按模式规则更新大厅或对局状态。 */
    fun onPlayerQuit(player: Player)

    /**
     * 校验当前大厅能否开局。
     *
     * @return 可以开始时为 `null`，否则返回面向命令发送者的失败原因
     */
    fun validateStart(): String?

    /** 返回倒计时结束时应固定到 [GameManager.participants] 中的在线参赛者 UUID。 */
    fun participants(): Set<UUID>

    /**
     * 返回当前仍有资格参与终止投票的参赛者。
     *
     * 模式可在默认参赛者快照基础上排除已淘汰玩家。
     */
    fun stopVoters(): Set<UUID>

    /** 初始化本局模式状态和模式专属任务。 */
    fun start()

    /** 取消当前模式为本局创建的全部 Bukkit 任务。 */
    fun cancelTasks()

    /** 处理已经确定的结果、恢复玩家状态并生成只包含普通数据的最终记录快照。 */
    fun finish(outcome: GameOutcome): CompletedGameRecord

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
