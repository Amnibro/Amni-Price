package com.amniscient.price.ui.compare

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceEntity
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.ProductDetail
import com.amniscient.price.data.ProductEntity
import com.amniscient.price.domain.Category
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.UnitParser
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.scan.ShelfDraft
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.CategoryPicker
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.components.LocalUiPrefs
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.SectionHeader
import com.amniscient.price.ui.components.Tag
import com.amniscient.price.ui.components.TrendBadge
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.relativeTime
import com.amniscient.price.ui.components.showUndo
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
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
        session.pendingShelf = ShelfDraft(
            productName = p.name, barcode = p.barcode.orEmpty(), sizeText = p.sizeText.orEmpty(), productId = p.id,
        )
    }

    suspend fun deletePrice(price: PriceEntity) = repository.deletePrice(price)
    suspend fun restorePrice(price: PriceEntity) = repository.restorePrice(price)

    suspend fun addToList(product: ProductEntity) = repository.addToList(product.name, product.id)

    fun update(product: ProductEntity, name: String, size: String, category: Category) = viewModelScope.launch {
        repository.updateProduct(product.copy(name = name.trim(), sizeText = size.trim().ifEmpty { null }, category = category))
    }

    fun deleteProduct(product: ProductEntity, onDone: () -> Unit) = viewModelScope.launch {
        repository.deleteProduct(product)
        onDone()
    }

    fun shareText(): String? {
        val d = detail.value ?: return null
        val lines = d.byStore.joinToString("\n") { "• ${it.storeName}: ${Money.format(it.latest.priceCents)}" }
        return "${d.product.name}${d.product.sizeText?.let { " ($it)" } ?: ""}\n$lines\n\nTracked with Amni-Price"
    }
}

@Composable
fun ProductDetailScreen(productId: Long, onBack: () -> Unit, onAddPrice: () -> Unit, onOpenStore: (Long) -> Unit) {
    val vm = appViewModel { ProductDetailViewModel(it.repository, it.scanSession, productId) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val imperial = LocalUiPrefs.current.imperialUnits
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AmniTopBar(
            title = "Product",
            eyebrow = detail?.product?.category?.label,
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    vm.shareText()?.let { text ->
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share prices"),
                        )
                    }
                }) { Icon(Icons.Default.Share, "Share") }
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit product") }
            },
        )
        val d = detail ?: return@Column
        val quantity = UnitParser.parse(d.product.sizeText)
        val best = d.byStore.firstOrNull()
        val worst = d.byStore.lastOrNull()

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text(d.product.name, style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    d.product.sizeText?.let { Tag(it) }
                    d.product.barcode?.let { Text(it, style = AmniText.priceSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (best != null) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Panel(highlighted = d.byStore.size > 1) {
                        Column(Modifier.padding(16.dp)) {
                            Eyebrow(if (d.byStore.size > 1) "Cheapest" else "Latest price", color = Amni.palette.brass)
                            Row(verticalAlignment = Alignment.Bottom) {
                                PriceText(best.latest.priceCents, style = AmniText.hero, color = if (d.byStore.size > 1) Amni.palette.deal else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(12.dp))
                                Text(best.storeName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            quantity?.let {
                                Text(UnitParser.unitPriceLabel(best.latest.priceCents, it, imperial), style = AmniText.priceSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (worst != null && worst != best && worst.latest.priceCents > best.latest.priceCents) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Up to ${Money.format(worst.latest.priceCents)} at ${worst.storeName}: you save " +
                                        Money.format(worst.latest.priceCents - best.latest.priceCents),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                vm.addToList(d.product)
                                snackbar.showSnackbar("Added to your list")
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.AddShoppingCart, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to list")
                    }
                    Button(onClick = { vm.prepareAddPrice(); onAddPrice() }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PostAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Log price")
                    }
                }
            }
            item {
                SectionHeader("Price history")
                PriceChart(d.history)
            }
            item {
                SectionHeader("By store")
                Panel {
                    d.byStore.forEachIndexed { i, sp ->
                        if (i > 0) HorizontalDivider(color = Amni.palette.hairline)
                        val cheapest = i == 0 && d.byStore.size > 1
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenStore(sp.storeId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(sp.storeName, style = MaterialTheme.typography.titleSmall)
                                    if (sp.latest.onSale) Tag("Sale", color = Amni.palette.brass, modifier = Modifier.padding(start = 6.dp))
                                }
                                val sub = buildList {
                                    add(relativeTime(sp.latest.observedAt))
                                    quantity?.let { add(UnitParser.unitPriceLabel(sp.latest.priceCents, it, imperial)) }
                                }.joinToString(" · ")
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            d.changes[sp.storeId]?.let { TrendBadge(it.percent, Modifier.padding(end = 10.dp)) }
                            Column(horizontalAlignment = Alignment.End) {
                                PriceText(sp.latest.priceCents, color = if (cheapest) Amni.palette.deal else MaterialTheme.colorScheme.onSurface)
                                val diff = sp.latest.priceCents - d.byStore.first().latest.priceCents
                                if (diff > 0) {
                                    Text("+${Money.format(diff)}", style = AmniText.priceSmall, color = Amni.palette.rise)
                                } else if (cheapest) {
                                    Eyebrow("Cheapest", color = Amni.palette.deal)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader("Every observation")
                Panel {
                    d.history.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(color = Amni.palette.hairline)
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.storeName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    relativeTime(h.price.observedAt) + " · " + h.price.source.name.lowercase() + if (h.price.onSale) " · sale" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PriceText(h.price.priceCents, style = AmniText.priceSmall)
                            IconButton(onClick = {
                                scope.launch {
                                    vm.deletePrice(h.price)
                                    snackbar.showUndo("Price removed") { vm.restorePrice(h.price) }
                                }
                            }) { Icon(Icons.Default.Delete, "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }

        if (editing) {
            EditProductDialog(
                product = d.product,
                onDismiss = { editing = false },
                onSave = { name, size, category -> vm.update(d.product, name, size, category); editing = false },
                onDelete = { editing = false; vm.deleteProduct(d.product, onBack) },
            )
        }
    }
}

@Composable
private fun EditProductDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, Category) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(product.name) }
    var size by remember { mutableStateOf(product.sizeText.orEmpty()) }
    var category by remember { mutableStateOf(product.category) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = Amni.palette.panel,
        title = { Text("Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.small)
                OutlinedTextField(size, { size = it }, label = { Text("Size") }, singleLine = true, shape = MaterialTheme.shapes.small)
                CategoryPicker(category, { category = it })
                TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                    Text(
                        if (confirmDelete) "Tap again to delete permanently" else "Delete product and all its prices",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, size, category) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
