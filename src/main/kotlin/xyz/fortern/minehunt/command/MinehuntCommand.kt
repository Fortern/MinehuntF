package xyz.fortern.minehunt.command

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import xyz.fortern.minehunt.Console
import xyz.fortern.minehunt.rule.RuleKey

/**
 * 主命令 minehunt
 */
class MinehuntCommand(
    private val console: Console,
    private val adventure: BukkitAudiences,
    private val plugin: JavaPlugin,
) : TabExecutor {

    private val subCommands: List<String> = listOf("help", "join", "leave", "rule", "start", "stop", "give", "remake")
    private val teams: List<String> = listOf("hunter", "speedrunner", "audience")
    private val rules: List<String> = listOf("hunter_respawn_cd", "hunter_ready_cd", "friendly_fire", "hunter_intentional", "speedrun_loot_up")
    private val items: List<String> = listOf("compass")

    private val helpMessages = listOf(
        Component.text("Minehunt v${plugin.description.version}", NamedTextColor.GREEN),
        Component.text("/minehunt help  ", NamedTextColor.GOLD)
            .append(Component.text("帮助信息", NamedTextColor.WHITE)),
        Component.text("/minehunt join (hunter|speedrunner|audience)  ", NamedTextColor.GOLD)
            .append(Component.text("加入一个阵营", NamedTextColor.WHITE)),
        Component.text("/minehunt leave  ", NamedTextColor.GOLD)
            .append(Component.text("加入观众阵营", NamedTextColor.WHITE)),
        Component.text("/minehunt rule <ruleItem> [value]  ", NamedTextColor.GOLD)
            .append(Component.text("查看或修改游戏规则", NamedTextColor.WHITE)),
        Component.text("/minehunt start  ", NamedTextColor.GOLD)
            .append(Component.text("开始游戏", NamedTextColor.WHITE)),
        Component.text("/minehunt stop  ", NamedTextColor.GOLD)
            .append(Component.text("进行中止游戏的投票", NamedTextColor.WHITE)),
        Component.text("/minehunt give  ", NamedTextColor.GOLD)
            .append(Component.text("给予游戏中所需的特殊物品", NamedTextColor.WHITE)),
        Component.text("/minehunt remake  ", NamedTextColor.GOLD)
            .append(Component.text("重开游戏，只能在开始前或结束后执行", NamedTextColor.WHITE)),
    )
    private val ruleHelpMessages = listOf(
        Component.text("/minehunt rule <ruleItem>  ", NamedTextColor.GREEN)
            .append(Component.text("查看一项规则的详情", NamedTextColor.WHITE)),
        Component.text("/minehunt rule <ruleItem> <value>  ", NamedTextColor.GREEN)
            .append(Component.text("为一项规则设置新的值", NamedTextColor.WHITE)),
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
            "help" -> {
                onHelp(sender, flag)
            }

            "join" -> {
                onJoin(sender, args, flag)
            }

            "leave" -> {
                onLeave(sender, flag)
            }

            "rule" -> {
                onRule(sender, args, flag)
            }

            "start" -> {
                onStart(sender, flag)
            }

            "stop" -> {
                onStop(sender, flag)
            }

            "give" -> {
                onGive(sender, args, flag)
            }

            "remake" -> {
                onRemake(sender, flag)
            }

            else -> {
                if (flag) {
                    adventure.sender(sender).sendMessage(Component.text("错误的子命令"))
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

    /**
     * 玩家加入队伍
     */
    private fun onJoin(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        if (console.stage != Console.GameStage.PREPARING && console.beginningCountdown != null) {
            if (flag) {
                adventure.sender(sender).sendMessage(Component.text("只能在准备阶段加入队伍", NamedTextColor.RED))
            }
            return null
        }
        if (args.size == 1) {
            if (flag) {
                adventure.sender(sender).sendMessage(Component.text("输入正确的队伍名称", NamedTextColor.RED))
            }
            return null
        }
        val teamName = args[1]
        if (flag) {
            if (sender !is Player) {
                adventure.sender(sender).sendMessage(Component.text("The sender is not a player.", NamedTextColor.RED))
                return null
            }
            when (teamName) {
                "hunter" -> {
                    console.joinHunter(sender)
                }

                "speedrunner" -> {
                    console.joinSpeedrunner(sender)
                }

                "audience" -> {
                    console.joinAudience(sender)
                }

                else -> {
                    adventure.sender(sender).sendMessage(Component.text("输入正确的队伍名称", NamedTextColor.RED))
                }
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
                adventure.sender(sender).sendMessage(Component.text("The sender is not a player.", NamedTextColor.RED))
            } else {
                console.joinAudience(sender)
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

        return when (val rule = args[1]) {
            "hunter_respawn_cd" -> {
                getOrChangeRule(args, flag, sender, RuleKey.HUNTER_RESPAWN_CD)
            }

            "hunter_ready_cd" -> {
                getOrChangeRule(args, flag, sender, RuleKey.HUNTER_READY_CD)
            }

            "friendly_fire" -> {
                getOrChangeRule(args, flag, sender, RuleKey.FRIENDLY_FIRE)
            }

            "hunter_intentional" -> {
                getOrChangeRule(args, flag, sender, RuleKey.HUNTER_INTENTIONAL)
            }

            "speedrun_loot_up" -> {
                getOrChangeRule(args, flag, sender, RuleKey.SPEEDRUN_LOOT_UP)
            }

            else -> {
                if (flag) {
                    adventure.sender(sender).sendMessage(Component.text("不存在的规则项"))
                    null
                } else {
                    if (args.size == 2) rules.filter { it.startsWith(rule) } else null
                }
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
                if (console.stage == Console.GameStage.PREPARING) {
                    if (console.gameRules.setGameRuleValueSafe(ruleKey, args[2])) {
                        adventure.sender(sender).sendMessage(
                            Component.text(sender.name, NamedTextColor.YELLOW)
                                .append(Component.text("修改规则项", NamedTextColor.WHITE))
                                .append(Component.text(ruleKey.name, NamedTextColor.GOLD))
                                .append(Component.text("值为", NamedTextColor.WHITE))
                                .append(Component.text(args[2], NamedTextColor.GREEN))
                        )
                        console.refreshEntry(ruleKey)
                    } else {
                        adventure.sender(sender).sendMessage(Component.text("不合适的值", NamedTextColor.RED))
                    }
                } else {
                    adventure.sender(sender).sendMessage(Component.text("只有准备阶段才能修改规则", NamedTextColor.RED))
                }
                null
            } else {
                ruleKey.recommendedValues
            }
        } else {
            // 参数过多
            if (flag) {
                adventure.sender(sender).sendMessage(Component.text("参数过多"))
            }
            null
        }
    }

    /**
     * 游戏开始
     */
    private fun onStart(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            if (console.stage == Console.GameStage.PREPARING && console.beginningCountdown == null) {
                val result = console.tryStart()
                if (result.isNotEmpty()) {
                    adventure.sender(sender)
                        .sendMessage(Component.text("游戏开始失败，原因：${result}", NamedTextColor.RED))
                }
            } else {
                adventure.sender(sender).sendMessage(Component.text("现在不能开始游戏", NamedTextColor.RED))
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
                console.voteForStop(sender)
            } else {
                adventure.sender(sender).sendMessage(Component.text("只有游戏中的玩家才能投票"))
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
                adventure.sender(sender).sendMessage(Component.text("缺少参数", NamedTextColor.RED))
            }
            return null
        }

        val item = args[1]
        if (flag) {
            if (sender !is Player) {
                adventure.sender(sender).sendMessage(Component.text("The sender is not a player.", NamedTextColor.RED))
                return null
            }
            when (item) {
                "compass" -> {
                    console.giveCompassIfNeed(sender)
                }

                else -> {
                    adventure.sender(sender).sendMessage(Component.text("输入正确的物品名称", NamedTextColor.RED))
                }
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
                console.voteForRemake(sender)
            } else {
                adventure.sender(sender).sendMessage(Component.text("The sender is not a player.", NamedTextColor.RED))
            }
        }
        return null
    }

    /**
     * 发送规则详情
     */
    private fun sendRuleInfo(sender: CommandSender, ruleKey: RuleKey<*>) {
        adventure.sender(sender).sendMessage(Component.text("游戏规则: ${ruleKey.name}"))
        adventure.sender(sender).sendMessage(Component.text("显示名称: ${ruleKey.displayName}"))
        adventure.sender(sender).sendMessage(Component.text("描述: ${ruleKey.info}"))
        adventure.sender(sender).sendMessage(Component.text("值类型: ${ruleKey.typeInfo}"))
        adventure.sender(sender).sendMessage(Component.text("数值: ${console.gameRules.getRuleValue(ruleKey)}"))
    }

    /**
     * 发送帮助信息
     */
    private fun sendHelp(sender: CommandSender) {
        helpMessages.forEach { adventure.sender(sender).sendMessage(it) }
    }

    /**
     * 发送rule子命令的帮助信息
     */
    private fun sendHelpRule(sender: CommandSender) {
        ruleHelpMessages.forEach { adventure.sender(sender).sendMessage(it) }
    }
}