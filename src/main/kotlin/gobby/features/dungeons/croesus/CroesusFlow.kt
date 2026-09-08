package gobby.features.dungeons.croesus

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.InteractUtils
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import gobby.utils.rotation.RotationUtils
import gobby.utils.render.Interpolate
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.Entity

private enum class Phase(val readsMenu: Boolean = true) {
    IDLE(false), REACHING_NPC(false), OPENING_MENU(false),
    SCANNING, NAVIGATING, OPENING_RUN, PICKING_CHEST, CONFIRMING
}

object CroesusFlow {

    private var phase = Phase.IDLE
    private var target: TrackedRun? = null
    private var targetTier: ChestTier? = null
    private var rerolledTarget = false
    private var leavingChestMenu = false
    private var aimingAtNpc = false
    private var ownsAngleLock = false
    private var npc: Entity? = null
    private var pageBeforeTurn: Int? = null
    private val reachClock = Clock(1_500L)
    private val pageClock = Clock(2_500L)
    private val menuClock = Clock(5_000L)

    fun start() {
        val entity = InteractUtils.findNamed("Croesus", 4.0)
        if (entity == null) {
            errorMessage("Croesus not nearby")
            AutoCroesus.stop()
            return
        }
        npc = entity
        returnToNpc()
    }

    fun abort() {
        phase = Phase.IDLE
        clearTarget()
        leavingChestMenu = false
        aimingAtNpc = false
        pageBeforeTurn = null
        npc = null
        releaseAngleLock()
        CroesusClicker.clear()
    }

