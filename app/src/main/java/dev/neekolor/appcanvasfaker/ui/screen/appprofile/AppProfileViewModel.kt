package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.ScopeGate
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
     * 代次计数：每次 load 自增，随机化/开关等异步写回前比对代次，
     * 防止旧 seed 的指纹计算结果覆盖新状态。
     */
    private var generation = 0

    fun load(packageName: String) {
        if (loadedPackageName == packageName) return
        loadedPackageName = packageName
        loadJob?.cancel()
        val gen = ++generation
        loadJob = viewModelScope.launch {
            // 应用基本信息立即上屏（对齐 KSU：头部卡不等任何重活）
            val quick = withContext(Dispatchers.IO) {
                buildQuickState(packageName)
            }
            if (gen == generation && loadedPackageName == packageName) {
                // 切包：旧包的对照卡不带过来
                _uiState.value = quick.copy(reseed = null)
                refreshScope(packageName)
            }
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val gen = ++generation
        viewModelScope.launch {
            repo.setHookEnabled(packageName, enabled)
            if (gen == generation && loadedPackageName == packageName) {
                _uiState.update { it.copy(enabled = enabled) }
            }
        }
    }

    /** 随机化 seed 并给出新旧对照；返回是否成功。 */
    suspend fun randomize(packageName: String): Boolean {
        val gen = ++generation
        return try {
            val oldSeed = repo.getRule(packageName).seed
            val newSeed = repo.randomizeSeed(packageName)
            val (oldA1, newA1) = withContext(Dispatchers.Default) {
                repo.trialA1(oldSeed) to repo.trialA1(newSeed)
            }
            if (gen == generation && loadedPackageName == packageName) {
                _uiState.update { it.copy(reseed = ReseedPreview(oldSeed, newSeed, oldA1, newA1)) }
            }
            true
        } catch (e: CancellationException) {
            // 取消继续向上传播，不视为操作失败
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** 作用域门禁重查（binder，IO 线程）：未知时保持 null，UI 不误报。 */
    fun refreshScope(packageName: String) {
        if (loadedPackageName != packageName) return
        viewModelScope.launch(Dispatchers.IO) {
            val inScope = runCatching { ScopeGate.scopedPackages()?.contains(packageName) }.getOrNull()
            if (loadedPackageName == packageName) {
                _uiState.update { it.copy(scopeInScope = inScope) }
            }
        }
    }

    /**
     * 申请加作用域：结果回调在 binder 线程，切回主线程后刷新门禁并回执。
     * 获批≠即时生效，目标应用需重启（提示文案会讲）。
     */
    fun requestScope(packageName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            ScopeGate.requestScope(packageName) { approved ->
                viewModelScope.launch {
                    if (approved) refreshScope(packageName)
                    onDone(approved)
                }
            }
        }
    }
    /** 菜单操作：启动应用（root，与 KSU 同路径）。返回是否成功。 */
    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPackageName(packageName)) return@withContext false
        val pkg = RootShell.shellQuote(packageName)
        RootShell.exec(
            "cmd package resolve-activity --brief $pkg | tail -n 1 | xargs cmd activity start-activity -n"
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
        val pkg = RootShell.shellQuote(packageName)
        RootShell.exec(
            "cmd package resolve-activity --brief $pkg | tail -n 1 | xargs cmd activity start-activity -n"
        ).isSuccess
    }

    private fun forceStopRaw(packageName: String) =
        RootShell.exec("am force-stop ${RootShell.shellQuote(packageName)}")

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
        /** 包名合法性：拼入 root shell 命令前的纵深防御校验。 */
        private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z0-9_.]+$")

        private fun isValidPackageName(packageName: String): Boolean =
            packageName.matches(PACKAGE_NAME_REGEX)
    }
}
