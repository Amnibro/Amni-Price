package com.amniscient.price.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amniscient.price.AmniPriceApp
import com.amniscient.price.data.DemoSeeder
import com.amniscient.price.domain.ReceiptParser
import com.amniscient.price.scan.ShelfDraft
import com.amniscient.price.ui.compare.CompareScreen
import com.amniscient.price.ui.compare.ProductDetailScreen
import com.amniscient.price.ui.home.HomeScreen
import com.amniscient.price.ui.list.ShoppingListScreen
import com.amniscient.price.ui.onboarding.OnboardingScreen
import com.amniscient.price.ui.review.ReceiptReviewScreen
import com.amniscient.price.ui.review.ShelfReviewScreen
import com.amniscient.price.ui.settings.SettingsScreen
import com.amniscient.price.ui.stores.StoreDetailScreen
import com.amniscient.price.ui.theme.AmniPriceTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Renders the real screens with seeded demo data. Run `./gradlew recordRoborazziDebug`
 * to (re)generate images in app/screenshots/, `verifyRoborazziDebug` to diff against them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi", application = AmniPriceApp::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<AmniPriceApp>()
    private val container get() = app.container

    @Before fun seed() = runBlocking {
        DemoSeeder.seed(container.repository)
        container.settings.setOnboarded()
        val aldi = container.repository.allStores.first().first { it.name == "Kroger" }
        container.settings.setCurrentStore(aldi.id)
    }

    private fun shoot(name: String, dark: Boolean = true, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            AmniPriceTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        settle()
        before()
        settle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    /** Room delivers on background threads; give it a few rounds to land on the main looper. */
    private fun settle() {
        repeat(6) {
            Thread.sleep(150)
            ShadowLooper.idleMainLooper()
            compose.waitForIdle()
        }
    }

    private val noop: () -> Unit = {}
    private val noopId: (Long) -> Unit = {}

    @Test fun onboarding() = shoot("01_onboarding") { OnboardingScreen(onDone = noop) }

    @Test fun home() = shoot("02_home") {
        HomeScreen(noop, noop, noop, noopId, noopId, noop, noop)
    }

    @Test fun homeLight() = shoot("03_home_light", dark = false) {
        HomeScreen(noop, noop, noop, noopId, noopId, noop, noop)
    }

    @Test fun compareProducts() = shoot("04_compare_products") {
        CompareScreen(noopId, noopId, noop, noop)
    }

    @Test fun compareStores() = shoot("05_compare_stores", before = {
        compose.onNodeWithText("CHEAPEST STORES").performClick()
    }) { CompareScreen(noopId, noopId, noop, noop) }

    @Test fun productDetail() = shoot("06_product_detail") {
        ProductDetailScreen(productId = 1L, onBack = noop, onAddPrice = noop, onOpenStore = noopId)
    }

    @Test fun productDetailLight() = shoot("07_product_detail_light", dark = false) {
        ProductDetailScreen(productId = 1L, onBack = noop, onAddPrice = noop, onOpenStore = noopId)
    }

    @Test fun storeDetail() = shoot("08_store_detail") {
        StoreDetailScreen(storeId = 1L, onBack = noop, onOpenProduct = noopId)
    }

    @Test fun shoppingList() = shoot("09_shopping_list") {
        ShoppingListScreen(onOpenProduct = noopId, onOpenStore = noopId)
    }

    @Test fun shelfReview() = shoot("10_shelf_review") {
        container.scanSession.pendingShelf = ShelfDraft(
            productName = "Great Value Whole Milk", barcode = "078742351865", priceCents = 419, sizeText = "1 gal", fromScan = true,
        )
        ShelfReviewScreen(onDone = noop)
    }

    @Test fun receiptReview() = shoot("11_receipt_review") {
        container.scanSession.pendingReceipt = ReceiptParser.parse(
            listOf(
                "KROGER", "Main St", "10/03/26 14:22",
                "GV WHL MLK 078742351865 F  3.99", "LG EGGS 12CT  3.49", "BANANAS  0.59",
                "2 @ 1.79", "BARILLA SPAGHETTI  3.58", "COUPON  0.50-", "TOTAL  11.15",
            ),
        )
        ReceiptReviewScreen(onDone = noop)
    }

    @Test fun settings() = shoot("12_settings") { SettingsScreen(onBack = noop, onManageStores = noop) }
}
