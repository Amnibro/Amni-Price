package com.amniscient.price.scan
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.amniscient.price.domain.OcrLine
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
class VisionEngine(private val context: Context) {
    private val lock = Mutex()
    private val tess by lazy { TessBaseAPI().apply { check(init(dataDir().absolutePath, "eng", TessBaseAPI.OEM_LSTM_ONLY)) { "OCR model missing" }; setVariable("user_defined_dpi", "300") } }
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.CODE_128), DecodeHintType.TRY_HARDER to true)
    suspend fun analyze(proxy: ImageProxy, withBarcode: Boolean = true): VisionResult = coroutineScope { val b = proxy.upright(LIVE_MAX); val code = async(Dispatchers.Default) { if (withBarcode) barcode(b) else null }; VisionResult(ocr(b, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT), code.await()) }
    suspend fun readText(proxy: ImageProxy): List<OcrLine> = ocr(binarize(proxy.upright(STILL_MAX).fit(STILL_MAX)), TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
    suspend fun readText(uri: Uri): List<OcrLine> = ocr(binarize(withContext(Dispatchers.IO) { decode(uri) }), TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
    private suspend fun binarize(src: Bitmap, offset: Double = 0.08): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width; val h = src.height; val r = maxOf(8, maxOf(w, h) / 80)
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val lum = IntArray(w * h) { val c = px[it]; ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 }
        val sum = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) { var row = 0L; for (x in 0 until w) { row += lum[y * w + x]; sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row } }
        for (y in 0 until h) { val y0 = maxOf(0, y - r); val y1 = minOf(h, y + r + 1)
            for (x in 0 until w) { val x0 = maxOf(0, x - r); val x1 = minOf(w, x + r + 1)
                val mean = (sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0] + sum[y0 * (w + 1) + x0]).toDouble() / ((x1 - x0) * (y1 - y0))
                px[y * w + x] = if (lum[y * w + x] < mean * (1 - offset)) -0x1000000 else -1 } }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
    private suspend fun ocr(bitmap: Bitmap, mode: Int): List<OcrLine> = withContext(Dispatchers.Default) {
        lock.withLock {
            tess.pageSegMode = mode
            tess.setImage(bitmap)
            tess.utF8Text
            val it = tess.resultIterator ?: return@withLock emptyList()
            val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
            val out = mutableListOf<OcrLine>()
            it.begin()
            do { it.getUTF8Text(level)?.trim()?.takeIf(String::isNotEmpty)?.let { t -> it.getBoundingRect(level).let { r -> out += OcrLine(t, r.left, r.top, r.right, r.bottom) } } } while (it.next(level))
            it.delete()
            tess.clear()
            out
        }
    }
    private fun barcode(bitmap: Bitmap): String? = runCatching {
        val px = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, px))), hints).text
    }.getOrNull()?.takeIf(String::isNotBlank)
    private fun ImageProxy.upright(max: Int): Bitmap = toBitmap().rotated(imageInfo.rotationDegrees.toFloat()).scaled(max)
    private fun decode(uri: Uri): Bitmap {
        val deg = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { o -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } }
        val sample = generateSequence(1) { it * 2 }.first { maxOf(bounds.outWidth, bounds.outHeight) / (it * 2) < STILL_MAX }
        val raw = requireNotNull(context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }) { "Unreadable image" }
        return raw.rotated(deg.toFloat()).fit(STILL_MAX)
    }
    private fun Bitmap.rotated(deg: Float): Bitmap = if (deg == 0f) this else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(deg) }, true)
    private fun Bitmap.scaled(max: Int): Bitmap = maxOf(width, height).let { m -> if (m <= max) this else Bitmap.createScaledBitmap(this, width * max / m, height * max / m, true) }
    private fun Bitmap.fit(max: Int): Bitmap = maxOf(width, height).let { m -> if (m == max) this else Bitmap.createScaledBitmap(this, width * max / m, height * max / m, true) }
    private fun dataDir(): File = File(context.filesDir, "ocr").also { root ->
        val model = File(root, "tessdata/eng.traineddata")
        if (model.length() != context.assets.openFd("tessdata/eng.traineddata").use { it.length }) { model.parentFile?.mkdirs(); context.assets.open("tessdata/eng.traineddata").use { i -> model.outputStream().use { i.copyTo(it) } } }
    }
    companion object { const val LIVE_MAX = 1280; const val STILL_MAX = 2400 }
}
