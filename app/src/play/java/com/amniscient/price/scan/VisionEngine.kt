package com.amniscient.price.scan
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
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
class VisionEngine(private val context: Context) {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_CODE_128).build())
    suspend fun analyze(proxy: ImageProxy, withBarcode: Boolean = true): VisionResult = analyze(proxy.input(), withBarcode)
    suspend fun readText(proxy: ImageProxy): List<OcrLine> = read(proxy.input())
    suspend fun readText(uri: Uri): List<OcrLine> = read(InputImage.fromFilePath(context, uri))
    private suspend fun analyze(image: InputImage, withBarcode: Boolean): VisionResult = coroutineScope {
        val text = async { textRecognizer.process(image).await() }
        val codes = if (withBarcode) async { barcodeScanner.process(image).await() } else null
        VisionResult(text.await().toLines(), codes?.await()?.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) })
    }
    private suspend fun read(image: InputImage): List<OcrLine> = textRecognizer.process(image).await().toLines()
    @OptIn(ExperimentalGetImage::class)
    private fun ImageProxy.input(): InputImage = InputImage.fromMediaImage(requireNotNull(image), imageInfo.rotationDegrees)
    private fun Text.toLines(): List<OcrLine> = textBlocks.flatMap { b -> b.lines.map { l -> l.boundingBox.let { OcrLine(l.text, it?.left ?: 0, it?.top ?: 0, it?.right ?: 0, it?.bottom ?: 0) } } }
}
