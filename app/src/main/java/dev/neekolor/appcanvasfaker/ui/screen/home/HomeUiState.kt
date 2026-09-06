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
)

@Immutable
data class HomeActions(
    val onOpenHookedApps: () -> Unit,
    val onOpenStats: () -> Unit,
    val onOpenUrl: (String) -> Unit,
)