package dev.neekolor.appcanvasfaker.hook

import dev.neekolor.appcanvasfaker.util.HookLog
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import dev.neekolor.appcanvasfaker.core.FingerprintEngine
import dev.neekolor.appcanvasfaker.core.ProtectionMode
import dev.neekolor.appcanvasfaker.core.RemoteConfig
import dev.neekolor.appcanvasfaker.util.HashUtils
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
 * Hook 链实现：
 * - A1 getPixels / A3 copyPixelsToBuffer / A4+A4b compress（v0.5.0 起现役）
 * - H-01 Bitmap.getPixel 单点读取（scanner A2）
 * - H-05 Paint 文本度量族 18 重载（scanner E1，含 getTextWidths 系与 getFontMetricsInt）
 * - H-02 GLES20.glReadPixels GPU 帧缓冲直读（scanner D1，默认关）
 * - C2 PixelCopy.request 监听器包装（scanner C2 延迟持有例外，默认开）
 * 递归保护：ThreadLocal 标志，compress 内层 fake.compress 直接 proceed 放行。
 * 新增三链均为热路径：不做跨进程统计、不加锁、不逐次打日志，
 * 仅做纯数学扰动；原生异常语义与宿主一致（proceed 失败原样上抛）。
 */
object BitmapHooks {

    private const val TAG = "ACF-Hook"

    private const val GL_RGBA = 0x1908
    private const val GL_UNSIGNED_BYTE = 0x1401

    /** H-05 文本哈希采样上限：长文本只混入前 N 字符 + 总长，控制热路径开销且保持确定性。 */
    private const val TEXT_SAMPLE_CHARS = 48

    // compress 内部递归标志：置位时内层 getPixels/compress 放行，避免死循环
    private val insideFake = ThreadLocal<Boolean>()

    // 统计节流：每包距上次记录 <1s 跳过（哈希 + 远端写放后台，允许轻度丢失）
    private const val STATS_MIN_INTERVAL_MS = 1000L
    private const val MAX_REMOTE_LOGS = 1000
    private val lastStatsTime = ConcurrentHashMap<String, Long>()

    fun install(
        module: XposedInterface,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        remotePrefs: SharedPreferences?,
        enableLogging: Boolean,
        param: XposedModuleInterface.PackageLoadedParam,
        hookGetPixel: Boolean,
        hookTextMetrics: Boolean,
        hookGlReadPixels: Boolean,
        hookPixelCopy: Boolean
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
            handleGetPixels(chain, packageName, mode, seed, remotePrefs, enableLogging)
        }

        // A3 Bitmap.copyPixelsToBuffer(java.nio.Buffer dst)
        val copyPixelsToBuffer = bitmapClass.getDeclaredMethod("copyPixelsToBuffer", Buffer::class.java)
        module.hook(copyPixelsToBuffer).intercept { chain ->
            handleCopyPixelsToBuffer(chain, packageName, mode, seed, remotePrefs, enableLogging)
        }

        // A4/A4b Bitmap.compress(CompressFormat format, int quality, OutputStream stream)
        val compress = bitmapClass.getDeclaredMethod(
            "compress",
            Bitmap.CompressFormat::class.java,
            Int::class.javaPrimitiveType,
            OutputStream::class.java
        )
        module.hook(compress).intercept { chain ->
            handleCompress(chain, packageName, mode, seed, remotePrefs, enableLogging)
        }

