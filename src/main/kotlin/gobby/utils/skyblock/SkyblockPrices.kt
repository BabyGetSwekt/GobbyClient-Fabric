package gobby.utils.skyblock

import gobby.utils.ConfigUtils
import gobby.utils.session.FetchState
import gobby.utils.session.SignedResource
import gobby.utils.timer.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BazaarProduct(val buy: Double = 0.0, val sell: Double = 0.0)

class CatalogItem(val name: String = "", val tier: String = "", val npc: Double = 0.0)

private class BazaarSnapshot(val products: Map<String, BazaarProduct> = emptyMap())

private class CatalogSnapshot(val items: Map<String, CatalogItem> = emptyMap())

private class AuctionSnapshot(val auctions: Map<String, Double> = emptyMap())

object SkyblockPrices {

    private const val PRICES_FOLDER = "prices"
    private const val STARRED_PREFIX = "STARRED_"
    private const val REFRESH_INTERVAL_MS = 300_000L

    private val bazaarResource = SignedResource("/v1/prices/bazaar", "bazaar", PRICES_FOLDER)
    private val catalogResource = SignedResource("/v1/prices/items", "items", PRICES_FOLDER)
    private val auctionResource = SignedResource("/v1/prices/auctions", "auctions", PRICES_FOLDER)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val refreshClock = Clock(REFRESH_INTERVAL_MS).apply { lastTime = 0L }

    @Volatile
    private var products: Map<String, BazaarProduct> = emptyMap()

    @Volatile
    private var itemsById: Map<String, CatalogItem> = emptyMap()

    @Volatile
    private var idsByName: Map<String, String> = emptyMap()

    @Volatile
    private var lowestBins: Map<String, Double> = emptyMap()

    @Volatile
    private var refreshing = false

    val isLoaded: Boolean get() = products.isNotEmpty() && idsByName.isNotEmpty()

    init {
        applyBazaar(bazaarResource.cachedBody())
        applyCatalog(catalogResource.cachedBody())
        applyAuctions(auctionResource.cachedBody())
    }

    fun bazaarProduct(itemId: String): BazaarProduct? = products[itemId]

    fun catalogItem(itemId: String): CatalogItem? = itemsById[itemId]

    fun lowestBin(itemId: String): Double? = lowestBins[itemId]

    fun idForName(displayName: String): String? = idsByName[displayName]

    fun refreshIfStale(onDone: (Boolean) -> Unit = {}) {
        if (refreshClock.hasTimePassed()) refresh(onDone)
    }

    fun refresh(onDone: (Boolean) -> Unit = {}) {
        if (refreshing) return onDone(false)
        refreshing = true
        refreshClock.update()
        scope.launch {
            val bazaar = runCatching { handle(bazaarResource.fetch(), ::applyBazaar) }.getOrDefault(false)
            val catalog = runCatching { handle(catalogResource.fetch(), ::applyCatalog) }.getOrDefault(false)
            val auctions = runCatching { handle(auctionResource.fetch(), ::applyAuctions) }.getOrDefault(false)
            refreshing = false
            onDone(bazaar && catalog && auctions)
        }
    }

    private fun handle(state: FetchState, apply: (String?) -> Boolean): Boolean = when (state) {
        is FetchState.Updated -> apply(state.body)
        FetchState.Unchanged -> true
        FetchState.Failed -> false
    }

    private fun applyBazaar(body: String?): Boolean {
        val snapshot = parse(body, BazaarSnapshot::class.java) ?: return false
        if (snapshot.products.isEmpty()) return false
        products = snapshot.products
        return true
    }

    private fun applyCatalog(body: String?): Boolean {
        val snapshot = parse(body, CatalogSnapshot::class.java) ?: return false
        if (snapshot.items.isEmpty()) return false
        itemsById = snapshot.items
        idsByName = snapshot.items.entries
            .filterNot { it.key.startsWith(STARRED_PREFIX) }
            .associate { it.value.name to it.key }
        return true
    }

    private fun applyAuctions(body: String?): Boolean {
        val snapshot = parse(body, AuctionSnapshot::class.java) ?: return false
        if (snapshot.auctions.isEmpty()) return false
        lowestBins = snapshot.auctions
        return true
    }

    private fun <T : Any> parse(body: String?, type: Class<T>): T? =
        body?.let { runCatching { ConfigUtils.gson.fromJson(it, type) }.getOrNull() }
}
