package com.amniscient.price.data
import com.amniscient.price.BuildConfig
import com.amniscient.price.domain.Geo
import com.amniscient.price.domain.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
data class CommunityStore(val externalId: String, val name: String, val city: String, val position: LatLng)
data class CommunityProduct(val barcode: String, val name: String, val brand: String?, val quantity: String?)
data class CommunityPrice(val store: Int, val product: Int, val cents: Long, val observedAt: Long, val onSale: Boolean)
data class CommunityPack(val stores: List<CommunityStore>, val products: List<CommunityProduct>, val prices: List<CommunityPrice>)
data class CommunityResult(val stores: Int, val prices: Int, val generated: String)
object CommunityPrices {
    const val SOURCE = "Open Prices (prices.openfoodfacts.org)"
    const val LICENSE_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
    suspend fun fetch(center: LatLng, radiusKm: Int, currency: String? = localCurrency(), base: String = BuildConfig.PRICE_PACKS_URL): Pair<CommunityPack, String> = withContext(Dispatchers.IO) {
        val index = JSONObject(String(get("${base}index.json")))
        val deg = index.optInt("tileDegrees", 2)
        val available = index.getJSONObject("tiles")
        val keys = tilesAround(center, radiusKm, deg).filter(available::has)
        val packs = coroutineScope { keys.map { k -> async { parse(String(gunzip(get("${base}tile_$k.json.gz")))) } }.awaitAll() }
        merge(packs, center, radiusKm, currency) to index.optString("generated")
    }
    fun tilesAround(center: LatLng, radiusKm: Int, deg: Int): List<String> {
        val dLat = radiusKm / 111.0
        val dLng = radiusKm / (111.0 * cos(Math.toRadians(center.lat)).coerceAtLeast(0.05))
        val lats = generateSequence(snap(center.lat - dLat, deg)) { it + deg }.takeWhile { it <= center.lat + dLat }.toList()
        val lngs = generateSequence(snap(center.lng - dLng, deg)) { it + deg }.takeWhile { it <= center.lng + dLng }.toList()
        return lats.flatMap { a -> lngs.map { b -> "${a}_${((b + 180).mod(360)) - 180}" } }.distinct()
    }
    fun parse(json: String): Pair<CommunityPack, List<String>> {
        val o = JSONObject(json)
        val s = o.getJSONArray("stores").rows { CommunityStore(getString(0), getString(1), optString(2), LatLng(getDouble(3), getDouble(4))) }
        val p = o.getJSONArray("products").rows { CommunityProduct(getString(0), optString(1), optString(2).ifBlank { null }, optString(3).ifBlank { null }) }
        val raw = o.getJSONArray("prices")
        val currencies = ArrayList<String>(raw.length())
        val prices = raw.rows { CommunityPrice(getInt(0), getInt(1), getLong(2), LocalDate.parse(getString(3)).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), getInt(4) == 1).also { currencies += optString(5) } }
        return CommunityPack(s, p, prices) to currencies
    }
    fun merge(packs: List<Pair<CommunityPack, List<String>>>, center: LatLng, radiusKm: Int, currency: String?): CommunityPack {
        val stores = LinkedHashMap<String, Int>(); val storeList = mutableListOf<CommunityStore>()
        val products = LinkedHashMap<String, Int>(); val productList = mutableListOf<CommunityProduct>()
        val prices = mutableListOf<CommunityPrice>()
        packs.forEach { (pack, currencies) ->
            val near = pack.stores.map { Geo.distanceMeters(center, it.position) <= radiusKm * 1000.0 }
            pack.prices.forEachIndexed { i, pr ->
                if (!near[pr.store] || (currency != null && currencies[i].isNotEmpty() && currencies[i] != currency)) return@forEachIndexed
                val st = pack.stores[pr.store]; val pd = pack.products[pr.product]
                val si = stores.getOrPut(st.externalId) { storeList += st; storeList.size - 1 }
                val pi = products.getOrPut(pd.barcode) { productList += pd; productList.size - 1 }
                prices += pr.copy(store = si, product = pi)
            }
        }
        return CommunityPack(storeList, productList, prices)
    }
    fun localCurrency(): String? = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
    private fun snap(v: Double, deg: Int) = (floor(v / deg) * deg).toInt()
    private inline fun <T> JSONArray.rows(f: JSONArray.() -> T): List<T> = List(length()) { getJSONArray(it).f() }
    private fun gunzip(b: ByteArray) = GZIPInputStream(b.inputStream()).use { it.readBytes() }
    private fun get(url: String): ByteArray {
        var u = URL(url)
        repeat(5) {
            val c = (u.openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = false; setRequestProperty("User-Agent", "Amni-Price/${BuildConfig.VERSION_NAME}") }
            try {
                when (c.responseCode) {
                    in 200..299 -> return c.inputStream.use { it.readBytes() }
                    in 300..399 -> u = URL(u, c.getHeaderField("Location"))
                    else -> error("HTTP ${c.responseCode} for ${u.path.substringAfterLast('/')}")
                }
            } finally { c.disconnect() }
        }
        error("Too many redirects")
    }
}
