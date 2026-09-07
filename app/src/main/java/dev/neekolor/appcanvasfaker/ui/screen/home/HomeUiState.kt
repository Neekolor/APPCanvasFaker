package dev.neekolor.appcanvasfaker.ui.screen.home

import androidx.compose.runtime.Immutable

@Immutable
data class HomeUiState(
    val moduleActive: Boolean,
    val versionName: String,
    val hookedAppCount: Int,
    val totalHookCount: Long,
    val isLoading: Boolean = false,
    /** 远端配置通道探针：service 绑定且远端可读。false + 已激活 = 通道故障，脚注明示。 */
    val remoteChannelOk: Boolean = false,
    /** 当前 Hook 模式展示名（如"噪声模式"）。 */
    val modeTitle: String = "",
    /** 已 Hook 基线动态拼串：A1/A3/A4 恒开 + A2/E1/C2/D1 跟各自开关。 */
    val baselineText: String = "",
)

@Immutable
data class HomeActions(
    val onOpenHookedApps: () -> Unit,
    val onOpenStats: () -> Unit,
    val onOpenUrl: (String) -> Unit,
)