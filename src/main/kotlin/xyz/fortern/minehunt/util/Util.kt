package xyz.fortern.minehunt.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object Util {
    fun fillItem(inventory: Inventory, itemStack: ItemStack) {
        for (i in 0 until inventory.size) {
            inventory.setItem(i, itemStack)
        }
    }

}

fun Component.serialize(): String {
    return LegacyComponentSerializer.legacyAmpersand().serialize(this)
}
