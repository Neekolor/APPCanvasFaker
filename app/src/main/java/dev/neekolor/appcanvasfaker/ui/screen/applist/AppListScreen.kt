package dev.neekolor.appcanvasfaker.ui.screen.applist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.navigation3.Navigator
import dev.neekolor.appcanvasfaker.ui.navigation3.Route

@Composable
fun AppListPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true
) {
    val viewModel = viewModel<AppListViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    if (hasActivated) {
        LaunchedEffect(Unit) {
            viewModel.loadAppList()
        }
    }

    val onSearchTextChange: (String) -> Unit = viewModel::updateSearchText
    val onOpenProfile: (String) -> Unit = { packageName ->
        navigator.push(Route.AppProfile(packageName))
        viewModel.markNeedRefresh()
    }
    val actions = AppListActions(
        onRefresh = { viewModel.loadAppList(force = true) },
        onOpenLogs = { navigator.push(Route.Log) },
        onSearchTextChange = onSearchTextChange,
        onSearchStatusChange = viewModel::updateSearchStatus,
        onClearSearch = { onSearchTextChange("") },
        onToggleShowSystemApps = viewModel::toggleShowSystemApps,
        onUpdateSortConfig = viewModel::updateSortConfig,
        onOpenProfile = onOpenProfile,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AppListPagerMiuix(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> AppListPagerMaterial(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}