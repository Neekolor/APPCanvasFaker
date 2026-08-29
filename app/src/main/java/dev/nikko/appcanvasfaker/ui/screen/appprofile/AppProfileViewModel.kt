package dev.nikko.appcanvasfaker.ui.screen.appprofile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nikko.appcanvasfaker.core.ConfigRepository
import dev.nikko.appcanvasfaker.core.SsaidManager
import dev.nikko.appcanvasfaker.util.RootShell
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
            // 第一步：应用基本信息立即上屏（对齐 KSU：头部卡不等任何重活）
            val quick = withContext(Dispatchers.IO) {
                buildQuickState(packageName)
            }
            if (loadedPackageName == packageName) {
                _uiState.value = quick
            }
            // 第二步：指纹计算（渲染 + PNG + SHA-256）异步补充
            val fingerprints = withContext(Dispatchers.Default) {
                runCatching { repo.simulatedFingerprints(packageName) }.getOrElse { emptyList() }
            }
            if (loadedPackageName == packageName) {
                _uiState.update { it.copy(fingerprints = fingerprints) }
            }
        }
        // SSAID 读取需要启动 su 进程（KernelSU 授权检查可达秒级），
        // 完全独立加载，不阻塞头部卡与指纹
        viewModelScope.launch {
            val ssaid = withContext(Dispatchers.IO) {
                runCatching { readSsaidRaw(packageName) }.getOrNull()
            }
            if (loadedPackageName == packageName) {
                _uiState.update {
                    if (ssaid == null) it.copy(ssaidLoadFailed = true) else it.copy(ssaid = ssaid, ssaidLoadFailed = false)
                }
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

    /**
     * 随机化 SSAID：先强制停止目标应用（避免其进程缓存旧值），再真实改写
     * settings_ssaid.xml，成功后回读刷新展示。全程 IO 线程。
     */
    suspend fun randomizeSsaid(packageName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            forceStopRaw(packageName)
            val ok = SsaidManager.randomize(packageName)
            if (ok) reloadSsaid(packageName)
            ok
        }.getOrElse {
            Log.w(TAG, "randomize ssaid failed", it)
            false
        }
    }

    /** 删除 SSAID 条目：同样先强制停止目标应用。 */
    suspend fun deleteSsaid(packageName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            forceStopRaw(packageName)
            val ok = SsaidManager.delete(packageName)
            if (ok) reloadSsaid(packageName)
            ok
        }.getOrElse {
            Log.w(TAG, "delete ssaid failed", it)
            false
        }
    }

    /** 菜单操作：启动应用（root，与 KSU 同路径）。 */
    suspend fun launchApp(packageName: String) = withContext(Dispatchers.IO) {
        RootShell.exec(
            "cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n"
        )
    }

    /** 菜单操作：强制停止应用。 */
    suspend fun forceStopApp(packageName: String) = withContext(Dispatchers.IO) {
        forceStopRaw(packageName)
    }

    /** 菜单操作：重启应用（强制停止 + 启动）。 */
    suspend fun restartApp(packageName: String) = withContext(Dispatchers.IO) {
        forceStopRaw(packageName)
        RootShell.exec(
            "cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n"
        )
    }

    private fun forceStopRaw(packageName: String) {
        RootShell.exec("am force-stop $packageName")
    }

    private suspend fun reloadSsaid(packageName: String) {
        val value = readSsaidRaw(packageName)
        _uiState.update {
            if (value == null) it.copy(ssaidLoadFailed = true) else it.copy(ssaid = value, ssaidLoadFailed = false)
        }
    }

    private fun readSsaidRaw(packageName: String): String? {
        if (!RootShell.isAvailable()) return null
        return SsaidManager.readSsaid(packageName)
    }

    private suspend fun buildQuickState(packageName: String): AppProfileUiState = withContext(Dispatchers.IO) {
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
        )
    }

    private companion object {
        const val TAG = "AppProfileViewModel"
    }
}
