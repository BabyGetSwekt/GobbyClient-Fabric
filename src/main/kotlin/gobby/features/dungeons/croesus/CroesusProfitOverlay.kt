package gobby.features.dungeons.croesus

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.ScreenMouseClickEvent
import gobby.events.gui.ScreenRenderEvent
import gobby.gui.click.GobbyDraw
import gobby.gui.click.cInkSoft
import gobby.gui.click.cShellBg
import gobby.gui.click.cShellEdge
import gobby.gui.click.cViolet
import gobby.gui.click.drawTextScaled
import gobby.gui.click.textWScaled
import gobby.mixin.accessor.AbstractContainerScreenAccessor
import gobby.utils.ContainerClicks
import gobby.utils.timer.Clock
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import java.awt.Color

private class ProfitRow(val slot: Int, val icon: ItemStack, val label: String, val evaluation: ChestEvaluation) {
    val priced: PricedChest? get() = (evaluation as? ChestEvaluation.Priced)?.chest
    val profit: Double? get() = priced?.profit
}

private class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun contains(px: Int, py: Int): Boolean = px in x..(x + w) && py in y..(y + h)
}

object CroesusProfitOverlay {

    private const val PANEL_GAP = 6
    private const val PANEL_PAD = 6
    private const val ROW_H = 18
    private const val ICON = 16
    private const val ICON_GAP = 5
    private const val RADIUS = 3
    private const val TEXT_SCALE = 0.9f
    private const val MIN_PANEL_W = 92
    private const val LINE_GAP = 2
    private const val TOOLTIP_GAP = 8
    private const val BUY_LABEL = "Buy Chest"
    private const val BUY_PAD = 5
    private const val BUY_H = 12
    private const val BUY_GAP = 5
    private const val PURCHASE_TIMEOUT_MS = 3_000L

    private val BUY_TEXT = Color.WHITE.rgb
    private val PROFIT_UP = Color(88, 214, 141).rgb
    private val PROFIT_DOWN = Color(231, 108, 108).rgb

    private val buyTargets = mutableMapOf<Rect, Int>()
    private val purchaseClock = Clock(PURCHASE_TIMEOUT_MS)

    private var confirmingPurchase = false

    @SubscribeEvent
    fun onScreenRender(event: ScreenRenderEvent) {
        buyTargets.clear()
        if (!AutoCroesus.showProfitOverlay) return
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        when {
            CroesusChestMenu.isOpen(screen) -> drawOverview(event.drawContext, screen, event.mouseX, event.mouseY)
            CroesusRewardMenu.isOpen(screen) -> drawSingle(event.drawContext, screen)
        }
    }

