package com.amniscient.price.data
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amniscient.price.domain.Category
import com.amniscient.price.domain.ChannelObservation
import com.amniscient.price.domain.Markups
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class RestaurantTest {
    @Test fun markupsCompareEachChannelWithTheLatestInStorePrice() {
        val obs = listOf(
            ChannelObservation(1, 1, "IN_STORE", 1000, 1), ChannelObservation(1, 1, "IN_STORE", 1095, 2),
            ChannelObservation(1, 1, "DOORDASH", 1370, 3), ChannelObservation(1, 1, "UBER_EATS", 1290, 3),
            ChannelObservation(2, 1, "DOORDASH", 500, 3),
        )
        val m = Markups.latest(obs).associateBy { it.channel }
        assertEquals(2, m.size)
        assertEquals(25.1, m.getValue("DOORDASH").percent, 0.1)
        assertEquals("DOORDASH", Markups.byChannel(m.values.toList()).first().channel)
    }
    @Test fun menuPricesMarkRestaurantsAndDeliveryStaysOutOfComparisons() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = PriceRepository(db)
        val main = repo.addStore("Chipotle", "Main St", null, null)
        val polaris = repo.addStore("Chipotle", "Polaris", null, null)
        repo.recordPrice(PriceInput("Burrito Bowl", main, 1095, source = PriceSource.MENU))
        repo.recordPrice(PriceInput("Burrito Bowl", polaris, 1145, source = PriceSource.MENU))
        repo.recordPrice(PriceInput("Burrito Bowl", main, 1370, source = PriceSource.MENU, channel = PriceChannel.DOORDASH, observedAt = System.currentTimeMillis() + 1000))
        assertTrue(repo.allStores.first().all { it.kind == StoreKind.RESTAURANT })
        val bowl = repo.productSummaries.first().single()
        assertEquals(Category.MEALS, bowl.product.category)
        assertEquals(1095L, bowl.cheapestCents)
        assertEquals(1145L, bowl.highestCents)
        val detail = repo.productDetail(bowl.product.id).first()!!
        assertEquals(listOf(1095L, 1145L), detail.byStore.map { it.latest.priceCents })
        assertTrue(detail.changes.isEmpty())
        assertEquals(1370L, detail.markups.single().priceCents)
        assertEquals("DOORDASH", repo.insights.first().channels.single().channel)
        val csv = repo.exportCsv()
        assertTrue(csv.contains("DOORDASH"))
        repo.clearAll()
        assertEquals(3, repo.importCsv(csv))
        assertEquals(1, repo.insights.first().channels.size)
        db.close()
    }
}
