package dev.neekolor.appcanvasfaker.scanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import dev.neekolor.appcanvasfaker.scanner.core.StandardCanvas

/**
 * 小型自绘 View：绘制与标准画布相同的内容，
 * 用于 C1 (buildDrawingCache) 等视图抓取采集（避免对大 Compose 根视图采集导致 ANR）。
 */
class ProbeView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 尊重 MeasureSpec mode：EXACTLY 直接采用，AT_MOST/UNSPECIFIED 取内容期望尺寸
        val w = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(StandardCanvas.WIDTH)
            else -> StandardCanvas.WIDTH
        }
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(heightMeasureSpec).coerceAtMost(StandardCanvas.HEIGHT)
            else -> StandardCanvas.HEIGHT
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        StandardCanvas.drawContent(canvas)
    }
}