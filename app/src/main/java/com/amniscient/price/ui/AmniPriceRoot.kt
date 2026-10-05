package com.amniscient.price.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amniscient.price.ui.compare.CompareScreen
import com.amniscient.price.ui.compare.ProductDetailScreen
import com.amniscient.price.ui.review.ReceiptReviewScreen
import com.amniscient.price.ui.review.ShelfReviewScreen
import com.amniscient.price.ui.scan.ScanScreen
import com.amniscient.price.ui.stores.StoresScreen

object Routes {
    const val SCAN = "scan"
    const val COMPARE = "compare"
    const val STORES = "stores"
    const val REVIEW_SHELF = "review/shelf"
    const val REVIEW_RECEIPT = "review/receipt"
    const val PRODUCT = "product/{id}"
    fun product(id: Long) = "product/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.SCAN, "Scan", Icons.Default.DocumentScanner),
    TopLevel(Routes.COMPARE, "Compare", Icons.Default.Leaderboard),
    TopLevel(Routes.STORES, "Stores", Icons.Default.Storefront),
)

@Composable
fun AmniPriceRoot(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (topLevel.any { it.route == route }) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.SCAN, modifier = Modifier.padding(padding)) {
            composable(Routes.SCAN) {
                ScanScreen(
                    onShelfCaptured = { nav.navigate(Routes.REVIEW_SHELF) },
                    onReceiptCaptured = { nav.navigate(Routes.REVIEW_RECEIPT) },
                    onOpenProduct = { nav.navigate(Routes.product(it)) },
                )
            }
            composable(Routes.COMPARE) {
                CompareScreen(
                    onOpenProduct = { nav.navigate(Routes.product(it)) },
                    onAddManually = { nav.navigate(Routes.REVIEW_SHELF) },
                )
            }
            composable(Routes.STORES) { StoresScreen() }
            composable(Routes.REVIEW_SHELF) { ShelfReviewScreen(onDone = { nav.popBackStack() }) }
            composable(Routes.REVIEW_RECEIPT) { ReceiptReviewScreen(onDone = { nav.popBackStack() }) }
            composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ProductDetailScreen(
                    productId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onAddPrice = { nav.navigate(Routes.REVIEW_SHELF) },
                )
            }
        }
    }
}
