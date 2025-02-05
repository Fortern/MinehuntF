package xyz.fortern.minehunt.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * 主命令 minehunt
 */
class MinehuntCommand: CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>?
    ): Boolean {
        if (args!!.isEmpty()) {
            sender.sendMessage("§aMinehunt v1.0.0")
            sender.sendMessage("§a/minehunt help")
            sender.sendMessage("§a/minehunt start")
            sender.sendMessage("§a/minehunt stop")
            sender.sendMessage("§a/minehunt pause")
            sender.sendMessage("§a/minehunt resume")
            sender.sendMessage("§a/minehunt set <time>")
            sender.sendMessage("§a/minehunt set <time>")
        }
        
        return true
    }
}