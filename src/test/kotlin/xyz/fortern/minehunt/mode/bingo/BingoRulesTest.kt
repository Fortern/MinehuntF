package xyz.fortern.minehunt.mode.bingo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BingoRulesTest {
    @Test
    fun `defaults match the standard first version`() {
        val rules = BingoRules()

        assertEquals(0L, rules.getRuleValue(BingoRuleKeys.CARD_SEED))
        assertTrue(rules.getRuleValue(BingoRuleKeys.KEEP_INVENTORY))
        assertFalse(rules.getRuleValue(BingoRuleKeys.PVP))
    }

    @Test
    fun `invalid values do not replace existing rules`() {
        val rules = BingoRules()

        assertTrue(rules.setRuleValueFromString(BingoRuleKeys.CARD_SEED, "123"))
        assertEquals(123L, rules.getRuleValue(BingoRuleKeys.CARD_SEED))
        assertFalse(rules.setRuleValueFromString(BingoRuleKeys.PVP, "sometimes"))
        assertFalse(rules.getRuleValue(BingoRuleKeys.PVP))
    }
}
