package dev.neekolor.appcanvasfaker.scanner.fingerprint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Parcel
import dev.neekolor.appcanvasfaker.util.HashUtils
import dev.neekolor.appcanvasfaker.scanner.core.StandardCanvas
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * E 组：非像素类信号（E1-E5）
 */
object NonPixelSignals {

    /** E1: Paint.measureText() / breakText() / getFontMetrics() 字体度量指纹 */
    fun fontMetrics(): String {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f * StandardCanvas.DENSITY }
        val metrics = paint.fontMetrics
        val text = StandardCanvas.TEXT
        val advance = paint.measureText(text)
        val breakCount = paint.breakText(text, true, 150f, null)
        val raw = listOf(
            advance,
            breakCount.toFloat(),
            metrics.ascent,
            metrics.descent,
            metrics.top,
            metrics.bottom,
            paint.getFontMetricsInt().toString()
        ).joinToString("|")
        return HashUtils.ofString(raw)
    }

    /** E2: Bitmap 元数据（宽/高/Config/ColorSpace/字节数） */
    fun bitmapMetadata(bitmap: Bitmap): String {
        val raw = listOf(
            bitmap.width, bitmap.height,
            bitmap.config?.name ?: "null",
            bitmap.colorSpace?.name ?: "null",
            bitmap.allocationByteCount,
            bitmap.rowBytes,
            bitmap.density
        ).joinToString("|")
        return HashUtils.ofString(raw)
    }

    /** E3: Canvas.isHardwareAccelerated() 渲染环境信号 */
    fun hardwareAcceleration(): String {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hw = canvas.isHardwareAccelerated
        val result = HashUtils.ofString("hw=$hw,api=${android.os.Build.VERSION.SDK_INT}")
        bitmap.recycle()
        return result
    }

    /** E4: BitmapRegionDecoder 分块解码区域像素后哈希 */
    fun regionDecoder(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        return try {
            val decoder = BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes), false)
            if (decoder == null) return "解码器创建失败"
            val region = Rect(
                0, 0,
                minOf(StandardCanvas.WIDTH / 2, decoder.width),
                minOf(StandardCanvas.HEIGHT / 2, decoder.height)
            )
            val cropped = decoder.decodeRegion(region, null)
            val pixels = IntArray(cropped.width * cropped.height)
            cropped.getPixels(pixels, 0, cropped.width, 0, 0, cropped.width, cropped.height)
            cropped.recycle()
            decoder.recycle()
            HashUtils.ofIntArray(pixels)
        } catch (e: Throwable) {
            "失败: ${e.javaClass.simpleName}"
        }
    }

    /** E5: Parcel 序列化出口 —— 像素数据经 Parcel 打包后哈希 */
    fun parcelSerialize(bitmap: Bitmap): String {
        return try {
            val parcel = Parcel.obtain()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            parcel.writeInt(bitmap.width)
            parcel.writeInt(bitmap.height)
            parcel.writeString(bitmap.config?.name)
            parcel.writeIntArray(pixels)
            val bytes = parcel.marshall()
            parcel.recycle()
            HashUtils.ofBytes(bytes)
        } catch (e: Throwable) {
            "失败: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}