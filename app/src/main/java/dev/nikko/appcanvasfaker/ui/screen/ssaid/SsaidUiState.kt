package dev.nikko.appcanvasfaker.ui.screen.ssaid

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
}

/** 读表结果：su 不可用 / 文件读取失败 / 成功 三态。 */
enum class SsaidLoadState { LOADING, UNAVAILABLE, FAILED, READY }

@Immutable
data class SsaidUiState(
    val items: List<SsaidItemUi> = emptyList(),
    val loadState: SsaidLoadState = SsaidLoadState.LOADING,
    /** 正在执行随机化/删除的条目包名（同一时间仅允许一个操作，其余按钮禁用）。 */
    val busyPkg: String? = null,
)

@Immutable
data class SsaidActions(
    val onBack: () -> Unit,
    val onRandomize: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onRetry: () -> Unit,
)
