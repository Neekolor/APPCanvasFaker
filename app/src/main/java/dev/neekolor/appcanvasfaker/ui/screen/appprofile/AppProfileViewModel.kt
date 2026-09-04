package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.util.RootShell
import kotlinx.coroutines.CancellationException
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

    /**
     * 代次计数（审计 N-05）：每次 load 自增，随机化/开关等异步写回前比对代次，
     * 防止旧 seed 的指纹计算结果覆盖新状态。
     */
    private var generation = 0

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
                generation++
                _uiState.value = quick.copy(fingerprints = _uiState.value.fingerprints)
            }
            // 第二步：指纹计算（渲染 + PNG + SHA-256）异步补充
            val fingerprints = withContext(Dispatchers.Default) {
                runCatching { repo.simulatedFingerprints(packageName) }
                    .recoverCatching { e ->
                        if (e is CancellationException) throw e   // 审计 N-22
                        emptyList()
                    }.getOrDefault(emptyList())
            }
            if (loadedPackageName == packageName) {
                _uiState.update { it.copy(fingerprints = fingerprints) }
            }
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setHookEnabled(packageName, enabled)
            // 画布渲染 + PNG 压缩 + 哈希为 CPU 密集操作，必须离开主线程
            val fingerprints = withContext(Dispatchers.Default) {
                runCatching { repo.simulatedFingerprints(packageName) }
                    .recoverCatching { e ->
                        if (e is CancellationException) throw e   // 审计 N-22
                        emptyList()
                    }.getOrDefault(emptyList())
            }
            if (loadedPackageName == packageName) {
                _uiState.update { it.copy(enabled = enabled, fingerprints = fingerprints) }
            }
        }
    }

    /** 随机化 seed 并刷新指纹展示；返回是否成功。 */
    suspend fun randomize(packageName: String): Boolean {
        val gen = ++generation
        return try {
            repo.randomizeSeed(packageName)
            val fingerprints = withContext(Dispatchers.Default) {
                repo.simulatedFingerprints(packageName)
            }
            if (gen == generation && loadedPackageName == packageName) {
                _uiState.update { it.copy(fingerprints = fingerprints) }
            }
            true
        } catch (e: CancellationException) {
            throw e   // 审计 N-22
        } catch (_: Exception) {
            false
        }
    }

    /** 菜单操作：启动应用（root，与 KSU 同路径）。返回是否成功（审计 N-19）。 */
    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPackageName(packageName)) return@withContext false
        RootShell.exec(
            "cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n"
        ).isSuccess
    }

    /** 菜单操作：强制停止应用。 */
    suspend fun forceStopApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPackageName(packageName)) return@withContext false
        forceStopRaw(packageName).isSuccess
    }

    /** 菜单操作：重启应用（强制停止 + 启动）。 */
    suspend fun restartApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPackageName(packageName)) return@withContext false
        forceStopRaw(packageName)
        RootShell.exec(
            "cmd package resolve-activity --brief $packageName | tail -n 1 | xargs cmd activity start-activity -n"
        ).isSuccess
    }

    private fun forceStopRaw(packageName: String) = RootShell.exec("am force-stop $packageName")

    private fun buildQuickState(packageName: String): AppProfileUiState {
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val label = appInfo?.let {
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        }
        val version = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        val rule = repo.getRule(packageName)
        return AppProfileUiState(
            packageName = packageName,
            label = label,
            versionName = version?.versionName,
            versionCode = version?.longVersionCode ?: 0L,
            applicationInfo = appInfo,
            enabled = rule.enabled,
        )
    }

    companion object {
        /** 包名合法性（审计 N-17）：拼入 root shell 命令前的纵深防御校验。 */
        private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z0-9_.$]+$")

        private fun isValidPackageName(packageName: String): Boolean =
            packageName.matches(PACKAGE_NAME_REGEX)
    }
}
