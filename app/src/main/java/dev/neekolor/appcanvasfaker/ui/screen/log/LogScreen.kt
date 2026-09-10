package dev.neekolor.appcanvasfaker.ui.screen.log

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
import dev.neekolor.appcanvasfaker.ui.viewmodel.MainActivityViewModel

@Composable
fun LogScreen() {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val viewModel = viewModel<LogViewModel>()
    // 设置页是主页 tab（序号 3）而非独立路由：先切 tab 再退栈，避免污染返回栈
    val mainViewModel = viewModel<MainActivityViewModel>(LocalContext.current as ComponentActivity)
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
        onOpenSettings = dropUnlessResumed {
            mainViewModel.setSelectedMainPage(3)
            navigator.pop()
        },
        onSelectDate = viewModel::selectDate,
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
        // 筛选项与条目 tag 统一显示 Random（存量"随机化"仅作筛选键，见 logTagLabel）
        LogFilter.RANDOMIZE -> "Random"
    }
}

/** 详情弹窗全量文本：基本行 + 技术字段（su log 式等宽展示）。老条目无技术字段时只显示基本行。 */
fun logDetailText(item: LogItem): String {
    val lines = ArrayList<String>(8)
    lines.add(item.appLabel)
    lines.add(item.packageName)
    lines.add("${logTagLabel(item.tag)} · ${item.timeText}")
    item.path?.let { lines.add("path=$it") }
    item.seed?.let { lines.add("seed=$it") }
    if (item.hitCount != null || item.mode != null) {
        lines.add("hit=#${item.hitCount ?: "?"} mode=${item.mode ?: "?"}")
    }
    if (item.newHash != null || item.moved != null) {
        // 英文短词：false 易被误读为出错，用 Stable/Changed/First 三态直述画像状态
        val movedText = when (item.moved) {
            true -> "Changed"
            false -> "Stable"
            null -> "First"
        }
        val hashText = if (item.oldHash != null) "${item.oldHash} -> ${item.newHash}"
            else item.newHash.orEmpty()
        lines.add("$movedText $hashText".trim())
    }
    return lines.joinToString("\n")
}