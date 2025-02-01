package xyz.fortern.minehunt.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * 开发过程中的测试命令
 */
class TestCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args[0]) {
            "sendTo" -> {
                // 给某个玩家发消息
                val name = args[1]
                val player = Bukkit.getPlayer(name)
                if (player != null) {
                    player.sendMessage("离线发送消息")
                } else {
                    sender.sendMessage("玩家 $name 不存在")
                }
            }
        }
        return true
    }
}