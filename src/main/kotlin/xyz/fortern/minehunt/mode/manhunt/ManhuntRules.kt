package xyz.fortern.minehunt.mode.manhunt

import xyz.fortern.minehunt.rule.RuleKey
import xyz.fortern.minehunt.rule.RuleSet

/**
 * Manhunt 规则的有序值集合，负责默认值和字符串输入校验。
 */
class ManhuntRules : RuleSet {
    private val map: MutableMap<RuleKey<*>, Any> = LinkedHashMap()

    init {
        setRuleValue(ManhuntRuleKeys.HUNTER_RESPAWN_CD, 30)
        setRuleValue(ManhuntRuleKeys.HUNTER_READY_CD, 30)
        setRuleValue(ManhuntRuleKeys.FRIENDLY_FIRE, true)
        setRuleValue(ManhuntRuleKeys.HUNTER_INTENTIONAL, false)
        setRuleValue(ManhuntRuleKeys.SPEEDRUN_LOOT_UP, true)
    }

    /**
     * 设置一项游戏规则
     */
    fun <T> setGameRuleValueSafe(rule: RuleKey<T>, value: String): Boolean {
        val okValue: T = rule.validate(value) ?: return false
        setRuleValue(rule, okValue)
        return true
    }

    private fun <T> setRuleValue(rule: RuleKey<T>, value: T) {
        map[rule] = value!!
    }

    /**
     * 根据key获取一项游戏规则
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getRuleValue(rule: RuleKey<T>): T {
        return map[rule] as T
    }

    /**
     * 获取所有游戏规则
     */
    override fun findRule(name: String): RuleKey<*>? = map.keys.firstOrNull { it.name == name }

    override fun setRuleValueFromString(rule: RuleKey<*>, value: String): Boolean {
        val parsed = rule.validate(value) ?: return false
        map[rule] = parsed
        return true
    }

    override fun getRuleValueUntyped(rule: RuleKey<*>): Any = map.getValue(rule)

    override fun getAllRules(): Map<RuleKey<*>, Any> {
        return map
    }
}
