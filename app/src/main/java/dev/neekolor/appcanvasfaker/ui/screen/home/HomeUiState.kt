package dev.neekolor.appcanvasfaker.ui.screen.home

import androidx.compose.runtime.Immutable

@Immutable
data class HomeUiState(
    val moduleActive: Boolean,
    val versionName: String,
    val hookedAppCount: Int,
    val totalHookCount: Long,
    val isLoading: Boolean = false,
    /** 远端配置通道探针：service 绑定且远端可读。null = 首轮探测未完成，不显示红字防闪现。 */
    val remoteChannelOk: Boolean? = null,
    /** 当前 Hook 模式展示名（如"噪声模式"）。 */
    val modeTitle: String = "",
    /** 默认链英文名恒显（A1/A3/A4/A4b）。 */
    val mainChains: String = "",
    /** 扩展链英文名跟开关；全关显示"全关"。 */
    val extChains: String = "",
)

@Immutable
data class HomeActions(
    val onOpenHookedApps: () -> Unit,
    val onOpenStats: () -> Unit,
    val onOpenUrl: (String) -> Unit,
)