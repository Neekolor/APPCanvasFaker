package dev.neekolor.appcanvasfaker.ui.screen.log

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable

/** 单条 Hook/随机化日志：message 为包名，tag 为 "Hook" 或 "随机化"。 */
data class LogItem(
    val timestamp: Long,
    val timeText: String,
    val tag: String,
    val packageName: String,
    val appLabel: String,
    val applicationInfo: ApplicationInfo?,
)

/** 日志筛选类型：null 表示「全部」。 */
enum class LogFilter(val tag: String?) {
    ALL(null),
    HOOK("Hook"),
    RANDOMIZE("随机化");

    companion object {
        fun fromTag(tag: String?): LogFilter = entries.firstOrNull { it.tag == tag } ?: ALL
    }
}

/** 日志类型展示名："随机化"存量 tag 统一显示为 random（KSU su log 方法对齐，见 §8-15）。 */
fun logTagLabel(tag: String): String =
    if (tag == LogFilter.RANDOMIZE.tag) "random" else tag

/** 时间戳 → 日期栏 key（yyyy-MM-dd，与 KSU sulog-日期文件名同格式）。 */
fun logDateKey(timestamp: Long): String =
    runCatching {
        java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.ROOT))
    }.getOrDefault("")

@Immutable
data class LogUiState(
    val searchText: String = "",
    val selectedFilters: Set<String> = emptySet(),
    val items: List<LogItem> = emptyList(),
    val visibleItems: List<LogItem> = emptyList(),
    val loggingEnabled: Boolean = true,
    /** 日期栏：null = 全部日期，否则只看该天（轻量过滤，存储仍单存）。 */
    val selectedDate: String? = null,
    val availableDates: List<String> = emptyList(),
)

data class LogActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onClear: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onToggleFilter: (LogFilter) -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectDate: (String?) -> Unit,
)

fun buildVisibleLogItems(
    items: List<LogItem>,
    searchText: String,
    selectedFilters: Set<String>,
    selectedDate: String? = null,
): List<LogItem> {
    val query = searchText.trim()
    return items.filter { item ->
        val filterOk = selectedFilters.isEmpty() || item.tag in selectedFilters
        val searchOk = query.isBlank() ||
            item.appLabel.contains(query, ignoreCase = true) ||
            item.packageName.contains(query, ignoreCase = true)
        val dateOk = selectedDate == null || logDateKey(item.timestamp) == selectedDate
        filterOk && searchOk && dateOk
    }
}