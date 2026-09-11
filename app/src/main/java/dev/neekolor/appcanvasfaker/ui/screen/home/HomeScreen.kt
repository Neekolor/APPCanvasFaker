package dev.neekolor.appcanvasfaker.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.ui.LocalMainPagerState
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.navigation3.Navigator
import dev.neekolor.appcanvasfaker.ui.navigation3.Route
import dev.neekolor.appcanvasfaker.ui.viewmodel.HomeViewModel

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val mainPagerState = LocalMainPagerState.current

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    // 主页常驻底栏 pager，切 tab 不会重组：refresh 必须跟 isCurrentPage 跑，
    // 否则工具页拨开关/设置改预设后回来，计数、模式、基线全是旧的。
    if (hasActivated) {
        LaunchedEffect(isCurrentPage) {
            if (isCurrentPage) viewModel.refresh()
        }
    }
    // 从 LSPosed 管理器授权回来不经过切页：每次恢复都重探一次，否则通道红字滞留。
    // 首帧由上面的 LaunchedEffect 负责，这里跳过第一次（避免冷启动双刷）。
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedOnce && isCurrentPage) viewModel.refresh()
        resumedOnce = true
        onPauseOrDispose { }
    }

    val actions = HomeActions(
        onOpenHookedApps = { mainPagerState.animateToPage(1) },
        onOpenStats = { navigator.push(Route.Stats) },
        onOpenUrl = uriHandler::openUri,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}