package xyz.fortern.minehunt.rule

import xyz.fortern.minehunt.rule.RuleKey.Companion.FRIENDLY_FIRE
import xyz.fortern.minehunt.rule.RuleKey.Companion.HUNTER_READY_CD
import xyz.fortern.minehunt.rule.RuleKey.Companion.HUNTER_RESPAWN_CD

/**
 * 描述所有游戏规则的类
 */
class GameRules internal constructor() {
    private val map: MutableMap<RuleKey<*>, Any> = HashMap()
    
    init {
        setRuleValue(HUNTER_RESPAWN_CD, 30)
        setRuleValue(HUNTER_READY_CD, 30)
        setRuleValue(FRIENDLY_FIRE, true)
    }
    
    fun <T> setGameRuleValueSafe(rule: RuleKey<T>, value: String): Boolean {
        val okValue: T = rule.validate(value) ?: return false
        setRuleValue(rule, okValue)
        return true
    }
    
    private fun <T> setRuleValue(rule: RuleKey<T>, value: T) {
        map[rule] = value!!
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T> getRuleValue(rule: RuleKey<T>): T {
        return map[rule] as T
    }
}
