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
    val onOpenToolset: () -> Unit,
    val onOpenSsaid: () -> Unit,
    val onOpenLog: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onSetPreset: (Int) -> Unit,
)

/**
 * 行为预设派生下标（开关顺序 E1/D1/C2）：
 * 0 默认 = 全关；1 增强 = 开/关/开；2 自定义 = 其余一切组合。
 */
fun presetIndex(hookTextMetrics: Boolean, hookGlReadPixels: Boolean, hookPixelCopy: Boolean): Int =
    if (!hookTextMetrics && !hookGlReadPixels && !hookPixelCopy) 0
    else if (hookTextMetrics && !hookGlReadPixels && hookPixelCopy) 1
    else 2