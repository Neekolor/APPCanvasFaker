package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable
import dev.neekolor.appcanvasfaker.core.FingerprintValue

@Immutable
data class AppProfileUiState(
    val packageName: String = "",
    val label: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val applicationInfo: ApplicationInfo? = null,
    val enabled: Boolean = false,
    val fingerprints: List<FingerprintValue> = emptyList(),
) {
    val displayLabel: String
        get() = label ?: packageName
}

@Immutable
data class AppProfileActions(
    val onBack: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onRandomize: () -> Unit,
    val onLaunchApp: () -> Unit,
    val onForceStopApp: () -> Unit,
    val onRestartApp: () -> Unit,
)
