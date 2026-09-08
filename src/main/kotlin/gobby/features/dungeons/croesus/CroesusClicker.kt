package gobby.features.dungeons.croesus

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ContainerClicks
import gobby.utils.Utils.getRandomInt
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket

object CroesusClicker {

    private val clickClock = Clock()
    private val pendingClock = Clock(5_000L)

    @Volatile
    private var pendingSlot: Int? = null

    @Volatile
    private var nextClickDelay = 0L

    val isPending: Boolean get() = pendingSlot != null

    val isMenuSettled: Boolean get() = clickClock.hasTimePassed(nextClickDelay)

    val isStuck: Boolean get() = isPending && pendingClock.hasTimePassed()

    val awaitingSlot: Int? get() = pendingSlot

    fun queue(slot: Int) {
        pendingSlot = slot
        pendingClock.update()
    }

    fun clear() {
        pendingSlot = null
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        when (event.packet) {
            is ClientboundContainerSetSlotPacket,
            is ClientboundContainerSetContentPacket,
            is ClientboundOpenScreenPacket -> restartDelay()
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val slot = pendingSlot ?: return
        if (!clickClock.hasTimePassed(nextClickDelay)) return
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        val stack = screen.menu.slots.getOrNull(slot)?.item?.takeUnless { it.isEmpty } ?: return
        modMessage("§7Clicking slot §f$slot §7(§f${stack.hoverName.string.noControlCodes}§7) in §f${screen.title.string.noControlCodes}")
        ContainerClicks.pickup(screen.menu.containerId, slot)
        pendingSlot = null
        restartDelay()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = clear()

    private fun restartDelay() {
        clickClock.update()
        nextClickDelay = AutoCroesus.clickDelayMs + getRandomInt(0, 100)
    }
}
