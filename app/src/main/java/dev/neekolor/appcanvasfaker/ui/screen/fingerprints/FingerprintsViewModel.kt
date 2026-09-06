package dev.neekolor.appcanvasfaker.ui.screen.fingerprints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.FingerprintValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 指纹基准行：method + 括号内 title 简写，如 "A1（像素直读）"。 */
internal fun FingerprintValue.displayTitle(): String {
    val short = title.substringBefore("（").ifBlank { title }
    return "$method（$short）"
}

data class FingerprintsUiState(
    val items: List<FingerprintValue> = emptyList(),
    val isLoading: Boolean = true
)

/** 指纹基准页：本机未污染的标准指纹值（模块自身不可被 Hook，恒为基准）。 */
class FingerprintsViewModel(
    private val configRepo: ConfigRepository = ConfigRepository(acfApp)
) : ViewModel() {

    private val _ui = MutableStateFlow(FingerprintsUiState())
    val ui: StateFlow<FingerprintsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            _ui.value = FingerprintsUiState(isLoading = true)
            val items = runCatching { configRepo.standardFingerprints() }.getOrDefault(emptyList())
            _ui.value = FingerprintsUiState(items, false)
        }
    }
}
