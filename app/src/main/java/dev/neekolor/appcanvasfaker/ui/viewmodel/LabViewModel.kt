package dev.neekolor.appcanvasfaker.ui.viewmodel

import androidx.lifecycle.ViewModel
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.ProtectionMode
import dev.neekolor.appcanvasfaker.data.repository.SettingsRepository
import dev.neekolor.appcanvasfaker.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 实验性功能页状态：当前伪装执行模式（读本地配置，无 IO）。 */
class LabViewModel(
    private val repo: ConfigRepository = ConfigRepository(acfApp),
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {

    private val _mode = MutableStateFlow(repo.mode())
    val mode: StateFlow<ProtectionMode> = _mode.asStateFlow()

    private val _ssaidEnabled = MutableStateFlow(settingsRepo.ssaidEnabled)
    val ssaidEnabled: StateFlow<Boolean> = _ssaidEnabled.asStateFlow()

    fun refresh() {
        _mode.value = repo.mode()
        _ssaidEnabled.value = settingsRepo.ssaidEnabled
    }

    fun setMode(mode: ProtectionMode) {
        repo.setMode(mode)
        _mode.value = mode
    }

    /** 「启用随机化 SSAID」开关：控制工具页 SSAID 管理入口显隐（纯 UI 设置）。 */
    fun setSsaidEnabled(enabled: Boolean) {
        settingsRepo.ssaidEnabled = enabled
        _ssaidEnabled.value = enabled
    }
}
