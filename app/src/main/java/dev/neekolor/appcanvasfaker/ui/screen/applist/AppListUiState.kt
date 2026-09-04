package dev.neekolor.appcanvasfaker.ui.screen.applist

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import dev.neekolor.appcanvasfaker.core.AppRule
import dev.neekolor.appcanvasfaker.ui.component.SearchStatus

/** 应用列表的排序维度，[key] 与 ConfigRepository#getInstalledApps 的 sortMode 取值一致。 */
enum class AppSortType {
    NAME, PACKAGE_NAME, INSTALL_TIME, UPDATE_TIME;

    val key: String
        get() = when (this) {
            NAME -> "app_name"
            PACKAGE_NAME -> "package_name"
            INSTALL_TIME -> "install_time"
            UPDATE_TIME -> "update_time"
        }

    companion object {
        fun fromKey(key: String): AppSortType = when (key) {
            "package_name" -> PACKAGE_NAME
            "install_time" -> INSTALL_TIME
            "update_time" -> UPDATE_TIME
            else -> NAME
        }
    }
}

/** 列表行条目：InstalledApp + 供 AppIconImage 使用的 ApplicationInfo。 */
@Immutable
data class AppListItem(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val applicationInfo: ApplicationInfo?,
    val rule: AppRule,
)

@Stable
data class AppListUiState(
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val apps: List<AppListItem> = emptyList(),
    val searchResults: List<AppListItem> = emptyList(),
    val recentlyInstalledResults: List<AppListItem> = emptyList(),
    val showSystemApps: Boolean = false,
    val sortType: AppSortType = AppSortType.NAME,
    val reversed: Boolean = false,
    val searchStatus: SearchStatus = SearchStatus(""),
    val error: Throwable? = null
)

@Immutable
data class AppListActions(
    val onRefresh: () -> Unit,
    val onOpenLogs: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onSearchStatusChange: (SearchStatus) -> Unit,
    val onClearSearch: () -> Unit,
    val onToggleShowSystemApps: () -> Unit,
    val onUpdateSortConfig: (AppSortType, Boolean) -> Unit,
    val onOpenProfile: (String) -> Unit,
)

@Immutable
data class StatusMeta(
    val label: String,
    val bg: Color,
    val fg: Color
)