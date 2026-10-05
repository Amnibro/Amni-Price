package com.amniscient.price.ui.compare

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.ProductSummary
import com.amniscient.price.data.RankedStore
import com.amniscient.price.domain.Category
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.EmptyState
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.Tag
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

class CompareViewModel(repository: PriceRepository) : ViewModel() {
    val products: StateFlow<List<ProductSummary>> =
        repository.productSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ranking: StateFlow<List<RankedStore>> =
        repository.storeRanking.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private enum class SortOrder(val label: String) { RECENT("Recently seen"), SAVINGS("Biggest price gap"), NAME("Name") }

@Composable
fun CompareScreen(
    onOpenProduct: (Long) -> Unit,
    onOpenStore: (Long) -> Unit,
    onAddManually: () -> Unit,
    onManageStores: () -> Unit,
) {
    val vm = appViewModel { CompareViewModel(it.repository) }
    val products by vm.products.collectAsStateWithLifecycle()
    val ranking by vm.ranking.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AmniTopBar(
                title = "Compare",
                eyebrow = "${products.size} products · ${ranking.size} stores ranked",
                actions = { IconButton(onClick = onManageStores) { Icon(Icons.Default.Storefront, "Manage stores") } },
            )
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[tab]),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                divider = { androidx.compose.material3.HorizontalDivider(color = Amni.palette.hairline) },
            ) {
                listOf("Products", "Cheapest stores").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label.uppercase(), style = AmniText.eyebrow) },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (tab) {
                0 -> ProductList(products, onOpenProduct)
                else -> StoreRanking(ranking, onOpenStore)
            }
        }
        if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = onAddManually,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add price") },
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun ProductList(products: List<ProductSummary>, onOpenProduct: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }

    if (products.isEmpty()) {
        EmptyState(
            Icons.Default.Inventory2,
            "No prices yet",
            "Scan a shelf tag or receipt, or add a price by hand. Products show up here with the cheapest store for each.",
        )
        return
    }

    val q = normalizeName(query)
    val presentCategories = products.map { it.product.category }.distinct().sortedBy { it.ordinal }
    val filtered = products
        .filter { q.isEmpty() || it.product.normalizedName.contains(q) || it.product.barcode?.contains(q) == true }
        .filter { category == null || it.product.category == category }
        .let { list ->
            when (sort) {
                SortOrder.RECENT -> list.sortedByDescending { it.lastSeen ?: 0 }
                SortOrder.SAVINGS -> list.sortedByDescending { it.spreadCents }
                SortOrder.NAME -> list.sortedBy { it.product.name.lowercase() }
            }
        }

    Column {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search products or barcodes") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.label, color = if (o == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                            onClick = { sort = o; sortMenu = false },
                        )
                    }
                }
            }
        }
        if (presentCategories.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { CategoryChip("All", category == null) { category = null } }
                items(presentCategories) { c -> CategoryChip(c.label, category == c) { category = if (category == c) null else c } }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.product.id }) { summary ->
                ProductRow(summary, onClick = { onOpenProduct(summary.product.id) })
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = Amni.palette.hairline, selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ProductRow(summary: ProductSummary, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(summary.product.category.label)
                    summary.product.sizeText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        if (summary.storeCount == 1) "1 store" else "${summary.storeCount} stores",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PriceText(summary.cheapestCents, color = if (summary.storeCount > 1) Amni.palette.deal else MaterialTheme.colorScheme.onSurface)
                summary.cheapestStore?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.spreadCents > 0) {
                    Text("save ${Money.format(summary.spreadCents)}", style = AmniText.priceSmall, color = Amni.palette.brass)
                }
            }
        }
    }
}

@Composable
private fun StoreRanking(ranking: List<RankedStore>, onOpenStore: (Long) -> Unit) {
    if (ranking.isEmpty()) {
        EmptyState(
            Icons.Default.Storefront,
            "Not enough to rank yet",
            "Log the same products at two or more stores and Amni-Price will rank which one is cheapest overall.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Based on the latest price of every product seen at more than one store. " +
                    "The percentage is how much more a store costs than the cheapest option, on average.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(ranking, key = { _, r -> r.store.id }) { i, r ->
            val premium = ((r.score.index - 1.0) * 100).roundToInt()
            Panel(onClick = { onOpenStore(r.store.id) }, highlighted = i == 0) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).border(1.dp, if (i == 0) Amni.palette.brass else Amni.palette.hairline, MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = AmniText.price, color = if (i == 0) Amni.palette.brass else MaterialTheme.colorScheme.onSurface)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(r.store.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Cheapest on ${r.score.cheapestCount} of ${r.score.comparedProducts} compared items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (premium <= 0) "BEST" else "+$premium%",
                            style = AmniText.price,
                            color = if (premium <= 0) Amni.palette.deal else Amni.palette.rise,
                        )
                        Eyebrow(if (premium <= 0) "lowest" else "vs cheapest")
                    }
                }
            }
        }
    }
}
