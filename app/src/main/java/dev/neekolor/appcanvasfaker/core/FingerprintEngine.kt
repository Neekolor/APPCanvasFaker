package dev.neekolor.appcanvasfaker.core

import java.nio.ByteBuffer

/**
 * 伪装算法：与读取路径无关，只依赖位图绝对坐标 (x, y) + seed，保证 A1/A3/A4/A4b 结果一致。
 * v0.6.0 扩展：H-01 单点读取（getPixel）、H-02 GL 帧缓冲直读、H-05 文本度量微扰。
 */
object FingerprintEngine {

    fun applyPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        offset: Int,
        stride: Int,
        originX: Int,
        originY: Int,
        mode: ProtectionMode,
        seed: Long
    ) {
        if (width <= 0 || height <= 0 || stride <= 0 || pixels.isEmpty()) return
        applyBiasNoise(pixels, width, height, offset, stride, originX, originY, seed)
    }

    /**
     * H-01 单点扰动（getPixel）：与 A1 同源算法按绝对坐标取噪，
     * 保证"同一物理像素在整图读取与单点读取下扰动一致"的坐标绑定性质。
     */
    fun perturbPoint(color: Int, x: Int, y: Int, seed: Long): Int {
        val n = stableNoise(seed, y, x)
        val dR = ((n ushr 16) and 0x07).toInt() - 1
        val dG = ((n ushr 24) and 0x07).toInt() - 1
        val dB = ((n ushr 32) and 0x07).toInt() - 1
        return perturb(color, dR, dG, dB)
    }

    /**
     * H-05 文本度量相对微扰因子：输入键 = seed ⊕ 文本内容哈希 ⊕ textSize（0.1px 离散），
     * 同输入恒同输出（否则自定义布局会在帧间抖动）；幅度 ∈ ±[0.3%, 0.7%]，符号由哈希派生。
     */
    fun textFactor(seed: Long, textHash: Long, textSizePx: Float): Float {
        val tsKey = (textSizePx * 10f).toInt()
        var z = seed xor textHash xor (tsKey.toLong() * 0x9E3779B97F4A7C15uL.toLong())
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        z = z xor (z ushr 31)
        val milli = ((z ushr 8) % 5).toInt() + 3   // 千分之 3~7
        val negative = ((z ushr 40) and 0x01).toInt() == 1
        val f = milli / 1000f
        return if (negative) -f else f
    }

    /** H-05 度量值统一施加方式：原值 × (1 + factor)。 */
    fun scaleMetric(value: Float, factor: Float): Float = value * (1f + factor)

    /**
     * H-05 getTextWidths：输出数组逐元缩放，输入键与返回计数语义由调用方保持。
     * 空数组直接返回，避免无意义遍历。
     */
    fun scaleWidths(widths: FloatArray, factor: Float) {
        for (i in widths.indices) widths[i] = scaleMetric(widths[i], factor)
    }

    /**
     * H-05 getFontMetricsInt：整数度量字段同比缩放（四舍五入保证确定性）。
     * leading 不动（与浮点版 FontMetrics 决策一致）。
     */
    fun scaleFontMetricsInt(fm: android.graphics.Paint.FontMetricsInt, factor: Float) {
        fm.top = scaleIntMetric(fm.top, factor)
        fm.ascent = scaleIntMetric(fm.ascent, factor)
        fm.descent = scaleIntMetric(fm.descent, factor)
        fm.bottom = scaleIntMetric(fm.bottom, factor)
    }

    /** H-05 整数度量值统一施加方式：round(原值 × (1 + factor))。 */
    fun scaleIntMetric(value: Int, factor: Float): Int = Math.round(value * (1f + factor))

    /** H-05 文本边界框：以左上角为锚点缩放宽高，保持确定性。 */
    fun scaleBounds(rect: android.graphics.Rect, factor: Float) {
        val w = rect.width()
        val h = rect.height()
        rect.right = rect.left + Math.round(w * (1f + factor))
        rect.bottom = rect.top + Math.round(h * (1f + factor))
    }

    /**
     * H-02 GL 帧缓冲直读扰动：对 RGBA/UNSIGNED_BYTE 直接缓冲按像素序号均匀施加偏置噪声。
     * 该通道与位图通道本就没有对齐承诺，故噪声键用线性序号而非坐标；
     * 仅处理直接 ByteBuffer，其余布局宁可放过不伪装。
     *
     * @return 实际扰动的字节数（0 表示未处理）
     */
    fun applyGlPixels(
        buffer: ByteBuffer,
        startPosition: Int,
        width: Int,
        height: Int,
        seed: Long
    ): Int {
        val need = width * height * 4
        if (need <= 0 || !buffer.isDirect) return 0
        // 审计 N-16：绝对 put 受 limit 约束，边界必须按 limit 判定（capacity > limit 时按 capacity 判会 IOOBE）
        if (startPosition < 0 || startPosition + need > buffer.limit()) return 0
        val glSeed = seed xor GL_DOMAIN_SALT
        var i = 0
        while (i < width * height) {
            val o = startPosition + i * 4
            val n = stableNoise(glSeed, i, 0)
            val dR = ((n ushr 16) and 0x07).toInt() - 1
            val dG = ((n ushr 24) and 0x07).toInt() - 1
            val dB = ((n ushr 32) and 0x07).toInt() - 1
            buffer.put(o, ((buffer.get(o).toInt() and 0xFF) + dR).coerceIn(0, 255).toByte())
            buffer.put(o + 1, ((buffer.get(o + 1).toInt() and 0xFF) + dG).coerceIn(0, 255).toByte())
            buffer.put(o + 2, ((buffer.get(o + 2).toInt() and 0xFF) + dB).coerceIn(0, 255).toByte())
            i++
        }
        return need
    }

    /**
     * 噪声模式：坐标相关确定性偏置噪声。
     * 每个像素的抖动量由位图绝对坐标 (originY+row, originX+col) + seed 经 SplitMix64 确定，
     * 同 seed 多次读取结果一致；子区域读取与整图读取对同一物理像素产生相同扰动；
     * 通道抖动范围 [-1, 6]（均值 +2.5，非零偏置），防"零均值噪声"统计检测。
     */
    private fun applyBiasNoise(
        pixels: IntArray,
        width: Int,
        height: Int,
        offset: Int,
        stride: Int,
        originX: Int,
        originY: Int,
        seed: Long
    ) {
        for (row in 0 until height) {
            val base = offset + row * stride
            if (base < 0 || base >= pixels.size) continue
            for (col in 0 until width) {
                val index = base + col
                if (index !in pixels.indices) continue
                // 噪声 key 用位图绝对坐标，保证同一物理像素跨 A1/A3/A4 路径一致
                val n = stableNoise(seed, originY + row, originX + col)
                val dR = ((n ushr 16) and 0x07).toInt() - 1
                val dG = ((n ushr 24) and 0x07).toInt() - 1
                val dB = ((n ushr 32) and 0x07).toInt() - 1
                pixels[index] = perturb(pixels[index], dR, dG, dB)
            }
        }
    }

    private fun perturb(color: Int, dR: Int, dG: Int, dB: Int): Int {
        val a = (color ushr 24) and 0xFF
        val r = (((color ushr 16) and 0xFF) + dR).coerceIn(0, 255)
        val g = (((color ushr 8) and 0xFF) + dG).coerceIn(0, 255)
        val b = ((color and 0xFF) + dB).coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** SplitMix64 混合：seed + 坐标派生值，确定性、均匀。 */
    private fun stableNoise(seed: Long, row: Int, col: Int): Long {
        var z = seed +
            row.toLong() * 0x9E3779B97F4A7C15uL.toLong() +
            col.toLong() * 0xC2B2AE3D27D4EB4FuL.toLong()
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    /** H-02 GL 通道域隔离盐：避免 GL 序号噪声与位图坐标噪声同键。 */
    private val GL_DOMAIN_SALT: Long = 0x1B873593L
}