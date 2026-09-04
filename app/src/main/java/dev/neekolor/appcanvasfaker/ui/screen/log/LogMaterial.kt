package dev.neekolor.appcanvasfaker.ui.screen.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.ScrollToTopOnChange
import dev.neekolor.appcanvasfaker.ui.component.dialog.rememberConfirmDialog
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SearchAppBar
import dev.neekolor.appcanvasfaker.ui.component.material.TonalCard
import dev.neekolor.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.neekolor.appcanvasfaker.ui.component.statustag.StatusTag

@Composable
fun LogScreenMaterial(
    state: LogUiState,
    actions: LogActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var showFilterMenu by remember { mutableStateOf(false) }
    var localSearchText by remember { mutableStateOf(state.searchText) }
    val clearDialog = rememberConfirmDialog(onConfirm = actions.onClear)

    LaunchedEffect(state.searchText) {
        localSearchText = state.searchText
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val emptyText = stringResource(R.string.log_empty)
    val clearTitle = stringResource(R.string.log_clear)
    val clearMessage = stringResource(R.string.log_clear_confirm)
    val confirmText = stringResource(R.string.confirm)

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.log_title)) },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onSearchTextChange("")
                },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    IconButton(onClick = {
                        clearDialog.showConfirm(
                            title = clearTitle,
                            content = clearMessage,
                            confirm = confirmText,
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.log_clear),
                        )
                    }
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.log_filter_all),
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                    ) {
                        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                            LogFilter.entries.forEachIndexed { index, filter ->
                                DropdownMenuItem(
                                    text = { Text(logFilterLabel(filter)) },
                                    checked = when (filter) {
                                        LogFilter.ALL -> state.selectedFilters.isEmpty()
                                        else -> filter.tag in state.selectedFilters
                                    },
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onToggleFilter(filter)
                                    },
                                    shapes = MenuDefaults.itemShape(index = index, count = LogFilter.entries.size),
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                searchContent = { bottomPadding, _ ->
                    val latestVisibleItems = rememberUpdatedState(state.visibleItems)
                    ScrollToTopOnChange(
                        searchListState,
                        state.searchText,
                    ) { latestVisibleItems.value }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp + bottomPadding,
                        ),
                    ) {
                        if (!state.loggingEnabled) {
                            item(key = "logging_disabled_banner") {
                                TonalCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    containerColor = colorScheme.errorContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                        )
                                        Text(
                                            text = stringResource(R.string.log_disabled_banner),
                                            style = typography.bodyMedium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp),
                                        )
                                        TextButton(onClick = actions.onOpenSettings) {
                                            Text(stringResource(R.string.log_disabled_action))
                                        }
                                    }
                                }
                            }
                        }
                        logEntriesSection(
                            items = state.visibleItems,
                            emptyText = emptyText,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = false,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = false,
                    state = pullToRefreshState,
                )
            },
        ) {
            val latestEntries = rememberUpdatedState(state.visibleItems)
            ScrollToTopOnChange(
                listState,
                state.selectedFilters,
            ) { latestEntries.value }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                if (!state.loggingEnabled) {
                    item(key = "logging_disabled_banner") {
                        TonalCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            containerColor = colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                )
                                Text(
                                    text = stringResource(R.string.log_disabled_banner),
                                    style = typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                )
                                TextButton(onClick = actions.onOpenSettings) {
                                    Text(stringResource(R.string.log_disabled_action))
                                }
                            }
                        }
                    }
                }
                logEntriesSection(
                    items = state.visibleItems,
                    emptyText = emptyText,
                )

                item {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                                    16.dp
                        )
                    )
                }
            }
        }
    }
}

private fun LazyListScope.logEntriesSection(
    items: List<LogItem>,
    emptyText: String,
) {
    if (items.isEmpty()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = typography.bodyLarge.fontSize,
                )
            }
        }
        return
    }
    itemsIndexed(items, key = { index, item -> "$index-${item.timestamp}-${item.tag}-${item.packageName}" }) { _, item ->
        LogEntryCard(item = item)
    }
}

@Composable
private fun LogEntryCard(
    item: LogItem,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.applicationInfo?.let { appInfo ->
                    AppIconImage(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(40.dp),
                        applicationInfo = appInfo,
                        label = item.appLabel,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.appLabel,
                            modifier = Modifier.weight(1f),
                            style = typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val (bg, fg) = if (item.tag == LogFilter.RANDOMIZE.tag) {
                            colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
                        } else {
                            colorScheme.primary to colorScheme.onPrimary
                        }
                        StatusTag(label = item.tag, backgroundColor = bg, contentColor = fg)
                    }
                    Text(
                        text = item.packageName,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.timeText,
                        style = typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    )
}