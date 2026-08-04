package xyz.fortern.minehunt.rule

/**
 * 描述每一个规则项的类
 */
class RuleKey<T>(
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
)
