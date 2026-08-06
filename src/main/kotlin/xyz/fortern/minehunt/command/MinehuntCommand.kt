package xyz.fortern.minehunt.command

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.config.ConfigManager
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GamePhase
import xyz.fortern.minehunt.rule.RuleKey

/**
 * 主命令 minehunt
 */
class MinehuntCommand(
    private val gameManager: GameManager,
    private val configManager: ConfigManager,
    private val plugin: JavaPlugin,
) : TabExecutor {

    private val subCommands: List<String> =
        listOf("help", "mode", "join", "leave", "rule", "start", "stop", "give", "remake", "reload")
    private val modes: List<String>
        get() = gameManager.registeredModes().map { it.name.lowercase() }
    private val teams: List<String>
        get() = gameManager.currentMode.roles
    private val rules: List<String>
        get() = gameManager.currentMode.rules.getAllRules().keys.map { it.name }
    private val items: List<String>
        get() = gameManager.currentMode.specialItems

    private val helpMessages = listOf(
        "${ChatColor.GREEN}Minehunt v${plugin.description.version}",
        "${ChatColor.GOLD}/minehunt help  ${ChatColor.WHITE}帮助信息",
        "${ChatColor.GOLD}/minehunt mode [mode]  ${ChatColor.WHITE}查看或切换游戏模式（切换需要管理员）",
        "${ChatColor.GOLD}/minehunt join <role>  ${ChatColor.WHITE}加入一个阵营",
        "${ChatColor.GOLD}/minehunt leave  ${ChatColor.WHITE}加入观众阵营",
        "${ChatColor.GOLD}/minehunt rule <ruleItem> [value]  ${ChatColor.WHITE}查看或修改游戏规则",
        "${ChatColor.GOLD}/minehunt start  ${ChatColor.WHITE}开始游戏",
        "${ChatColor.GOLD}/minehunt stop  ${ChatColor.WHITE}进行中止游戏的投票",
        "${ChatColor.GOLD}/minehunt give  ${ChatColor.WHITE}给予游戏中所需的特殊物品",
        "${ChatColor.GOLD}/minehunt remake  ${ChatColor.WHITE}重开游戏，只能在开始前或结束后执行",
        "${ChatColor.GOLD}/minehunt reload  ${ChatColor.WHITE}重新加载配置(管理员命令)",
    )
    private val ruleHelpMessages = listOf(
        "${ChatColor.GREEN}/minehunt rule <ruleItem>  ${ChatColor.WHITE}查看一项规则的详情",
        "${ChatColor.GREEN}/minehunt rule <ruleItem> <value>  ${ChatColor.WHITE}为一项规则设置新的值",
    )

    /**
     * 执行命令
     */
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // onCommand接受到的参数中没有空字符串
        handlerCommand(sender, args.toList(), true)

        return true
    }

    /**
     * 命令补全
     */
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String>? {
        return handlerCommand(
            sender,
            args.filterIndexed { index, s -> s != "" || index == args.size - 1 },
            false
        )
    }

    /**
     * 执行命令或补全命令
     *
     * @param flag true 表示执行命令，false 表示补全命令
     * @param args 命令的参数列表，除最后一条前面的每一条都应当是非空的
     */
    private fun handlerCommand(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        if (args.isEmpty())
            return if (flag) {
                sendHelp(sender)
                null
            } else {
                subCommands
            }

        return when (args[0]) {
            "help" -> onHelp(sender, flag)
            "mode" -> onMode(sender, args, flag)
            "join" -> onJoin(sender, args, flag)
            "leave" -> onLeave(sender, flag)
            "rule" -> onRule(sender, args, flag)
            "start" -> onStart(sender, flag)
            "stop" -> onStop(sender, flag)
            "give" -> onGive(sender, args, flag)
            "remake" -> onRemake(sender, flag)
            "reload" -> onReload(sender, flag)
            else -> {
                if (flag) {
                    sender.sendMessage("错误的子命令")
                    null
                } else {
                    if (args.size == 1) subCommands.filter { it.startsWith(args[0]) } else null
                }
            }
        }
    }

    private fun onHelp(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) sendHelp(sender)
        return null
    }

    /** 查看当前模式，或由管理员在准备阶段切换模式。 */
    private fun onMode(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        if (args.size == 1) {
            if (flag) {
                sender.sendMessage("${ChatColor.GREEN}当前游戏模式：${gameManager.currentMode.id.name.lowercase()}")
            }
            return if (flag) null else modes
        }

        val input = args[1]
        if (!flag) return if (args.size == 2) modes.filter { it.startsWith(input, true) } else null
        if (args.size > 2) {
            sender.sendMessage("${ChatColor.RED}参数过多")
            return null
        }
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("${ChatColor.RED}只有管理员可以切换游戏模式")
            return null
        }
        if (gameManager.phase != GamePhase.LOBBY) {
            sender.sendMessage("${ChatColor.RED}只能在准备阶段切换游戏模式")
            return null
        }
        val mode = gameManager.registeredModes().firstOrNull { it.name.equals(input, true) }
        if (mode == null) {
            sender.sendMessage("${ChatColor.RED}不存在或尚未注册的游戏模式")
            return null
        }
        if (gameManager.currentMode.id == mode) {
            sender.sendMessage("当前已经是 ${mode.name.lowercase()} 模式")
            return null
        }
        gameManager.selectMode(mode)
        sender.sendMessage("${ChatColor.GREEN}已切换到 ${mode.name.lowercase()} 模式")
        return null
    }

    /**
     * 玩家加入队伍
     */
    private fun onJoin(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        if (gameManager.phase != GamePhase.LOBBY) {
            if (flag) {
                sender.sendMessage("${ChatColor.RED}只能在准备阶段加入队伍")
            }
            return null
        }
        if (args.size == 1) {
            if (flag) {
                sender.sendMessage("${ChatColor.RED}输入正确的队伍名称")
            }
            return null
        }
        val teamName = args[1]
        if (flag) {
            if (sender !is Player) {
                sender.sendMessage("${ChatColor.RED}The sender is not a player.")
                return null
            }
            if (!gameManager.assignRole(sender, teamName)) {
                sender.sendMessage("${ChatColor.RED}输入正确的队伍名称")
            }
            return null
        } else {
            if (args.size == 2) {
                return teams.filter { it.startsWith(teamName) }
            }
        }

        return null
    }

    /**
     * 玩家离开队伍
     */
    private fun onLeave(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            if (sender !is Player) {
                sender.sendMessage("${ChatColor.RED}The sender is not a player.")
            } else {
                gameManager.assignRole(sender, gameManager.currentMode.spectatorRole)
            }
        }
        return null
    }

    /**
     * 查看或修改游戏规则
     */
    private fun onRule(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        // args[0] == rule
        if (args.size == 1) {
            if (flag) {
                sendHelpRule(sender)
            }
            return null
        }

        val ruleName = args[1]
        val rule = gameManager.currentMode.rules.findRule(ruleName)
        return if (rule != null) {
            getOrChangeRule(args, flag, sender, rule)
        } else {
            if (flag) {
                sender.sendMessage("不存在的规则项")
                null
            } else {
                if (args.size == 2) rules.filter { it.startsWith(ruleName) } else null
            }
        }
    }

    /**
     * 读取或改变游戏规则
     */
    private fun getOrChangeRule(
        args: List<String>,
        flag: Boolean,
        sender: CommandSender,
        ruleKey: RuleKey<*>
    ): List<String>? {
        return if (args.size == 2) {
            // 获取规则详情
            if (flag) {
                sendRuleInfo(sender, ruleKey)
            }
            null
        } else if (args.size == 3) {
            // 给规则赋值
            if (flag) {
                if (gameManager.phase == GamePhase.LOBBY) {
                    if (gameManager.currentMode.rules.setRuleValueFromString(ruleKey, args[2])) {
                        sender.sendMessage(
                            "${ChatColor.YELLOW}${sender.name}${ChatColor.WHITE}修改规则项" +
                                "${ChatColor.GOLD}${ruleKey.name}${ChatColor.WHITE}值为" +
                                "${ChatColor.GREEN}${args[2]}"
                        )
                        gameManager.currentMode.onRuleChanged(ruleKey)
                    } else {
                        sender.sendMessage("${ChatColor.RED}不合适的值")
                    }
                } else {
                    sender.sendMessage("${ChatColor.RED}只有准备阶段才能修改规则")
                }
                null
            } else {
                ruleKey.recommendedValues
            }
        } else {
            // 参数过多
            if (flag) {
                sender.sendMessage("参数过多")
            }
            null
        }
    }

    /**
     * 游戏开始
     */
    private fun onStart(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            if (gameManager.phase == GamePhase.LOBBY) {
                val result = gameManager.tryStart()
                if (result != null) {
                    sender.sendMessage("${ChatColor.RED}游戏开始失败，原因：$result")
                }
            } else {
                sender.sendMessage("${ChatColor.RED}现在不能开始游戏")
            }
        }
        return null
    }

    /**
     * 游戏结束
     */
    private fun onStop(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            if (sender is Player) {
                gameManager.voteForStop(sender)
            } else {
                sender.sendMessage("只有游戏中的玩家才能投票")
            }
        }
        return null
    }

    /**
     * 给予玩家特殊物品
     */
    private fun onGive(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        // args[0] == give
        if (args.size == 1) {
            if (flag) {
                sender.sendMessage("${ChatColor.RED}缺少参数")
            }
            return null
        }

        val item = args[1]
        if (flag) {
            if (sender !is Player) {
                sender.sendMessage("${ChatColor.RED}The sender is not a player.")
                return null
            }
            if (!gameManager.currentMode.giveSpecialItem(sender, item)) {
                sender.sendMessage("${ChatColor.RED}输入正确的物品名称")
            }
            return null
        } else {
            if (args.size == 2) {
                return items.filter { it.startsWith(item) }
            }
        }
        return null
    }

    /**
     * 重开游戏
     */
    fun onRemake(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            if (sender is Player) {
                gameManager.voteForRemake(sender)
            } else {
                sender.sendMessage("${ChatColor.RED}The sender is not a player.")
            }
        }
        return null
    }

    /**
     * 重载配置
     */
    fun onReload(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            if (sender is Player && !sender.isOp) {
                sender.sendMessage("${ChatColor.RED}只有管理员可以执行")
            } else {
                configManager.reload(false)
            }
        }
        return null
    }

    /**
     * 发送规则详情
     */
    private fun sendRuleInfo(sender: CommandSender, ruleKey: RuleKey<*>) {
        sender.sendMessage("游戏规则: ${ruleKey.name}")
        sender.sendMessage("显示名称: ${ruleKey.displayName}")
        sender.sendMessage("描述: ${ruleKey.info}")
        sender.sendMessage("值类型: ${ruleKey.typeInfo}")
        sender.sendMessage("数值: ${gameManager.currentMode.rules.getRuleValueUntyped(ruleKey)}")
    }

    /**
     * 发送帮助信息
     */
    private fun sendHelp(sender: CommandSender) {
        helpMessages.forEach(sender::sendMessage)
    }

    /**
     * 发送rule子命令的帮助信息
     */
    private fun sendHelpRule(sender: CommandSender) {
        ruleHelpMessages.forEach(sender::sendMessage)
    }
}
