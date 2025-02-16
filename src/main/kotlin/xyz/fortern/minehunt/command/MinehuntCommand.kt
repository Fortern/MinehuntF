package xyz.fortern.minehunt.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import xyz.fortern.minehunt.Console

/**
 * 主命令 minehunt
 */
class MinehuntCommand(
    private val console: Console
) : TabExecutor {
    
    private val subCommand: List<String> = listOf("help", "join", "leave", "rule", "stat", "stop")
    private val teams: List<String> = listOf("hunter", "speedrunner", "spectator")
    
    /**
     * 执行命令
     */
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
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
        if (args.isEmpty() || args[0] == "" || args[0] == "help" || args[0] == "?")
            return if (flag) sendHelp(sender) else subCommand
        
        return when (args[0]) {
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
            
            else -> {
                if (flag) {
                    sender.sendMessage(Component.text("错误的子命令"))
                    null
                } else {
                    if (args.size == 1) subCommand.filter { it.startsWith(args[0]) } else null
                }
            }
        }
    }
    
    /**
     * 玩家加入队伍
     */
    private fun onJoin(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        if (args.size == 1) {
            if (flag) {
                sender.sendMessage(Component.text("输入正确的队伍名称", NamedTextColor.RED))
            }
            return null
        }
        val teamName = args[1]
        if (flag) {
            if (sender !is Player) {
                sender.sendMessage(Component.text("The sender is not a player.", NamedTextColor.RED))
                return null
            }
            when (teamName) {
                "hunter" -> {
                    console.joinHunter(sender)
                }
                "speedrunner" -> {
                    console.joinSpeedrunner(sender)
                }
                "spectator" -> {
                    console.joinSpectator(sender)
                }
                else -> {
                    sender.sendMessage(Component.text("输入正确的队伍名称", NamedTextColor.RED))
                }
            }
            return null
        } else {
            if (args.size == 2) {
                return teams.filter { it.startsWith(args[0]) }
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
                sender.sendMessage(Component.text("The sender is not a player.", NamedTextColor.RED))
            } else {
                console.joinSpectator(sender)
            }
        }
        return null
    }
    
    /**
     * 查看或修改游戏规则
     */
    private fun onRule(sender: CommandSender, args: List<String>, flag: Boolean): List<String>? {
        TODO("Not yet implemented")
    }
    
    /**
     * 游戏开始
     */
    private fun onStart(sender: CommandSender, flag: Boolean): List<String>? {
        if (flag) {
            // TODO 正在编辑规则时，也不能开始
            if (console.stage == Console.GameStage.PREPARING) {
                console.tryStart()
            } else {
                sender.sendMessage("游戏已经开始或已经结束")
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
                sender.sendMessage(Component.text("只有游戏中的玩家才能投票"))
            }
        }
        return null
    }
    
    /**
     * 发送帮助信息
     */
    private fun sendHelp(sender: CommandSender): List<String>? {
        sender.sendMessage("§aMinehunt v1.0.0")
        sender.sendMessage("§a/minehunt help")
        sender.sendMessage("§a/minehunt start")
        sender.sendMessage("§a/minehunt stop")
        sender.sendMessage("§a/minehunt set <time>")
        return null
    }
}