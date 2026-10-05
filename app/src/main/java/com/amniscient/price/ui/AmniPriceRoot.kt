package com.amniscient.price.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amniscient.price.AmniPriceApp
import com.amniscient.price.ui.compare.CompareScreen
import com.amniscient.price.ui.compare.ProductDetailScreen
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.home.HomeScreen
import com.amniscient.price.ui.list.ShoppingListScreen
import com.amniscient.price.ui.map.LocationPickerScreen
import com.amniscient.price.ui.map.MapScreen
import com.amniscient.price.ui.onboarding.OnboardingScreen
import com.amniscient.price.ui.review.ReceiptReviewScreen
import com.amniscient.price.ui.review.ShelfReviewScreen
import com.amniscient.price.ui.scan.ScanScreen
import com.amniscient.price.ui.settings.SettingsScreen
import com.amniscient.price.ui.stores.StoreDetailScreen
import com.amniscient.price.ui.stores.StoresScreen
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCAN = "scan"
    const val COMPARE = "compare"
    const val LIST = "list"
    const val MAP = "map"
    const val STORE_LOCATION = "store/{id}/location"
    const val SETTINGS = "settings"
    const val STORES = "stores"
    const val STORE = "store/{id}"
    const val PRODUCT = "product/{id}"
    const val REVIEW_SHELF = "review/shelf"
    const val REVIEW_RECEIPT = "review/receipt"
    fun product(id: Long) = "product/$id"
    fun store(id: Long) = "store/$id"
    fun storeLocation(id: Long) = "store/$id/location"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "Home", Icons.Default.SpaceDashboard),
    TopLevel(Routes.SCAN, "Scan", Icons.Default.DocumentScanner),
    TopLevel(Routes.COMPARE, "Compare", Icons.Default.Insights),
    TopLevel(Routes.MAP, "Map", Icons.Default.Map),
    TopLevel(Routes.LIST, "List", Icons.Default.Checklist),
)

fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AmniPriceRoot(startOnboarding: Boolean, nav: NavHostController = rememberNavController()) {
    val container = (LocalContext.current.applicationContext as AmniPriceApp).container
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = topLevel.any { it.route == route }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = if (showBar) WindowInsets(0) else WindowInsets.navigationBars,
            bottomBar = {
                if (showBar) {
                    Column {
                        HorizontalDivider(color = Amni.palette.hairline)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            topLevel.forEach { item ->
                                NavigationBarItem(
                                    selected = route == item.route,
                                    onClick = { nav.navigateTopLevel(item.route) },
                                    icon = { Icon(item.icon, null) },
                                    label = { Text(item.label.uppercase(), style = AmniText.eyebrow) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                nav,
                startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onDone = {
                        container.settings.setOnboarded()
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onScanShelf = { nav.navigateTopLevel(Routes.SCAN) },
                        onScanReceipt = {
                            container.scanSession.requestReceiptMode = true
                            nav.navigateTopLevel(Routes.SCAN)
                        },
                        onOpenList = { nav.navigateTopLevel(Routes.LIST) },
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onOpenStore = { nav.navigate(Routes.store(it)) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenCompare = { nav.navigateTopLevel(Routes.COMPARE) },
                    )
                }
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
                        onOpenStore = { nav.navigate(Routes.store(it)) },
                        onAddManually = { nav.navigate(Routes.REVIEW_SHELF) },
                        onManageStores = { nav.navigate(Routes.STORES) },
                    )
                }
                composable(Routes.LIST) {
                    ShoppingListScreen(
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onOpenStore = { nav.navigate(Routes.store(it)) },
                    )
                }
                composable(Routes.MAP) {
                    MapScreen(onOpenStore = { nav.navigate(Routes.store(it)) }, onManageStores = { nav.navigate(Routes.STORES) })
                }
                composable(Routes.STORE_LOCATION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    LocationPickerScreen(storeId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() }, onManageStores = { nav.navigate(Routes.STORES) })
                }
                composable(Routes.STORES) {
                    StoresScreen(onBack = { nav.popBackStack() }, onOpenStore = { nav.navigate(Routes.store(it)) })
                }
                composable(Routes.STORE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    StoreDetailScreen(
                        storeId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                        onOpenProduct = { nav.navigate(Routes.product(it)) },
                        onSetLocation = { nav.navigate(Routes.storeLocation(it)) },
                    )
                }
                composable(Routes.REVIEW_SHELF) { ShelfReviewScreen(onDone = { nav.popBackStack() }) }
                composable(Routes.REVIEW_RECEIPT) { ReceiptReviewScreen(onDone = { nav.popBackStack() }) }
                composable(Routes.PRODUCT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    ProductDetailScreen(
                        productId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                        onAddPrice = { nav.navigate(Routes.REVIEW_SHELF) },
                        onOpenStore = { nav.navigate(Routes.store(it)) },
                        onViewOnMap = { nav.navigateTopLevel(Routes.MAP) },
                    )
                }
            }
        }
    }
}
