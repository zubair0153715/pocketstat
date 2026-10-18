package com.pocketstat.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.log2

/**
 * Original histogram view drawn with Canvas.
 * Bins via Sturges' rule: k = ceil(log2(n)) + 1.
 */
class HistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var values: List<Double> = emptyList()
        set(v) {
            field = v
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B6CA8")
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 90f
        val padB = 70f
        val padT = 30f
        val padR = 30f

        if (values.isEmpty()) {
            canvas.drawText("No data", w / 2, h / 2, emptyPaint)
            return
        }

        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val span = if (max - min < 1e-9) 1.0 else max - min
        val bins = maxOf(1, ceil(log2(values.size.toDouble())).toInt() + 1)
        val counts = IntArray(bins)
        values.forEach { v ->
            var b = ((v - min) / span * bins).toInt()
            if (b >= bins) b = bins - 1
            if (b < 0) b = 0
            counts[b]++
        }
        val maxCount = (counts.maxOrNull() ?: 1).toFloat()

        val plotW = w - padL - padR
        val plotH = h - padT - padB
        val barW = plotW / bins

        for (b in 0 until bins) {
            val barH = counts[b] / maxCount * plotH
            val left = padL + b * barW + 2f
            val right = padL + (b + 1) * barW - 2f
            val top = padT + plotH - barH
            canvas.drawRect(left, top, right, padT + plotH, barPaint)
            if (bins <= 12) {
                val edge = min + span * b / bins
                canvas.drawText(
                    Stats.formatNum(edge),
                    padL + b * barW,
                    padT + plotH + 34f,
                    textPaint
                )
            }
        }

        // axes
        canvas.drawLine(padL, padT, padL, padT + plotH, axisPaint)
        canvas.drawLine(padL, padT + plotH, w - padR, padT + plotH, axisPaint)
        // y labels: 0 and max
        canvas.drawText("0", 20f, padT + plotH + 8f, textPaint)
        canvas.drawText(maxCount.toInt().toString(), 20f, padT + 12f, textPaint)
        canvas.drawText("n=${values.size}, bins=$bins", padL, padT - 6f, textPaint)
    }
}
