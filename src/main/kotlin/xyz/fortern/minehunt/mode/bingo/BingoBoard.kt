package xyz.fortern.minehunt.mode.bingo

import org.bukkit.Material
import java.util.Collections
import java.util.Random

/** 一张固定为 5×5 的 Bingo 物品卡。 */
data class BingoBoard(
    val seed: Long,
    val targets: List<Material>,
) {
    init {
        require(targets.size == SLOT_COUNT) { "A Bingo board must contain $SLOT_COUNT targets" }
        require(targets.toSet().size == SLOT_COUNT) { "Bingo targets must be unique" }
    }

    private val slotsByTarget = targets.withIndex().associate { (slot, target) -> target to slot }

    fun slotOf(target: Material): Int? = slotsByTarget[target]

    /** 返回当前完成集合形成的全部获胜连线。 */
    fun winningLines(completed: Set<Material>): List<List<Int>> =
        LINES.filter { line -> line.all { targets[it] in completed } }

    companion object {
        const val SIZE = 5
        const val SLOT_COUNT = SIZE * SIZE

        /** 五行、五列和两条对角线。 */
        val LINES: List<List<Int>> = buildList {
            repeat(SIZE) { row -> add(List(SIZE) { column -> row * SIZE + column }) }
            repeat(SIZE) { column -> add(List(SIZE) { row -> row * SIZE + column }) }
            add(List(SIZE) { index -> index * SIZE + index })
            add(List(SIZE) { index -> index * SIZE + (SIZE - index - 1) })
        }

        /**
         * 使用同一种子生成相同卡片。
         *
         * 难度布局是一个五阶拉丁方阵，保证每一条可能获胜的线都恰好包含一个
         * 1—5 级目标，避免随机卡片出现明显的“简单线”。
         */
        fun generate(seed: Long): BingoBoard {
            val random = Random(seed)
            val selectedByTier = BingoTargetPool.tiers.map { pool ->
                pool.toMutableList().also { Collections.shuffle(it, random) }.take(SIZE)
            }
            val nextInTier = IntArray(SIZE)
            val targets = DIFFICULTY_LAYOUT.map { tier ->
                selectedByTier[tier][nextInTier[tier]++]
            }
            return BingoBoard(seed, targets)
        }

        private val DIFFICULTY_LAYOUT = intArrayOf(
            2, 4, 1, 3, 0,
            3, 0, 2, 4, 1,
            4, 1, 3, 0, 2,
            0, 2, 4, 1, 3,
            1, 3, 0, 2, 4,
        )
    }
}

/** 只包含 Minecraft 1.20.6 中可作为物品获得的目标。 */
internal object BingoTargetPool {
    val tiers: List<List<Material>> = listOf(
        listOf(
            Material.COBBLESTONE,
            Material.DIRT,
            Material.STICK,
            Material.CRAFTING_TABLE,
            Material.FURNACE,
            Material.CHEST,
            Material.TORCH,
            Material.STONE_PICKAXE,
            Material.STONE_AXE,
            Material.CHARCOAL,
            Material.FLINT,
            Material.BOWL,
            Material.GLASS,
            Material.LEATHER,
            Material.FEATHER,
        ),
        listOf(
            Material.COAL,
            Material.RAW_IRON,
            Material.IRON_INGOT,
            Material.RAW_COPPER,
            Material.COPPER_INGOT,
            Material.BUCKET,
            Material.SHIELD,
            Material.SHEARS,
            Material.WHITE_WOOL,
            Material.BONE,
            Material.STRING,
            Material.ROTTEN_FLESH,
            Material.GUNPOWDER,
            Material.BREAD,
            Material.PAPER,
        ),
        listOf(
            Material.GOLD_INGOT,
            Material.REDSTONE,
            Material.LAPIS_LAZULI,
            Material.AMETHYST_SHARD,
            Material.COMPASS,
            Material.CLOCK,
            Material.SPYGLASS,
            Material.MINECART,
            Material.RAIL,
            Material.CAULDRON,
            Material.HOPPER,
            Material.BOOKSHELF,
            Material.ITEM_FRAME,
            Material.PAINTING,
            Material.TNT,
        ),
        listOf(
            Material.DIAMOND,
            Material.OBSIDIAN,
            Material.ENDER_PEARL,
            Material.BLAZE_ROD,
            Material.BREWING_STAND,
            Material.ENCHANTING_TABLE,
            Material.ANVIL,
            Material.GOLDEN_APPLE,
            Material.CROSSBOW,
            Material.FIREWORK_ROCKET,
            Material.SLIME_BALL,
            Material.HONEY_BOTTLE,
            Material.SADDLE,
            Material.NAME_TAG,
            Material.GHAST_TEAR,
        ),
        listOf(
            Material.DIAMOND_BLOCK,
            Material.NETHERITE_SCRAP,
            Material.ENDER_EYE,
            Material.ENDER_CHEST,
            Material.END_CRYSTAL,
            Material.MAGMA_CREAM,
            Material.GLISTERING_MELON_SLICE,
            Material.GOLDEN_CARROT,
            Material.FERMENTED_SPIDER_EYE,
            Material.RABBIT_STEW,
            Material.FIRE_CHARGE,
            Material.TNT_MINECART,
            Material.ACTIVATOR_RAIL,
            Material.CAKE,
            Material.JUKEBOX,
        ),
    )

    private val tierByTarget: Map<Material, Int> = buildMap {
        tiers.forEachIndexed { tier, targets -> targets.forEach { put(it, tier) } }
    }

    fun tierOf(target: Material): Int? = tierByTarget[target]
}
