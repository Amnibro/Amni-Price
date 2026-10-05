package com.amniscient.price.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.ListEntry
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.ProductEntity
import com.amniscient.price.data.ShoppingItemEntity
import com.amniscient.price.data.ShoppingPlan
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.EmptyState
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.LocalSnackbar
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.SectionHeader
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.showUndo
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(private val repository: PriceRepository) : ViewModel() {
    val plan: StateFlow<ShoppingPlan?> =
        repository.shoppingPlan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val products: StateFlow<List<ProductEntity>> =
        repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, productId: Long? = null) = viewModelScope.launch {
        if (name.isNotBlank()) repository.addToList(name, productId)
    }
    fun toggle(item: ShoppingItemEntity) = viewModelScope.launch { repository.updateListItem(item.copy(checked = !item.checked)) }
    fun setQuantity(item: ShoppingItemEntity, q: Int) = viewModelScope.launch { repository.updateListItem(item.copy(quantity = q.coerceIn(1, 99))) }
    suspend fun delete(item: ShoppingItemEntity) = repository.deleteListItem(item)
    suspend fun restore(item: ShoppingItemEntity) = repository.restoreListItem(item)
    fun clearChecked() = viewModelScope.launch { repository.clearCheckedItems() }
}

@Composable
fun ShoppingListScreen(onOpenProduct: (Long) -> Unit, onOpenStore: (Long) -> Unit) {
    val vm = appViewModel { ShoppingListViewModel(it.repository) }
    val plan by vm.plan.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().imePadding()) {
        val p = plan
        val open = p?.entries?.filterNot { it.item.checked }.orEmpty()
        val done = p?.entries?.filter { it.item.checked }.orEmpty()
        AmniTopBar(
            title = "Shopping list",
            eyebrow = if (open.isEmpty()) "Plan the cheapest trip" else "${open.size} to get",
            actions = {
                if (done.isNotEmpty()) IconButton(onClick = vm::clearChecked) { Icon(Icons.Default.DeleteSweep, "Clear checked") }
            },
        )
        AddItemField(products, onAdd = vm::add)

        if (p == null) return@Column
        if (p.entries.isEmpty()) {
            EmptyState(
                Icons.Default.Checklist,
                "Your list is empty",
                "Add what you need. Amni-Price works out which store, or which two stores, gets it all for the least.",
            )
            return@Column
        }

        val assignment = p.plan.best?.assignments?.associateBy { it.itemKey }.orEmpty()
        val delete: (ShoppingItemEntity) -> Unit = { item ->
            scope.launch {
                vm.delete(item)
                snackbar.showUndo("Removed ${item.name}") { vm.restore(item) }
            }
        }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (p.plan.pricedCount > 0) item { PlanPanel(p, onOpenStore) }

            // Group open items by the store the plan sends you to.
            val groups = open.groupBy { assignment[it.item.id]?.storeId }
            p.plan.best?.storeIds?.forEach { storeId ->
                val entries = groups[storeId].orEmpty()
                if (entries.isNotEmpty()) {
                    item(key = "h$storeId") {
                        SectionHeader("Buy at ${p.storeNames[storeId] ?: "store"}", action = {
                            PriceText(entries.sumOf { (assignment[it.item.id]?.unitCents ?: 0) * it.item.quantity }, style = AmniText.priceSmall)
                        })
                    }
                    items(entries, key = { it.item.id }) { e ->
                        ListRow(e, assignment[e.item.id]?.unitCents, vm::toggle, vm::setQuantity, delete, onOpenProduct)
                    }
                }
            }
            groups[null]?.let { unpriced ->
                item(key = "hnone") { SectionHeader("No price yet") }
                items(unpriced, key = { it.item.id }) { e -> ListRow(e, null, vm::toggle, vm::setQuantity, delete, onOpenProduct) }
            }
            if (done.isNotEmpty()) {
                item(key = "hdone") { SectionHeader("In the cart · ${done.size}") }
                items(done, key = { it.item.id }) { e -> ListRow(e, null, vm::toggle, vm::setQuantity, delete, onOpenProduct) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemField(products: List<ProductEntity>, onAdd: (String, Long?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val q = normalizeName(text)
    val suggestions = if (q.length < 2) emptyList() else products.filter { it.normalizedName.contains(q) }.take(5)
    var expanded by remember { mutableStateOf(false) }

    fun submit(name: String = text, id: Long? = null) {
        onAdd(name, id)
        text = ""
        expanded = false
    }

    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; expanded = true },
                placeholder = { Text("Add an item, e.g. milk") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) submit() }),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
                suggestions.forEach { s ->
                    DropdownMenuItem(text = { Text(s.name) }, onClick = { submit(s.name, s.id) })
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank(), shape = MaterialTheme.shapes.small) {
            Icon(Icons.Default.Add, "Add")
        }
    }
}

@Composable
private fun PlanPanel(p: ShoppingPlan, onOpenStore: (Long) -> Unit) {
    val best = p.plan.best ?: return
    val single = p.plan.singleStore.firstOrNull()
    Panel(highlighted = true) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, null, tint = Amni.palette.brass, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Eyebrow("Cheapest trip", color = Amni.palette.brass)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    best.storeIds.mapNotNull { p.storeNames[it] }.joinToString(" + "),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                PriceText(best.totalCents, style = AmniText.priceLarge, color = Amni.palette.deal)
            }
            Text(
                buildString {
                    append("${best.covered} of ${p.plan.pricedCount + p.plan.unpriced.size} items priced")
                    if (p.plan.savingsVsHighest > 0) append(" · saves ${Money.format(p.plan.savingsVsHighest)} vs. highest prices")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (best.storeIds.size > 1 && single != null) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Amni.palette.hairline)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenStore(single.storeId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("One stop instead")
                        Text(
                            "${p.storeNames[single.storeId]} · ${single.covered} of ${p.plan.pricedCount} items",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PriceText(single.totalCents, style = AmniText.priceSmall)
                        if (single.covered == best.covered && single.totalCents > best.totalCents) {
                            Text("+${Money.format(single.totalCents - best.totalCents)}", style = AmniText.priceSmall, color = Amni.palette.rise)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListRow(
    entry: ListEntry,
    plannedCents: Long?,
    onToggle: (ShoppingItemEntity) -> Unit,
    onQuantity: (ShoppingItemEntity, Int) -> Unit,
    onDelete: (ShoppingItemEntity) -> Unit,
    onOpenProduct: (Long) -> Unit,
) {
    val item = entry.item
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) onDelete(item)
        it != SwipeToDismissBoxValue.Settled
    })
    SwipeToDismissBox(
        state = dismiss,
        backgroundContent = {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Eyebrow("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Panel {
            Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { onToggle(item) },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
                Column(
                    Modifier.weight(1f).let { m -> entry.product?.let { p -> m.clickable { onOpenProduct(p.id) } } ?: m }.padding(vertical = 10.dp),
                ) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.product == null && !item.checked) {
                        Text("Not matched to a scanned product yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!item.checked) {
                    IconButton(onClick = { onQuantity(item, item.quantity - 1) }, enabled = item.quantity > 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, "Less", Modifier.size(16.dp))
                    }
                    Text("${item.quantity}", style = AmniText.priceSmall)
                    IconButton(onClick = { onQuantity(item, item.quantity + 1) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, "More", Modifier.size(16.dp))
                    }
                }
                val cents = plannedCents ?: entry.cheapestCents
                if (cents != null) {
                    PriceText(cents * item.quantity, style = AmniText.priceSmall, modifier = Modifier.padding(start = 6.dp, end = 8.dp))
                }
            }
        }
    }
}
