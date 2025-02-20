package xyz.fortern.minehunt.rule

import xyz.fortern.minehunt.rule.RuleKey.Companion.FRIENDLY_FIRE
import xyz.fortern.minehunt.rule.RuleKey.Companion.HUNTER_READY_CD
import xyz.fortern.minehunt.rule.RuleKey.Companion.HUNTER_RESPAWN_CD

/**
 * 描述所有游戏规则的类
 */
class GameRules internal constructor() {
    private val map: MutableMap<RuleKey<*>, Any> = LinkedHashMap()
    
    init {
        setRuleValue(HUNTER_RESPAWN_CD, 30)
        setRuleValue(HUNTER_READY_CD, 30)
        setRuleValue(FRIENDLY_FIRE, true)
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
    fun getAllRules(): Map<RuleKey<*>, Any> {
        return map
    }
}
