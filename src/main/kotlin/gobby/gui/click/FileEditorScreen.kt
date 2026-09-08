package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color

private const val MARGIN = 34
private const val EDIT_PAD = 10
private const val HEADER_H = 22
private const val RADIUS = 5
private const val TEXT_SCALE = 0.9f
private const val LINE_GAP = 2
private const val GUTTER_W = 22
private const val CARET_W = 1
private const val MAX_CHARS = 200_000
private const val SCROLL_LINES = 3
private const val HINT = "Ctrl+S save   Esc exit"
private const val CONFIRM_W = 220
private const val CONFIRM_H = 60
private const val BUTTON_W = 92
private const val BUTTON_H = 18
private const val BUTTON_GAP = 12
private const val CONFIRM_TITLE = "You have unsaved changes"
private const val SAVE_LABEL = "Save and exit"
private const val RETURN_LABEL = "Return"
private val BACKDROP = Color(0, 0, 0, 170).rgb

class FileEditorScreen(private val setting: FileSetting, private val parent: Screen?) :
    Screen(Component.literal(setting.name)) {

    private val field = TextField({ it.filter { c -> c == '\n' || c.code >= ' '.code } }, MAX_CHARS)
    private var scroll = 0
    private var loaded = false
    private var savedText = ""
    private var confirming = false
    private var pressedInside = false

    override fun isPauseScreen() = false

    override fun init() {
        if (loaded) return
        field.load(setting.readText())
        savedText = field.text
        loaded = true
    }

    override fun onClose() {
        if (field.text == savedText) return exit()
        confirming = true
    }

    private fun exit() = mc.gui.setScreen(parent)

    private fun saveAndExit() {
        save()
        exit()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        ctx.fill(0, 0, width, height, BACKDROP)
        GobbyDraw.roundedBox(ctx, MARGIN, MARGIN, bodyW + EDIT_PAD * 2, panelH, RADIUS, cShellBg, cShellEdge)
        drawTextScaled(ctx, MARGIN + EDIT_PAD, MARGIN + EDIT_PAD, setting.name, TEXT_SCALE, cInk, false)
        drawTextScaled(ctx, MARGIN + EDIT_PAD, MARGIN + panelH - EDIT_PAD - lineH, HINT, TEXT_SCALE, cInkGhost, false)
        clampScroll()
        drawLines(ctx)
        if (confirming) drawConfirm(ctx)
    }

    private fun drawLines(ctx: GuiGraphicsExtractor) {
        val all = TextArea.lines(field.text)
        val caretLine = TextArea.lineOf(field.text, field.caret)
        var offset = all.take(scroll).sumOf { it.length + 1 }
        for (row in scroll until minOf(all.size, scroll + visibleLines)) {
            val line = all[row]
            val y = textTop + (row - scroll) * lineH
            drawTextScaled(ctx, MARGIN + EDIT_PAD, y, "${row + 1}", TEXT_SCALE, cInkGhost, false)
            drawSelection(ctx, line, offset, y)
            drawTextScaled(ctx, textX, y, line, TEXT_SCALE, cInk, false)
            if (row == caretLine && field.caretVisible()) drawCaret(ctx, line, offset, y)
            offset += line.length + 1
        }
    }

    private fun drawSelection(ctx: GuiGraphicsExtractor, line: String, offset: Int, y: Int) {
        if (!field.hasSelection) return
        if (field.selectionStart > offset + line.length || field.selectionEnd <= offset) return
        val from = (field.selectionStart - offset).coerceIn(0, line.length)
        val to = (field.selectionEnd - offset).coerceIn(0, line.length)
        val left = textX + textWScaled(line.take(from), TEXT_SCALE)
        val right = textX + textWScaled(line.take(to), TEXT_SCALE)
        ctx.fill(left, y, if (right > left) right else left + CARET_W, y + lineH, cSelection)
    }

    private fun drawCaret(ctx: GuiGraphicsExtractor, line: String, offset: Int, y: Int) {
        val x = textX + textWScaled(line.take(field.caret - offset), TEXT_SCALE)
        ctx.fill(x, y, x + CARET_W, y + lineH - LINE_GAP, cInk)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (confirming) return handleConfirmKey(input.key())
        if (FileEditorKeys.handle(field, input.key())) {
            if (FileEditorKeys.savesFile(input.key())) save()
            followCaret()
            return true
        }
        return super.keyPressed(input)
    }

    private fun handleConfirmKey(key: Int): Boolean {
        when (key) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> saveAndExit()
            GLFW.GLFW_KEY_ESCAPE -> confirming = false
        }
        return true
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (confirming) return true
        field.insert(input.codepoint().toChar().toString())
        followCaret()
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (confirming) return handleConfirmClick(click.x().toInt(), click.y().toInt())
        pressedInside = true
        placeCaretAt(click.x(), click.y(), extend = Modifiers.shift())
        return true
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        pressedInside = false
        return super.mouseReleased(click)
    }

    private fun handleConfirmClick(mouseX: Int, mouseY: Int): Boolean {
        val point = mouseX to mouseY
        if (point in saveRect) saveAndExit()
        if (point in returnRect) confirming = false
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (confirming || !pressedInside) return true
        placeCaretAt(click.x(), click.y(), extend = true)
        return true
    }

    private fun placeCaretAt(mouseX: Double, mouseY: Double, extend: Boolean) {
        val all = TextArea.lines(field.text)
        val row = (rowAt(mouseY) + scroll).coerceIn(0, all.lastIndex)
        val column = TextFieldView.caretIndexAt(all[row], textX, mouseX.toInt(), TEXT_SCALE)
        field.placeCaret(TextArea.indexAt(field.text, row, column), extend)
        followCaret()
    }

    private fun rowAt(mouseY: Double): Int = Math.floorDiv(mouseY.toInt() - textTop, lineH)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        scroll -= vertical.toInt() * SCROLL_LINES
        clampScroll()
        return true
    }

    private fun followCaret() {
        val line = TextArea.lineOf(field.text, field.caret)
        if (line < scroll) scroll = line
        if (line >= scroll + visibleLines) scroll = line - visibleLines + 1
        clampScroll()
    }

    private fun clampScroll() {
        scroll = scroll.coerceIn(0, (TextArea.lines(field.text).size - visibleLines).coerceAtLeast(0))
    }

    private fun save() {
        if (setting.writeText(field.text)) savedText = field.text
        else errorMessage("Could not save ${setting.name}")
    }

    private fun drawConfirm(ctx: GuiGraphicsExtractor) {
        ctx.fill(0, 0, width, height, BACKDROP)
        GobbyDraw.roundedBox(ctx, confirmX, confirmY, CONFIRM_W, CONFIRM_H, RADIUS, cShellBg, cShellEdge)
        val titleX = confirmX + (CONFIRM_W - textWScaled(CONFIRM_TITLE, TEXT_SCALE)) / 2
        drawTextScaled(ctx, titleX, confirmY + EDIT_PAD, CONFIRM_TITLE, TEXT_SCALE, cInk, false)
        drawButton(ctx, saveRect, SAVE_LABEL, cViolet)
        drawButton(ctx, returnRect, RETURN_LABEL, cTrack)
    }

    private fun drawButton(ctx: GuiGraphicsExtractor, rect: Rect, label: String, colour: Int) {
        GobbyDraw.roundedRect(ctx, rect.x, rect.y, rect.w, rect.h, RADIUS, colour)
        val textH = (tr.lineHeight * TEXT_SCALE).toInt()
        val x = rect.x + (rect.w - textWScaled(label, TEXT_SCALE)) / 2
        drawTextScaled(ctx, x, rect.y + (rect.h - textH) / 2, label, TEXT_SCALE, cInk, false)
    }

    private val confirmX: Int get() = (width - CONFIRM_W) / 2
    private val confirmY: Int get() = (height - CONFIRM_H) / 2
    private val buttonsY: Int get() = confirmY + CONFIRM_H - EDIT_PAD - BUTTON_H
    private val saveRect: Rect
        get() = Rect(confirmX + (CONFIRM_W - BUTTON_W * 2 - BUTTON_GAP) / 2, buttonsY, BUTTON_W, BUTTON_H)
    private val returnRect: Rect get() = Rect(saveRect.x + BUTTON_W + BUTTON_GAP, buttonsY, BUTTON_W, BUTTON_H)

    private val lineH: Int get() = (tr.lineHeight * TEXT_SCALE).toInt() + LINE_GAP
    private val panelH: Int get() = height - MARGIN * 2
    private val bodyW: Int get() = width - MARGIN * 2 - EDIT_PAD * 2
    private val textX: Int get() = MARGIN + EDIT_PAD + GUTTER_W
    private val textTop: Int get() = MARGIN + EDIT_PAD + HEADER_H
    private val visibleLines: Int get() = ((panelH - EDIT_PAD * 2 - HEADER_H - lineH) / lineH).coerceAtLeast(1)
}
