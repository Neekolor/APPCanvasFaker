package dev.nikko.appcanvasfaker.hook

import dev.nikko.appcanvasfaker.util.HookLog
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import dev.nikko.appcanvasfaker.core.FingerprintEngine
import dev.nikko.appcanvasfaker.core.ProtectionMode
import dev.nikko.appcanvasfaker.util.HashUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 三个 Hook 的实现：A1 getPixels / A3 copyPixelsToBuffer / A4+A4b compress。
 * 递归保护：ThreadLocal 标志，compress 内层 fake.compress 直接 proceed 放行。
 */
object BitmapHooks {

    private const val TAG = "ACF-Hook"

    // compress 内部递归标志：置位时内层 getPixels/compress 放行，避免死循环
    private val insideFake = ThreadLocal<Boolean>()

    // 统计节流：每包距上次记录 <1s 跳过（哈希+跨进程 call 放后台，允许轻度丢失）
    private const val STATS_MIN_INTERVAL_MS = 1000L
    private val lastStatsTime = ConcurrentHashMap<String, Long>()

    fun install(
        module: XposedInterface,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        context: Context?,
        enableLogging: Boolean,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        val bitmapClass = param.defaultClassLoader.loadClass("android.graphics.Bitmap")

        // A1 Bitmap.getPixels(int[], int offset, int stride, int x, int y, int width, int height)
        val getPixels = bitmapClass.getDeclaredMethod(
            "getPixels",
            IntArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        module.hook(getPixels).intercept { chain ->
            handleGetPixels(chain, packageName, mode, seed, context, enableLogging)
        }

        // A3 Bitmap.copyPixelsToBuffer(java.nio.Buffer dst)
        val copyPixelsToBuffer = bitmapClass.getDeclaredMethod("copyPixelsToBuffer", Buffer::class.java)
        module.hook(copyPixelsToBuffer).intercept { chain ->
            handleCopyPixelsToBuffer(chain, packageName, mode, seed, context, enableLogging)
        }

        // A4/A4b Bitmap.compress(CompressFormat format, int quality, OutputStream stream)
        val compress = bitmapClass.getDeclaredMethod(
            "compress",
            Bitmap.CompressFormat::class.java,
            Int::class.javaPrimitiveType,
            OutputStream::class.java
        )
        module.hook(compress).intercept { chain ->
            handleCompress(chain, packageName, mode, seed, context, enableLogging)
        }
    }

    /** A1：void native，先 proceed 再改像素数组（索引 offset + row*stride + col）。 */
    private fun handleGetPixels(
        chain: XposedInterface.Chain,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        context: Context?,
        enableLogging: Boolean
    ): Any? {
        var proceeded = false
        try {
            val pixels = chain.getArg(0) as IntArray
            val offset = chain.getArg(1) as Int
            val stride = chain.getArg(2) as Int
            val x = chain.getArg(3) as Int
            val y = chain.getArg(4) as Int
            val width = chain.getArg(5) as Int
            val height = chain.getArg(6) as Int
            proceeded = true
            // 原生异常（如 recycled bitmap）不捕获、不吞——保持与未装 Hook 一致的宿主语义
            chain.proceed()
            runCatching {
                // 传位图绝对坐标 (x, y)，保证子区域读取与整图读取对同一物理像素扰动一致
                FingerprintEngine.applyPixels(pixels, width, height, offset, stride, x, y, mode, seed)
            }.onFailure { Log.e(TAG, "A1 applyPixels failed for $packageName", it) }
            // compress 内部读取像素时也触发本 hook（天然一致），但统计只在最外层记一次
            if (insideFake.get() != true) {
                recordStats(packageName, mode, seed, pixels, context, enableLogging)
            }
        } catch (t: Throwable) {
            // 仅可能来自参数读取或原生调用本身：确保原方法已执行后原样上抛，绝不静默吞掉
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
        return null
    }

    /** A3：void native，proceed 后先从 buffer 读回原始像素做伪装基数，再写回；仅处理 ARGB_8888。 */
    private fun handleCopyPixelsToBuffer(
        chain: XposedInterface.Chain,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        context: Context?,
        enableLogging: Boolean
    ): Any? {
        var proceeded = false
        try {
            val bmp = chain.getThisObject() as Bitmap
            val w = bmp.width
            val h = bmp.height
            val dst = chain.getArg(0) as Buffer
            val startPos = dst.position()
            proceeded = true
            // 原生异常（如 buffer 容量不足）不吞——保持与未装 Hook 一致的宿主语义
            chain.proceed()
            if (w <= 0 || h <= 0) return null
            // 非 ARGB_8888 的字节布局不同（RGB_565 2B/px、RGBA_F16 8B/px），int 视图覆写会破坏数据：
            // 宁可放过不伪装，也不写坏调用方持有的 buffer
            if (bmp.config != Bitmap.Config.ARGB_8888) {
                Log.w(TAG, "A3 skip non-ARGB_8888 config=${bmp.config}")
                return null
            }
            val fake = IntArray(w * h)
            // 读回原始像素（与 A1 同基数，才能得出完全一致的伪装结果）
            val len = when (dst) {
                is ByteBuffer -> {
                    dst.position(startPos)
                    val view = dst.asIntBuffer() // 视图与底 buffer 共享内容、position 独立
                    val l = minOf(fake.size, view.remaining())
                    view.get(fake, 0, l)
                    l
                }
                is IntBuffer -> {
                    dst.position(startPos)
                    val l = minOf(fake.size, dst.remaining())
                    dst.get(fake, 0, l)
                    l
                }
                else -> 0
            }
            if (len > 0) {
                runCatching {
                    // 绝对坐标 originX=0, originY=0（A3 读的是整图）
                    FingerprintEngine.applyPixels(fake, w, h, 0, w, 0, 0, mode, seed)
                }.onFailure { Log.e(TAG, "A3 applyPixels failed for $packageName", it) }
                when (dst) {
                    is ByteBuffer -> {
                        val view = dst.asIntBuffer() // 新视图 base=startPos、position=0
                        view.put(fake, 0, len)
                        // 恢复 dst position 为 proceed 后应有的位置（数据末尾）
                        dst.position((startPos + len * 4).coerceAtMost(dst.limit()))
                    }
                    is IntBuffer -> {
                        dst.position(startPos)
                        dst.put(fake, 0, len)
                    }
                }
                if (insideFake.get() != true) {
                    recordStats(packageName, mode, seed, fake, context, enableLogging)
                }
            }
        } catch (t: Throwable) {
            // 确保原方法已执行后原样上抛，绝不静默吞掉
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
        return null
    }

    /** A4/A4b：吞掉原编码，构造伪装位图自行编码；内层递归直接 proceed 放行。 */
    private fun handleCompress(
        chain: XposedInterface.Chain,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        context: Context?,
        enableLogging: Boolean
    ): Any? {
        if (insideFake.get() == true) {
            return chain.proceed()
        }
        insideFake.set(true)
        try {
            return compressFake(chain, packageName, mode, seed, context, enableLogging)
        } finally {
            insideFake.remove()
        }
    }

    /**
     * 先在内存完成伪装编码，成功后才一次性写入目标流；
     * 任一环节失败则回退原始编码（整条链上 proceed 至多一次），
     * 杜绝"半截伪製数据已写入流 + 回退再写原始数据"造成的输出损坏。
     */
    private fun compressFake(
        chain: XposedInterface.Chain,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        context: Context?,
        enableLogging: Boolean
    ): Boolean {
        val format = chain.getArg(0) as Bitmap.CompressFormat
        val quality = chain.getArg(1) as Int
        val stream = chain.getArg(2) as OutputStream
        val bmp = chain.getThisObject() as Bitmap
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) {
            return proceedRaw(chain)
        }
        val faked = runCatching {
            val pixels = IntArray(w * h)
            // 读原始像素：内部 getPixels 会触发 A1 hook → 拿到伪装像素（天然一致）
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            // receiver 可能是 HARDWARE 等 config，必须新建 software ARGB_8888
            val fake = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            fake.setPixels(pixels, 0, w, 0, 0, w, h)
            try {
                val buf = ByteArrayOutputStream()
                if (!fake.compress(format, quality, buf)) return@runCatching null
                pixels to buf.toByteArray()
            } finally {
                runCatching { fake.recycle() }
            }
        }.getOrNull()

        if (faked != null) {
            // 写入失败（对端流问题）时直接上抛交由调用方感知——此时绝不能再回退重写
            stream.write(faked.second)
            recordStats(packageName, mode, seed, faked.first, context, enableLogging)
            return true
        }
        // 伪装失败（如 HARDWARE 位图读不出像素）：按原样执行原始编码，
        // 原生异常语义与未装 Hook 时保持一致
        HookLog.i(TAG, "compress fake unavailable for $packageName, fallback to raw")
        return proceedRaw(chain)
    }

    private fun proceedRaw(chain: XposedInterface.Chain): Boolean = chain.proceed() as Boolean

    /**
     * 统计：每包 1 秒节流 + 共享后台线程执行（哈希 + 跨进程 call），不拖目标 App 主线程。
     * 使用进程级共享的守护线程，避免每次统计都创建/销毁线程。
     */
    private val statsExecutor: java.util.concurrent.ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "acf-stats").apply { isDaemon = true }
        }

    private fun recordStats(
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        pixels: IntArray,
        context: Context?,
        enableLogging: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        val last = lastStatsTime.put(packageName, now)
        if (last != null && now - last < STATS_MIN_INTERVAL_MS) return
        runCatching {
            statsExecutor.execute {
                runCatching {
                    val fp = fingerprintOf(pixels)
                    StatsProvider.recordHook(context, packageName, mode.name, seed, fp, enableLogging)
                }.onFailure { Log.e(TAG, "recordStats failed", it) }
            }
        }
    }

    /** 指纹哈希：超大数组抽样控制开销。 */
    private fun fingerprintOf(pixels: IntArray): String = runCatching {
        val max = 1_000_000
        if (pixels.size <= max) {
            HashUtils.ofIntArray(pixels)
        } else {
            val step = pixels.size / max
            val sample = IntArray(max)
            var i = 0
            var idx = 0
            while (idx < max && i < pixels.size) {
                sample[idx] = pixels[i]
                idx++
                i += step
            }
            HashUtils.ofIntArray(sample)
        }
    }.getOrDefault("unknown")
}