    @SubscribeEvent
    fun onMouseClick(event: ScreenMouseClickEvent) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        val slot = buyTargets.entries.firstOrNull { it.key.contains(event.mouseX.toInt(), event.mouseY.toInt()) }?.value ?: return
        event.cancel()
        if (AutoCroesus.isAutoOpening) return
        ContainerClicks.pickup(screen.menu.containerId, slot)
        if (!CroesusChestMenu.isOpen(screen)) return
        confirmingPurchase = true
        purchaseClock.update()
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!confirmingPurchase) return
        if (purchaseClock.hasTimePassed()) return run { confirmingPurchase = false }
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!CroesusRewardMenu.isOpen(screen)) return
        confirmingPurchase = false
        CroesusClicker.queue(CroesusRewardMenu.OPEN_SLOT)
    }

    private fun drawOverview(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, mouseX: Int, mouseY: Int) {
        val rows = CroesusChestMenu.chests(screen).map { chest ->
            ProfitRow(chest.slot, iconAt(screen, chest.slot), chest.tier.displayName, CroesusPricing.evaluate(chest.costLines, chest.rewardLines, chest.requiresKey))
        }
        if (rows.isEmpty()) return

        val accessor = screen as AbstractContainerScreenAccessor
        val buyW = buyWidth()
        val widest = rows.maxOf { textWScaled(textOf(it), TEXT_SCALE) + if (showsBuy(it)) BUY_GAP + buyW else 0 }
        val width = (PANEL_PAD * 2 + ICON + ICON_GAP + widest).coerceAtLeast(MIN_PANEL_W)
        val height = PANEL_PAD * 2 + rows.size * ROW_H
        val x = accessor.x + accessor.backgroundWidth + PANEL_GAP
        val y = accessor.y

        GobbyDraw.roundedBox(ctx, x, y, width, height, RADIUS, cShellBg, cShellEdge)
        var hovered: ProfitRow? = null
        rows.forEachIndexed { index, row ->
            val rowY = y + PANEL_PAD + index * ROW_H
            if (mouseX in x..(x + width) && mouseY in rowY until (rowY + ROW_H)) hovered = row
            ctx.item(row.icon, x + PANEL_PAD, rowY + (ROW_H - ICON) / 2)
            val textX = x + PANEL_PAD + ICON + ICON_GAP
            drawLine(ctx, textX, rowY + (ROW_H - textHeight()) / 2, textOf(row), colorOf(row.profit))
            if (showsBuy(row)) {
                val buyX = textX + textWScaled(textOf(row), TEXT_SCALE) + BUY_GAP
                drawBuy(ctx, buyX, rowY + (ROW_H - BUY_H) / 2, row.slot)
            }
        }
        hovered?.let { drawBreakdown(ctx, it, mouseX + TOOLTIP_GAP, mouseY, withBuy = false) }
    }

    private fun drawSingle(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        val evaluation = CroesusRewardMenu.evaluate(screen) ?: return
        val accessor = screen as AbstractContainerScreenAccessor
        val row = ProfitRow(CroesusRewardMenu.OPEN_SLOT, iconAt(screen, CroesusRewardMenu.OPEN_SLOT), screen.title.string, evaluation)
        drawBreakdown(ctx, row, accessor.x + accessor.backgroundWidth + PANEL_GAP, accessor.y, withBuy = true)
    }

    private fun drawBreakdown(
        ctx: GuiGraphicsExtractor, row: ProfitRow, anchorX: Int, anchorY: Int, withBuy: Boolean
    ) {
        val lines = breakdownOf(row)
        if (lines.isEmpty()) return
        val showBuy = withBuy && showsBuy(row)
        val widest = lines.maxOf { textWScaled(it.first, TEXT_SCALE) }.coerceAtLeast(if (showBuy) buyWidth() else 0)
        val width = PANEL_PAD * 2 + widest
        val textBlock = lines.size * (textHeight() + LINE_GAP) - LINE_GAP
        val height = PANEL_PAD * 2 + textBlock + if (showBuy) BUY_GAP + BUY_H else 0
        val x = anchorX.coerceAtMost(mc.window.guiScaledWidth - width - TOOLTIP_GAP)
        val y = anchorY.coerceAtMost(mc.window.guiScaledHeight - height - TOOLTIP_GAP)

        GobbyDraw.roundedBox(ctx, x, y, width, height, RADIUS, cShellBg, cShellEdge)
        lines.forEachIndexed { index, (text, colour) ->
            drawLine(ctx, x + PANEL_PAD, y + PANEL_PAD + index * (textHeight() + LINE_GAP), text, colour)
        }
        if (showBuy) drawBuy(ctx, x + PANEL_PAD, y + PANEL_PAD + textBlock + BUY_GAP, row.slot)
    }

    private fun drawBuy(ctx: GuiGraphicsExtractor, x: Int, y: Int, slot: Int) {
        val width = buyWidth()
        GobbyDraw.roundedRect(ctx, x, y, width, BUY_H, RADIUS, cViolet)
        val textX = x + (width - textWScaled(BUY_LABEL, TEXT_SCALE)) / 2
        drawTextScaled(ctx, textX, y + (BUY_H - textHeight()) / 2, BUY_LABEL, TEXT_SCALE, BUY_TEXT, true)
        buyTargets[Rect(x, y, width, BUY_H)] = slot
    }

    private fun showsBuy(row: ProfitRow): Boolean =
        AutoCroesus.showBuyButton && (row.profit ?: 0.0) > 0.0

    private fun breakdownOf(row: ProfitRow): List<Pair<String, Int>> {
        val chest = row.priced ?: return listOf((row.evaluation as? ChestEvaluation.Unknown)?.reason.orEmpty() to cInkSoft)
        val rewards = chest.rewards.sortedByDescending { it.total }
            .map { "${it.displayName}: ${formatProfit(it.total)}" to colorOf(it.total) }
        val cost = "Chest Price: ${formatProfit(-chest.cost.toDouble())}" to colorOf(-chest.cost.toDouble())
        return rewards + cost + ("Result: ${formatProfit(chest.profit)}" to colorOf(chest.profit))
    }

    private fun drawLine(ctx: GuiGraphicsExtractor, x: Int, y: Int, text: String, colour: Int) =
        drawTextScaled(ctx, x, y, text, TEXT_SCALE, colour, true)

    private fun iconAt(screen: AbstractContainerScreen<*>, slot: Int): ItemStack =
        screen.menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

    private fun buyWidth(): Int = textWScaled(BUY_LABEL, TEXT_SCALE) + BUY_PAD * 2

    private fun textHeight(): Int = (mc.font.lineHeight * TEXT_SCALE).toInt()

    private fun textOf(row: ProfitRow): String = "${row.label}: ${formatProfit(row.profit)}"

    private fun colorOf(profit: Double?): Int = when {
        profit == null -> cInkSoft
        profit >= 0 -> PROFIT_UP
        else -> PROFIT_DOWN
    }

    private fun formatProfit(profit: Double?): String {
        if (profit == null) return "?"
        val sign = if (profit >= 0) "+" else "-"
        val amount = kotlin.math.abs(profit)
        return sign + when {
            amount >= 1_000_000 -> "%.1fM".format(amount / 1_000_000)
            amount >= 1_000 -> "%.0fk".format(amount / 1_000)
            else -> "%.0f".format(amount)
        }
    }
}
