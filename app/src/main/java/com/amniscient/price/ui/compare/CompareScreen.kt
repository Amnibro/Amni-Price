package com.amniscient.price.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.ProductSummary
import com.amniscient.price.data.RankedStore
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.DealColor
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

@Composable
fun CompareScreen(onOpenProduct: (Long) -> Unit, onAddManually: () -> Unit) {
    val vm = appViewModel { CompareViewModel(it.repository) }
    val products by vm.products.collectAsStateWithLifecycle()
    val ranking by vm.ranking.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Products") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Cheapest stores") })
            }
            when (tab) {
                0 -> ProductList(products, onOpenProduct)
                else -> StoreRanking(ranking)
            }
        }
        if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = onAddManually,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add price") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun ProductList(products: List<ProductSummary>, onOpenProduct: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = normalizeName(query)
    val filtered = products
        .filter { q.isEmpty() || it.product.normalizedName.contains(q) || it.product.barcode?.contains(q) == true }
        .sortedWith(compareByDescending<ProductSummary> { it.storeCount > 1 }.thenByDescending { it.lastSeen ?: 0 })

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search products or barcodes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (products.isEmpty()) {
            EmptyHint("No prices yet.\nScan a shelf tag or receipt to get started.")
            return
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
private fun ProductRow(summary: ProductSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = buildList {
                    summary.product.sizeText?.let(::add)
                    add(if (summary.storeCount == 1) "1 store" else "${summary.storeCount} stores")
                    if (summary.storeCount > 1 && summary.highestCents != null) {
                        add("up to ${Money.format(summary.highestCents)}")
                    }
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    summary.cheapestCents?.let(Money::format) ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.storeCount > 1) DealColor else MaterialTheme.colorScheme.onSurface,
                )
                summary.cheapestStore?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StoreRanking(ranking: List<RankedStore>) {
    if (ranking.isEmpty()) {
        EmptyHint("Scan the same products at two or more stores\nto see which one is cheapest overall.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Based on the latest price of every product seen at more than one store. " +
                    "0% means the store always had the lowest price.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(ranking, key = { _, r -> r.store.id }) { i, r ->
            val premium = ((r.score.index - 1.0) * 100).roundToInt()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (i == 0) DealColor else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${i + 1}",
                                fontWeight = FontWeight.Bold,
                                color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(r.store.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Cheapest on ${r.score.cheapestCount} of ${r.score.comparedProducts} compared items",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        if (premium <= 0) "Best" else "+$premium%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (premium <= 0) DealColor else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
