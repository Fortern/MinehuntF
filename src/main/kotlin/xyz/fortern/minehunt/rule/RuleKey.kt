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
     * 对输入的String进行校验，成功则返回转换后的值，失败则返回null
     */
    val validate: (String) -> T?
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
        
        val HUNTER_READY_CD = RuleKey("hunter_ready_cd", "猎人出生倒计时(秒)", Int::class.java, "Integer") {
            try {
                val i = it.toInt()
                if (i in 0..120) i else null
            } catch (ex: NumberFormatException) {
                null
            }
        }
        val HUNTER_RESPAWN_CD = RuleKey("hunter_respawn_cd", "猎人重生倒计时(秒)", Int::class.java, "Integer") {
            try {
                val i = it.toInt()
                if (i in 0..120) i else null
            } catch (ex: NumberFormatException) {
                null
            }
        }
        val FRIENDLY_FIRE = RuleKey("friendly_fire", "队友之间是否有伤害", Boolean::class.java, "Boolean", boolValidate)
    }
}
