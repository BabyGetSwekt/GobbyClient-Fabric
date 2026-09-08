package gobby.features.dungeons.croesus

import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.getLoreStrings
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

object CroesusRewardMenu {

    const val OPEN_SLOT = 31
    const val REROLL_SLOT = 50

    private const val REROLL_NAME = "Reroll Chest"
    private const val COST_HEADER = "Cost"

    fun isOpen(screen: AbstractContainerScreen<*>): Boolean = nameAt(screen, OPEN_SLOT) == "Open Reward Chest"

    fun hasReroll(screen: AbstractContainerScreen<*>): Boolean = nameAt(screen, REROLL_SLOT) == REROLL_NAME

    fun hasKismet(screen: AbstractContainerScreen<*>): Boolean {
        val stack = stackAt(screen, REROLL_SLOT) ?: return false
        if (stack.hoverName.string.noControlCodes.trim() != REROLL_NAME) return false
        return stack.getLoreStrings().none { it.noControlCodes.trim() == "Bring a Kismet Feather" }
    }

    fun evaluate(screen: AbstractContainerScreen<*>): ChestEvaluation? {
        val stack = stackAt(screen, OPEN_SLOT) ?: return null
        val lore = stack.getLoreStrings()
        val rewards = section(lore, "Contents")
        val costs = section(lore, COST_HEADER)
        if (rewards.isEmpty() || costs.isEmpty()) return null
        val needsKey = costs.any { it.noControlCodes.trim() == CroesusChestMenu.CHEST_KEY_COST }
        return CroesusPricing.evaluate(costs, rewards, needsKey)
    }

    fun requiresKey(screen: AbstractContainerScreen<*>): Boolean {
        val stack = stackAt(screen, OPEN_SLOT) ?: return false
        return section(stack.getLoreStrings(), COST_HEADER)
            .any { it.noControlCodes.trim() == CroesusChestMenu.CHEST_KEY_COST }
    }

    private fun section(lore: List<String>, header: String): List<String> {
        val start = lore.indexOfFirst { it.noControlCodes.trim() == header }
        if (start == -1) return emptyList()
        return lore.drop(start + 1).takeWhile { it.noControlCodes.isNotBlank() }
    }

    private fun stackAt(screen: AbstractContainerScreen<*>, slot: Int): ItemStack? =
        screen.menu.slots.getOrNull(slot)?.item?.takeUnless { it.isEmpty }

    private fun nameAt(screen: AbstractContainerScreen<*>, slot: Int): String? =
        stackAt(screen, slot)?.hoverName?.string?.noControlCodes?.trim()
}
