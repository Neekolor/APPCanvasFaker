package dev.nikko.appcanvasfaker.ui.screen.appprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nikko.appcanvasfaker.core.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ConfigRepository(application)
    private val pm = application.packageManager

    private val _uiState = MutableStateFlow(AppProfileUiState())
    val uiState: StateFlow<AppProfileUiState> = _uiState.asStateFlow()

    private var loadedPackageName: String? = null
    private var loadJob: Job? = null

    fun load(packageName: String) {
        if (loadedPackageName == packageName) return
        loadedPackageName = packageName
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                buildState(packageName)
            }
            // 过期结果保护：快速切换应用时，仅提交最后一次请求的结果
            if (loadedPackageName == packageName) {
                _uiState.value = state
            }
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setHookEnabled(packageName, enabled)
            // 画布渲染 + PNG 压缩 + 哈希为 CPU 密集操作，必须离开主线程
            val fingerprints = withContext(Dispatchers.Default) {
                repo.simulatedFingerprints(packageName)
            }
            _uiState.update { it.copy(enabled = enabled, fingerprints = fingerprints) }
        }
    }

    /** 随机化 seed 并刷新指纹展示；返回是否成功。挂起函数，重活在 Default 调度器执行。 */
    suspend fun randomize(packageName: String): Boolean = runCatching {
        repo.randomizeSeed(packageName)
        val fingerprints = withContext(Dispatchers.Default) {
            repo.simulatedFingerprints(packageName)
        }
        _uiState.update { it.copy(fingerprints = fingerprints) }
    }.isSuccess

    private suspend fun buildState(packageName: String): AppProfileUiState = withContext(Dispatchers.Default) {
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val label = appInfo?.let {
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        }
        val version = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        val rule = repo.getRule(packageName)
        AppProfileUiState(
            packageName = packageName,
            label = label,
            versionName = version?.versionName,
            versionCode = version?.longVersionCode ?: 0L,
            applicationInfo = appInfo,
            enabled = rule.enabled,
            fingerprints = repo.simulatedFingerprints(packageName),
        )
    }
}
