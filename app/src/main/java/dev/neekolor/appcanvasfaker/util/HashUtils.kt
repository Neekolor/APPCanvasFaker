package dev.neekolor.appcanvasfaker.util

import java.nio.ByteBuffer
import java.security.MessageDigest

object HashUtils {

    private const val HEX = "0123456789abcdef"

    fun ofIntArray(values: IntArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteBuffer.allocate(values.size * 4)
        for (v in values) buf.putInt(v)
        md.update(buf.array())
        return toHex(md.digest())
    }

    fun ofString(value: String): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    fun ofBytes(bytes: ByteArray): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** 完整 SHA-256 输出：64 个 hex 字符。 */
    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * 哈希折叠：SHA-256 的 4 个 64-bit 块逐块 XOR 折叠为单个 64-bit 值，
     * 输出 16 个 hex 字符。每个输入 bit 都参与计算，非简单截断。
     * 算法与配套测试应用 canvas-fingerprint-scanner 的 foldHash16 一致，
     * 两边展示的短哈希可直接比对。非十六进制/长度不符的文本原样返回。
     */
    fun foldHash16(sha256: String): String {
        if (sha256.length != 64 || !sha256.all { it in "0123456789abcdefABCDEF" }) return sha256
        val blocks = LongArray(4)
        for (i in 0 until 4) {
            blocks[i] = java.lang.Long.parseUnsignedLong(sha256.substring(i * 16, i * 16 + 16), 16)
        }
        val folded = blocks.reduce { acc, b -> acc xor b }
        return folded.toULong().toString(16).padStart(16, '0')
    }
}