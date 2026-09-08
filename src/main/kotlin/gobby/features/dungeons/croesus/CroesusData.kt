package gobby.features.dungeons.croesus

import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent

class TrackedRun(
    val page: Int,
    val slot: Int,
    val floor: Int,
    val masterMode: Boolean,
    val state: RunState,
    val openedChest: ChestTier?,
    val modifiers: RunModifiers
) {
    val canOpen: Boolean get() = state != RunState.EXHAUSTED

    val hasRerolled: Boolean get() = !modifiers.kismetFeather

    val hasUsedChestKey: Boolean get() = !modifiers.dungeonChestKey

    val needsChestKey: Boolean get() = state == RunState.PARTIALLY_OPENED
}

object CroesusData {

    private val runs = linkedMapOf<Pair<Int, Int>, TrackedRun>()
    private val handled = mutableSetOf<Pair<Int, Int>>()

    var scannedPages = false
        private set

    var lastPage = 1
        private set

    val openable: List<TrackedRun>
        get() = runs.values.filter { it.canOpen && it.key !in handled }.sortedByDescending { it.page }

    private val TrackedRun.key: Pair<Int, Int> get() = page to slot

    fun record(page: Int, found: List<CroesusRun>) {
        found.forEach { run ->
            runs[page to run.slot] = TrackedRun(
                page, run.slot, run.floor, run.masterMode, run.state, run.openedChest, run.modifiers
            )
        }
        if (page > lastPage) lastPage = page
    }

    fun markScanned() {
        scannedPages = true
    }

    fun markHandled(run: TrackedRun) {
        handled.add(run.key)
    }

    fun clear() {
        runs.clear()
        handled.clear()
        scannedPages = false
        lastPage = 1
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = clear()
}
