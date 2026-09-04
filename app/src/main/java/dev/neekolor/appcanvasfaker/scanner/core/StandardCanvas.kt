package dev.neekolor.appcanvasfaker.scanner.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 统一标准画布：所有指纹采集方法共用同一张画布，
 * 保证各采集项结果的哈希可比对。
 */
object StandardCanvas {
    const val WIDTH = 320
    const val HEIGHT = 160
    const val TEXT = "CanvasFingerprintScanner 0.1.0"
    const val DENSITY = 1f

    fun createBitmap(): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
            drawContent(Canvas(this))
        }

    fun drawContent(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f * DENSITY
            strokeWidth = 2f * DENSITY
        }

        canvas.drawColor(Color.rgb(18, 20, 32))
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(130, 177, 255)
        canvas.drawRoundRect(RectF(18f, 18f, 154f, 84f), 18f, 18f, paint)
        paint.color = Color.rgb(255, 171, 145)
        canvas.drawCircle(238f, 54f, 34f, paint)
        paint.color = Color.rgb(185, 246, 202)
        canvas.drawText(TEXT, 20f, 124f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(206, 147, 216)
        canvas.drawPath(Path().apply {
            moveTo(184f, 112f)
            cubicTo(206f, 40f, 252f, 150f, 294f, 86f)
        }, paint)
    }
}
