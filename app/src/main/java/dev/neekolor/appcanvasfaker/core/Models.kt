package dev.neekolor.appcanvasfaker.core

enum class ProtectionMode(val title: String) {
    NOISE("噪声模式")
}

enum class AppSortMode(val title: String) {
    APP_NAME("应用名"),
    PACKAGE_NAME("包名"),
    INSTALL_TIME("安装时间"),
    UPDATE_TIME("更新时间")
}

data class AppRule(
    val packageName: String,
    val enabled: Boolean = false,
    val seed: Long = 0L,
    val lastCanvasHash: String = "暂无",
    val hookCount: Long = 0L,
    val lastHookTime: Long = 0L
)

/** Hook 统计行：某包被 Hook 的累计次数与末次时间（调用方负责后台线程）。 */
data class HookStat(
    val packageName: String,
    val enabled: Boolean,
    val count: Long,
    val lastTime: Long
)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val rule: AppRule
)

data class ModuleSnapshot(
    val moduleActive: Boolean,
    val frameworkName: String,
    val frameworkApi: String,
    val totalHookCount: Long,
    val todayHookCount: Long,
    val widevineId: String,
    val versionName: String,
    val buildType: String,
    val mode: ProtectionMode,
    val logs: List<String>
)

data class LogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
    val packageName: String? = null
)

/** 固定方法计算的标准化指纹：method 为路径编号（A1/A3/A4/A4b），title 为中文名。 */
data class FingerprintValue(
    val method: String,
    val title: String,
    val hash: String
)