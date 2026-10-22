package com.amniscient.price.scan
import com.amniscient.price.domain.OcrLine
import com.amniscient.price.domain.TagFocus
data class VisionResult(val lines: List<OcrLine>, val codes: List<TagFocus.Code> = emptyList(), val width: Int = 0, val height: Int = 0) {
    fun focused() = TagFocus.pick(lines, codes, width, height)
}
