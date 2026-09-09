package dev.neekolor.appcanvasfaker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.RemoteBridge
import dev.neekolor.appcanvasfaker.core.RemoteConfig
import dev.neekolor.appcanvasfaker.ui.screen.home.HomeUiState

class HomeViewModel(
    private val configRepository: ConfigRepository = ConfigRepository(acfApp),
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val serviceListener: () -> Unit = {
        // 服务绑定/解绑（含运行中在 LSPosed 停用模块触发 onServiceDied）实时生效
        applyServiceState(acfApp.xposedService != null)
    }

    init {
        refresh()
        acfApp.addServiceListener(serviceListener)
        viewModelScope.launch {
            // 绑定结果兜底：模块被停用时服务不会绑定，"检测中"不能永远转圈
            delay(SERVICE_TIMEOUT_MS)
            _uiState.update { if (it.isLoading) it.copy(isLoading = false, moduleActive = false) else it }
        }
    }

    override fun onCleared() {
        acfApp.removeServiceListener(serviceListener)
        super.onCleared()
    }

    /**
     * 模块是否生效只认 Xposed 服务绑定状态：绑定成功=生效；未绑定/解绑=未生效。
     * 计数与历史提示都只是过去时，不能作为当前状态依据。
     */
    private fun applyServiceState(active: Boolean) {
        _uiState.update { it.copy(moduleActive = active, isLoading = false) }
    }

    /** 首帧同步水合：仅廉价本地读取；激活状态未知 → 显示"检测中"。 */
    private fun initialState(): HomeUiState = HomeUiState(
        moduleActive = false,
        versionName = configRepository.versionName(),
        hookedAppCount = configRepository.hookedAppCountQuick(),
        totalHookCount = configRepository.totalHookCount(),
        isLoading = true,
        modeTitle = acfApp.getString(configRepository.mode().titleRes),
        mainChains = mainChains(),
        extChains = extChains(configRepository),
    )

    /**
     * 默认四链英文名恒显（getPixels/copyPixelsToBuffer/compress/getPixel，常驻常开无界面开关）；
     * 扩展项跟各自开关。值恒为英文（用户指定），分隔符跟随系统语言。全部读本地配置，无 IO。
     */
    private fun mainChains(): String = listOf(
        acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_a1),
        acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_a3),
        acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_a4),
        acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_a2),
    ).joinToString(separator())

    private fun extChains(repo: ConfigRepository): String {
        val out = ArrayList<String>(3)
        if (repo.hookTextMetrics()) out.add(acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_e1))
        if (repo.hookGlReadPixels()) out.add(acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_d1))
        if (repo.hookPixelCopy()) out.add(acfApp.getString(dev.neekolor.appcanvasfaker.R.string.chain_c2))
        if (out.isEmpty()) out.add(acfApp.getString(dev.neekolor.appcanvasfaker.R.string.home_chains_none))
        return out.joinToString(separator())
    }

    private fun separator(): String =
        if (acfApp.resources.configuration.locales[0].language == "zh") "、" else ", "

    fun refresh() {
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) { buildState() }
            // 只更新计数；激活状态由服务事件 + 超时兜底统一裁决，
            // 避免绑定仍在进行中被误判为"未激活"
            _uiState.update { currentState ->
                currentState.copy(
                    hookedAppCount = newState.hookedAppCount,
                    totalHookCount = newState.totalHookCount,
                    remoteChannelOk = newState.remoteChannelOk,
                    modeTitle = newState.modeTitle,
                    mainChains = newState.mainChains,
                    extChains = newState.extChains,
                )
            }
        }
    }

    private suspend fun buildState(): HomeUiState {
        val snapshot = configRepository.snapshot()
        return HomeUiState(
            moduleActive = snapshot.moduleActive,
            versionName = snapshot.versionName,
            hookedAppCount = configRepository.enabledAppCount(),
            totalHookCount = snapshot.totalHookCount,
            isLoading = false,
            remoteChannelOk = probeRemoteChannel(),
            modeTitle = acfApp.getString(configRepository.mode().titleRes),
            mainChains = mainChains(),
            extChains = extChains(configRepository),
        )
    }

    /** 远端通道探针（IO 线程 binder 调用）：服务已绑定即畅通；从未写过配置时远端本就为 null，不算异常。 */
    private fun probeRemoteChannel(): Boolean = runCatching {
        RemoteBridge.remote() != null
    }.getOrDefault(false)

    private companion object {
        const val SERVICE_TIMEOUT_MS = 2_500L
    }
}
