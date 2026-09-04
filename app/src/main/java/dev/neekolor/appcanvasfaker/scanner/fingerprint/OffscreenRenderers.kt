package dev.neekolor.appcanvasfaker.scanner.fingerprint

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.neekolor.appcanvasfaker.util.HashUtils
import dev.neekolor.appcanvasfaker.scanner.core.StandardCanvas

/**
 * B 组：Canvas 离屏渲染出口（B1-B2）
 */
object OffscreenRenderers {

    /** B1: createBitmap + new Canvas(bitmap) 绘制后读取哈希 */
    fun createBitmapCanvas(): String {
        val bitmap = Bitmap.createBitmap(
            StandardCanvas.WIDTH,
            StandardCanvas.HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        StandardCanvas.drawContent(canvas)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return HashUtils.ofIntArray(pixels)
    }

    /** B1b: 同样离屏渲染后走 compress 编码出口 */
    fun createBitmapCanvasCompress(): String {
        val bitmap = Bitmap.createBitmap(
            StandardCanvas.WIDTH,
            StandardCanvas.HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        StandardCanvas.drawContent(canvas)
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return HashUtils.ofBytes(out.toByteArray())
    }

    /** B2: Canvas.setBitmap(bitmap) 绑定后绘制读取哈希 */
    fun setBitmapCanvas(): String {
        val bitmap = Bitmap.createBitmap(
            StandardCanvas.WIDTH,
            StandardCanvas.HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas()
        canvas.setBitmap(bitmap)
        StandardCanvas.drawContent(canvas)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return HashUtils.ofIntArray(pixels)
    }
}