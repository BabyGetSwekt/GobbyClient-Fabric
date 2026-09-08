package gobby.events.gui

import gobby.events.Events
import net.minecraft.client.gui.screens.Screen

class ScreenMouseClickEvent(
    val screen: Screen,
    val mouseX: Double,
    val mouseY: Double,
    val button: Int
) : Events.Cancelable<Unit>()
