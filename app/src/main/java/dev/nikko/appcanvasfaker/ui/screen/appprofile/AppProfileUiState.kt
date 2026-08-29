package dev.nikko.appcanvasfaker.ui.screen.appprofile

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable
import dev.nikko.appcanvasfaker.core.FingerprintValue

@Immutable
data class AppProfileUiState(
    val packageName: String = "",
    val label: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val applicationInfo: ApplicationInfo? = null,
    val enabled: Boolean = false,
    val fingerprints: List<FingerprintValue> = emptyList(),
    /** SSAID 值：null=未加载；""=该应用无条目（显示"空"）。 */
    val ssaid: String? = null,
    /** SSAID 读取失败（无 root 等），界面显示失败文案。 */
    val ssaidLoadFailed: Boolean = false,
) {
    val displayLabel: String
        get() = label ?: packageName
}

@Immutable
data class AppProfileActions(
    val onBack: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onRandomize: () -> Unit,
    val onRandomizeSsaid: () -> Unit,
    val onDeleteSsaid: () -> Unit,
    val onLaunchApp: () -> Unit,
    val onForceStopApp: () -> Unit,
    val onRestartApp: () -> Unit,
)
