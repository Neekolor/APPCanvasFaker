package dev.neekolor.appcanvasfaker.scanner.fingerprint

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import dev.neekolor.appcanvasfaker.util.HashUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * C 组：视图/屏幕抓取出口（C1-C2）
 */
object ViewCapturers {

    /**
     * C1: View.buildDrawingCache() + getDrawingCache() 哈希
     * 注意：buildDrawingCache 是软件渲染，仅适用于小型/普通 View；
     * 对巨大视图（如全屏 Compose 根）会极慢触发 ANR。
     * 此处仅对传入的小型 View 调用。
     */
    fun buildDrawingCache(view: View): String {
        return try {
            if (view.width <= 0 || view.height <= 0) return "不可用(未布局)"
            view.buildDrawingCache()
            val cached = view.getDrawingCache()
            if (cached == null) return "不可用(无缓存)"
            val pixels = IntArray(cached.width * cached.height)
            cached.getPixels(pixels, 0, cached.width, 0, 0, cached.width, cached.height)
            view.destroyDrawingCache()
            HashUtils.ofIntArray(pixels)
        } catch (e: Throwable) {
            "异常: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * C1b: 离屏构建 ProbeView（不显示在 UI 中），手动 measure/layout 后
     * buildDrawingCache。用于 UI 不再嵌入 AndroidView 的场景。
     */
    fun buildOffscreen(activity: Activity): String {
        return try {
            val v = dev.neekolor.appcanvasfaker.scanner.ui.ProbeView(activity)
            val density = activity.resources.displayMetrics.density
            val w = (320f * density).toInt()
            val h = (160f * density).toInt()
            v.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
            )
            v.layout(0, 0, w, h)
            buildDrawingCache(v)
        } catch (e: Throwable) {
            "异常: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * C2: PixelCopy.request() 从 Surface 拷贝帧缓冲后哈希（异步）
     * 拷贝整个窗口（宽度取屏幕宽，高度取 ProbeView 区域高度）。
     */
    suspend fun pixelCopy(activity: Activity, width: Int, height: Int): String = suspendCancellableCoroutine { cont ->
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())

        PixelCopy.request(activity.window, bitmap, { result ->
            val hash = if (result == PixelCopy.SUCCESS) {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                HashUtils.ofIntArray(pixels)
            } else {
                "失败(PixelCopy.$result)"
            }
            bitmap.recycle()
            if (cont.isActive) cont.resume(hash)
        }, handler)
    }
}