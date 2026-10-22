package com.amniscient.price.ui.review

import com.amniscient.price.R
import com.amniscient.price.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.data.PriceEntity
import com.amniscient.price.data.PriceInput
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.PriceSource
import com.amniscient.price.data.ProductSummary
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.domain.Categorizer
import com.amniscient.price.domain.Category
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.Observation
import com.amniscient.price.domain.PriceInsights
import com.amniscient.price.domain.UnitParser
import com.amniscient.price.domain.normalizeName
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.ui.components.AmniTopBar
import com.amniscient.price.ui.components.CategoryPicker
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.LocalUiPrefs
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.components.StorePicker
import com.amniscient.price.ui.components.TrendBadge
import com.amniscient.price.ui.components.appViewModel
import com.amniscient.price.ui.components.relativeTime
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShelfReviewViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    session: ScanSession,
    private val location: LocationService,
) : ViewModel() {
    private val draft = session.pendingShelf.also { session.pendingShelf = null }
    private val sessionRef = session
    var channel by mutableStateOf(session.channel)

    val fromScan = draft?.fromScan == true
    private val fixedProductId = draft?.productId
    var name by mutableStateOf(draft?.productName.orEmpty())
    var barcode by mutableStateOf(draft?.barcode.orEmpty())
    var size by mutableStateOf(draft?.sizeText.orEmpty())
    var price by mutableStateOf(draft?.priceCents?.let(Money::toPlain).orEmpty())
    var onSale by mutableStateOf(draft?.onSale == true)
    var storeId by mutableStateOf(settings.currentStoreId.value)
    var category by mutableStateOf(Categorizer.categorize(name))
    private var categoryTouched = false

    /** Context about the product being entered, if we've seen it before. */
    var known by mutableStateOf<ProductSummary?>(null)
        private set
    var lastHere by mutableStateOf<PriceEntity?>(null)
        private set

    val stores: StateFlow<List<StoreEntity>> =
        repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // A known barcode fills in the name we saved last time.
        if (name.isBlank() && barcode.isNotBlank()) {
            viewModelScope.launch {
                repository.findByBarcode(barcode)?.let { if (name.isBlank()) name = it.name }
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        if (!categoryTouched) category = Categorizer.categorize(value)
    }

    fun onCategoryChange(value: Category) {
        category = value
        categoryTouched = true
    }

    /** Looks up what we know about this product and store, as the form changes. */
    suspend fun refreshContext() {
        val summaries = repository.productSummaries.first()
        val match = summaries.firstOrNull { s ->
            (fixedProductId != null && s.product.id == fixedProductId) ||
                (barcode.isNotBlank() && s.product.barcode == barcode) ||
                (name.isNotBlank() && s.product.normalizedName == normalizeName(name))
        }
        known = match
        if (match != null && !categoryTouched) category = match.product.category
        lastHere = match?.let { m -> storeId?.let { repository.lastPriceAt(m.product.id, it) } }
    }

    val priceCents: Long? get() = Money.parse(price)?.takeIf { it > 0 }
    val canSave: Boolean get() = name.isNotBlank() && priceCents != null && storeId != null

    fun addStore(name: String, branch: String?, pinHere: Boolean) {
        viewModelScope.launch {
            val pin = if (pinHere) location.hereWithRegion() else null
            storeId = repository.addStore(name, branch, pin?.first, pin?.second)
        }
    }

    fun save(onDone: () -> Unit) {
        val store = storeId ?: return
        val cents = priceCents ?: return
        viewModelScope.launch {
            repository.recordPrice(
                PriceInput(
                    productName = name.trim(),
                    storeId = store,
                    priceCents = cents,
                    barcode = barcode.trim().ifEmpty { null },
                    sizeText = size.trim().ifEmpty { null },
                    onSale = onSale,
                    source = if (fromScan) PriceSource.SHELF else PriceSource.MANUAL,
                    productId = fixedProductId,
                    category = category,
                    channel = channel,
                ),
            )
            sessionRef.channel = channel
            settings.setCurrentStore(store)
            onDone()
        }
    }
}

@Composable
fun ShelfReviewScreen(onDone: () -> Unit) {
    val vm = appViewModel { ShelfReviewViewModel(it.repository, it.settings, it.scanSession, it.location) }
    val stores by vm.stores.collectAsStateWithLifecycle()
    val imperial = LocalUiPrefs.current.imperialUnits

    LaunchedEffect(vm.name, vm.barcode, vm.storeId) { vm.refreshContext() }

    Column(Modifier.fillMaxSize().imePadding()) {
        AmniTopBar(
            title = if (vm.fromScan) tr(R.string.s_confirm_price) else tr(R.string.s_add_price),
            eyebrow = if (vm.fromScan) tr(R.string.s_scanned) else tr(R.string.s_manual_entry),
            onBack = onDone,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.fromScan) {
                Text(
                    tr(R.string.s_check_what_the_camera_read),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = vm.name, onValueChange = vm::onNameChange,
                label = { Text(tr(R.string.s_product)) }, singleLine = true, shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.price, onValueChange = { vm.price = it },
                label = { Text(tr(R.string.s_price)) }, singleLine = true, prefix = { Text("$") }, shape = MaterialTheme.shapes.small,
                textStyle = AmniText.price,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.size, onValueChange = { vm.size = it },
                    label = { Text(tr(R.string.s_size)) }, placeholder = { Text(tr(R.string.s_12_oz)) }, singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = vm.barcode, onValueChange = { vm.barcode = it },
                    label = { Text(tr(R.string.s_barcode)) }, singleLine = true, shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            val quantity = UnitParser.parse(vm.size)
            val cents = vm.priceCents
            if (quantity != null && cents != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(tr(R.string.s_unit_price), Modifier.weight(1f))
                    Text(UnitParser.unitPriceLabel(cents, quantity, imperial), style = AmniText.priceSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            StorePicker(
                stores = stores,
                selectedId = vm.storeId,
                onSelect = { vm.storeId = it },
                onAddStore = vm::addStore,
                modifier = Modifier.fillMaxWidth(),
            )
            com.amniscient.price.ui.components.ChannelPicker(vm.channel, { vm.channel = it }, Modifier.fillMaxWidth())
            CategoryPicker(vm.category, vm::onCategoryChange, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.s_sale_or_promo_price), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr(R.string.s_marked_so_temporary_deals_don),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.onSale, onCheckedChange = { vm.onSale = it })
            }

            ContextPanel(vm.known, vm.lastHere, cents)

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.save(onDone) },
                enabled = vm.canSave,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(tr(R.string.s_save_price)) }
        }
    }
}

@Composable
private fun ContextPanel(known: ProductSummary?, lastHere: PriceEntity?, currentCents: Long?) {
    if (known == null) return
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow(tr(R.string.s_you_ve_seen_this_before), color = Amni.palette.brass)
            if (lastHere != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.s_last_time_here_x, relativeTime(lastHere.observedAt)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    PriceText(lastHere.priceCents, style = AmniText.priceSmall)
                    if (currentCents != null) {
                        PriceInsights.changeAgainst(
                            Observation(lastHere.productId, lastHere.storeId, lastHere.priceCents, lastHere.observedAt), currentCents,
                        )?.let {
                            Spacer(Modifier.height(0.dp))
                            TrendBadge(it, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            if (known.cheapestCents != null) {
                HorizontalDivider(color = Amni.palette.hairline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.s_best_known_x, known.cheapestStore), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    PriceText(known.cheapestCents, style = AmniText.priceSmall, color = Amni.palette.deal)
                }
            }
        }
    }
}
