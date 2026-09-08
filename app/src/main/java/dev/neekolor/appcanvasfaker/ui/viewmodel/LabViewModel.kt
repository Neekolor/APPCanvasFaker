package dev.neekolor.appcanvasfaker.ui.viewmodel

import androidx.lifecycle.ViewModel
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.ProtectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 实验性功能页状态：当前伪装执行模式（读本地配置，无 IO）。 */
class LabViewModel(
    private val repo: ConfigRepository = ConfigRepository(acfApp),
) : ViewModel() {

    private val _mode = MutableStateFlow(repo.mode())
    val mode: StateFlow<ProtectionMode> = _mode.asStateFlow()

    fun refresh() {
        _mode.value = repo.mode()
    }

    fun setMode(mode: ProtectionMode) {
        repo.setMode(mode)
        _mode.value = mode
    }
}
