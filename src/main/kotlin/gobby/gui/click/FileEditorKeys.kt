package gobby.gui.click

import gobby.utils.Utils
import org.lwjgl.glfw.GLFW

internal object FileEditorKeys {

    fun savesFile(key: Int): Boolean = Modifiers.ctrl() && key == GLFW.GLFW_KEY_S

    fun handle(field: TextField, key: Int): Boolean {
        val ctrl = Modifiers.ctrl()
        val shift = Modifiers.shift()
        val text = field.text
        when {
            ctrl && key == GLFW.GLFW_KEY_S -> Unit
            ctrl && key == GLFW.GLFW_KEY_A -> field.selectAll()
            ctrl && key == GLFW.GLFW_KEY_C -> Utils.setClipboard(field.selectedText())
            ctrl && key == GLFW.GLFW_KEY_X -> cut(field)
            ctrl && key == GLFW.GLFW_KEY_V -> field.insert(Utils.getClipboard())
            ctrl && key == GLFW.GLFW_KEY_Y -> field.redo()
            ctrl && key == GLFW.GLFW_KEY_Z -> if (shift) field.redo() else field.undo()
            key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER -> field.insert("\n")
            key == GLFW.GLFW_KEY_BACKSPACE -> field.deleteBackward()
            key == GLFW.GLFW_KEY_DELETE -> field.deleteForward()
            key == GLFW.GLFW_KEY_LEFT -> field.placeCaret(field.caret - 1, shift)
            key == GLFW.GLFW_KEY_RIGHT -> field.placeCaret(field.caret + 1, shift)
            key == GLFW.GLFW_KEY_UP -> field.placeCaret(TextArea.moved(text, field.caret, -1), shift)
            key == GLFW.GLFW_KEY_DOWN -> field.placeCaret(TextArea.moved(text, field.caret, 1), shift)
            key == GLFW.GLFW_KEY_HOME -> field.placeCaret(homeTarget(field, ctrl), shift)
            key == GLFW.GLFW_KEY_END -> field.placeCaret(endTarget(field, ctrl), shift)
            else -> return false
        }
        return true
    }

    private fun cut(field: TextField) {
        Utils.setClipboard(field.selectedText())
        if (field.hasSelection) field.deleteBackward()
    }

    private fun homeTarget(field: TextField, ctrl: Boolean): Int =
        if (ctrl) 0 else TextArea.lineStart(field.text, field.caret)

    private fun endTarget(field: TextField, ctrl: Boolean): Int =
        if (ctrl) field.text.length else TextArea.lineEnd(field.text, field.caret)
}
