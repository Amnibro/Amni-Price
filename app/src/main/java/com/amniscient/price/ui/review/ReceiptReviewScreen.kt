package com.amniscient.price.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.ReceiptItem
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.ui.components.StorePicker
import com.amniscient.price.ui.components.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReceiptRow(name: String, price: String, val quantity: Int, val discounted: Boolean) {
    val id = nextId++
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)

    private companion object {
        var nextId = 0L
    }
}

class ReceiptReviewViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    session: ScanSession,
) : ViewModel() {
    private val receipt = session.pendingReceipt.also { session.pendingReceipt = null }

    val detectedStore: String? = receipt?.storeName
    val totalCents: Long? = receipt?.totalCents
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
                val match = repository.allStores.first().firstOrNull { store ->
                    val n = normalizeName(store.name)
                    n.isNotBlank() && (guess.contains(n) || n.contains(guess))
                }
                if (match != null) {
                    storeId = match.id
                    storeMatched = true
                }
            }
        }
    }

    val canSave: Boolean
        get() = storeId != null && rows.any { it.name.isNotBlank() && Money.parse(it.price) != null }

    fun addStore(name: String, location: String?) {
        viewModelScope.launch {
            storeId = repository.addStore(name, location)
            storeMatched = true
        }
    }

    fun save(onDone: () -> Unit) {
        val store = storeId ?: return
        val items = rows.mapNotNull { row ->
            val cents = Money.parse(row.price)?.takeIf { it > 0 } ?: return@mapNotNull null
            if (row.name.isBlank()) null else ReceiptItem(row.name.trim(), cents, 1, row.discounted)
        }
        viewModelScope.launch {
            repository.recordReceipt(store, items)
            settings.setCurrentStore(store)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(onDone: () -> Unit) {
    val vm = appViewModel { ReceiptReviewViewModel(it.repository, it.settings, it.scanSession) }
    val stores by vm.stores.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Receipt · ${vm.rows.size} items") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StorePicker(
                stores = stores,
                selectedId = vm.storeId,
                onSelect = { vm.storeId = it },
                onAddStore = vm::addStore,
                modifier = Modifier.fillMaxWidth(),
            )
            val detected = vm.detectedStore
            if (detected != null && !vm.storeMatched) {
                AssistChip(onClick = { vm.addStore(detected, null) }, label = { Text("Create store \"$detected\"") })
            }
            vm.totalCents?.let {
                Text(
                    "Receipt total ${Money.format(it)} (includes tax)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(vm.rows, key = { _, row -> row.id }) { _, row ->
                Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = row.name, onValueChange = { row.name = it },
                            label = { Text(if (row.quantity > 1) "Item (×${row.quantity}, unit price)" else "Item") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = row.price, onValueChange = { row.price = it },
                            label = { Text("Price") }, singleLine = true, prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp).padding(start = 8.dp),
                        )
                        IconButton(onClick = { vm.rows.remove(row) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
            }
        }
        Button(
            onClick = { vm.save(onDone) },
            enabled = vm.canSave,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Save ${vm.rows.size} prices") }
    }
}
