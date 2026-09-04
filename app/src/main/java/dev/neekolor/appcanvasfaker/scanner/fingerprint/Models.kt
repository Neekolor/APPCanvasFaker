package dev.neekolor.appcanvasfaker.scanner.fingerprint

/**
 * 采集项定义与结果模型
 */
data class FingerprintItem(
    val id: String,
    val name: String,
    val group: String,
    val description: String
)

data class FingerprintResult(
    val item: FingerprintItem,
    val hash: String,
    val elapsedMs: Long
)
