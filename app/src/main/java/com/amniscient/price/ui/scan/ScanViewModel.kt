package com.amniscient.price.ui.scan

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amniscient.price.data.PriceInput
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.PriceSource
import com.amniscient.price.data.ProductSummary
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.data.StoreEntity
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.PriceParser
import com.amniscient.price.domain.ReceiptParser
import com.amniscient.price.domain.RowGrouper
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.scan.ShelfDraft
import com.amniscient.price.scan.VisionEngine
import com.amniscient.price.scan.VisionResult
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class ScanMode { SHELF, RECEIPT }

data class ScanUiState(
    val mode: ScanMode = ScanMode.SHELF,
    val live: ShelfDraft = ShelfDraft(),
    val known: ProductSummary? = null,
    val processing: Boolean = false,
    val message: String? = null,
)

class ScanViewModel(
    private val repository: PriceRepository,
    private val settings: SettingsStore,
    private val vision: VisionEngine,
    private val session: ScanSession,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val stores: StateFlow<List<StoreEntity>> =
        repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentStoreId: StateFlow<Long?> = settings.currentStoreId

    private val busy = AtomicBoolean(false)
    @Volatile private var lastFrameAt = 0L

    /** Live analysis of camera frames in shelf mode, throttled so the UI stays smooth. */
    @OptIn(ExperimentalGetImage::class)
    val analyzer = ImageAnalysis.Analyzer { proxy ->
        val media = proxy.image
        val now = SystemClock.elapsedRealtime()
        if (media == null || _state.value.mode != ScanMode.SHELF || now - lastFrameAt < FRAME_INTERVAL_MS ||
            !busy.compareAndSet(false, true)
        ) {
            proxy.close()
            return@Analyzer
        }
        lastFrameAt = now
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        viewModelScope.launch {
            try {
                onFrame(vision.analyze(input))
            } catch (_: Exception) {
                // A dropped frame is fine; the next one will be analyzed.
            } finally {
                proxy.close()
                busy.set(false)
            }
        }
    }

    private suspend fun onFrame(result: VisionResult) {
        val tag = PriceParser.parse(result.lines)
        val previous = _state.value.live
        val newBarcode = result.barcode?.takeIf { it != previous.barcode }

        // Results flicker frame to frame: keep the last good value for each field.
        var live = if (newBarcode != null) ShelfDraft(barcode = newBarcode) else previous
        live = live.copy(
            productName = tag.productName ?: live.productName,
            priceCents = tag.priceCents ?: live.priceCents,
            sizeText = tag.sizeText ?: live.sizeText,
            onSale = live.onSale || tag.onSale,
        )
        var known = if (newBarcode != null) null else _state.value.known
        if (newBarcode != null) {
            known = repository.summaryForBarcode(newBarcode)
        }
        if (known != null) {
            live = live.copy(productName = known.product.name, sizeText = known.product.sizeText ?: live.sizeText)
        }
        _state.update { it.copy(live = live, known = known) }
    }

    fun setMode(mode: ScanMode) = _state.update { it.copy(mode = mode, message = null) }

    fun clearLive() = _state.update { it.copy(live = ShelfDraft(), known = null) }

    fun selectStore(id: Long) = settings.setCurrentStore(id)

    fun addStore(name: String, location: String?) {
        viewModelScope.launch { settings.setCurrentStore(repository.addStore(name, location)) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Opens the review screen pre-filled with what the camera currently sees. */
    fun captureShelf() {
        session.pendingShelf = _state.value.live.copy(fromScan = true)
        clearLive()
    }

    val canQuickSave: Boolean
        get() = _state.value.live.let { it.priceCents != null && it.productName.isNotBlank() } &&
            currentStoreId.value != null

    /** One-tap save straight from the camera when everything was detected. */
    fun quickSave() {
        val live = _state.value.live
        val storeId = currentStoreId.value ?: return
        val price = live.priceCents ?: return
        if (live.productName.isBlank()) return
        viewModelScope.launch {
            repository.recordPrice(
                PriceInput(
                    productName = live.productName,
                    storeId = storeId,
                    priceCents = price,
                    barcode = live.barcode.ifBlank { null },
                    sizeText = live.sizeText.ifBlank { null },
                    onSale = live.onSale,
                    source = PriceSource.SHELF,
                ),
            )
            _state.update {
                it.copy(live = ShelfDraft(), known = null, message = "Saved ${live.productName} · ${Money.format(price)}")
            }
        }
    }

    /** Full OCR on a still photo of a receipt (from the camera or the gallery). */
    fun processReceipt(image: InputImage, onReady: () -> Unit, onFinally: () -> Unit = {}) {
        _state.update { it.copy(processing = true, message = null) }
        viewModelScope.launch {
            try {
                val rows = RowGrouper.group(vision.readText(image))
                val receipt = ReceiptParser.parse(rows)
                if (receipt.items.isEmpty()) {
                    _state.update { it.copy(message = "No items found. Flatten the receipt and try again in good light.") }
                } else {
                    session.pendingReceipt = receipt
                    onReady()
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't read that image: ${e.message}") }
            } finally {
                onFinally()
                _state.update { it.copy(processing = false) }
            }
        }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 350L
    }
}
