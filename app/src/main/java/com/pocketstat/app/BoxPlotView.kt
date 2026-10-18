package com.pocketstat.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Original boxplot view drawn with Canvas.
 * Shows min, Q1, median, Q3, max with whiskers.
 */
class BoxPlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var values: List<Double> = emptyList()
        set(v) {
            field = v
            invalidate()
        }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4C9AD1")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14537F")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val medianPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2A541")
        style = Paint.Style.STROKE
        strokeWidth = 6f
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

        if (values.isEmpty()) {
            canvas.drawText("No data", w / 2, h / 2, emptyPaint)
            return
        }

        val sorted = values.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val (q1, median, q3) = Stats.quartiles(sorted)
        val span = if (max - min < 1e-9) 1.0 else max - min

        val padL = 90f
        val padR = 90f

        fun x(v: Double): Float = (padL + (v - min) / span * (w - padL - padR)).toFloat()
        val cy = h / 2
        val boxTop = cy - 55f
        val boxBot = cy + 55f

        // whiskers
        canvas.drawLine(x(min), cy, x(q1), cy, linePaint)
        canvas.drawLine(x(q3), cy, x(max), cy, linePaint)
        // caps
        canvas.drawLine(x(min), cy - 25f, x(min), cy + 25f, linePaint)
        canvas.drawLine(x(max), cy - 25f, x(max), cy + 25f, linePaint)
        // box
        canvas.drawRect(x(q1), boxTop, x(q3), boxBot, boxPaint)
        canvas.drawRect(x(q1), boxTop, x(q3), boxBot, linePaint)
        // median
        canvas.drawLine(x(median), boxTop, x(median), boxBot, medianPaint)

        // labels
        val labelY = boxBot + 44f
        canvas.drawText("min ${Stats.formatNum(min)}", x(min) - 60f, labelY, textPaint)
        canvas.drawText("Q1 ${Stats.formatNum(q1)}", x(q1) - 60f, labelY, textPaint)
        canvas.drawText("med ${Stats.formatNum(median)}", x(median) - 60f, labelY + 38f, textPaint)
        canvas.drawText("Q3 ${Stats.formatNum(q3)}", x(q3) - 60f, labelY, textPaint)
        canvas.drawText("max ${Stats.formatNum(max)}", x(max) - 60f, labelY, textPaint)
        canvas.drawText("n=${values.size}", padL, 40f, textPaint)
    }
}
