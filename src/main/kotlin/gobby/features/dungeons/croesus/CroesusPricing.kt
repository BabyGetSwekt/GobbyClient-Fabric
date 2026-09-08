package gobby.features.dungeons.croesus

import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.skyblock.SkyblockPrices
import gobby.utils.RomanNumerals

class CroesusReward(val itemId: String, val amount: Int, val unitValue: Double, val displayName: String) {
    val total: Double get() = unitValue * amount
}

class PricedChest(val cost: Double, val rewards: List<CroesusReward>) {
    val value: Double get() = rewards.sumOf { it.total }
    val profit: Double get() = value - cost
}

sealed class ChestEvaluation {
    class Priced(val chest: PricedChest) : ChestEvaluation()
    class Unknown(val reason: String) : ChestEvaluation()
}

object CroesusPricing {

    private const val ESSENCE_PREFIX = "ESSENCE_"
    private const val PET_PREFIX = "PET-"
    private const val CHEST_KEY_ID = "DUNGEON_CHEST_KEY"

    private val PET_TIERS = mapOf(
        'f' to "COMMON", 'a' to "UNCOMMON", '9' to "RARE",
        '5' to "EPIC", '6' to "LEGENDARY", 'd' to "MYTHIC"
    )

    private val COST = Regex("""^([\d,]+) Coins$""")
    private val ESSENCE = Regex("""^(\w+) Essence x(\d+)$""")
    private val PET = Regex("""^\[Lvl 1] (\w+)$""")
    private val BOOK = Regex("""^Enchanted Book \(([\w ]+) (\w+)\)$""")
    private val PET_COLOR = Regex("""\[Lvl 1] §(.)""")

    private val NAME_OVERRIDES = mapOf(
        "Shiny Wither Helmet" to "WITHER_HELMET",
        "Shiny Wither Chestplate" to "WITHER_CHESTPLATE",
        "Shiny Wither Leggings" to "WITHER_LEGGINGS",
        "Shiny Wither Boots" to "WITHER_BOOTS",
        "Shiny Necron's Handle" to "NECRON_HANDLE",
        "Wither Shard" to "SHARD_WITHER",
        "Thorn Shard" to "SHARD_THORN",
        "Scarf Shard" to "SHARD_SCARF",
        "Apex Dragon Shard" to "SHARD_APEX_DRAGON",
        "Power Dragon Shard" to "SHARD_POWER_DRAGON",
        "Necron Dye" to "DYE_NECRON",
        "Livid Dye" to "DYE_LIVID"
    )

    fun evaluate(costLines: List<String>, rewardLines: List<String>, requiresKey: Boolean = false): ChestEvaluation {
        val coins = coinsOf(costLines)
        val keyPrice = if (!requiresKey) 0.0
        else valueOf(CHEST_KEY_ID) ?: return ChestEvaluation.Unknown("No price for a Dungeon Chest Key")
        val rewards = rewardLines.map { line ->
            val reward = rewardOf(line) ?: return ChestEvaluation.Unknown("No price for \"${line.noControlCodes}\"")
            reward
        }
        return ChestEvaluation.Priced(PricedChest(coins + keyPrice, rewards))
    }

    fun rewardOf(line: String): CroesusReward? {
        val plain = line.noControlCodes.trim()
        val (itemId, amount) = identify(line, plain) ?: return null
        if (isIgnored(itemId)) return CroesusReward(itemId, amount, 0.0, plain)
        val value = valueOf(itemId) ?: return null
        return CroesusReward(itemId, amount, value, plain)
    }

    private fun isIgnored(itemId: String): Boolean =
        CroesusFilters.isWorthless(itemId) || (AutoCroesus.ignoresEssence && itemId.startsWith(ESSENCE_PREFIX))

    fun isFreeCost(costLines: List<String>): Boolean = coinsOf(costLines) == 0L

    fun valueOf(itemId: String): Double? =
        SkyblockPrices.bazaarProduct(itemId)?.sell ?: SkyblockPrices.lowestBin(itemId)

    private fun coinsOf(costLines: List<String>): Long = costLines.firstNotNullOfOrNull { line ->
        COST.matchEntire(line.noControlCodes.trim())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
    } ?: 0L

    private fun identify(line: String, plain: String): Pair<String, Int>? =
        asEssence(plain) ?: asBook(plain) ?: asPet(line, plain) ?: asItem(plain)

    private fun asEssence(plain: String): Pair<String, Int>? = ESSENCE.matchEntire(plain)?.let {
        "$ESSENCE_PREFIX${it.groupValues[1].uppercase()}" to it.groupValues[2].toInt()
    }

    private fun asBook(plain: String): Pair<String, Int>? {
        val match = BOOK.matchEntire(plain) ?: return null
        val level = match.groupValues[2].toIntOrNull() ?: RomanNumerals.parse(match.groupValues[2]) ?: return null
        val name = match.groupValues[1].uppercase().replace(' ', '_')
        val normal = "ENCHANTMENT_${name}_$level"
        val ultimate = "ENCHANTMENT_ULTIMATE_${name}_$level"
        return (if (SkyblockPrices.bazaarProduct(normal) != null) normal else ultimate) to 1
    }

    private fun asPet(line: String, plain: String): Pair<String, Int>? {
        val name = PET.matchEntire(plain)?.groupValues?.get(1) ?: return null
        val tier = PET_TIERS[petColorOf(line)] ?: return null
        return "$PET_PREFIX${name.uppercase()}-$tier" to 1
    }

    private fun petColorOf(line: String): Char? =
        PET_COLOR.find(line)?.groupValues?.get(1)?.firstOrNull()

    private fun asItem(plain: String): Pair<String, Int>? =
        (NAME_OVERRIDES[plain] ?: SkyblockPrices.idForName(plain))?.let { it to 1 }
}