    private fun releaseAngleLock() {
        if (!ownsAngleLock) return
        ownsAngleLock = false
        RotationUtils.stopAngleLock()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = abort()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!AutoCroesus.isAutoOpening) return if (phase != Phase.IDLE) abort() else Unit
        if (CroesusClicker.isStuck) return failStuckClick()
        if (CroesusClicker.isPending) return
        if (phase.readsMenu && !CroesusClicker.isMenuSettled) return
        when (phase) {
            Phase.IDLE -> {}
            Phase.REACHING_NPC -> reachNpc()
            Phase.OPENING_MENU -> awaitCroesusMenu()
            Phase.SCANNING -> scanPages()
            Phase.NAVIGATING -> navigateToTarget()
            Phase.OPENING_RUN -> openRun()
            Phase.PICKING_CHEST -> pickChest()
            Phase.CONFIRMING -> confirmChest()
        }
    }

    private fun reachNpc() {
        val entity = npc ?: return abort()
        if (!aimingAtNpc) return beginReachingNpc()
        if (!InteractUtils.leftClick(entity)) {
            if (reachClock.hasTimePassed()) failToReachNpc()
            return
        }
        aimingAtNpc = false
        releaseAngleLock()
        phase = Phase.OPENING_MENU
        menuClock.update()
    }

    private fun awaitCroesusMenu() {
        if (croesusScreen() == null) {
            if (menuClock.hasTimePassed()) failToOpenMenu()
            return
        }
        phase = if (CroesusData.scannedPages) Phase.NAVIGATING else Phase.SCANNING
        if (phase == Phase.SCANNING) modMessage("§eChecking all pages")
    }

    private fun failToOpenMenu() {
        errorMessage("Croesus did not open, stopping")
        AutoCroesus.stop()
    }

    private fun returnToNpc() {
        phase = Phase.REACHING_NPC
        aimingAtNpc = false
    }

    private fun beginReachingNpc() {
        if (mc.gui.screen() != null) mc.player?.closeContainer()
        aimingAtNpc = true
        ownsAngleLock = true
        reachClock.update()
        RotationUtils.startAngleLock(260L, 0.5f, ::aimAtNpc)
    }

    private fun failStuckClick() {
        val slot = CroesusClicker.awaitingSlot
        val title = (mc.gui.screen() as? AbstractContainerScreen<*>)?.title?.string?.noControlCodes.orEmpty()
        if (leavingChestMenu) {
            leavingChestMenu = false
            CroesusClicker.clear()
            modMessage("§7Back button on slot $slot is missing in \"$title\", walking back to Croesus")
            returnToNpc()
            return
        }
        errorMessage("Slot $slot never loaded in \"$title\", stopping")
        AutoCroesus.stop()
    }

    private fun failToReachNpc() {
        errorMessage("Could not reach Croesus, stand closer with a clear line of sight")
        AutoCroesus.stop()
    }

    private fun scanPages() {
        val screen = croesusScreen() ?: return returnToNpc()
        val page = CroesusMenu.pageOf(screen) ?: return
        if (awaitingPageTurn(page.current)) return
        CroesusData.record(page.current, CroesusMenu.runs(screen))
        if (CroesusMenu.hasNextPageButton(screen)) return turnPage(page.current, CroesusMenu.NEXT_PAGE_SLOT)
        CroesusData.markScanned()
        modMessage("§aFound ${worthVisiting().size} runs to open")
        phase = Phase.NAVIGATING
    }

    private fun worthVisiting(): List<TrackedRun> = CroesusData.openable
        .filter { !it.needsChestKey || (AutoCroesus.usesChestKeys && !it.hasUsedChestKey) }

    private fun navigateToTarget() {
        val screen = croesusScreen() ?: return returnToNpc()
        leavingChestMenu = false
        val page = CroesusMenu.pageOf(screen) ?: return
        if (awaitingPageTurn(page.current)) return
        val run = target ?: worthVisiting().firstOrNull() ?: return finish()
        target = run
        when {
            page.current == run.page -> phase = Phase.OPENING_RUN
            page.current < run.page -> turnPage(page.current, CroesusMenu.NEXT_PAGE_SLOT)
            else -> turnPage(page.current, CroesusMenu.PREVIOUS_PAGE_SLOT)
        }
    }

    private fun awaitingPageTurn(current: Int): Boolean {
        val clicked = pageBeforeTurn ?: return false
        if (current != clicked || pageClock.hasTimePassed()) pageBeforeTurn = null
        return pageBeforeTurn != null
    }

    private fun turnPage(from: Int, slot: Int) {
        pageBeforeTurn = from
        pageClock.update()
        CroesusClicker.queue(slot)
    }

    private fun openRun() {
        val screen = croesusScreen() ?: return returnToNpc()
        val run = target ?: return finish()
        if (CroesusMenu.pageOf(screen)?.current != run.page) {
            phase = Phase.NAVIGATING
            return
        }
        CroesusClicker.queue(run.slot)
        phase = Phase.PICKING_CHEST
    }

    private fun pickChest() {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!CroesusChestMenu.isOpen(screen)) return
        val run = target ?: return finish()
        val chests = CroesusChestMenu.chests(screen)
        val best = mostProfitable(chests, run)
            ?: chests.firstOrNull { it.isFree }
            ?: return skipRun("nothing worth opening")
        targetTier = best.tier
        CroesusClicker.queue(best.slot)
        phase = Phase.CONFIRMING
    }

    private fun mostProfitable(chests: List<CroesusChest>, run: TrackedRun): CroesusChest? = chests
        .mapNotNull { chest -> pricedOf(chest)?.let { chest to it } }
        .filter { (chest, _) -> !chest.requiresKey || AutoCroesus.canSpendKey(run) }
        .filter { (chest, priced) -> CroesusFilters.holdsAlwaysBuy(priced) || meetsMinimum(chest, priced) }
        .maxWithOrNull(compareBy({ CroesusFilters.holdsAlwaysBuy(it.second) }, { it.second.profit }))?.first

    private fun meetsMinimum(chest: CroesusChest, priced: PricedChest): Boolean =
        priced.profit >= if (chest.requiresKey) AutoCroesus.chestKeyMinimum else AutoCroesus.minimumProfitFor(chest.tier)

    private fun pricedOf(chest: CroesusChest): PricedChest? =
        (CroesusPricing.evaluate(chest.costLine, chest.rewardLines) as? ChestEvaluation.Priced)?.chest

    private fun confirmChest() {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!CroesusRewardMenu.isOpen(screen)) return
        val run = target ?: return finish()
        val priced = (CroesusRewardMenu.evaluate(screen) as? ChestEvaluation.Priced)?.chest
        val tier = targetTier
        val mayReroll = !rerolledTarget && !run.hasRerolled && priced != null && tier != null &&
            !CroesusFilters.holdsAlwaysBuy(priced) &&
            AutoCroesus.shouldReroll(run.floor, run.masterMode, tier, priced)
        if (mayReroll) {
            if (!CroesusRewardMenu.hasKismet(screen)) return AutoCroesus.onMissingKismet()
            rerolledTarget = true
            CroesusClicker.queue(CroesusRewardMenu.REROLL_SLOT)
            return
        }
        CroesusClicker.queue(CroesusRewardMenu.OPEN_SLOT)
        CroesusData.markHandled(run)
        clearTarget()
        returnToNpc()
    }

    private fun clearTarget() {
        target = null
        targetTier = null
        rerolledTarget = false
    }

    private fun skipRun(reason: String) {
        target?.let { CroesusData.markHandled(it) }
        modMessage("§7Skipping run: $reason")
        clearTarget()
        leavingChestMenu = true
        CroesusClicker.queue(CroesusChestMenu.BACK_SLOT)
        phase = Phase.NAVIGATING
    }

    private fun finish() {
        modMessage("§aAuto Croesus finished")
        AutoCroesus.stop()
    }

    private fun croesusScreen(): AbstractContainerScreen<*>? =
        (mc.gui.screen() as? AbstractContainerScreen<*>)?.takeIf { CroesusMenu.isOpen(it) }

    private fun aimAtNpc(): Pair<Float, Float>? =
        npc?.let { calcAimAnglesBetween(Interpolate.interpolatedEyePos(), it.boundingBox.center) }
}
