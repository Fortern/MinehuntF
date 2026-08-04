package xyz.fortern.minehunt.mode.manhunt

import xyz.fortern.minehunt.rule.RuleKey

/** Manhunt 对外公开的稳定规则定义；名称会用于命令和持久化。 */
object ManhuntRuleKeys {
    private val booleanValue: (String) -> Boolean? = {
        when {
            "true".equals(it, true) -> true
            "false".equals(it, true) -> false
            else -> null
        }
    }

    val HUNTER_READY_CD = integerSecondsRule(
        "hunter_ready_cd",
        "猎人出生倒计时(秒)",
        "猎人出生的等待时间",
    )

    val HUNTER_RESPAWN_CD = integerSecondsRule(
        "hunter_respawn_cd",
        "猎人重生倒计时(秒)",
        "猎人重生的等待时间",
    )

    val FRIENDLY_FIRE = RuleKey(
        "friendly_fire",
        "队友间伤害",
        "队友之间互相造成伤害",
        Boolean::class.java,
        "Boolean",
        listOf("true", "false"),
        booleanValue,
    )

    val HUNTER_INTENTIONAL = RuleKey(
        "hunter_intentional",
        "允许猎人的刻意游戏设计",
        "允许猎人触发刻意的游戏设计，速通者不受影响",
        Boolean::class.java,
        "Boolean",
        listOf("true", "false"),
        booleanValue,
    )

    val SPEEDRUN_LOOT_UP = RuleKey(
        "speedrun_loot_up",
        "更多末影珍珠和烈焰棒",
        "增加烈焰棒与末影珍珠的获取概率",
        Boolean::class.java,
        "Boolean",
        listOf("true", "false"),
        booleanValue,
    )

    private fun integerSecondsRule(name: String, displayName: String, info: String) = RuleKey(
        name,
        displayName,
        info,
        Int::class.java,
        "Integer",
        listOf("0", "15", "30"),
    ) { value -> value.toIntOrNull()?.takeIf { it in 0..120 } }
}
