package dev.nikko.appcanvasfaker.core

/**
 * 伪装算法：与读取路径无关，只依赖位图绝对坐标 (x, y) + seed，保证 A1/A3/A4/A4b 结果一致。
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
}