        // H-01 Bitmap.getPixel(int x, int y)：单点读取逃逸口（scanner A2）
        if (hookGetPixel) {
            runCatching {
                val getPixel = bitmapClass.getDeclaredMethod(
                    "getPixel",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                module.hook(getPixel).intercept { chain ->
                    handleGetPixel(chain, packageName, seed)
                }
            }.onFailure { Log.w(TAG, "H-01 hook getPixel unavailable", it) }
        }

        // H-05 Paint 文本度量族（scanner E1）：逐重载独立注册，缺失的重载静默跳过
        if (hookTextMetrics) {
            installTextMetricHooks(module, packageName, seed, param)
        }

        // H-02 GLES20.glReadPixels：GPU 直读旁路（scanner D1），默认关、副作用自负
        if (hookGlReadPixels) {
            runCatching {
                val gles20 = param.defaultClassLoader.loadClass("android.opengl.GLES20")
                val glReadPixels = gles20.getDeclaredMethod(
                    "glReadPixels",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Buffer::class.java
                )
                module.hook(glReadPixels).intercept { chain ->
                    handleGlReadPixels(chain, packageName, seed)
                }
            }.onFailure { Log.w(TAG, "H-02 hook glReadPixels unavailable", it) }
        }

        // C2 PixelCopy.request 系列：拷贝完成监听器包装（scanner C2 延迟持有例外）
        if (hookPixelCopy) {
            installPixelCopyHooks(module, packageName, param)
        }
    }

