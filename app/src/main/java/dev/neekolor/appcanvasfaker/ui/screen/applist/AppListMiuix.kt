package dev.neekolor.appcanvasfaker.ui.screen.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.ListPopupDefaults
import dev.neekolor.appcanvasfaker.ui.component.ScrollToTopOnChange
import dev.neekolor.appcanvasfaker.ui.component.SearchStatus
import dev.neekolor.appcanvasfaker.ui.component.miuix.SearchBarFake
import dev.neekolor.appcanvasfaker.ui.component.miuix.SearchBox
import dev.neekolor.appcanvasfaker.ui.component.miuix.SearchPager
import dev.neekolor.appcanvasfaker.ui.component.statustag.StatusTag
import dev.neekolor.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.neekolor.appcanvasfaker.ui.theme.isInDarkTheme
import dev.neekolor.appcanvasfaker.ui.util.BlurredBar
import dev.neekolor.appcanvasfaker.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AppListPagerMiuix(
    uiState: AppListUiState,
    actions: AppListActions,
    bottomInnerPadding: Dp,
) {
    val searchStatus = uiState.searchStatus
    // pointerInput(Unit) 只执行一次，手势回调需经 rememberUpdatedState 读取最新状态
    val latestSearchStatus = rememberUpdatedState(searchStatus)
    val enableBlur = LocalEnableBlur.current
    val density = LocalDensity.current

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.superuser),
                        navigationIcon = {
                            IconButton(
                                onClick = actions.onOpenLogs,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Notes,
                                    tint = colorScheme.onSurface,
                                    contentDescription = stringResource(R.string.settings_sulog)
                                )
                            }
                        },
                        actions = {
                            Box {
                                val showSortPopup = remember { mutableStateOf(false) }
                                OverlayListPopup(
                                    show = showSortPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showSortPopup.value = false },
                                    content = {
                                        ListPopupColumn {
                                            val sortEntries = listOf(
                                                AppSortType.NAME to R.string.sort_by_name,
                                                AppSortType.PACKAGE_NAME to R.string.sort_by_package_name,
                                                AppSortType.INSTALL_TIME to R.string.sort_by_install_time,
                                                AppSortType.UPDATE_TIME to R.string.sort_by_update_time,
                                            )
                                            val sortGroupSize = sortEntries.size + 1

                                            sortEntries.forEachIndexed { index, (type, resId) ->
                                                DropdownImpl(
                                                    text = stringResource(resId),
                                                    optionSize = sortGroupSize,
                                                    isSelected = uiState.sortType == type,
                                                    index = index,
                                                    onSelectedIndexChange = {
                                                        actions.onUpdateSortConfig(type, uiState.reversed)
                                                        showSortPopup.value = false
                                                    }
                                                )
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                                thickness = 1.5.dp,
                                            )

                                            DropdownImpl(
                                                text = stringResource(R.string.sort_reverse),
                                                optionSize = sortGroupSize,
                                                isSelected = uiState.reversed,
                                                index = sortEntries.size,
                                                onSelectedIndexChange = {
                                                    actions.onUpdateSortConfig(uiState.sortType, !uiState.reversed)
                                                    showSortPopup.value = false
                                                }
                                            )
                                        }
                                    }
                                )

                                IconButton(
                                    onClick = { showSortPopup.value = true },
                                    holdDownState = showSortPopup.value,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        tint = colorScheme.onSurface,
                                        contentDescription = stringResource(R.string.menu_sort)
                                    )
                                }
                            }

                            Box {
                                val showTopPopup = remember { mutableStateOf(false) }
                                OverlayListPopup(
                                    show = showTopPopup.value,
                                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showTopPopup.value = false },
                                    content = {
                                        ListPopupColumn {
                                            DropdownImpl(
                                                text = stringResource(R.string.show_system_apps),
                                                isSelected = uiState.showSystemApps,
                                                optionSize = 1,
                                                onSelectedIndexChange = {
                                                    actions.onToggleShowSystemApps()
                                                    showTopPopup.value = false
                                                },
                                                index = 0
                                            )
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = { showTopPopup.value = true },
                                    holdDownState = showTopPopup.value
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.MoreCircle,
                                        tint = colorScheme.onSurface,
                                        contentDescription = null
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
                                                actions.onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    val s = latestSearchStatus.value
                                                    actions.onSearchStatusChange(s.copy(current = SearchStatus.Status.EXPANDING))
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
                onSearchStatusChange = actions.onSearchStatusChange,
                defaultResult = {
                    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                    if (uiState.recentlyInstalledResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .overScrollVertical(),
                        ) {
                            item {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.recently_installed),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                                )
                            }
                            items(
                                uiState.recentlyInstalledResults,
                                key = { it.packageName },
                                contentType = { "recent-app" }
                            ) { app ->
                                AppItem(
                                    app = app,
                                    onClick = { actions.onOpenProfile(app.packageName) },
                                )
                            }
                            item {
                                Spacer(Modifier.height(maxOf(bottomInnerPadding, imeBottomPadding)))
                            }
                        }
                    }
                },
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
                    if (uiState.searchResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.search_no_result),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {
                        items(uiState.searchResults, key = { it.packageName }, contentType = { "app" }) { app ->
                            AppItem(
                                app = app,
                                onClick = { actions.onOpenProfile(app.packageName) },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(maxOf(bottomInnerPadding, imeBottomPadding)))
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            val lazyListState = rememberLazyListState()
            val refreshTick = remember { mutableIntStateOf(0) }
            val latestApps = rememberUpdatedState(uiState.apps)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                lazyListState,
                uiState.sortType,
                uiState.reversed,
                uiState.showSystemApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestApps.value }
            val pullToRefreshState = rememberPullToRefreshState()
            val refreshTexts = listOf(
                stringResource(R.string.refresh_pulling),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh),
                stringResource(R.string.refresh_complete),
            )

            PullToRefresh(
                isRefreshing = uiState.isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = {
                    actions.onRefresh()
                    refreshTick.intValue++
                },
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                ),
            ) {
                Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection)
                        ),
                        overscrollEffect = null,
                    ) {
                        items(uiState.apps, key = { it.packageName }, contentType = { "app" }) { app ->
                            AppItem(
                                app = app,
                                onClick = { actions.onOpenProfile(app.packageName) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(bottomInnerPadding))
                        }
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
    val isInDarkTheme = isInDarkTheme()
    val hookBg = colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val hookFg = colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    val inactiveBg = if (isInDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
    val inactiveFg = if (isInDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.8f)

    val hookLabel = stringResource(R.string.tag_hook)
    val inactiveLabel = stringResource(R.string.tag_inactive)

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        onClick = onClick,
        showIndication = true,
        insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.applicationInfo != null) {
                AppIconImage(
                    applicationInfo = app.applicationInfo,
                    label = app.label,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(48.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.secondaryContainer)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = app.label,
                    modifier = Modifier.basicMarquee(),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = app.packageName,
                    modifier = Modifier.basicMarquee(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp),
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
            val layoutDirection = LocalLayoutDirection.current
            Image(
                modifier = Modifier
                    .graphicsLayer {
                        if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                    }
                    .padding(start = 8.dp)
                    .size(width = 10.dp, height = 16.dp),
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
            )
        }
    }
}