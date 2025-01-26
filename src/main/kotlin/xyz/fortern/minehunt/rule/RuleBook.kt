package xyz.fortern.minehunt.rule

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import xyz.fortern.minehunt.util.Util
import xyz.fortern.minehunt.util.serialize

// TODO 使用物品栏修改游戏规则
class RuleBook {
    private val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).also {
        it.itemMeta!!.addItemFlags(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_PLACED_ON,
            ItemFlag.HIDE_UNBREAKABLE
        )
    }

    val rootInventory = Bukkit.createInventory(null, 9, Component.text("点击任意一个规则项进行设置").serialize()).also {
        Util.fillItem(it, filler)
        val rule1 = ItemStack(Material.CLOCK)
        val itemMeta1 = rule1.itemMeta!!
        itemMeta1.setDisplayName(Component.text(RuleKey.HUNTER_READY_CD.info).serialize())
        itemMeta1.lore = listOf(Component.text(RuleKey.HUNTER_READY_CD.name).serialize())
        it.setItem(0, rule1)
        val rule2 = ItemStack(Material.CLOCK)
        val itemMeta2 = rule2.itemMeta!!
        itemMeta2.setDisplayName(Component.text(RuleKey.HUNTER_RESPAWN_CD.info).serialize())
        itemMeta2.lore = listOf(Component.text(RuleKey.HUNTER_RESPAWN_CD.name).serialize())
        it.setItem(4, rule2)
        val rule3 = ItemStack(Material.CLOCK)
        val itemMeta3 = rule3.itemMeta!!
        itemMeta3.setDisplayName(Component.text(RuleKey.FRIENDLY_FIRE.info).serialize())
        itemMeta3.lore = listOf(Component.text(RuleKey.FRIENDLY_FIRE.name).serialize())
        it.setItem(8, rule3)
    }
}