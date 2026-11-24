package com.fieldcapture.app.quality

import android.graphics.Bitmap
import android.graphics.Rect
import com.fieldcapture.app.core.CaptureType
import com.fieldcapture.app.core.QualityMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * Seed thresholds. These are starting points ONLY. Replace them with cutoffs measured
 * on your own frame set (see tools/eval/README.md); every value recorded here must be
 * defensible from your data. Work size is fixed because Laplacian variance scales with
 * resolution.
 */
object QualityTuning {
    const val WORK_SIZE = 256
    const val MIN_LAPLACIAN_VARIANCE = 100.0
    const val MAX_MEAN_LUMA = 45.0
    const val MAX_DARK_FRACTION = 0.25
    const val MAX_CLIPPED_FRACTION = 0.05
    const val MIN_DETAIL_FRACTION = 0.08
    const val MIN_TEXT_COVERAGE = 0.08
}

object QualityGate {
    fun evaluate(
        bitmap: Bitmap,
        type: CaptureType,
        textBoxes: List<Rect> = emptyList(),
    ): QualityMetrics {
        val scale = min(
            QualityTuning.WORK_SIZE.toFloat() / bitmap.width,
            QualityTuning.WORK_SIZE.toFloat() / bitmap.height,
        ).coerceAtMost(1f)
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { i ->
            val p = pixels[i]
            ((299 * (p shr 16 and 0xFF) + 587 * (p shr 8 and 0xFF) + 114 * (p and 0xFF)) / 1000)
        }

        var sum = 0.0
        var sumSq = 0.0
        var dark = 0
        var clipped = 0
        var detailed = 0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val c = gray[i]
                val lap = 4 * c - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]
                sum += lap
                sumSq += lap.toDouble() * lap
                count++
                if (c < 10) dark++
                if (c > 250) clipped++
                if (kotlin.math.abs(lap) > 12) detailed++
            }
        }
        val mean = gray.average()
        val variance = if (count == 0) 0.0 else sumSq / count - (sum / count) * (sum / count)
        val darkFraction = if (count == 0) 1.0 else dark.toDouble() / count
        val clippedFraction = if (count == 0) 1.0 else clipped.toDouble() / count
        val detailFraction = if (count == 0) 0.0 else detailed.toDouble() / count

        val scaledBoxArea = textBoxes.sumOf { box ->
            val bw = box.width() * scale
            val bh = box.height() * scale
            (bw * bh).toDouble()
        }
        val coverage = (scaledBoxArea / (w * h)).coerceIn(0.0, 1.0)

        val reasons = mutableListOf<String>()
        if (variance < QualityTuning.MIN_LAPLACIAN_VARIANCE) reasons += "blurry (lap var ${variance.toInt()} < ${QualityTuning.MIN_LAPLACIAN_VARIANCE.toInt()})"
        if (mean < QualityTuning.MAX_MEAN_LUMA) reasons += "dark (mean luma ${mean.toInt()} < ${QualityTuning.MAX_MEAN_LUMA.toInt()})"
        if (darkFraction > QualityTuning.MAX_DARK_FRACTION) reasons += "dark (${(darkFraction * 100).toInt()}% near-black)"
        if (clippedFraction > QualityTuning.MAX_CLIPPED_FRACTION) reasons += "glare (${(clippedFraction * 100).toInt()}% clipped)"
        if (type == CaptureType.NAMEPLATE && textBoxes.isNotEmpty()) {
            if (coverage < QualityTuning.MIN_TEXT_COVERAGE) reasons += "framing (text ${(coverage * 100).toInt()}% of frame)"
        } else if (detailFraction < QualityTuning.MIN_DETAIL_FRACTION) {
            reasons += "framing (subject detail ${(detailFraction * 100).toInt()}% of frame)"
        }

        if (small !== bitmap) small.recycle()
        return QualityMetrics(
            laplacianVariance = variance,
            meanLuma = mean,
            darkFraction = darkFraction,
            clippedFraction = clippedFraction,
            detailFraction = detailFraction,
            textCoverage = coverage,
            passed = reasons.isEmpty(),
            reasons = reasons,
        )
    }
}
