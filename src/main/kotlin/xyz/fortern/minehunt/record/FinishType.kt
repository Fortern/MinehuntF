package xyz.fortern.minehunt.record

/**
 * 游戏结果
 */
enum class FinishType {
    /**
     * 初始状态，代表游戏尚未结束
     */
    NULL,

    /**
     * 游戏正常结束
     */
    FINISHED,

    /**
     * 游戏被终止
     */
    STOPPED,
}
