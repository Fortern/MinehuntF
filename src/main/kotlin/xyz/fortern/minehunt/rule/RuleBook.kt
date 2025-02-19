package xyz.fortern.minehunt.rule

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import xyz.fortern.minehunt.util.Util

// TODO 使用物品栏修改游戏规则
class RuleBook {
    val rootInventory = Bukkit.createInventory(null, 9, Component.text("点击任意一个规则项进行设置")).also {
        Util.fillItem(it, filler)
        val rule1 = ItemStack(Material.CLOCK)
        val itemMeta1 = rule1.itemMeta
        itemMeta1.customName(Component.text(RuleKey.HUNTER_READY_CD.info))
        itemMeta1.lore(listOf(Component.text(RuleKey.HUNTER_READY_CD.name)))
        it.setItem(1, rule1)
        val rule2 = ItemStack(Material.CLOCK)
        val itemMeta2 = rule2.itemMeta
        itemMeta2.customName(Component.text(RuleKey.HUNTER_RESPAWN_CD.info))
        itemMeta2.lore(listOf(Component.text(RuleKey.HUNTER_RESPAWN_CD.name)))
        it.setItem(5, rule2)
        val rule3 = ItemStack(Material.CLOCK)
        val itemMeta3 = rule3.itemMeta
        itemMeta3.customName(Component.text(RuleKey.FRIENDLY_FIRE.info))
        itemMeta3.lore(listOf(Component.text(RuleKey.FRIENDLY_FIRE.name)))
        it.setItem(9, rule3)
    }
    
    private val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).also {
        it.addItemFlags(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_PLACED_ON,
            ItemFlag.HIDE_UNBREAKABLE
        )
    }
}