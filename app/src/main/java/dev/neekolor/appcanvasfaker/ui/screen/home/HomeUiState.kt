package dev.neekolor.appcanvasfaker.ui.screen.home

import androidx.compose.runtime.Immutable
import dev.neekolor.appcanvasfaker.core.FingerprintValue

@Immutable
data class HomeUiState(
    val moduleActive: Boolean,
    val versionName: String,
    val hookedAppCount: Int,
    val totalHookCount: Long,
    val standardFingerprints: List<FingerprintValue>,
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

/** InfoCard 指纹行的标题：method + 括号内 title 简写，如 "A1（像素直读）"。 */
internal fun FingerprintValue.displayTitle(): String {
    val short = title.substringBefore("（").ifBlank { title }
    return "$method（$short）"
}