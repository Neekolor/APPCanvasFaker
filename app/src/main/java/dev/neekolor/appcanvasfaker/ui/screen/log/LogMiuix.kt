package dev.neekolor.appcanvasfaker.ui.screen.log

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.ListPopupDefaults
import dev.neekolor.appcanvasfaker.ui.component.ScrollToTopOnChange
import dev.neekolor.appcanvasfaker.ui.component.SearchStatus
import dev.neekolor.appcanvasfaker.ui.component.dialog.rememberConfirmDialog
import dev.neekolor.appcanvasfaker.ui.component.miuix.SearchBarFake
import dev.neekolor.appcanvasfaker.ui.component.miuix.SearchBox
import dev.neekolor.appcanvasfaker.ui.component.miuix.SearchPager
import dev.neekolor.appcanvasfaker.ui.component.statustag.StatusTag
import dev.neekolor.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.neekolor.appcanvasfaker.ui.util.BlurredBar
import dev.neekolor.appcanvasfaker.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LogScreenMiuix(
    state: LogUiState,
    actions: LogActions,
) {
    val enableBlur = LocalEnableBlur.current
    val density = LocalDensity.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val searchHint = stringResource(R.string.log_search_hint)
    val refreshTexts = listOf(
        stringResource(R.string.refresh_pulling),
        stringResource(R.string.refresh_release),
        stringResource(R.string.refresh_refresh),
        stringResource(R.string.refresh_complete),
    )
    var searchStatus by remember { mutableStateOf(SearchStatus(searchHint)) }
    val clearDialog = rememberConfirmDialog(onConfirm = actions.onClear)

    val emptyText = stringResource(R.string.log_empty)
    val clearTitle = stringResource(R.string.log_clear)
    val clearMessage = stringResource(R.string.log_clear_confirm)
    val confirmText = stringResource(R.string.confirm)

    LaunchedEffect(state.searchText, state.visibleItems) {
        searchStatus = searchStatus.copy(
            searchText = state.searchText,
            resultStatus = if (state.searchText.isBlank()) SearchStatus.ResultStatus.DEFAULT else SearchStatus.ResultStatus.SHOW,
        )
    }

    fun onSearchStatusChange(nextStatus: SearchStatus) {
        searchStatus = nextStatus.copy(
            resultStatus = if (nextStatus.searchText.isBlank()) SearchStatus.ResultStatus.DEFAULT else SearchStatus.ResultStatus.SHOW,
        )
        actions.onSearchTextChange(nextStatus.searchText)
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.log_title),
                        navigationIcon = {
                            IconButton(
                                onClick = actions.onBack,
                            ) {
                                val layoutDirection = LocalLayoutDirection.current
                                Icon(
                                    modifier = Modifier.graphicsLayer {
                                        if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                    },
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                modifier = Modifier.padding(end = 8.dp),
                                onClick = {
                                    clearDialog.showConfirm(
                                        title = clearTitle,
                                        content = clearMessage,
                                        confirm = confirmText,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    tint = colorScheme.onSurface,
                                    contentDescription = stringResource(R.string.log_clear),
                                )
                            }

                            Box {
                                val showFilterPopup = remember { mutableStateOf(false) }
                                OverlayListPopup(
                                    show = showFilterPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = {
                                        showFilterPopup.value = false
                                    },
                                    content = {
                                        ListPopupColumn {
                                            LogFilter.entries.forEachIndexed { index, filter ->
                                                DropdownImpl(
                                                    text = logFilterLabel(filter),
                                                    isSelected = when (filter) {
                                                        LogFilter.ALL -> state.selectedFilters.isEmpty()
                                                        else -> filter.tag in state.selectedFilters
                                                    },
                                                    optionSize = LogFilter.entries.size,
                                                    onSelectedIndexChange = {
                                                        actions.onToggleFilter(filter)
                                                    },
                                                    index = index,
                                                )
                                            }
                                        }
                                    },
                                )
                                IconButton(
                                    onClick = { showFilterPopup.value = true },
                                    holdDownState = showFilterPopup.value,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Filter,
                                        tint = colorScheme.onSurface,
                                        contentDescription = stringResource(R.string.log_filter_all),
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    onSearchStatusChange(searchStatus.copy(current = SearchStatus.Status.EXPANDING))
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = ::onSearchStatusChange,
                defaultResult = { },
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                ) {
                    item {
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!state.loggingEnabled) {
                        item(key = "logging_disabled_banner") {
                            LoggingDisabledBannerMiuix(onOpenSettings = actions.onOpenSettings)
                        }
                    }
                    logEntriesSection(
                        items = state.visibleItems,
                        emptyText = emptyText,
                    )
                    item {
                        Spacer(Modifier.height(imeBottomPadding))
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            PullToRefresh(
                isRefreshing = false,
                pullToRefreshState = pullToRefreshState,
                onRefresh = actions.onRefresh,
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
            ) {
                val latestVisibleItems = rememberUpdatedState(state.visibleItems)
                ScrollToTopOnChange(
                    listState,
                    state.searchText,
                    state.selectedFilters,
                ) { latestVisibleItems.value }
                Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                        ),
                        overscrollEffect = null,
                    ) {
                        if (!state.loggingEnabled) {
                            item(key = "logging_disabled_banner") {
                                LoggingDisabledBannerMiuix(onOpenSettings = actions.onOpenSettings)
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
                                            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.logEntriesSection(
    items: List<LogItem>,
    emptyText: String,
) {
    if (items.isEmpty()) {
        item {
            LogEmptyCard(
                modifier = Modifier.fillParentMaxSize(),
                text = emptyText,
            )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
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
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
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
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.timeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogEmptyCard(
    modifier: Modifier,
    text: String,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight(550),
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun LoggingDisabledBannerMiuix(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.defaultColors(color = colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.log_disabled_banner),
                fontSize = 13.sp,
                color = colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            TextButton(
                text = stringResource(R.string.log_disabled_action),
                onClick = onOpenSettings,
            )
        }
    }
}
