package dev.neekolor.appcanvasfaker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.data.repository.SettingsRepository
import dev.neekolor.appcanvasfaker.data.repository.SettingsRepositoryImpl
import dev.neekolor.appcanvasfaker.ui.screen.settings.SettingsUiState
import dev.neekolor.appcanvasfaker.ui.theme.ColorMode

class SettingsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl(),
    private val configRepo: ConfigRepository = ConfigRepository(acfApp),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    uiMode = repo.uiMode,
                    checkUpdate = repo.checkUpdate,
                    themeMode = repo.themeMode,
                    miuixMonet = repo.miuixMonet,
                    keyColor = repo.keyColor,
                    enablePredictiveBack = repo.enablePredictiveBack,
                    enableBlur = repo.enableBlur,
                    enableFloatingBottomBar = repo.enableFloatingBottomBar,
                    enableFloatingBottomBarBlur = repo.enableFloatingBottomBarBlur,
                    enableNavigationBadge = repo.enableNavigationBadge,
                    pageScale = repo.pageScale,
                    colorStyle = repo.colorStyle,
                    colorSpec = repo.colorSpec,
                    enableLogging = configRepo.enableLogging(),
                    hookTextMetrics = configRepo.hookTextMetrics(),
                    hookGlReadPixels = configRepo.hookGlReadPixels(),
                    hookPixelCopy = configRepo.hookPixelCopy(),
                    ssaidEnabled = repo.ssaidEnabled,
                )
            }
        }
    }

    fun setCheckUpdate(enabled: Boolean) {
        repo.checkUpdate = enabled
        _uiState.update { it.copy(checkUpdate = enabled) }
    }

    fun setUiMode(mode: String) {
        val oldMode = repo.uiMode
        val currentThemeMode = repo.themeMode

        val newThemeMode = when (oldMode) {
            "material" if mode == "miuix" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                val baseMode = if (colorMode == ColorMode.DARK_AMOLED) 2 else currentThemeMode
                if (repo.miuixMonet && !colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toMonetMode()
                } else if (!repo.miuixMonet && colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toNonMonetMode()
                } else baseMode
            }

            "miuix" if mode == "material" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                if (colorMode.isMonet) {
                    colorMode.toNonMonetMode()
                } else currentThemeMode
            }

            else -> currentThemeMode
        }

        repo.uiMode = mode
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(uiMode = mode, themeMode = newThemeMode) }
    }

    fun setEnableLogging(enabled: Boolean) {
        configRepo.setEnableLogging(enabled)
        _uiState.update { it.copy(enableLogging = enabled) }
    }

    fun setHookTextMetrics(enabled: Boolean) {
        configRepo.setHookTextMetrics(enabled)
        _uiState.update { it.copy(hookTextMetrics = enabled) }
    }

    fun setHookGlReadPixels(enabled: Boolean) {
        configRepo.setHookGlReadPixels(enabled)
        _uiState.update { it.copy(hookGlReadPixels = enabled) }
    }

    fun setHookPixelCopy(enabled: Boolean) {
        configRepo.setHookPixelCopy(enabled)
        _uiState.update { it.copy(hookPixelCopy = enabled) }
    }

    /**
     * 行为预设：0 默认（开关全关）/ 1 增强（开/关/开，按 H-05/H-02/C2 顺序）/
     * 2 自定义（保持现状的纯展示态，不写配置——派生下标会自动落回实际组合）。
     */
    fun applyPreset(index: Int) {
        when (index) {
            0 -> {
                configRepo.setHookTextMetrics(false)
                configRepo.setHookGlReadPixels(false)
                configRepo.setHookPixelCopy(false)
                _uiState.update { it.copy(hookTextMetrics = false, hookGlReadPixels = false, hookPixelCopy = false) }
            }
            1 -> {
                configRepo.setHookTextMetrics(true)
                configRepo.setHookGlReadPixels(false)
                configRepo.setHookPixelCopy(true)
                _uiState.update { it.copy(hookTextMetrics = true, hookGlReadPixels = false, hookPixelCopy = true) }
            }
            else -> Unit
        }
    }

    /** 「启用随机化 SSAID」开关：控制设置页"SSAID 管理"入口的显示（纯 UI 设置，不下发 hook 进程）。 */
    fun setSsaidEnabled(enabled: Boolean) {
        repo.ssaidEnabled = enabled
        _uiState.update { it.copy(ssaidEnabled = enabled) }
    }

    fun setThemeMode(mode: Int) {
        val currentUiMode = repo.uiMode
        // 用枚举换算替代算术 +3：0/1/2 → 3/4/5 行为不变；
        // 传入 DARK_AMOLED(6) 时保持自身而非越界回退 SYSTEM（上游的 mode+3 写法存在此隐患）
        val effectiveMode = if (currentUiMode == "miuix" && _uiState.value.miuixMonet) {
            ColorMode.fromValue(mode).toMonetMode()
        } else {
            mode
        }
        repo.themeMode = effectiveMode
        _uiState.update { it.copy(themeMode = effectiveMode) }
    }

    fun setColorMode(mode: ColorMode) {
        repo.themeMode = mode.value
        _uiState.update { it.copy(themeMode = mode.value) }
    }

    fun setMiuixMonet(enabled: Boolean) {
        val currentThemeMode = repo.themeMode
        val colorMode = ColorMode.fromValue(currentThemeMode)
        val newThemeMode = if (enabled) {
            if (!colorMode.isMonet) colorMode.toMonetMode() else currentThemeMode
        } else {
            if (colorMode.isMonet) colorMode.toNonMonetMode() else currentThemeMode
        }
        repo.miuixMonet = enabled
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(miuixMonet = enabled, themeMode = newThemeMode) }
    }

    fun setKeyColor(color: Int) {
        repo.keyColor = color
        _uiState.update { it.copy(keyColor = color) }
    }

    fun setColorStyle(style: String) {
        repo.colorStyle = style
        _uiState.update { it.copy(colorStyle = style) }
    }

    fun setColorSpec(spec: String) {
        repo.colorSpec = spec
        _uiState.update { it.copy(colorSpec = spec) }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        repo.enablePredictiveBack = enabled
        _uiState.update { it.copy(enablePredictiveBack = enabled) }
    }

    fun setEnableBlur(enabled: Boolean) {
        repo.enableBlur = enabled
        _uiState.update { it.copy(enableBlur = enabled) }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        repo.enableFloatingBottomBar = enabled
        _uiState.update { it.copy(enableFloatingBottomBar = enabled) }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        repo.enableFloatingBottomBarBlur = enabled
        _uiState.update { it.copy(enableFloatingBottomBarBlur = enabled) }
    }

    fun setEnableNavigationBadge(enabled: Boolean) {
        repo.enableNavigationBadge = enabled
        _uiState.update { it.copy(enableNavigationBadge = enabled) }
    }

    fun setPageScale(scale: Float) {
        repo.pageScale = scale
        _uiState.update { it.copy(pageScale = scale) }
    }
}