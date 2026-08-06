package xyz.fortern.minehunt.mode.bingo

import xyz.fortern.minehunt.rule.RuleKey

/** Bingo 对外公开的稳定规则定义。 */
object BingoRuleKeys {
    private val booleanValue: (String) -> Boolean? = {
        when {
            "true".equals(it, true) -> true
            "false".equals(it, true) -> false
            else -> null
        }
    }

    val CARD_SEED = RuleKey(
        "card_seed",
        "卡片种子",
        "相同的非零种子会生成相同卡片；0 表示开局时随机生成",
        Long::class.java,
        "Long",
        listOf("0"),
    ) { it.toLongOrNull() }

    val KEEP_INVENTORY = RuleKey(
        "keep_inventory",
        "死亡保留物品",
        "参赛者死亡后是否保留背包和经验",
        Boolean::class.java,
        "Boolean",
        listOf("true", "false"),
        booleanValue,
    )

    val PVP = RuleKey(
        "pvp",
        "玩家间伤害",
        "是否允许不同队伍的参赛者互相造成伤害；队友伤害始终关闭",
        Boolean::class.java,
        "Boolean",
        listOf("false", "true"),
        booleanValue,
    )
}
