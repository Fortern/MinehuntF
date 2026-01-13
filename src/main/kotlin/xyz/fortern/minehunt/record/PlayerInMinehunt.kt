package xyz.fortern.minehunt.record

/**
 * Minehunt模式下，某位玩家的一局游戏信息
 */
class PlayerInMinehunt(

    /**
     * 各种生物击杀数
     *
     * key为命名空间ID
     */
    val kills: Map<String, Int>,

    /**
     * 被击杀数
     *
     * key为命名空间ID
     */
    val killedBy: Map<String, Int>,

    /**
     * 常见食物使用数
     *
     * key为命名空间ID
     */
    val foodUse: Map<String, Int>,

    /**
     * 常见工具使用数
     *
     * key为命名空间ID
     */
    val toolsUse: Map<String, Int>,

    /**
     * 常见武器使用数
     *
     * key为命名空间ID
     */
    val weaponUse: Map<String, Int>,

    /**
     * 矿石开采
     *
     * key为命名空间ID
     */
    val oreMine: Map<String, Int>,
) : PlayerDetails
