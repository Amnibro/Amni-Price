package com.amniscient.price.domain

/** A single line of recognized text with its bounding box, independent of the OCR engine. */
data class OcrLine(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val height: Int get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
}
