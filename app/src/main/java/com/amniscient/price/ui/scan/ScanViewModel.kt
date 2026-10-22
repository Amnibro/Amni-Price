package com.amniscient.price.ui.scan

import com.amniscient.price.R
import com.amniscient.price.tr
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.LocationService
import com.amniscient.price.domain.OcrLine
import com.amniscient.price.data.PriceEntity
import com.amniscient.price.data.PriceInput
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.PriceSource
import com.amniscient.price.data.ProductSummary
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.PriceParser
import com.amniscient.price.domain.RegionalPrices
import com.amniscient.price.domain.ReceiptParser
import com.amniscient.price.domain.RowGrouper
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.scan.ShelfDraft
import com.amniscient.price.scan.VisionEngine
import com.amniscient.price.scan.VisionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class ScanMode(val label: String) { SHELF(tr(R.string.s_shelf_tag)), RECEIPT("RECEIPT"), MENU("MENU") }

data class ScanUiState(
    val mode: ScanMode = ScanMode.SHELF,
    val live: ShelfDraft = ShelfDraft(),
    val known: ProductSummary? = null,
    /** The last price of the recognized product at the current store, for "↑ 8% since last visit". */
    val lastHere: PriceEntity? = null,
    val processing: Boolean = false,
    val message: String? = null,
    val savedThisSession: Int = 0,
    /** Increments whenever something new is recognized; drives haptics. */
    val detectionTick: Int = 0,
    /** A saved store you appear to be standing in. */
    val suggestedStore: StoreEntity? = null,
)

class ScanViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    private val vision: VisionEngine,
    private val session: ScanSession,
    private val location: LocationService,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val stores: StateFlow<List<StoreEntity>> =
        repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentStoreId: StateFlow<Long?> = settings.currentStoreId

    private val busy = AtomicBoolean(false)
    @Volatile private var lastFrameAt = 0L
    private var lastPrice: Long? = null

    /** Live analysis of camera frames in shelf mode, throttled so the UI stays smooth. */
    val analyzer = ImageAnalysis.Analyzer { proxy ->
        val now = SystemClock.elapsedRealtime()
        if (_state.value.mode != ScanMode.SHELF || now - lastFrameAt < FRAME_INTERVAL_MS ||
            !busy.compareAndSet(false, true)
        ) {
            proxy.close()
            return@Analyzer
        }
        lastFrameAt = now
        viewModelScope.launch {
            try {
                onFrame(vision.analyze(proxy))
            } catch (_: Exception) {
                // A dropped frame is fine; the next one will be analyzed.
            } finally {
                proxy.close()
                busy.set(false)
            }
        }
    }

    /** Applies a pending "open in receipt mode" request from another screen. */
    fun consumeModeRequest() {
        if (session.requestReceiptMode) {
            session.requestReceiptMode = false
            setMode(ScanMode.RECEIPT)
        }
    }

    private suspend fun onFrame(result: VisionResult) {
        val focus = result.focused()
        val tag = PriceParser.parse(focus.lines)
        val previous = _state.value.live
        val newBarcode = focus.barcode?.takeIf { it != previous.barcode }
        val price = tag.priceCents?.takeIf { it == lastPrice || previous.priceCents == null || it == previous.priceCents }
        lastPrice = tag.priceCents
        val newTag = newBarcode == null && price != null && previous.priceCents != null && price != previous.priceCents
        val sameCode = focus.barcode != null && focus.barcode == previous.barcode
        var live = when {
            newBarcode != null -> if (previous.barcode.isBlank()) previous.copy(barcode = newBarcode, productId = null) else ShelfDraft(barcode = newBarcode)
            newTag -> if (sameCode) ShelfDraft(barcode = previous.barcode, productId = previous.productId) else ShelfDraft()
            else -> previous
        }
        live = live.copy(
            productName = tag.productName?.takeIf { price != null || live.productName.isBlank() } ?: live.productName,
            priceCents = price ?: live.priceCents,
            sizeText = tag.sizeText ?: live.sizeText,
            onSale = live.onSale || tag.onSale,
        )
        val drop = newBarcode != null || (newTag && !sameCode)
        var known = if (drop) null else _state.value.known
        var lastHere = if (drop) null else _state.value.lastHere
        if (newBarcode != null) {
            known = repository.summaryForBarcode(newBarcode)
            lastHere = known?.let { k -> currentStoreId.value?.let { repository.lastPriceAt(k.product.id, it) } }
        }
        if (known != null) {
            live = live.copy(
                productName = known.product.name,
                sizeText = known.product.sizeText ?: live.sizeText,
                productId = known.product.id,
            )
        }
        val newDetection = newBarcode != null || newTag || (previous.priceCents == null && live.priceCents != null)
        _state.update {
            it.copy(
                live = live,
                known = known,
                lastHere = lastHere,
                detectionTick = if (newDetection) it.detectionTick + 1 else it.detectionTick,
            )
        }
    }

    fun setMode(mode: ScanMode) = _state.update { it.copy(mode = mode, message = null) }

    fun clearLive() { lastPrice = null; _state.update { it.copy(live = ShelfDraft(), known = null, lastHere = null) } }

    fun selectStore(id: Long) {
        settings.setCurrentStore(id)
        // Re-evaluate "since last visit" for the new store.
        val productId = _state.value.known?.product?.id ?: return
        viewModelScope.launch { _state.update { it.copy(lastHere = repository.lastPriceAt(productId, id)) } }
    }

    fun addStore(name: String, branch: String?, pinHere: Boolean) {
        viewModelScope.launch {
            val pin = if (pinHere) location.hereWithRegion() else null
            settings.setCurrentStore(repository.addStore(name, branch, pin?.first, pin?.second))
        }
    }

    /** If you're standing in a saved store other than the current one, suggest switching to it. */
    fun detectNearbyStore() {
        if (!location.hasPermission()) return
        viewModelScope.launch {
            val here = location.current() ?: return@launch
            val stores = repository.allStores.first()
            val near = RegionalPrices.nearest(here, stores, NEARBY_METERS) { it.position }
            _state.update { it.copy(suggestedStore = near?.takeIf { s -> s.id != currentStoreId.value }) }
        }
    }

    fun acceptSuggestion() {
        val s = _state.value.suggestedStore ?: return
        selectStore(s.id)
        _state.update { it.copy(suggestedStore = null) }
    }

    fun dismissSuggestion() = _state.update { it.copy(suggestedStore = null) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Opens the review screen pre-filled with what the camera currently sees. */
    fun captureShelf() {
        session.pendingShelf = _state.value.live.copy(fromScan = true)
        clearLive()
    }

    fun canQuickSave(state: ScanUiState, storeId: Long?): Boolean =
        state.live.priceCents != null && state.live.productName.isNotBlank() && storeId != null

    /** One-tap save straight from the camera when everything was detected. */
    fun quickSave() {
        val live = _state.value.live
        val storeId = currentStoreId.value ?: return
        val price = live.priceCents ?: return
        if (live.productName.isBlank()) return
        viewModelScope.launch {
            if (repository.isRecentDuplicate(live.productName, live.barcode, storeId, price)) {
                clearLive()
                _state.update { it.copy(message = tr(R.string.s_already_saved_x_at_this, live.productName)) }
                return@launch
            }
            repository.recordPrice(
                PriceInput(
                    productName = live.productName,
                    storeId = storeId,
                    priceCents = price,
                    barcode = live.barcode.ifBlank { null },
                    sizeText = live.sizeText.ifBlank { null },
                    onSale = live.onSale,
                    source = PriceSource.SHELF,
                    productId = live.productId,
                ),
            )
            _state.update {
                it.copy(
                    live = ShelfDraft(), known = null, lastHere = null,
                    savedThisSession = it.savedThisSession + 1,
                    message = tr(R.string.s_saved_x_x, live.productName, Money.format(price)),
                )
            }
        }
    }

    /** Full OCR on a still photo of a receipt (from the camera or the gallery). */
    fun processReceipt(read: suspend (VisionEngine) -> List<OcrLine>, onReady: () -> Unit, onFinally: () -> Unit = {}) {
        _state.update { it.copy(processing = true, message = null) }
        viewModelScope.launch {
            try {
                val rows = RowGrouper.group(read(vision))
                val menu = _state.value.mode == ScanMode.MENU
                val receipt = if (menu) com.amniscient.price.domain.MenuParser.parse(rows) else ReceiptParser.parse(rows)
                if (receipt.items.isEmpty()) {
                    _state.update { it.copy(message = if (menu) tr(R.string.s_no_prices_found_get_the) else tr(R.string.s_no_items_found_flatten_the)) }
                } else {
                    session.pendingReceipt = receipt
                    session.pendingIsMenu = menu
                    onReady()
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = tr(R.string.s_couldn_t_read_that_image, e.message)) }
            } finally {
                onFinally()
                _state.update { it.copy(processing = false) }
            }
        }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 350L
        const val NEARBY_METERS = 250.0
    }
}
