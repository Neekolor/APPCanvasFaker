package dev.neekolor.appcanvasfaker.ui.screen.ssaid

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.core.SsaidManager
import dev.neekolor.appcanvasfaker.util.RootShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SSAID 管理页：读取 settings_ssaid.xml 全部条目成列表，支持随机化/删除（均需 root）。
 * 操作经 SsaidManager 内部 Mutex 串行化（审计 N-07）；本 VM 用代次计数防止过期刷新
 * 覆盖新状态（审计 N-05 同款模式），所有挂起调用显式放行取消（审计 N-22）。
 */
class SsaidViewModel(application: Application) : AndroidViewModel(application) {

    private val pm = application.packageManager
    // 与应用列表页共用同一 prefs 文件（键名区分），"显示系统应用"开关跨会话记忆
    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SsaidUiState())
    val uiState: StateFlow<SsaidUiState> = _uiState.asStateFlow()

    /** 代次计数：每次 load 自增，过期协程结果一律丢弃（审计 N-05 模式）。 */
    private var generation = 0

    init {
        _uiState.update { it.copy(showSystemApps = prefs.getBoolean(KEY_SHOW_SYSTEM, false)) }
        refresh()
    }

    fun refresh() {
        val gen = ++generation
        viewModelScope.launch {
            _uiState.update { it.copy(loadState = SsaidLoadState.LOADING) }
            val state = withContext(Dispatchers.IO) { buildState(gen) }
            if (gen == generation) {
                _uiState.value = state
            }
        }
    }

    /**
     * 随机化指定条目：先强制停止目标应用（铁律），再真实改写系统文件。
     * 返回 (written, reloaded)；busyPkg 在整个操作期间占用以互斥其他操作。
     */
    suspend fun randomize(packageName: String): Pair<Boolean, Boolean> =
        operate(packageName) { SsaidManager.randomize(packageName) }

    /** 删除指定条目：同样先强制停止目标应用。 */
    suspend fun delete(packageName: String): Pair<Boolean, Boolean> =
        operate(packageName) { SsaidManager.delete(packageName) }

    private suspend fun operate(
        packageName: String,
        action: suspend () -> SsaidManager.WriteResult,
    ): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        // 审计 N-17：包名拼入 root shell 与系统文件前做格式校验（纵深防御）
        if (!packageName.matches(PACKAGE_NAME_REGEX)) {
            return@withContext false to false
        }
        val result = runCatching { action() }
            .recoverCatching { e ->
                // 审计 N-22：取消必须继续向上传播，不能被 runCatching 吞成"失败"
                if (e is CancellationException) throw e
                SsaidManager.WriteResult(written = false, reloaded = false)
            }.getOrDefault(SsaidManager.WriteResult(written = false, reloaded = false))
        if (result.written) {
            // 写入成功即整表重读（条目新增/值变化/删除都要反映到列表）
            val gen = ++generation
            val state = buildState(gen)
            if (gen == generation) {
                _uiState.value = state
            }
        }
        result.written to result.reloaded
    }

    /** Screen 在确认弹窗回调里先占 busy，再调 randomize/delete，结束后释放。 */
    fun setBusy(packageName: String?) {
        _uiState.update { it.copy(busyPkg = packageName) }
    }

    /**
     * 重启手机（root）：SSAID 写盘后的生效手段。
     * 调用方保证已二次确认，且在 IO 上下文调用（RootShell 要求）。
     */
    suspend fun reboot(): Boolean = withContext(Dispatchers.IO) {
        RootShell.exec("reboot").isSuccess
    }

    /** 顶栏菜单切换"显示系统应用"：持久化后整表重读（过滤在 buildState 内做）。 */
    fun toggleShowSystemApps() {
        val newValue = !_uiState.value.showSystemApps
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM, newValue).apply()
        _uiState.update { it.copy(showSystemApps = newValue) }
        refresh()
    }

    private suspend fun buildState(gen: Int): SsaidUiState {
        if (!RootShell.isAvailable()) {
            return SsaidUiState(loadState = SsaidLoadState.UNAVAILABLE)
        }
        val showSystemApps = _uiState.value.showSystemApps
        val entries = runCatching { SsaidManager.listEntries() }
            .recoverCatching { e ->
                if (e is CancellationException) throw e
                null
            }.getOrNull()
            ?: return SsaidUiState(showSystemApps = showSystemApps, loadState = SsaidLoadState.FAILED)
        // 列表严格保持 settings_ssaid.xml 文件原始条目顺序（不做名称排序）
        val all = entries.map { entry ->
            val appInfo = runCatching { pm.getApplicationInfo(entry.packageName, 0) }.getOrNull()
            val label = appInfo?.let {
                runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
            }
            SsaidItemUi(
                packageName = entry.packageName,
                value = entry.value,
                label = label,
                applicationInfo = appInfo as ApplicationInfo?,
            )
        }
        val items = if (showSystemApps) all else all.filterNot { it.isSystemApp }
        // 过期保护（审计 N-05）：仅提交最新代次
        return if (gen == generation) {
            SsaidUiState(items = items, showSystemApps = showSystemApps, loadState = SsaidLoadState.READY)
        } else {
            _uiState.value
        }
    }

    companion object {
        /** 包名合法性（审计 N-17）：拼入 root shell 与系统文件前的纵深防御校验。 */
        private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z0-9_.$]+$")

        /** 与 AppListViewModel 共用 prefs 文件，键名加 ssaid 前缀避免冲突。 */
        private const val PREFS_NAME = "app_list"
        private const val KEY_SHOW_SYSTEM = "ssaid_show_system_apps"
    }
}
