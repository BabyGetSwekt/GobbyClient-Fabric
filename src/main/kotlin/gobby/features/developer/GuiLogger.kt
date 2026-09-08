package gobby.features.developer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.PacketSentEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ConfigUtils
import gobby.utils.GuiDump
import gobby.utils.timer.Cooldown
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import java.io.File

object GuiLogger : Module(
    "GUI Logger", "Records every GUI you open and every slot you click so a menu flow can be replayed",
    Category.DEVELOPER
) {

    private const val RECORD_SECONDS = 5
    private const val LOGS_FOLDER = "guilogs"

    private val recordKey by KeybindSetting(
        "Record", desc = "Press to log every GUI and click for $RECORD_SECONDS seconds"
    )

    private val logsDir = ConfigUtils.directory(LOGS_FOLDER)
    private val recording = Cooldown()

    private var events = JsonArray()
    private var openScreen: AbstractContainerScreen<*>? = null
    private var active = false

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || recordKey == 0 || event.key != recordKey) return
        if (active) return
        events = JsonArray()
        openScreen = mc.gui.screen() as? AbstractContainerScreen<*>
        active = true
        recording.start(RECORD_SECONDS)
        modMessage("§aRecording GUIs for ${RECORD_SECONDS}s")
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (!active) return
        val packet = event.packet as? ServerboundContainerClickPacket ?: return
        events.add(clickEvent(packet))
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!active) return
        val screen = mc.gui.screen() as? AbstractContainerScreen<*>
        if (screen !== openScreen) {
            recordScreen()
            openScreen = screen
        }
        if (!recording.isActive) finish()
    }

    private fun clickEvent(packet: ServerboundContainerClickPacket): JsonObject = JsonObject().apply {
        addProperty("event", "click")
        addProperty("containerId", packet.containerId())
        addProperty("slot", packet.slotNum().toInt())
        addProperty("button", packet.buttonNum().toInt())
        addProperty("clickedName", clickedName(packet.slotNum().toInt()))
    }

    private fun clickedName(slot: Int): String =
        openScreen?.menu?.slots?.getOrNull(slot)?.item?.takeUnless { it.isEmpty }
            ?.hoverName?.string?.noControlCodes.orEmpty()

    private fun recordScreen() {
        val screen = openScreen ?: return
        events.add(JsonObject().apply {
            addProperty("event", "gui")
            add("contents", GuiDump.of(screen))
        })
    }

    private fun finish() {
        recordScreen()
        active = false
        openScreen = null
        val file = File(logsDir, "guilog_${System.currentTimeMillis()}.json")
        file.writeText(ConfigUtils.gson.toJson(events))
        modMessage("§aLogged ${events.size()} events to §e${file.name}")
    }
}
