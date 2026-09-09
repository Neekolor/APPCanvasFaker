package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable

@Immutable
data class AppProfileUiState(
    val packageName: String = "",
    val label: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val applicationInfo: ApplicationInfo? = null,
    val enabled: Boolean = false,
    /** 本次随机化的新旧对照；null = 本次进页尚未执行过。 */
    val reseed: ReseedPreview? = null,
) {
    val displayLabel: String
        get() = label ?: packageName
}

/** 一次随机化的回执：新旧 seed 与 A1 试算对照（本地标准画布，真引擎计算）。 */
@Immutable
data class ReseedPreview(
    val oldSeed: Long,
    val newSeed: Long,
    val oldA1: String,
    val newA1: String,
)

@Immutable
data class AppProfileActions(
    val onBack: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onRandomize: () -> Unit,
    val onLaunchApp: () -> Unit,
    val onForceStopApp: () -> Unit,
    val onRestartApp: () -> Unit,
    val onOpenLogs: () -> Unit,
)
