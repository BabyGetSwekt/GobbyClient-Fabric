package gobby.features.dungeons.croesus

import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.RomanNumerals
import gobby.utils.getLoreLines
import gobby.utils.isStruckThrough
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

enum class RunState { UNOPENED, PARTIALLY_OPENED, EXHAUSTED }

class RunModifiers(val kismetFeather: Boolean, val dungeonChestKey: Boolean)

class CroesusRun(
    val slot: Int,
    val floor: Int,
    val masterMode: Boolean,
    val state: RunState,
    val openedChest: ChestTier?,
    val modifiers: RunModifiers
) {
    val canOpen: Boolean get() = state != RunState.EXHAUSTED
}

class CroesusPage(val current: Int, val total: Int) {
    val hasNext: Boolean get() = current < total
    val hasPrevious: Boolean get() = current > 1
}

object CroesusMenu {

    const val NEXT_PAGE_SLOT = 53
    const val PREVIOUS_PAGE_SLOT = 45

    private const val OPENED_PREFIX = "Opened Chest: "
    private const val ENTRANCE_FLOOR = 0
    private const val CURRENT_PAGE_GROUP = "current"
    private const val TOTAL_PAGES_GROUP = "total"

    private val TITLE = Regex("""^\((?<$CURRENT_PAGE_GROUP>\d+)/(?<$TOTAL_PAGES_GROUP>\d+)\) Croesus$""")
    private val FLOOR = Regex("""^Floor (\w+)$""")
    private val RUN_SLOTS = listOf(10..16, 19..25, 28..34, 37..43).flatten()

    fun pageOf(title: String): CroesusPage? {
        val groups = TITLE.matchEntire(title.noControlCodes.trim())?.groups ?: return null
        val current = groups[CURRENT_PAGE_GROUP]?.value?.toIntOrNull() ?: return null
        val total = groups[TOTAL_PAGES_GROUP]?.value?.toIntOrNull() ?: return null
        return CroesusPage(current, total)
    }

    fun pageOf(screen: AbstractContainerScreen<*>): CroesusPage? = pageOf(screen.title.string)

    fun matchesTitle(title: String): Boolean = pageOf(title) != null

    fun isOpen(screen: AbstractContainerScreen<*>): Boolean = pageOf(screen) != null

    fun hasNextPageButton(screen: AbstractContainerScreen<*>): Boolean = nameAt(screen, NEXT_PAGE_SLOT) == "Next Page"

    fun hasPreviousPageButton(screen: AbstractContainerScreen<*>): Boolean =
        nameAt(screen, PREVIOUS_PAGE_SLOT) == "Previous Page"

    fun runs(screen: AbstractContainerScreen<*>): List<CroesusRun> =
        RUN_SLOTS.mapNotNull { slot -> stackAt(screen, slot)?.let { runOf(slot, it) } }

    private fun runOf(slot: Int, stack: ItemStack): CroesusRun? {
        val lines = stack.getLoreLines()
        val texts = lines.map { it.string.noControlCodes.trim() }
        val state = stateOf(texts) ?: return null
        val name = stack.hoverName.string.noControlCodes.trim()
        val opened = texts.firstOrNull { it.startsWith(OPENED_PREFIX) }
            ?.removePrefix(OPENED_PREFIX)?.let { ChestTier.of(it) }
        return CroesusRun(slot, floorOf(texts), name.startsWith("Master Mode "), state, opened, modifiersOf(lines, texts))
    }

    private fun stateOf(texts: List<String>): RunState? = when {
        texts.any { it == "No chests opened yet!" } -> RunState.UNOPENED
        texts.any { it == "No more chests to open!" } -> RunState.EXHAUSTED
        texts.any { it.startsWith(OPENED_PREFIX) } -> RunState.PARTIALLY_OPENED
        else -> null
    }

    private fun modifiersOf(lines: List<Component>, texts: List<String>): RunModifiers {
        val start = texts.indexOf("Available Modifiers:")
        if (start == -1) return RunModifiers(kismetFeather = false, dungeonChestKey = false)
        val available = texts.indices.drop(start + 1)
            .takeWhile { texts[it].isNotEmpty() }
            .filterNot { lines[it].isStruckThrough() }
            .map { texts[it] }
        return RunModifiers("Kismet Feather" in available, "Dungeon Chest Key" in available)
    }

    private fun floorOf(texts: List<String>): Int {
        val text = texts.firstNotNullOfOrNull { FLOOR.matchEntire(it)?.groupValues?.get(1) } ?: return ENTRANCE_FLOOR
        return RomanNumerals.parse(text) ?: ENTRANCE_FLOOR
    }

    private fun stackAt(screen: AbstractContainerScreen<*>, slot: Int): ItemStack? =
        screen.menu.slots.getOrNull(slot)?.item?.takeUnless { it.isEmpty }

    private fun nameAt(screen: AbstractContainerScreen<*>, slot: Int): String? =
        stackAt(screen, slot)?.hoverName?.string?.noControlCodes?.trim()
}
