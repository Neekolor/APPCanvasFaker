package dev.neekolor.appcanvasfaker.ui.screen.ssaid

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable

@Immutable
data class SsaidItemUi(
    val packageName: String,
    val value: String,
    val label: String? = null,
    val applicationInfo: ApplicationInfo? = null,
) {
    val displayName: String get() = label ?: packageName

    /** 系统应用判定（与 ConfigRepository.getInstalledApps 同口径：FLAG_SYSTEM 标志位）。
     *  无 PackageManager 信息的条目（应用已卸载等）不算系统应用，不受开关过滤。 */
    val isSystemApp: Boolean
        get() = applicationInfo != null && (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
}

/** 读表结果：su 不可用 / 文件读取失败 / 成功 三态。 */
enum class SsaidLoadState { LOADING, UNAVAILABLE, FAILED, READY }

@Immutable
data class SsaidUiState(
    val items: List<SsaidItemUi> = emptyList(),
    val loadState: SsaidLoadState = SsaidLoadState.LOADING,
    /** 正在执行随机化/删除的条目包名（同一时间仅允许一个操作，其余按钮禁用）。 */
    val busyPkg: String? = null,
    /** 是否显示系统应用（顶栏 MoreCircle 菜单切换，持久化到 app_list prefs）。 */
    val showSystemApps: Boolean = false,
)

@Immutable
data class SsaidActions(
    val onBack: () -> Unit,
    val onRandomize: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onRetry: () -> Unit,
    val onToggleShowSystemApps: () -> Unit,
)
