package xyz.fortern.minehunt.rule

/**
 * 描述一个规则项的类
 */
class RuleItem<T>(
    /**
     * 这项规则的名称
     */
    val name: String,
    
    /**
     * 这项规则的描述
     */
    val info: String,
    
    /**
     * 这项规则的具体值
     */
    val value: T
) {
    /**
     * 此伴生对象存放当前的游戏配置
     *
     * 这些配置是可以修改的
     */
    companion object {
        var HUNTER_RESPAWN_CD = RuleItem("hunter_respawn_cd", "猎人重生倒计时(秒)", 30)
        var HUNTER_READY_CD = RuleItem("hunter_ready_cd", "猎人出生倒计时(秒)", 30)
        var FRIENDLY_FIRE = RuleItem("friendly_fire", "队友之间是否有伤害", true)
    }
}
