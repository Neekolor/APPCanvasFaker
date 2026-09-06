package dev.neekolor.appcanvasfaker.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.ui.LocalMainPagerState
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.navigation3.Navigator
import dev.neekolor.appcanvasfaker.ui.navigation3.Route
import dev.neekolor.appcanvasfaker.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mainPagerState = LocalMainPagerState.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,

        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onSetEnableLogging = viewModel::setEnableLogging,
        onSetHookTextMetrics = viewModel::setHookTextMetrics,
        onSetHookGlReadPixels = viewModel::setHookGlReadPixels,
        onSetSsaidEnabled = viewModel::setSsaidEnabled,
        onOpenToolset = { mainPagerState.animateToPage(2) },
        onOpenSsaid = { navigator.push(Route.Ssaid) },
        onOpenLog = { navigator.push(Route.Log) },
        onOpenAbout = { navigator.push(Route.About) },
        onSetPreset = viewModel::applyPreset,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}