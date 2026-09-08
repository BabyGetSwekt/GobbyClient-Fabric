package gobby.utils

object RomanNumerals {

    private val VALUES = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)

    fun parse(text: String): Int? {
        if (text.isEmpty()) return null
        var total = 0
        var highest = 0
        text.reversed().forEach { symbol ->
            val value = VALUES[symbol] ?: return null
            total += if (value < highest) -value else value
            highest = maxOf(highest, value)
        }
        return total
    }
}
