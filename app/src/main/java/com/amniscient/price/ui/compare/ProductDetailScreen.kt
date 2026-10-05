package com.amniscient.price.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceEntity
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.ProductDetail
import com.amniscient.price.data.ProductEntity
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.UnitParser
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.scan.ShelfDraft
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.relativeTime
import com.amniscient.price.ui.theme.DealColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(
    private val repository: PriceRepository,
    private val session: ScanSession,
    productId: Long,
) : ViewModel() {
    val detail: StateFlow<ProductDetail?> =
        repository.productDetail(productId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun prepareAddPrice() {
        val p = detail.value?.product ?: return
        session.pendingShelf = ShelfDraft(productName = p.name, barcode = p.barcode.orEmpty(), sizeText = p.sizeText.orEmpty())
    }

    fun deletePrice(price: PriceEntity) = viewModelScope.launch { repository.deletePrice(price) }
    fun rename(product: ProductEntity, name: String, size: String) = viewModelScope.launch {
        repository.updateProduct(product.copy(name = name.trim(), sizeText = size.trim().ifEmpty { null }))
    }
    fun deleteProduct(product: ProductEntity, onDone: () -> Unit) = viewModelScope.launch {
        repository.deleteProduct(product)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(productId: Long, onBack: () -> Unit, onAddPrice: () -> Unit) {
    val vm = appViewModel { ProductDetailViewModel(it.repository, it.scanSession, productId) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.product?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit product") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.prepareAddPrice(); onAddPrice() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add price") },
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        val quantity = UnitParser.parse(d.product.sizeText)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val meta = listOfNotNull(d.product.sizeText, d.product.barcode?.let { "Barcode $it" }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Latest price by store", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(d.byStore, key = { _, sp -> "s" + sp.latest.storeId }) { i, sp ->
                val cheapest = i == 0 && d.byStore.size > 1
                Card(
                    colors = if (cheapest) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sp.storeName, style = MaterialTheme.typography.titleMedium)
                            val sub = buildList {
                                add(relativeTime(sp.latest.observedAt))
                                if (sp.latest.onSale) add("sale")
                                quantity?.let { add(UnitParser.unitPriceLabel(sp.latest.priceCents, it)) }
                            }.joinToString(" · ")
                            Text(sub, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                Money.format(sp.latest.priceCents),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (cheapest) DealColor else MaterialTheme.colorScheme.onSurface,
                            )
                            val diff = sp.latest.priceCents - d.byStore.first().latest.priceCents
                            if (diff > 0) {
                                Text("+${Money.format(diff)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            } else if (cheapest) {
                                Text("Cheapest", style = MaterialTheme.typography.labelMedium, color = DealColor)
                            }
                        }
                    }
                }
            }
            item {
                Text("History", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            }
            items(d.history, key = { "h" + it.price.id }) { h ->
                ListItem(
                    headlineContent = { Text("${Money.format(h.price.priceCents)} at ${h.storeName}") },
                    supportingContent = {
                        Text(relativeTime(h.price.observedAt) + " · " + h.price.source.name.lowercase() + if (h.price.onSale) " · sale" else "")
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.deletePrice(h.price) }) { Icon(Icons.Default.Delete, "Delete entry") }
                    },
                )
                HorizontalDivider()
            }
        }

        if (editing) {
            EditProductDialog(
                product = d.product,
                onDismiss = { editing = false },
                onSave = { name, size -> vm.rename(d.product, name, size); editing = false },
                onDelete = { editing = false; vm.deleteProduct(d.product, onBack) },
            )
        }
    }
}

@Composable
private fun EditProductDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(product.name) }
    var size by remember { mutableStateOf(product.sizeText.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit product") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(size, { size = it }, label = { Text("Size") }, singleLine = true)
                TextButton(onClick = onDelete) {
                    Text("Delete product and all its prices", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, size) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
