package gobby.features.dungeons.croesus

import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.RomanNumerals
import gobby.utils.getLoreStrings
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

enum class ChestTier(val displayName: String) {
    WOOD("Wood"), GOLD("Gold"), DIAMOND("Diamond"), EMERALD("Emerald"), OBSIDIAN("Obsidian"), BEDROCK("Bedrock");

    companion object {
        fun of(name: String): ChestTier? = entries.firstOrNull { it.displayName == name }
    }
}

class CroesusChest(
    val slot: Int,
    val tier: ChestTier,
    val costLine: String,
    val rewardLines: List<String>,
    val requiresKey: Boolean
) {
    val isFree: Boolean get() = !requiresKey && CroesusPricing.isFreeCost(costLine)
}

object CroesusChestMenu {

    private const val CONTENTS_HEADER = "Contents"
    private const val COST_HEADER = "Cost"
    private const val MASTER_GROUP = "master"
    private const val FLOOR_GROUP = "floor"
    const val CHEST_KEY_COST = "Dungeon Chest Key"
    const val BACK_SLOT = 30

    private val TITLE = Regex("""^(?<$MASTER_GROUP>Master )?Catacombs - Floor (?<$FLOOR_GROUP>\w+)$""")
    private val CHEST_SLOTS = 10..16

    fun matchesTitle(title: String): Boolean = titleMatch(title) != null

    fun isOpen(screen: AbstractContainerScreen<*>): Boolean = titleMatch(screen) != null

    fun floorOf(screen: AbstractContainerScreen<*>): Int? =
        titleMatch(screen)?.groups?.get(FLOOR_GROUP)?.value?.let { RomanNumerals.parse(it) }

    fun isMasterMode(screen: AbstractContainerScreen<*>): Boolean =
        titleMatch(screen)?.groups?.get(MASTER_GROUP) != null

    fun chests(screen: AbstractContainerScreen<*>): List<CroesusChest> =
        CHEST_SLOTS.mapNotNull { slot ->
            val stack = screen.menu.slots.getOrNull(slot)?.item?.takeUnless { it.isEmpty } ?: return@mapNotNull null
            chestOf(slot, stack)
        }

    private fun chestOf(slot: Int, stack: ItemStack): CroesusChest? {
        val tier = ChestTier.of(stack.hoverName.string.noControlCodes.trim()) ?: return null
        val lore = stack.getLoreStrings().map { it.noControlCodes.trimEnd() }
        val rewards = section(lore, CONTENTS_HEADER)
        val costs = section(lore, COST_HEADER)
        val coins = costs.firstOrNull() ?: return null
        if (rewards.isEmpty()) return null
        return CroesusChest(slot, tier, coins, rewards, costs.any { it == CHEST_KEY_COST })
    }

    private fun section(lore: List<String>, header: String): List<String> {
        val start = lore.indexOfFirst { it.trim() == header }
        if (start == -1) return emptyList()
        return lore.drop(start + 1).takeWhile { it.isNotBlank() }
    }

    private fun titleMatch(title: String): MatchResult? = TITLE.matchEntire(title.noControlCodes.trim())

    private fun titleMatch(screen: AbstractContainerScreen<*>): MatchResult? = titleMatch(screen.title.string)
}
