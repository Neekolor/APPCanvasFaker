package dev.neekolor.appcanvasfaker.ui.screen.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.navigation3.LocalNavigator
import dev.neekolor.appcanvasfaker.ui.viewmodel.LogViewModel

@Composable
fun LogScreen() {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val viewModel = viewModel<LogViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 返回本页即刷新：开关状态（横幅显隐）与条目都可能在设置页被改变，
    // 与 SettingPager 的 LifecycleResumeEffect 同模式（一次磁盘 IO，可接受）
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // 首帧数据由 ViewModel.init 的 refresh() 负责；手动刷新走 actions.onRefresh

    val actions = LogActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onRefresh = viewModel::refresh,
        onClear = viewModel::clearLogs,
        onSearchTextChange = viewModel::setSearchText,
        onToggleFilter = { filter -> viewModel.toggleFilter(filter.tag) },
        onOpenSettings = dropUnlessResumed { navigator.push(dev.neekolor.appcanvasfaker.ui.navigation3.Route.Settings) },
    )

    when (uiMode) {
        UiMode.Miuix -> LogScreenMiuix(uiState, actions)
        UiMode.Material -> LogScreenMaterial(uiState, actions)
    }
}

@Composable
fun logFilterLabel(filter: LogFilter): String {
    return when (filter) {
        LogFilter.ALL -> stringResource(R.string.log_filter_all)
        LogFilter.HOOK -> stringResource(R.string.log_filter_hook)
        LogFilter.RANDOMIZE -> stringResource(R.string.log_filter_randomize)
    }
}