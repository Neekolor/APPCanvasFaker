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

@Immutable
data class LogUiState(
    val searchText: String = "",
    val selectedFilters: Set<String> = emptySet(),
    val items: List<LogItem> = emptyList(),
    val visibleItems: List<LogItem> = emptyList(),
    val loggingEnabled: Boolean = true,
)

data class LogActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onClear: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onToggleFilter: (LogFilter) -> Unit,
    val onOpenSettings: () -> Unit,
)

fun buildVisibleLogItems(
    items: List<LogItem>,
    searchText: String,
    selectedFilters: Set<String>,
): List<LogItem> {
    val query = searchText.trim()
    return items.filter { item ->
        val filterOk = selectedFilters.isEmpty() || item.tag in selectedFilters
        val searchOk = query.isBlank() ||
            item.appLabel.contains(query, ignoreCase = true) ||
            item.packageName.contains(query, ignoreCase = true)
        filterOk && searchOk
    }
}