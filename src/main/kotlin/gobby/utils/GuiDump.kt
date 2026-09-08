package gobby.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import gobby.utils.ChatUtils.noControlCodes
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

object GuiDump {

    fun of(screen: AbstractContainerScreen<*>): JsonObject = JsonObject().apply {
        addProperty("title", screen.title.string.noControlCodes)
        addProperty("size", screen.menu.slots.size)
        addProperty("containerId", screen.menu.containerId)
        add("slots", filledSlots(screen))
    }

    fun filledCount(screen: AbstractContainerScreen<*>): Int = screen.menu.slots.count { !it.item.isEmpty }

    fun safeName(title: String): String =
        title.noControlCodes.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "container" }

    private fun filledSlots(screen: AbstractContainerScreen<*>): JsonArray = JsonArray().apply {
        screen.menu.slots.filter { !it.item.isEmpty }.forEach { add(slotOf(it.index, it.item)) }
    }

    private fun slotOf(index: Int, stack: ItemStack): JsonObject = JsonObject().apply {
        addProperty("slot", index)
        addProperty("item", BuiltInRegistries.ITEM.getKey(stack.item).toString())
        addProperty("count", stack.count)
        addProperty("name", stack.hoverName.string.noControlCodes)
        add("lore", JsonArray().apply { stack.getLoreStrings().forEach { add(it.noControlCodes) } })
        addProperty("nbt", stack.encodeNbt().orEmpty())
    }
}
