package com.amniscient.price.scan

import com.amniscient.price.domain.OcrLine
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class VisionResult(val lines: List<OcrLine>, val barcode: String?)

/**
 * Thin wrapper over on-device ML Kit. Everything downstream works on [OcrLine], so the
 * engine can be swapped (e.g. Tesseract, a cloud OCR) without touching parsers or UI.
 */
class VisionEngine {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
            )
            .build(),
    )

    suspend fun analyze(image: InputImage, withBarcode: Boolean = true): VisionResult = coroutineScope {
        val text = async { textRecognizer.process(image).await() }
        val codes = if (withBarcode) async { barcodeScanner.process(image).await() } else null
        VisionResult(
            lines = text.await().toLines(),
            barcode = codes?.await()?.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) },
        )
    }

    suspend fun readText(image: InputImage): List<OcrLine> = textRecognizer.process(image).await().toLines()

    private fun Text.toLines(): List<OcrLine> =
        textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(
                    text = line.text,
                    left = box?.left ?: 0,
                    top = box?.top ?: 0,
                    right = box?.right ?: 0,
                    bottom = box?.bottom ?: 0,
                )
            }
        }
}
