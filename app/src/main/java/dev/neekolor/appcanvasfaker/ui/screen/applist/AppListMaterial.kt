package dev.neekolor.appcanvasfaker.ui.screen.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.ScrollToTopOnChange
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SearchAppBar
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedItem
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedListItem
import dev.neekolor.appcanvasfaker.ui.component.statustag.StatusTag

@Composable
fun AppListPagerMaterial(
    uiState: AppListUiState,
    actions: AppListActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    var localSearchText by remember { mutableStateOf(uiState.searchStatus.searchText) }
    LaunchedEffect(uiState.searchStatus.searchText) {
        localSearchText = uiState.searchStatus.searchText
    }

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.superuser)) },
                searchText = localSearchText,
                onSearchTextChange = {
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onClearSearch()
                },
                navigationIcon = {
                    IconButton(onClick = actions.onOpenLogs) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Article,
                            contentDescription = stringResource(R.string.settings_sulog)
                        )
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.menu_sort)
                        )

                        DropdownMenuPopup(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortEntries = listOf(
                                AppSortType.NAME to R.string.sort_by_name,
                                AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                                AppSortType.INSTALL_TIME to R.string.sort_by_install_time,
                                AppSortType.UPDATE_TIME to R.string.sort_by_update_time,
                            )

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                                sortEntries.onEachIndexed { index, (type, resId) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = uiState.sortType == type,
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onUpdateSortConfig(type, uiState.reversed)
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortEntries.size
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_reverse)) },
                                    checked = uiState.reversed,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onUpdateSortConfig(uiState.sortType, !uiState.reversed)
                                        showSortMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(
                                        index = 0,
                                        count = 1
                                    ),
                                )
                            }
                        }
                    }

                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDropdown = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenuPopup(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.show_system_apps)) },
                                    checked = uiState.showSystemApps,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onToggleShowSystemApps()
                                        showDropdown = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(localSearchText) {
                        searchListState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp + bottomPadding
                        ),
                    ) {
                        if (uiState.recentlyInstalledResults.isNotEmpty()) {
                            item {
                                SegmentedColumn(
                                    title = stringResource(R.string.recently_installed),
                                    content = uiState.recentlyInstalledResults.map { app ->
                                        @Composable {
                                            AppItem(
                                                app = app,
                                                onClick = {
                                                    closeSearch()
                                                    actions.onOpenProfile(app.packageName)
                                                },
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                searchContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(localSearchText) {
                        searchListState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 0.dp,
                            bottom = 16.dp + bottomPadding
                        ),
                    ) {
                        if (uiState.searchResults.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.search_no_result),
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 24.dp),
                                )
                            }
                        } else {
                            items(
                                uiState.searchResults,
                                key = { it.packageName },
                            ) { app ->
                                AppItem(
                                    app = app,
                                    onClick = {
                                        closeSearch()
                                        actions.onOpenProfile(app.packageName)
                                    },
                                )
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            val latestApps = rememberUpdatedState(uiState.apps)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortType,
                uiState.reversed,
                uiState.showSystemApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestApps.value }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 16.dp + bottomInnerPadding
                ),
            ) {
                itemsIndexed(uiState.apps, key = { _, item -> item.packageName }) { index, app ->
                    SegmentedItem(index = index, count = uiState.apps.size) {
                        AppItem(
                            app = app,
                            onClick = { actions.onOpenProfile(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppListItem,
    onClick: () -> Unit,
) {
    val hookBg = colorScheme.primary
    val hookFg = colorScheme.onPrimary
    val inactiveBg = colorScheme.tertiaryContainer
    val inactiveFg = colorScheme.onTertiaryContainer

    val hookLabel = stringResource(R.string.tag_hook)
    val inactiveLabel = stringResource(R.string.tag_inactive)

    SegmentedListItem(
        onClick = onClick,
        headlineContent = {
            Text(
                text = app.label,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = app.packageName,
                color = colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        leadingContent = {
            if (app.applicationInfo != null) {
                AppIconImage(
                    applicationInfo = app.applicationInfo,
                    label = app.label,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(start = 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.secondaryContainer)
                )
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (app.rule.enabled) {
                    StatusTag(
                        label = hookLabel,
                        backgroundColor = hookBg,
                        contentColor = hookFg
                    )
                } else {
                    StatusTag(
                        label = inactiveLabel,
                        backgroundColor = inactiveBg,
                        contentColor = inactiveFg
                    )
                }
            }
        },
    )
}