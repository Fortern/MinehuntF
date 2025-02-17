package xyz.fortern.minehunt.command

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 开发过程中的测试命令
 */
class TestCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args[0]) {
            // 发送消息
            "sendTo" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("需要参数"))
                    return true
                }
                // 给某个玩家发消息
                val name = args[1]
                val player = Bukkit.getPlayer(name)
                if (player != null)
                    player.sendMessage("离线发送消息")
                else
                    sender.sendMessage("玩家 $name 不存在")
            }
            // 无敌
            "invulnerable" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("需要参数"))
                    return true
                }
                val name = args[1]
                val player = Bukkit.getPlayer(name)
                if (player != null)
                    player.isInvulnerable = !player.isInvulnerable
                else
                    sender.sendMessage("玩家 $name 不存在")
            }
            // 获得32k
            "get32k" -> {
                if (sender is Player) {
                    sender.give(
                        // 锋利等级被限制为255
                        ItemStack(Material.DIAMOND_SWORD).apply {
                            this.addUnsafeEnchantment(
                                Enchantment.SHARPNESS,
                                32000
                            )
                        }
                    )
                }
            }
        }
        return true
    }
}
