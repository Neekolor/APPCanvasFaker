package dev.neekolor.appcanvasfaker.ui.screen.stats

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Hook 统计行：应用名 + 包名 + 累计次数 + 末次时间 + 图标信息（可空）。 */
data class StatRow(
    val label: String,
    val packageName: String,
    val enabled: Boolean,
    val count: Long,
    val lastTime: Long,
    val info: ApplicationInfo?
)

data class StatsUiState(
    val rows: List<StatRow> = emptyList(),
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val configRepo: ConfigRepository = ConfigRepository(acfApp)
) : ViewModel() {

    private val _ui = MutableStateFlow(StatsUiState())
    val ui: StateFlow<StatsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            _ui.value = StatsUiState(isLoading = true)
            val pm = acfApp.packageManager
            val infoMap = runCatching {
                pm.getInstalledApplications(0).associateBy { it.packageName }
            }.getOrDefault(emptyMap())
            val rows = configRepo.hookStats()
                .filter { it.count > 0 }
                .map { s ->
                val info = infoMap[s.packageName]
                val label = info?.let {
                    runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(s.packageName)
                } ?: s.packageName
                StatRow(label, s.packageName, s.enabled, s.count, s.lastTime, info)
            }
            _ui.value = StatsUiState(rows, false)
        }
    }
}
