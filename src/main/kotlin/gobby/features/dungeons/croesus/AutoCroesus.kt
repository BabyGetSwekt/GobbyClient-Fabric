package gobby.features.dungeons.croesus

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.DungeonRunEndEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.ScreenReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.FileSetting
import gobby.gui.click.InfoSetting
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.gui.click.MultipleChoiceSetting
import gobby.gui.click.NumberSetting
import gobby.gui.click.SelectorSetting
import gobby.gui.click.SettingAlign
import gobby.gui.click.SettingSection
import gobby.gui.click.inGroup
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.Island
import gobby.utils.LocationUtils
import gobby.utils.Utils.executeLater
import gobby.utils.skyblock.SkyblockPrices
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

private val BUYING_SECTION = SettingSection("Buying Chest", SettingAlign.LEFT)
private val PROFIT_SECTION = SettingSection("Profit GUI", SettingAlign.LEFT)
private val FILTER_SECTION = SettingSection("Filter", SettingAlign.RIGHT)
private val SECOND_CHEST_SECTION = SettingSection("Buy Second Chest", SettingAlign.RIGHT)
private val REROLL_SECTION = SettingSection("Reroll Chests", SettingAlign.RIGHT)

object AutoCroesus : Module(
    "Auto Croesus", "Set a keybind so it automatically opens chests",
    Category.DUNGEONS
) {

    private const val BUY_INFO =
        "Minimum profit a chest MUST have before it will be bought. The most profitable chest will always be " +
            "bought. So if a Gold chest gives +800k and a Bedrock chest only +100k, then the Gold chest will be " +
            "bought. Do not set these values too high, because it will result in it only buying the wooden chest. " +
            "Setting this to 1 always buys the chest when it is profit. Recommended to keep at default settings."

    private const val KISMET_INFO =
        "It is HIGHLY recommended to enable \"Use Dungeon Chest Key\" as well, so that it actually opens those chests " +
            "too if it's profit in runs where you've already opened a chest, or else you are just wasting a kismet."

    private val FLOORS = listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7")

    private val openKey by KeybindSetting("Open Chests", desc = "Press while standing at Croesus to start opening chests")
        .inGroup(BUYING_SECTION)

    private val buyInfo by InfoSetting(BUY_INFO).inGroup(BUYING_SECTION)

    private val minimumProfits = ChestTier.entries.associateWith { tier ->
        NumberSetting(
            "Minimum Buy ${tier.displayName}", defaultMinimumFor(tier), 1, 50_000_000, 1,
            desc = "Only buy the ${tier.displayName} chest when it profits at least this much"
        ).inGroup(BUYING_SECTION).also { settings.add(it) }
    }

    private val ignoreEssence by BooleanSetting(
        "Ignore Essence Price", false, desc = "Leaves essence out of the profit calculation"
    ).inGroup(BUYING_SECTION)

    private val worthlessItems by FileSetting(
        "Worthless Items", "worthless.txt", CROESUS_FOLDER, CroesusFilters.DEFAULT_WORTHLESS,
        desc = "Click to edit. These items are valued at 0 coins, for prices that cannot be trusted"
    ).inGroup(FILTER_SECTION)

    private val alwaysBuyItems by FileSetting(
        "Always Buy Items", "always_buy.txt", CROESUS_FOLDER, CroesusFilters.DEFAULT_ALWAYS_BUY,
        desc = "Click to edit. A chest holding one of these is always bought and never rerolled"
    ).inGroup(FILTER_SECTION)

    private val useChestKeys by BooleanSetting(
        "Use Dungeon Chest Key", false, desc = "Spend a Dungeon Chest Key to open a second chest from the same run"
    ).inGroup(SECOND_CHEST_SECTION)

    private val chestKeyMinProfit by NumberSetting(
        "Minimum Profit", 500_000, 0, 2_000_000, 1,
        desc = "Only spend a key when the chest profits at least this much"
    ).withDependency { useChestKeys }.inGroup(SECOND_CHEST_SECTION)

    private val profitOverlay by BooleanSetting(
        "Profit GUI", true, desc = "Shows the profit of every chest next to the reward menu"
    ).inGroup(PROFIT_SECTION)

    private val buyNowButton by BooleanSetting(
        "Buy Now Button", true, desc = "Adds a Buy button to chests that turn a profit"
    ).withDependency { profitOverlay }.inGroup(PROFIT_SECTION)

    private val autoKismet by BooleanSetting("Auto Kismet", false, desc = "Reroll chests with a kismet feather")
        .inGroup(REROLL_SECTION)

    private val kismetInfo by InfoSetting(KISMET_INFO)
        .withDependency { autoKismet }.inGroup(REROLL_SECTION)

    private val rerollFloors by MultipleChoiceSetting(
        "Reroll Floors", FLOORS, setOf("F7", "M7"), desc = "Only reroll on these floors"
    ).withDependency { autoKismet }.inGroup(REROLL_SECTION)

    private val rerollTier by SelectorSetting(
        "Reroll Chest", ChestTier.BEDROCK.ordinal, ChestTier.entries.map { it.displayName },
        desc = "Which chest to reroll"
    ).withDependency { autoKismet }.inGroup(REROLL_SECTION)

    private val rerollBelow by NumberSetting(
        "Reroll Below", 5_000_000, 0, 50_000_000, 250_000, desc = "Reroll when the profit is under this amount"
    ).withDependency { autoKismet }.inGroup(REROLL_SECTION)

    val ignoresEssence: Boolean get() = ignoreEssence

    val usesChestKeys: Boolean get() = useChestKeys

    val worthless: Set<String> get() = worthlessItems

    val alwaysBuy: Set<String> get() = alwaysBuyItems


    val showProfitOverlay: Boolean get() = enabled && profitOverlay

    val showBuyButton: Boolean get() = showProfitOverlay && buyNowButton

    var isAutoOpening = false
        private set

    private var fetchingPrices = false

    fun minimumProfitFor(tier: ChestTier): Float = minimumProfits[tier]?.floatValue ?: 1f

    private fun defaultMinimumFor(tier: ChestTier): Int =
        if (tier == ChestTier.GOLD) 50_000 else 1

    val chestKeyMinimum: Float get() = chestKeyMinProfit.toFloat()

    fun rerollsFloor(floor: Int, masterMode: Boolean): Boolean =
        autoKismet && floorLabel(floor, masterMode) in rerollFloors

    fun shouldReroll(run: TrackedRun, tier: ChestTier, priced: PricedChest?): Boolean {
        if (run.hasRerolled || priced == null) return false
        if (CroesusFilters.holdsAlwaysBuy(priced)) return false
        if (!rerollsFloor(run.floor, run.masterMode)) return false
        if (tier.ordinal != rerollTier) return false
        return priced.profit < rerollBelow
    }

    fun onMissingKismet() {
        stop()
        errorMessage(Component.literal("No kismets detected, buy some or disable auto reroll")
            .withStyle { it.withClickEvent(ClickEvent.RunCommand("/bz Kismet Feather")) }
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to run \"/bz Kismet Feather\""))) }
        )
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!isAutoOpening) return
        if (event.message != "You need a Dungeon Chest Key to open another chest!") return
        event.cancel()
        stop()
        errorMessage(
            Component.literal("No Dungeon Chest Keys detected, buy some or disable Use Dungeon Chest Key")
                .withStyle { it.withClickEvent(ClickEvent.RunCommand("/bz Dungeon Chest Key")) }
                .withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to run \"/bz Dungeon Chest Key\""))) }
        )
    }

    fun stop() {
        isAutoOpening = false
        CroesusFlow.abort()
        mc.executeLater { mc.player?.closeContainer() }
    }

    @SubscribeEvent
    fun onScreenReceived(event: ScreenReceivedEvent) {
        if (!enabled) return
        if (!CroesusMenu.matchesTitle(event.title) && !CroesusChestMenu.matchesTitle(event.title)) return
        SkyblockPrices.refreshIfStale()
    }

    @SubscribeEvent
    fun onRunEnd(event: DungeonRunEndEvent) {
        if (!enabled) return
        SkyblockPrices.refreshIfStale()
    }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || openKey == 0 || event.key != openKey) return
        if (isAutoOpening) return cancelRun()
        if (mc.gui.screen() != null) return
        if (!LocationUtils.isIn(Island.DUNGEON_HUB)) return
        if (!SkyblockPrices.isLoaded) return fetchPricesThenStart()
        beginRun()
    }

    private fun fetchPricesThenStart() {
        if (fetchingPrices) return
        fetchingPrices = true
        modMessage("§eFetching prices")
        SkyblockPrices.refresh {
            mc.execute {
                fetchingPrices = false
                if (SkyblockPrices.isLoaded) beginRun() else errorMessage("Could not fetch prices, API down? Please report! ty")
            }
        }
    }

    private fun beginRun() {
        isAutoOpening = true
        modMessage("§aAuto Croesus armed")
        CroesusFlow.start()
    }

    private fun cancelRun() {
        stop()
        modMessage("§cAuto Croesus stopped")
    }

    private fun floorLabel(floor: Int, masterMode: Boolean): String = "${if (masterMode) "M" else "F"}$floor"

}
