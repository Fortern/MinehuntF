package xyz.fortern.minehunt.rule

/**
 * 描述所有游戏规则的类
 */
class GameRules internal constructor() {
    private val map: MutableMap<RuleKey<*>, Any> = LinkedHashMap()

    init {
        setRuleValue(RuleKey.HUNTER_RESPAWN_CD, 30)
        setRuleValue(RuleKey.HUNTER_READY_CD, 30)
        setRuleValue(RuleKey.FRIENDLY_FIRE, true)
        setRuleValue(RuleKey.HUNTER_INTENTIONAL, false)
        setRuleValue(RuleKey.SPEEDRUN_LOOT_UP, true)
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
