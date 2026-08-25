package dev.nikko.appcanvasfaker.ui.screen.cfc

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nikko.appcanvasfaker.acfApp
import dev.nikko.appcanvasfaker.core.ConfigRepository
import dev.nikko.appcanvasfaker.scanner.fingerprint.FingerprintCollector
import dev.nikko.appcanvasfaker.scanner.fingerprint.FingerprintResult
import dev.nikko.appcanvasfaker.scanner.fingerprint.HardwareReaders
import dev.nikko.appcanvasfaker.scanner.fingerprint.ViewCapturers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CfcCapture(
    val hash: String? = null,
    val elapsedMs: Long? = null,
)

data class CfcUiState(
    val syncResults: List<FingerprintResult> = emptyList(),
    val collectingAll: Boolean = false,
    val c1: CfcCapture = CfcCapture(),
    val c2: CfcCapture = CfcCapture(),
    val d1: CfcCapture = CfcCapture(),
)

class CfcViewModel : ViewModel() {

    private val configRepository = ConfigRepository(acfApp)

    private val _uiState = MutableStateFlow(CfcUiState())
    val uiState: StateFlow<CfcUiState> = _uiState.asStateFlow()

    /** A/B/E/F 组同步采集：全部在 Default 调度器执行，不占主线程。 */
    fun collectAll(context: Context) {
        if (_uiState.value.collectingAll) return
        _uiState.update { it.copy(collectingAll = true) }
        viewModelScope.launch {
            val results = withContext(Dispatchers.Default) {
                FingerprintCollector.collectSync(context)
            }
            _uiState.update { it.copy(collectingAll = false, syncResults = results) }
        }
    }

    /**
     * 自 Hook 随机化：为模块自身写入"启用 + 新 seed"规则。LSPosed 对模块自身
     * 强制生效作用域，重启管理器进程后，「哈希测试」页的采集值即为伪装结果。
     */
    fun randomizeSelf(onCompleted: () -> Unit) {
        viewModelScope.launch {
            val selfPackage = acfApp.packageName
            configRepository.setHookEnabled(selfPackage, true)
            configRepository.randomizeSeed(selfPackage)
            onCompleted()
        }
    }

    /** C1：View 测量/布局/绘制缓存必须在主线程执行（小视图，耗时可控）。 */
    fun runC1(activity: Activity) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val hash = ViewCapturers.buildOffscreen(activity)
            _uiState.update {
                it.copy(c1 = CfcCapture(hash, System.currentTimeMillis() - start))
            }
        }
    }

    /** C2：PixelCopy 异步回调采集整窗帧缓冲。 */
    fun runC2(activity: Activity) {
        val metrics = activity.resources.displayMetrics
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val hash = ViewCapturers.pixelCopy(activity, metrics.widthPixels, metrics.heightPixels)
            _uiState.update {
                it.copy(c2 = CfcCapture(hash, System.currentTimeMillis() - start))
            }
        }
    }

    /** D1：离屏 EGL + glReadPixels。 */
    fun runD1() {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val hash = withContext(Dispatchers.Default) {
                HardwareReaders.glReadPixels()
            }
            _uiState.update {
                it.copy(d1 = CfcCapture(hash, System.currentTimeMillis() - start))
            }
        }
    }
}
