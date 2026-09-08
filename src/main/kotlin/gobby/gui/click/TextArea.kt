package gobby.gui.click

private const val NEWLINE = '\n'

object TextArea {

    fun lines(text: String): List<String> = text.split(NEWLINE)

    fun lineStart(text: String, index: Int): Int = text.lastIndexOf(NEWLINE, (index - 1).coerceAtLeast(0))
        .let { if (it < 0 || index == 0) 0 else it + 1 }

    fun lineEnd(text: String, index: Int): Int =
        text.indexOf(NEWLINE, index).let { if (it < 0) text.length else it }

    fun lineOf(text: String, index: Int): Int = text.take(index).count { it == NEWLINE }

    fun columnOf(text: String, index: Int): Int = index - lineStart(text, index)

    fun indexAt(text: String, line: Int, column: Int): Int {
        val all = lines(text)
        val row = line.coerceIn(0, all.lastIndex)
        val start = all.take(row).sumOf { it.length + 1 }
        return start + column.coerceIn(0, all[row].length)
    }

    fun moved(text: String, index: Int, rows: Int): Int =
        indexAt(text, lineOf(text, index) + rows, columnOf(text, index))
}
