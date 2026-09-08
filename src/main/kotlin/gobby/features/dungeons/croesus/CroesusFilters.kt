package gobby.features.dungeons.croesus

const val CROESUS_FOLDER = "croesus"

object CroesusFilters {

    private val USELESS_ULTIMATES = listOf("NO_PAIN_NO_GAIN", "COMBO", "BANK", "JERRY")
    private val ULTIMATE_LEVELS = 1..5
    private val USELESS_ENCHANTS = listOf("FEATHER_FALLING", "INFINITE_QUIVER")
    private val USELESS_ENCHANT_LEVELS = 6..10

    val DEFAULT_WORTHLESS: List<String> =
        (1..5).map { "DUNGEON_DISC_$it" } +
            listOf("MAXOR_THE_FISH", "STORM_THE_FISH", "GOLDOR_THE_FISH" ) +
            leveled(USELESS_ULTIMATES, ULTIMATE_LEVELS, "ENCHANTMENT_ULTIMATE_") +
            leveled(USELESS_ENCHANTS, USELESS_ENCHANT_LEVELS, "ENCHANTMENT_")

    val DEFAULT_ALWAYS_BUY: List<String> = listOf(
        "NECRON_HANDLE", "DARK_CLAYMORE", "SHADOW_FURY", "SHADOW_WARP_SCROLL",
        "IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "DYE_LIVID", "DYE_NECRON",
        "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
        "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
    )

    fun isWorthless(itemId: String): Boolean = itemId in AutoCroesus.worthless

    fun holdsAlwaysBuy(chest: PricedChest): Boolean =
        chest.rewards.any { it.itemId in AutoCroesus.alwaysBuy }

    private fun leveled(names: List<String>, levels: IntRange, prefix: String): List<String> =
        names.flatMap { name -> levels.map { "$prefix${name}_$it" } }
}
