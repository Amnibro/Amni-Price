package com.amniscient.price.data
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amniscient.price.domain.LatLng
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class CommunityPricesTest {
    private val columbus = LatLng(39.9612, -83.0007)
    private val tile = """{"v":1,"tile":"38_-84","stores":[["osm:node:1","Kroger","Columbus",39.97,-83.0],["osm:node:2","Far Mart","Cincinnati",39.10,-84.51]],
        "products":[["0001","Whole Milk","Brand","1 gal"],["0002","","",""]],
        "prices":[[0,0,349,"2026-09-01",0,"USD"],[0,1,199,"2026-09-02",1,"USD"],[1,0,299,"2026-09-01",0,"USD"],[0,0,300,"2026-09-03",0,"EUR"]]}"""
    @Test fun tilesCoverTheRadius() {
        val tiles = CommunityPrices.tilesAround(columbus, 50, 2)
        assertTrue("38_-84" in tiles)
        assertTrue(tiles.all { Regex("-?\\d+_-?\\d+").matches(it) })
        assertEquals(listOf("-2_178", "-2_-180"), CommunityPrices.tilesAround(LatLng(-1.0, 179.95), 10, 2))
    }
    @Test fun mergeKeepsNearbyStoresInLocalCurrency() {
        val pack = CommunityPrices.merge(listOf(CommunityPrices.parse(tile)), columbus, 25, "USD")
        assertEquals(listOf("osm:node:1"), pack.stores.map { it.externalId })
        assertEquals(listOf(349L, 199L), pack.prices.map { it.cents })
        assertTrue(pack.prices[1].onSale)
    }
    @Test fun importKeepsCommunityOutOfRecentAndReplacesOnRefresh() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = PriceRepository(db)
        val mine = repo.addStore("Aldi", "Oak Ave", null, null)
        repo.recordPrice(PriceInput("Whole Milk", mine, 329, barcode = "0001", source = PriceSource.SHELF))
        repo.recordPrice(PriceInput("Item 0002", mine, 150, source = PriceSource.RECEIPT))
        val pack = CommunityPrices.merge(listOf(CommunityPrices.parse(tile)), columbus, 25, "USD")
        assertEquals(2, repo.importCommunity(pack))
        assertEquals(2, repo.importCommunity(pack))
        assertEquals(2, repo.communityPriceCount.first())
        assertEquals(listOf(150L, 329L), repo.recentPrices.first().map { it.price.priceCents })
        assertEquals(2, repo.allStores.first().size)
        val milk = repo.productSummaries.first().first { it.product.barcode == "0001" }
        assertEquals(2, milk.storeCount)
        assertEquals("Item 0002", repo.productSummaries.first().first { it.product.barcode == "0002" }.product.name)
        assertEquals(1, repo.productSummaries.first().first { it.product.barcode == null }.storeCount)
        val insights = repo.insights.first()
        assertEquals(2, insights.productCount)
        assertEquals(20L, insights.potentialSavingsCents)
        assertEquals("Aldi", insights.topStore?.store?.name)
        repo.addToList("milk")
        assertEquals(repo.productSummaries.first().first { it.product.barcode == "0001" }.product.id, repo.shoppingPlan.first().entries.single().item.productId)
        repo.addToList("Item 0002")
        assertEquals(null, repo.shoppingPlan.first().entries.last().item.productId?.takeIf { id -> repo.productSummaries.first().first { it.product.id == id }.product.barcode == "0002" })
        repo.removeCommunity()
        assertEquals(0, repo.communityPriceCount.first())
        assertEquals(listOf("Aldi"), repo.allStores.first().map { it.name })
        assertEquals(2, repo.productSummaries.first().size)
        db.close()
    }
}
