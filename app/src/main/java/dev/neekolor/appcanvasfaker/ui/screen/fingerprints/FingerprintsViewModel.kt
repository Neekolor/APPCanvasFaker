package dev.neekolor.appcanvasfaker.ui.screen.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.FingerprintValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 指纹行展示名：中文环境"中文名英文名（覆盖串）"，非中文环境只显示英文名，覆盖串小一号。 */
@Composable
internal fun fpDisplayTitle(method: String): AnnotatedString {
    // 与主页链分隔符同口径：locales[0] 语言判定
    val isZh = acfApp.resources.configuration.locales[0].language == "zh"
    val zh = stringResource(when (method) {
        "A1" -> R.string.fp_zh_a1
        "A3" -> R.string.fp_zh_a3
        "A4" -> R.string.fp_zh_a4
        "A2" -> R.string.fp_zh_a2
        "E1" -> R.string.fp_zh_e1
        "D1" -> R.string.fp_zh_d1
        else -> R.string.fp_unknown
    })
    val en = stringResource(when (method) {
        "A1" -> R.string.fp_en_a1
        "A3" -> R.string.fp_en_a3
        "A4" -> R.string.fp_en_a4
        "A2" -> R.string.fp_en_a2
        "E1" -> R.string.fp_en_e1
        "D1" -> R.string.fp_en_d1
        else -> R.string.fp_unknown
    })
    val cov = stringResource(when (method) {
        "A1" -> R.string.fp_cov_a1
        "A3" -> R.string.fp_cov_a3
        "A4" -> R.string.fp_cov_a4
        "A2" -> R.string.fp_cov_a2
        "E1" -> R.string.fp_cov_e1
        "D1" -> R.string.fp_cov_d1
        else -> R.string.fp_unknown
    })
    return buildAnnotatedString {
        if (isZh) append(zh)
        append(en)
        withStyle(SpanStyle(fontSize = 12.sp)) { append("($cov)") }
    }
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

    /** 手动重算基线（清缓存后走正常加载）。 */
    fun refreshCache() {
        runCatching { configRepo.clearBaselineCache() }
        refresh()
    }
}
