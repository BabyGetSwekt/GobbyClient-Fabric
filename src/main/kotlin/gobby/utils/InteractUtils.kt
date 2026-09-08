package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.noControlCodes
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

object InteractUtils {

    private const val REACH = 3.0
    private const val HITBOX_MARGIN = 0.1

    fun findNamed(name: String, maxDistance: Double = REACH): Entity? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val stand = level.entitiesForRendering()
            .filter { it.name.string.noControlCodes.trim() == name }
            .filter { it.distanceTo(player) <= maxDistance }
            .minByOrNull { it.distanceTo(player) } ?: return null
        return interactableAt(stand) ?: stand
    }

    fun rightClick(entity: Entity): Boolean {
        val connection = mc.connection ?: return false
        val player = mc.player ?: return false
        if (player.distanceTo(entity) > REACH) return false
        connection.send(ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, hitVector(entity), player.isShiftKeyDown))
        connection.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
        return true
    }

    fun rightClickNamed(name: String): Boolean = findNamed(name)?.let { rightClick(it) } == true

    fun leftClick(entity: Entity): Boolean {
        if (mc.gui.screen() != null) return false
        if ((mc.hitResult as? EntityHitResult)?.entity !== entity) return false
        PlayerUtils.leftClick()
        return true
    }

    private fun interactableAt(stand: Entity): Entity? {
        val level = mc.level ?: return null
        return level.entitiesForRendering()
            .filter { it !== stand && it.isPickable }
            .firstOrNull { it.position().distanceTo(stand.position()) < HITBOX_MARGIN }
    }

    private fun hitVector(entity: Entity): Vec3 {
        val player = mc.player ?: return Vec3.ZERO
        val origin = entity.position()
        val centre = origin.add(0.0, entity.bbHeight / 2.0, 0.0)
        val hit = entity.boundingBox.inflate(HITBOX_MARGIN).clip(player.eyePosition, centre).orElse(null)
        return hit?.subtract(origin) ?: Vec3.ZERO
    }
}
