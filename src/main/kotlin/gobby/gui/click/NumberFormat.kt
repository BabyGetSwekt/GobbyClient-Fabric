package gobby.gui.click

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val THOUSAND = 1_000f
private const val MILLION = 1_000_000f
private const val ABBREVIATE_FROM = 10_000f
private const val GROUP_FROM = 10_000f
private const val GROUP_SIZE = 3

private val GROUPED = Regex("""\d{1,3}(\.\d{3})+""")

object NumberFormat {

    fun abbreviate(value: Float, decimals: Int): String = when {
        abs(value) >= MILLION -> "${trim(value / MILLION)}m"
        abs(value) >= ABBREVIATE_FROM -> "${trim(value / THOUSAND)}k"
        else -> plain(value, decimals)
    }

    fun grouped(value: Float, decimals: Int): String {
        val text = plain(value, decimals)
        if (abs(value) < GROUP_FROM) return text
        val negative = text.startsWith("-")
        val digits = text.removePrefix("-").substringBefore('.')
        val fraction = text.substringAfter('.', "")
        val chunked = digits.reversed().chunked(GROUP_SIZE).joinToString(".").reversed()
        return (if (negative) "-" else "") + chunked + if (fraction.isEmpty()) "" else ".$fraction"
    }

    fun parse(text: String): Float? {
        val cleaned = text.trim().lowercase().replace(" ", "")
        if (cleaned.isEmpty()) return null
        val multiplier = when (cleaned.last()) {
            'k' -> THOUSAND
            'm' -> MILLION
            else -> 1f
        }
        val body = if (multiplier == 1f) cleaned else cleaned.dropLast(1)
        val number = if (multiplier == 1f) ungroup(body) else body
        return number.toFloatOrNull()?.times(multiplier)
    }

    private fun ungroup(body: String): String =
        if (GROUPED.matches(body.removePrefix("-"))) body.replace(".", "") else body

    private fun plain(value: Float, decimals: Int): String =
        if (decimals <= 0) value.roundToLong().toString() else String.format(Locale.US, "%.${decimals}f", value)

    private fun trim(scaled: Float): String {
        val text = String.format(Locale.US, "%.2f", scaled)
        return text.trimEnd('0').trimEnd('.').let { if ('.' in it || abs(scaled) >= 10f) it else "$it.0" }
    }
}
