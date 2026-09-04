package dev.neekolor.appcanvasfaker.scanner.fingerprint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Locale
import dev.neekolor.appcanvasfaker.util.HashUtils

/**
 * A 组：Bitmap 像素读取出口（A1-A6）
 */
object PixelReaders {

    /** A1: Bitmap.getPixels() 整幅读入 IntArray 后哈希 */
    fun getPixels(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return HashUtils.ofIntArray(pixels)
    }

    /** A2: Bitmap.getPixel(x,y) 单点采样拼接后哈希（按位图实际尺寸比例取 4 个固定采样点） */
    fun getPixel(bitmap: Bitmap, points: List<Pair<Int, Int>> = defaultPoints(bitmap.width, bitmap.height)): String {
        val sb = StringBuilder()
        for ((x, y) in points) {
            val safeX = x.coerceIn(0, bitmap.width - 1)
            val safeY = y.coerceIn(0, bitmap.height - 1)
            sb.append(String.format(Locale.ROOT, "%08x", bitmap.getPixel(safeX, safeY)))
        }
        return HashUtils.ofString(sb.toString())
    }

    /** A3: copyPixelsToBuffer() 灌入 ByteBuffer 后哈希 */
    fun copyPixelsToBuffer(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        val buffer = java.nio.IntBuffer.wrap(pixels)
        bitmap.copyPixelsToBuffer(buffer)
        return HashUtils.ofIntArray(pixels)
    }

    /** A4: Bitmap.compress() 压成 PNG/JPEG 字节流后哈希 */
    fun compressPng(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return HashUtils.ofBytes(out.toByteArray())
    }

    /** A4b: Bitmap.compress() JPEG 形式 */
    fun compressJpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return HashUtils.ofBytes(out.toByteArray())
    }

    /** A5: Bitmap.copy() 克隆副本后哈希 */
    fun copyClone(bitmap: Bitmap): String {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(copy.width * copy.height)
        copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
        copy.recycle()
        return HashUtils.ofIntArray(pixels)
    }

    /** A5b: Bitmap.createBitmap(源) 派生副本后哈希 */
    fun createBitmapFrom(bitmap: Bitmap): String {
        val derived = Bitmap.createBitmap(bitmap)
        val pixels = IntArray(derived.width * derived.height)
        derived.getPixels(pixels, 0, derived.width, 0, 0, derived.width, derived.height)
        derived.recycle()
        return HashUtils.ofIntArray(pixels)
    }

    /** A6: copyPixelsFromBuffer() 灌入 Buffer 后，真正从 Bitmap 读回像素再哈希 */
    fun copyPixelsFromBuffer(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        val out = java.nio.IntBuffer.wrap(pixels)
        bitmap.copyPixelsToBuffer(out)
        out.rewind()
        bitmap.copyPixelsFromBuffer(out)
        // 关键：从 bitmap 读回（而非哈希中间数组），否则本探针与 A3 永远同值、
        // 无法检测 copyPixelsFromBuffer 出口被 hook 后的差异
        val readBack = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(readBack, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return HashUtils.ofIntArray(readBack)
    }

    /** A4 辅助：字节流解码回 Bitmap 的哈希（验证编码-解码往返一致性） */
    fun compressRoundTrip(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val decoded = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        val pixels = IntArray(decoded.width * decoded.height)
        decoded.getPixels(pixels, 0, decoded.width, 0, 0, decoded.width, decoded.height)
        decoded.recycle()
        return HashUtils.ofIntArray(pixels)
    }

    private fun defaultPoints(width: Int, height: Int): List<Pair<Int, Int>> = listOf(
        (width / 4) to (height / 4),
        (width / 2) to (height / 2),
        (3 * width / 4) to (3 * height / 4),
        1 to 1
    )
}