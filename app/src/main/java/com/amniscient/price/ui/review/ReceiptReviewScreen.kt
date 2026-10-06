package com.amniscient.price.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.data.PriceInput
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.PriceSource
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.StorePicker
import com.amniscient.price.ui.components.Tag
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ReceiptRow(name: String, price: String, val quantity: Int, val discounted: Boolean) {
    val id = nextId++
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
    /** A saved product this line probably is (fuzzy match), and whether to attach to it. */
    var matchId by mutableStateOf<Long?>(null)
    var matchName by mutableStateOf<String?>(null)
    var linked by mutableStateOf(false)

    private companion object {
        var nextId = 0L
    }
}

class ReceiptReviewViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    session: ScanSession,
    private val location: LocationService,
) : ViewModel() {
    private val receipt = session.pendingReceipt.also { session.pendingReceipt = null }

    val detectedStore: String? = receipt?.storeName
    val totalCents: Long? = receipt?.totalCents
    val dateDetected = receipt?.date != null
    var date by mutableStateOf(receipt?.date ?: LocalDate.now())
    val rows = mutableStateListOf<ReceiptRow>().apply {
        receipt?.items?.forEach { add(ReceiptRow(it.name, Money.toPlain(it.unitPriceCents), it.quantity, it.discounted)) }
    }
    var storeId by mutableStateOf(settings.currentStoreId.value)
    var storeMatched by mutableStateOf(false)

    val stores: StateFlow<List<StoreEntity>> =
        repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Match the header of the receipt ("FRESH MART #123") to a saved store.
        val guess = detectedStore?.let(::normalizeName)
        if (!guess.isNullOrBlank()) {
            viewModelScope.launch {
                val candidates = repository.allStores.first().filter { store ->
                    val n = normalizeName(store.name)
                    n.isNotBlank() && (guess.contains(n) || n.contains(guess))
                }
                // Several branches of the chain: prefer the one you're shopping at.
                val match = candidates.firstOrNull { it.id == storeId } ?: candidates.firstOrNull()
                if (match != null) {
                    storeId = match.id
                    storeMatched = true
                } else {
                    storeId = null
                }
            }
        }
        // Receipts abbreviate ("GV WHL MLK"): link lines to products you've already saved.
        viewModelScope.launch {
            rows.toList().forEach { row ->
                repository.matchProduct(row.name)?.let { (product, score) ->
                    row.matchId = product.id
                    row.matchName = product.name
                    row.linked = score >= AUTO_LINK_SCORE
                }
            }
        }
    }

    val subtotalCents: Long get() = rows.sumOf { (Money.parse(it.price) ?: 0) * it.quantity }

    val canSave: Boolean
        get() = storeId != null && rows.any { it.name.isNotBlank() && Money.parse(it.price) != null }

    fun addStore(name: String, branch: String?, pinHere: Boolean) {
        viewModelScope.launch {
            val pin = if (pinHere) location.hereWithRegion() else null
            storeId = repository.addStore(name, branch, pin?.first, pin?.second)
            storeMatched = true
        }
    }

    fun save(onDone: () -> Unit) {
        val store = storeId ?: return
        val observedAt = if (date == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val inputs = rows.mapNotNull { row ->
            val cents = Money.parse(row.price)?.takeIf { it > 0 } ?: return@mapNotNull null
            if (row.name.isBlank()) return@mapNotNull null
            PriceInput(
                productName = row.name.trim(),
                storeId = store,
                priceCents = cents,
                onSale = row.discounted,
                source = PriceSource.RECEIPT,
                observedAt = observedAt,
                productId = if (row.linked) row.matchId else null,
            )
        }
        viewModelScope.launch {
            repository.recordAll(inputs)
            settings.setCurrentStore(store)
            onDone()
        }
    }

    private companion object {
        const val AUTO_LINK_SCORE = 0.75
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(onDone: () -> Unit) {
    val vm = appViewModel { ReceiptReviewViewModel(it.repository, it.settings, it.scanSession, it.location) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        AmniTopBar(title = "Receipt", eyebrow = "${vm.rows.size} items found", onBack = onDone)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorePicker(
                        stores = stores,
                        selectedId = vm.storeId,
                        onSelect = { vm.storeId = it },
                        onAddStore = vm::addStore,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val detected = vm.detectedStore
                        if (detected != null && !vm.storeMatched) {
                            AssistChip(onClick = { vm.addStore(detected, null, false) }, label = { Text("Create \"$detected\"") })
                        }
                        AssistChip(
                            onClick = { pickingDate = true },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) },
                            label = { Text(vm.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) },
                        )
                        if (vm.dateDetected) Tag("From receipt", color = Amni.palette.brass)
                    }
                }
            }
            item {
                Panel {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("Items subtotal")
                            vm.totalCents?.let {
                                Text(
                                    "Receipt total ${Money.format(it)} incl. tax",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        PriceText(vm.subtotalCents)
                    }
                }
            }
            items(vm.rows, key = { it.id }) { row -> ReceiptLine(row, onRemove = { vm.rows.remove(row) }) }
        }
        Button(
            onClick = { vm.save(onDone) },
            enabled = vm.canSave,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) { Text("Save ${vm.rows.size} prices") }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun ReceiptLine(row: ReceiptRow, onRemove: () -> Unit) {
    Panel {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.name, onValueChange = { row.name = it },
                    label = { Text(if (row.quantity > 1) "Item · ×${row.quantity}" else "Item") },
                    singleLine = true, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = row.price, onValueChange = { row.price = it },
                    label = { Text(if (row.quantity > 1) "Each" else "Price") },
                    singleLine = true, prefix = { Text("$") }, shape = MaterialTheme.shapes.small,
                    textStyle = AmniText.priceSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(104.dp),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val match = row.matchName
            if (match != null) {
                Row(
                    Modifier.padding(top = 6.dp, end = 8.dp).clickable { row.linked = !row.linked },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (row.linked) Icons.Default.Link else Icons.Default.LinkOff,
                        null,
                        Modifier.size(16.dp),
                        tint = if (row.linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (row.linked) "Saving as " else "Tap to save as ") + match,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.linked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.discounted) Tag("Coupon applied", color = Amni.palette.brass, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
