package xyz.fortern.minehunt.rule

/**
 * 一个模式公开给通用命令和展示层的规则集合。
 *
 * 实现应保持 [getAllRules] 的迭代顺序稳定，以便命令补全和计分板展示保持一致。
 */
interface RuleSet {
    /** 按稳定规则名查找定义；不存在时返回 `null`。 */
    fun findRule(name: String): RuleKey<*>?

    /** 校验字符串并更新规则值；校验失败时保持原值并返回 `false`。 */
    fun setRuleValueFromString(rule: RuleKey<*>, value: String): Boolean

    /** 在调用方不知道规则泛型类型时读取当前值。 */
    fun getRuleValueUntyped(rule: RuleKey<*>): Any

    /** 按模式定义顺序返回全部规则及当前值。 */
    fun getAllRules(): Map<RuleKey<*>, Any>
}
