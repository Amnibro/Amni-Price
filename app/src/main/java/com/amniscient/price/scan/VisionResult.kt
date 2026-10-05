package com.amniscient.price.scan
import com.amniscient.price.domain.OcrLine
data class VisionResult(val lines: List<OcrLine>, val barcode: String?)