    /** H-05：注册 Paint 度量族 Java 层重载（measureText×4 / getTextBounds×2 /
     *  getFontMetrics×2 / breakText×4 / getTextWidths×4 / getFontMetricsInt×2）。
     *  单个重载缺失不影响其余安装；每个重载绑定独立的"文本哈希提取器"。 */
    private fun installTextMetricHooks(
        module: XposedInterface,
        packageName: String,
        seed: Long,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        val paintClass = param.defaultClassLoader.loadClass("android.graphics.Paint")
        val tInt: Class<*> = Int::class.javaPrimitiveType!!
        val tFloat: Class<*> = Float::class.javaPrimitiveType!!
        val tBool: Class<*> = Boolean::class.javaPrimitiveType!!

        fun hook(name: String, handler: (XposedInterface.Chain) -> Any?, vararg types: Class<*>) {
            runCatching {
                val m = paintClass.getDeclaredMethod(name, *types)
                module.hook(m).intercept { chain -> handler(chain) }
            }.onFailure { Log.w(TAG, "H-05 hook $name unavailable", it) }
        }

        // measureText ×4
        hook("measureText", { c ->
            handleFloatMetric(c, packageName, seed) { ch ->
                val t = ch.getArg(0) as? String
                textHashOf(t, 0, t?.length ?: 0)
            }
        }, String::class.java)
        hook("measureText", { c ->
            handleFloatMetric(c, packageName, seed) { ch ->
                textHashOf(ch.getArg(0) as? String, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, String::class.java, tInt, tInt)
        hook("measureText", { c ->
            handleFloatMetric(c, packageName, seed) { ch ->
                textHashOf(ch.getArg(0) as? CharSequence, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharSequence::class.java, tInt, tInt)
        hook("measureText", { c ->
            handleFloatMetric(c, packageName, seed) { ch ->
                textHashOf(ch.getArg(0) as? CharArray, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharArray::class.java, tInt, tInt)

        // getTextBounds ×2（void 出参 Rect 固定在下标 3）
        hook("getTextBounds", { c ->
            handleBoundsMetric(c, packageName, seed) { ch ->
                textHashOf(ch.getArg(0) as? String, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, String::class.java, tInt, tInt, android.graphics.Rect::class.java)
        hook("getTextBounds", { c ->
            handleBoundsMetric(c, packageName, seed) { ch ->
                textHashOf(ch.getArg(0) as? CharArray, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharArray::class.java, tInt, tInt, android.graphics.Rect::class.java)

        // getFontMetrics ×2（度量族无文本输入，因子仅由 seed + textSize 决定）
        hook("getFontMetrics", { c -> handleFontMetricsNew(c, packageName, seed) })
        hook("getFontMetrics", { c -> handleFontMetricsInto(c, packageName, seed) },
            android.graphics.Paint.FontMetrics::class.java)

        // breakText ×4（measuredWidth 数组下标各异；返回的字符计数不动，避免调用方索引错乱）
        hook("breakText", { c ->
            handleBreakText(c, packageName, seed, 4) { ch ->
                textHashOf(ch.getArg(0) as? CharArray, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharArray::class.java, tInt, tInt, tFloat, FloatArray::class.java)
        hook("breakText", { c ->
            handleBreakText(c, packageName, seed, 3) { ch ->
                val t = ch.getArg(0) as? String
                textHashOf(t, 0, t?.length ?: 0)
            }
        }, String::class.java, tBool, tFloat, FloatArray::class.java)
        hook("breakText", { c ->
            handleBreakText(c, packageName, seed, 5) { ch ->
                textHashOf(ch.getArg(0) as? String, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, String::class.java, tInt, tInt, tBool, tFloat, FloatArray::class.java)
        hook("breakText", { c ->
            handleBreakText(c, packageName, seed, 5) { ch ->
                textHashOf(ch.getArg(0) as? CharSequence, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharSequence::class.java, tInt, tInt, tBool, tFloat, FloatArray::class.java)

        // getTextWidths ×4（返回的字符计数不动，只缩放输出数组各元）
        hook("getTextWidths", { c ->
            handleWidthsMetric(c, packageName, seed, 1) { ch ->
                val t = ch.getArg(0) as? String
                textHashOf(t, 0, t?.length ?: 0)
            }
        }, String::class.java, FloatArray::class.java)
        hook("getTextWidths", { c ->
            handleWidthsMetric(c, packageName, seed, 3) { ch ->
                textHashOf(ch.getArg(0) as? String, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, String::class.java, tInt, tInt, FloatArray::class.java)
        hook("getTextWidths", { c ->
            handleWidthsMetric(c, packageName, seed, 3) { ch ->
                textHashOf(ch.getArg(0) as? CharArray, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharArray::class.java, tInt, tInt, FloatArray::class.java)
        hook("getTextWidths", { c ->
            handleWidthsMetric(c, packageName, seed, 3) { ch ->
                textHashOf(ch.getArg(0) as? CharSequence, ch.getArg(1) as Int, ch.getArg(2) as Int)
            }
        }, CharSequence::class.java, tInt, tInt, FloatArray::class.java)

        // getFontMetricsInt ×2（度量族无文本输入，因子仅由 seed + textSize 决定；leading 不动）
        hook("getFontMetricsInt", { c -> handleFontMetricsIntNew(c, packageName, seed) })
        hook("getFontMetricsInt", { c -> handleFontMetricsIntInto(c, packageName, seed) },
            android.graphics.Paint.FontMetricsInt::class.java)
    }

    // ---------- H-01 / H-02 / H-05 处理器 ----------

    /**
     * H-01：int native，proceed 后按位图绝对坐标施加单点扰动。
     * 与 A1 同源噪声算法（stableNoise(seed, y, x)），保证坐标绑定性质：
     * 整图读取与单点读取对同一物理像素产生一致扰动。
     */
    private fun handleGetPixel(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long
    ): Any? {
        var proceeded = false
        try {
            val x = chain.getArg(0) as Int
            val y = chain.getArg(1) as Int
            proceeded = true
            val original = chain.proceed() as Int
            // 热路径：纯数学扰动，不做统计/加锁/逐次日志
            return FingerprintEngine.perturbPoint(original, x, y, seed)
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /**
     * H-02：void native，proceed 后对 RGBA/UNSIGNED_BYTE 帧缓冲数据按像素序号均匀扰动。
     * GLES30 PBO 异步路径在 Java 层无数据可拦，属已知理论上限（见评估报告 v2.1）。
     */
    private fun handleGlReadPixels(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long
    ): Any? {
        var proceeded = false
        try {
            val width = chain.getArg(2) as Int
            val height = chain.getArg(3) as Int
            val format = chain.getArg(4) as Int
            val type = chain.getArg(5) as Int
            val buffer = chain.getArg(6) as Buffer
            val startPos = (buffer as? java.nio.ByteBuffer)?.position() ?: 0
            proceeded = true
            // 原生异常（非直接缓冲等）不吞——保持宿主语义
            chain.proceed()
            if (format == GL_RGBA && type == GL_UNSIGNED_BYTE && buffer is java.nio.ByteBuffer) {
                runCatching {
                    FingerprintEngine.applyGlPixels(buffer, startPos, width, height, seed)
                }.onFailure { Log.e(TAG, "H-02 glReadPixels apply failed for $packageName", it) }
            }
            return null
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** H-05 浮点度量通用处理：proceed 后按确定性因子缩放。失败时回退原值不破坏布局。 */
    private fun handleFloatMetric(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long,
        hashOf: (XposedInterface.Chain) -> Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val original = chain.proceed() as Float
            val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, hashOf(chain))
            return FingerprintEngine.scaleMetric(original, factor)
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** H-05 getTextBounds：出参 Rect 以左上角为锚点确定性缩放宽高。 */
    private fun handleBoundsMetric(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long,
        hashOf: (XposedInterface.Chain) -> Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            chain.proceed()
            val rect = chain.getArg(3) as? android.graphics.Rect ?: return null
            val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, hashOf(chain))
            runCatching { FingerprintEngine.scaleBounds(rect, factor) }
                .onFailure { Log.e(TAG, "H-05 getTextBounds scale failed for $packageName", it) }
            return null
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** H-05 getFontMetrics()：新建 FontMetrics 的四个主字段统一缩放（leading 不动）。 */
    private fun handleFontMetricsNew(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val fm = chain.proceed() as? android.graphics.Paint.FontMetrics ?: return null
            val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, 0L)
            scaleFontMetrics(fm, factor)
            return fm
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** H-05 getFontMetrics(FontMetrics)：填充式重载，字段与返回值一并缩放保持自洽。 */
    private fun handleFontMetricsInto(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val original = chain.proceed() as Float
            val fm = chain.getArg(0) as? android.graphics.Paint.FontMetrics
            val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, 0L)
            if (fm != null) scaleFontMetrics(fm, factor)
            return FingerprintEngine.scaleMetric(original, factor)
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /**
     * H-05 getTextWidths：只缩放输出数组各元，返回的字符计数保持原值——
     * 与 breakText 同理，计数扰动可能引发调用方按错误下标切片；空数组直接返回。
     */
    private fun handleWidthsMetric(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long,
        widthsIndex: Int,
        hashOf: (XposedInterface.Chain) -> Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val count = chain.proceed() as Int
            val widths = chain.getArg(widthsIndex) as? FloatArray
            if (widths != null && widths.isNotEmpty()) {
                val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, hashOf(chain))
                FingerprintEngine.scaleWidths(widths, factor)
            }
            return count
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** H-05 getFontMetricsInt()：新建整数度量的四个主字段同比缩放（leading 不动）。 */
    private fun handleFontMetricsIntNew(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val fm = chain.proceed() as? android.graphics.Paint.FontMetricsInt ?: return null
            val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, 0L)
            FingerprintEngine.scaleFontMetricsInt(fm, factor)
            return fm
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** H-05 getFontMetricsInt(FontMetricsInt)：填充式重载，字段与返回值一并缩放保持自洽。 */
    private fun handleFontMetricsIntInto(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val original = chain.proceed() as Int
            val fm = chain.getArg(0) as? android.graphics.Paint.FontMetricsInt
            val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, 0L)
            if (fm != null) FingerprintEngine.scaleFontMetricsInt(fm, factor)
            return FingerprintEngine.scaleIntMetric(original, factor)
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /**
     * H-05 breakText：只改写 measuredWidth[0]，返回的可容纳字符数保持原值——
     * 计数若被扰动可能引发调用方按错误下标切片，风险不可控；宽度偏差 ≤0.7% 无感知。
     */
    private fun handleBreakText(
        chain: XposedInterface.Chain,
        packageName: String,
        seed: Long,
        measuredWidthIndex: Int,
        hashOf: (XposedInterface.Chain) -> Long
    ): Any? {
        var proceeded = false
        try {
            proceeded = true
            val count = chain.proceed() as Int
            val mw = chain.getArg(measuredWidthIndex) as? FloatArray
            if (mw != null && mw.isNotEmpty()) {
                val factor = metricFactor(chain.getThisObject() as? android.graphics.Paint, seed, hashOf(chain))
                runCatching { mw[0] = FingerprintEngine.scaleMetric(mw[0], factor) }
                    .onFailure { Log.e(TAG, "H-05 breakText scale failed for $packageName", it) }
            }
            return count
        } catch (t: Throwable) {
            if (!proceeded) runCatching { chain.proceed() }
            throw t
        }
    }

    /** 度量因子计算统一入口：paint/textSize 取不到时回退 0f（即原值直通）。 */
    private fun metricFactor(paint: android.graphics.Paint?, seed: Long, textHash: Long): Float =
        if (paint == null) 0f else runCatching {
            FingerprintEngine.textFactor(seed, textHash, paint.textSize)
        }.getOrDefault(0f)

    private fun scaleFontMetrics(fm: android.graphics.Paint.FontMetrics, factor: Float) {
        fm.top = FingerprintEngine.scaleMetric(fm.top, factor)
        fm.ascent = FingerprintEngine.scaleMetric(fm.ascent, factor)
        fm.descent = FingerprintEngine.scaleMetric(fm.descent, factor)
        fm.bottom = FingerprintEngine.scaleMetric(fm.bottom, factor)
    }

    // ---------- C2 PixelCopy 监听器包装 ----------
    //
    // 回填 Bitmap 被即时标准读取时本已落入像素漏斗；此包装只补
    // "消费方长期持有引用延迟读取/跨进程传递"的例外：拷贝成功回调时把
    // 目标位图存毒一层（经 A1 同源噪声），再转发原回调。
    // 存毒后后续标准读取会再叠一层确定性扰动——C2 内容本无跨路径对齐
    // 承诺，不破坏一致性；同一目标位图重复拷贝会累积层数（确定性，只与
    // 操作序列有关）。任何失败 fail-open：只转发原回调，不破坏应用行为。

    private fun installPixelCopyHooks(
        module: XposedInterface,
        packageName: String,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        val pixelCopyClass = runCatching {
            param.defaultClassLoader.loadClass("android.view.PixelCopy")
        }.onFailure { Log.w(TAG, "C2 PixelCopy class unavailable", it) }.getOrNull() ?: return
        val listenerClass = runCatching {
            param.defaultClassLoader.loadClass("android.view.PixelCopy\$OnPixelCopiedListener")
        }.onFailure { Log.w(TAG, "C2 PixelCopy listener unavailable", it) }.getOrNull() ?: return
        val tRect = android.graphics.Rect::class.java
        val tBitmap = android.graphics.Bitmap::class.java
        val tHandler = android.os.Handler::class.java
        val surfaceView = runCatching { param.defaultClassLoader.loadClass("android.view.SurfaceView") }.getOrNull()
        val surface = runCatching { param.defaultClassLoader.loadClass("android.view.Surface") }.getOrNull()
        val window = runCatching { param.defaultClassLoader.loadClass("android.view.Window") }.getOrNull()
        // 5 个 request 重载：因版本缺失的重载静默跳过，其余独立安装
        val candidates = listOfNotNull(
            surfaceView?.let { arrayOf(it, tRect, tBitmap, listenerClass, tHandler) },
            surface?.let { arrayOf(it, tRect, tBitmap, listenerClass, tHandler) },
            surface?.let { arrayOf(it, tBitmap, listenerClass, tHandler) },
            window?.let { arrayOf(it, tRect, tBitmap, listenerClass, tHandler) },
            window?.let { arrayOf(it, tBitmap, listenerClass, tHandler) }
        )
        for (types in candidates) {
            runCatching {
                val m = pixelCopyClass.getDeclaredMethod("request", *types)
                module.hook(m).intercept { chain ->
                    handlePixelCopy(chain, packageName, listenerClass)
                }
            }.onFailure { Log.w(TAG, "C2 hook PixelCopy.request unavailable", it) }
        }
    }

    /** C2：按参数类型定位目标位图与监听器，换参送入代理监听器后继续执行。 */
    private fun handlePixelCopy(
        chain: XposedInterface.Chain,
        packageName: String,
        listenerClass: Class<*>
    ): Any? {
        val args = chain.getArgs().toMutableList()
        val bitmapIndex = args.indexOfFirst { it is android.graphics.Bitmap }
        val listenerIndex = args.indexOfFirst { listenerClass.isInstance(it) }
        val bitmap = args.getOrNull(bitmapIndex) as? android.graphics.Bitmap
        val original = args.getOrNull(listenerIndex)
        if (bitmapIndex < 0 || listenerIndex < 0 || bitmap == null || original == null) {
            return chain.proceed()
        }
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            listenerClass.classLoader, arrayOf(listenerClass)
        ) { _, method, proxyArgs ->
            if (method.name == "onPixelCopied") {
                val result = (proxyArgs?.getOrNull(0) as? Int) ?: -1
                if (result == android.view.PixelCopy.SUCCESS) {
                    runCatching { poisonPixelCopyTarget(bitmap) }
                        .onFailure { Log.e(TAG, "C2 poison failed for $packageName", it) }
                }
            }
            method.invoke(original, *(proxyArgs ?: emptyArray()))
        }
        args[listenerIndex] = proxy
        return chain.proceed(args.toTypedArray())
    }

    /**
     * C2 存毒：经标准 getPixels 读出（触发 A1 链，附带一层同源噪声）再
     * setPixels 写回，使延迟读取/跨进程传递等非标准消费同样拿到假像素。
     * 调用方负责 fail-open（回收/硬件位图、超大位图 OOM 均直接放过）。
     */
    private fun poisonPixelCopyTarget(bitmap: android.graphics.Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    // ---------- H-05 文本哈希 ----------
    //
    // 输入键的"文本内容"部分：前 [TEXT_SAMPLE_CHARS] 字符采样 + 全长混入，
    // SplitMix64 终混保证均匀分布；同输入恒同输出，不同输入高概率不同键。

    private val TEXT_HASH_SEED = 0x9E3779B97F4A7C15uL.toLong()

    private fun textHashFinalize(sampled: Long, length: Int): Long {
        var z = sampled xor (length.toLong() * 0xC2B2AE3D27D4EB4FuL.toLong())
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    private fun textHashOf(text: String?, start: Int, end: Int): Long {
        if (text == null || start < 0 || end > text.length || start >= end) return 0L
        var h = TEXT_HASH_SEED
        val limit = minOf(end, start + TEXT_SAMPLE_CHARS)
        for (i in start until limit) h = h * 31L + text[i].code.toLong()
        return textHashFinalize(h, end - start)
    }

    private fun textHashOf(text: CharSequence?, start: Int, end: Int): Long {
        if (text == null || start < 0 || end > text.length || start >= end) return 0L
        var h = TEXT_HASH_SEED
        val limit = minOf(end, start + TEXT_SAMPLE_CHARS)
        for (i in start until limit) h = h * 31L + text[i].code.toLong()
        return textHashFinalize(h, end - start)
    }

    private fun textHashOf(chars: CharArray?, index: Int, count: Int): Long {
        if (chars == null || index < 0 || count <= 0 || index + count > chars.size) return 0L
        var h = TEXT_HASH_SEED
        val limit = minOf(count, TEXT_SAMPLE_CHARS)
        for (i in 0 until limit) h = h * 31L + chars[index + i].code.toLong()
        return textHashFinalize(h, count)
    }

    /** A1：void native，先 proceed 再改像素数组（索引 offset + row*stride + col）。 */
    private fun handleGetPixels(
        chain: XposedInterface.Chain,
        packageName: String,
        mode: ProtectionMode,
        seed: Long,
        remotePrefs: SharedPreferences?,
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
                recordStats(packageName, seed, pixels, remotePrefs, enableLogging)
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
        remotePrefs: SharedPreferences?,
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
                    recordStats(packageName, seed, fake, remotePrefs, enableLogging)
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
        remotePrefs: SharedPreferences?,
        enableLogging: Boolean
    ): Any? {
        if (insideFake.get() == true) {
            return chain.proceed()
        }
        insideFake.set(true)
        try {
            return compressFake(chain, packageName, mode, seed, remotePrefs, enableLogging)
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
        remotePrefs: SharedPreferences?,
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
            recordStats(packageName, seed, faked.first, remotePrefs, enableLogging)
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
        seed: Long,
        pixels: IntArray,
        remotePrefs: SharedPreferences?,
        enableLogging: Boolean
    ) {
        if (remotePrefs == null) return
        val now = SystemClock.elapsedRealtime()
        val last = lastStatsTime.put(packageName, now)
        if (last != null && now - last < STATS_MIN_INTERVAL_MS) return
        runCatching {
            statsExecutor.execute {
                runCatching {
                    val fp = fingerprintOf(pixels)
                    writeRemoteStats(remotePrefs, packageName, seed, fp, enableLogging)
                }.onFailure { Log.e(TAG, "recordStats failed", it) }
            }
        }
    }

    /** 远端统计写：key 与 UI 侧本地同名（见 RemoteConfig），计数允许轻度丢失。 */
    private fun writeRemoteStats(
        prefs: SharedPreferences,
        packageName: String,
        seed: Long,
        fingerprint: String,
        enableLogging: Boolean
    ) {
        val timestamp = System.currentTimeMillis()
        val editor = prefs.edit()
        editor.putLong(
            RemoteConfig.pkgCount(packageName),
            prefs.getLong(RemoteConfig.pkgCount(packageName), 0L) + 1L
        )
        editor.putString(RemoteConfig.pkgHash(packageName), fingerprint)
        editor.putLong(RemoteConfig.pkgLastTime(packageName), timestamp)
        editor.putLong(
            RemoteConfig.KEY_GLOBAL_COUNT,
            prefs.getLong(RemoteConfig.KEY_GLOBAL_COUNT, 0L) + 1L
        )
        rollRemoteToday(prefs, editor)
        if (enableLogging) {
            val arr = runCatching {
                org.json.JSONArray(prefs.getString(RemoteConfig.KEY_LOGS, "[]"))
            }.getOrElse { org.json.JSONArray() }
            arr.put(
                org.json.JSONObject()
                    .put("ts", timestamp)
                    .put("level", "I")
                    .put("tag", "Hook")
                    .put("msg", packageName)
                    .put("pkg", packageName)
            )
            while (arr.length() > MAX_REMOTE_LOGS) arr.remove(0)
            editor.putString(RemoteConfig.KEY_LOGS, arr.toString())
        }
        editor.apply()
    }

    private fun rollRemoteToday(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            .format(java.util.Date())
        val count = if (prefs.getString(RemoteConfig.KEY_TODAY_DATE, "") != today) {
            editor.putString(RemoteConfig.KEY_TODAY_DATE, today)
            1L
        } else {
            prefs.getLong(RemoteConfig.KEY_TODAY_COUNT, 0L) + 1L
        }
        editor.putLong(RemoteConfig.KEY_TODAY_COUNT, count)
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