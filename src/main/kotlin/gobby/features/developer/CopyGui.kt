package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ConfigUtils
import gobby.utils.GuiDump
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import java.io.File

object CopyGui : Module("Copy GUI", "Press the keybind in a GUI to dump its contents to /schematics", Category.DEVELOPER) {

    private val copyKey by KeybindSetting("Copy GUI", desc = "Press in any container GUI to copy its contents to a JSON file in /schematics")

    private val schematicsDir = ConfigUtils.directory("schematics")

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (copyKey == 0 || event.key != copyKey) return

        val screen = mc.gui.screen() as? AbstractContainerScreen<*>
        if (screen == null) {
            errorMessage("Not in a container GUI")
            enabled = false
            return
        }

        copyScreen(screen)
        enabled = false
    }

    private fun copyScreen(screen: AbstractContainerScreen<*>) {
        val title = screen.title.string
        val file = File(schematicsDir, "gui_${GuiDump.safeName(title)}_${System.currentTimeMillis()}.json")
        file.writeText(ConfigUtils.gson.toJson(GuiDump.of(screen)))
        modMessage(
            "§aCopied GUI §f\"$title\" §a(${GuiDump.filledCount(screen)}/${screen.menu.slots.size} slots) to §e${file.name}"
        )
    }
}
