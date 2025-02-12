package xyz.fortern.minehunt.command

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import xyz.fortern.minehunt.Console

/**
 * 主命令 minehunt
 */
class MinehuntCommand(
    val console: Console
) : TabExecutor {
    
    private val subCommand: List<String> = listOf("help", "join", "leave", "rule", "stat", "stop")
    
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
                onLeave(sender, args, flag)
            }
            
            "start" -> {
                onStart(sender, args, flag)
            }
            
            "stop" -> {
                onStop(sender, args, flag)
            }
            
            "rule" -> {
                onRule(sender, args, flag)
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
     * 查看或修改游戏规则
     */
    private fun onRule(sender: CommandSender, args: List<String>, execute: Boolean): List<String>? {
        TODO("Not yet implemented")
    }
    
    /**
     * 游戏结束
     */
    private fun onStop(sender: CommandSender, args: List<String>, execute: Boolean): List<String>? {
        TODO("Not yet implemented")
    }
    
    /**
     * 游戏开始
     */
    private fun onStart(sender: CommandSender, args: List<String>, execute: Boolean): List<String>? {
        TODO("Not yet implemented")
    }
    
    /**
     * 玩家离开队伍
     */
    private fun onLeave(sender: CommandSender, args: List<String>, execute: Boolean): List<String>? {
        TODO("Not yet implemented")
    }
    
    /**
     * 玩家加入队伍
     */
    private fun onJoin(sender: CommandSender, args: List<String>, execute: Boolean): List<String>? {
        TODO("Not yet implemented")
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