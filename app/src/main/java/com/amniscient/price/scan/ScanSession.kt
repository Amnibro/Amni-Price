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
)

/** Hands scan results from the camera screen to the review screens. */
class ScanSession {
    var pendingShelf: ShelfDraft? = null
    var pendingReceipt: ReceiptResult? = null
}
