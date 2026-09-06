package dev.neekolor.appcanvasfaker.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dev.neekolor.appcanvasfaker.ui.UiMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.SPEC_2025.name,
    val enablePredictiveBack: Boolean = false,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val enableNavigationBadge: Boolean = true,
    val pageScale: Float = 1.0f,
    val enableLogging: Boolean = true,
    val hookTextMetrics: Boolean = true,
    val hookGlReadPixels: Boolean = false,
    val hookPixelCopy: Boolean = true,
    val ssaidEnabled: Boolean = false,
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onSetEnableLogging: (Boolean) -> Unit,
    val onSetHookTextMetrics: (Boolean) -> Unit,
    val onSetHookGlReadPixels: (Boolean) -> Unit,
    val onSetSsaidEnabled: (Boolean) -> Unit,
    val onOpenTools: () -> Unit,
    val onOpenSsaid: () -> Unit,
    val onOpenLog: () -> Unit,
    val onOpenAbout: () -> Unit,
)