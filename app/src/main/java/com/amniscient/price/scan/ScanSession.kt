package com.amniscient.price.scan

import com.amniscient.price.domain.ReceiptResult

/** A captured shelf tag waiting for the user to confirm it. */
data class ShelfDraft(
    val productName: String = "",
    val barcode: String = "",
    val priceCents: Long? = null,
    val sizeText: String = "",
    val onSale: Boolean = false,
    val fromScan: Boolean = false,
    val productId: Long? = null,
)

/** Hands scan results between screens. */
class ScanSession {
    var pendingShelf: ShelfDraft? = null
    var pendingReceipt: ReceiptResult? = null
    /** Set by shortcuts (e.g. "Scan receipt" on Home) to open the camera in a given mode. */
    var requestReceiptMode: Boolean = false
    /** Product to show when the price map opens ("View on map" from a product page). */
    var mapProductId: Long? = null
}
