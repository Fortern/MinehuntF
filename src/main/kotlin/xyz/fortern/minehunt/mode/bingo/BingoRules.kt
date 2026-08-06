package xyz.fortern.minehunt.mode.bingo

import xyz.fortern.minehunt.rule.RuleKey
import xyz.fortern.minehunt.rule.RuleSet

/** Bingo 规则的有序值集合。 */
class BingoRules : RuleSet {
    private val values: MutableMap<RuleKey<*>, Any> = linkedMapOf(
        BingoRuleKeys.CARD_SEED to 0L,
        BingoRuleKeys.KEEP_INVENTORY to true,
        BingoRuleKeys.PVP to false,
    )

    override fun findRule(name: String): RuleKey<*>? = values.keys.firstOrNull { it.name == name }

    override fun setRuleValueFromString(rule: RuleKey<*>, value: String): Boolean {
        if (rule !in values) return false
        val parsed = rule.validate(value) ?: return false
        values[rule] = parsed
        return true
    }

    override fun getRuleValueUntyped(rule: RuleKey<*>): Any = values.getValue(rule)

    override fun getAllRules(): Map<RuleKey<*>, Any> = values

    @Suppress("UNCHECKED_CAST")
    fun <T> getRuleValue(rule: RuleKey<T>): T = values.getValue(rule) as T
}
