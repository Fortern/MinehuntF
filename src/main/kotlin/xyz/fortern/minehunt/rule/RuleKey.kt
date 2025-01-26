package xyz.fortern.minehunt.rule

/**
 * 描述每一个规则项的类
 */
class RuleKey<T> private constructor(
    /**
     * 这项规则的名称
     */
    val name: String,

    /**
     * 显示的名称
     */
    val displayName: String,

    /**
     * 这项规则的描述
     */
    val info: String,

    /**
     * 这项规则值的类型
     */
    val type: Class<T>,

    /**
     * 值类型的描述信息
     */
    val typeInfo: String,

    /**
     * 命令补全时的推荐值
     */
    val recommendedValues: List<String>,

    /**
     * 对输入的String进行校验，成功则返回转换后的值，失败则返回null
     */
    val validate: (String) -> T?,
) {
    /**
     * 这里存放每一个规则的Key
     */
    companion object {
        private val boolValidate: (String) -> Boolean? = {
            when {
                "true".equals(it, true) -> true
                "false".equals(it, true) -> false
                else -> null
            }
        }

        val HUNTER_READY_CD =
            RuleKey("hunter_ready_cd", "猎人出生倒计时(秒)", "猎人出生的等待时间", Int::class.java, "Integer", listOf("0", "15", "30")) {
                try {
                    val i = it.toInt()
                    if (i in 0..120) i else null
                } catch (ex: NumberFormatException) {
                    null
                }
            }
        val HUNTER_RESPAWN_CD =
            RuleKey("hunter_respawn_cd", "猎人重生倒计时(秒)", "猎人重生的等待时间", Int::class.java, "Integer", listOf("0", "15", "30")) {
                try {
                    val i = it.toInt()
                    if (i in 0..120) i else null
                } catch (ex: NumberFormatException) {
                    null
                }
            }
        val FRIENDLY_FIRE = RuleKey(
            "friendly_fire",
            "队友间伤害",
            "队友之间互相造成伤害",
            Boolean::class.java,
            "Boolean",
            listOf("true", "false"),
            boolValidate
        )
        val HUNTER_INTENTIONAL = RuleKey(
            "hunter_intentional",
            "允许猎人的刻意游戏设计",
            "允许猎人触发刻意的游戏设计。速通者不受影响，做能触发",
            Boolean::class.java,
            "Boolean",
            listOf("true", "false"),
            boolValidate
        )
        val SPEEDRUN_LOOT_UP = RuleKey(
            "speedrun_loot_up",
            "更多末影珍珠和烈焰棒",
            "增加烈焰人掉落烈焰棒的概率，增加末影人掉落末影珍珠的概率，增加猪灵给予末影珍珠的概率",
            Boolean::class.java,
            "Boolean",
            listOf("true", "false"),
            boolValidate
        )
    }
}
