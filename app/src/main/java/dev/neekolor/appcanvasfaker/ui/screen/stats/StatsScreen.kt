package dev.neekolor.appcanvasfaker.ui.screen.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.dialog.rememberConfirmDialog
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedListItem
import dev.neekolor.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.neekolor.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import dev.neekolor.appcanvasfaker.ui.navigation3.LocalNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Hook 统计二级页：各应用被 Hook 的累计次数（按次数降序）。
 * 数据全是本地 JSON + prefs 直读，后台线程一次取完，对 Hook 性能无影响。
 */
@Composable
fun StatsScreen() {
    val navigator = LocalNavigator.current
    val onBack = { navigator.pop() }
    val viewModel = viewModel<StatsViewModel>()
    val uiState by viewModel.ui.collectAsStateWithLifecycle()

    when (LocalUiMode.current) {
        UiMode.Miuix -> StatsScreenMiuix(uiState, onBack)
        UiMode.Material -> StatsScreenMaterial(uiState, onBack)
    }
}

private fun formatTime(ts: Long): String =
    if (ts <= 0L) "—"
    else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

@Composable
private fun StatsScreenMiuix(uiState: StatsUiState, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    // 清空统计：对标日志清空，二次确认
    val viewModel = viewModel<StatsViewModel>()
    val clearDialog = rememberConfirmDialog(onConfirm = viewModel::clearStats)
    val clearTitle = stringResource(R.string.stats_clear)
    val clearMessage = stringResource(R.string.stats_clear_confirm)
    val confirmText = stringResource(R.string.confirm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.stats_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MiuixIcon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clearDialog.showConfirm(
                                title = clearTitle,
                                content = clearMessage,
                                confirm = confirmText,
                            )
                        },
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = clearTitle,
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            uiState.rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.stats_empty),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                // 列表与标题拉开一点距离（需求文档：此前首项贴顶）
                item(key = "top_spacing") { Spacer(Modifier.height(8.dp)) }
                items(uiState.rows, key = { it.packageName }) { row ->
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (row.info != null) {
                                AppIconImage(
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(48.dp),
                                    applicationInfo = row.info,
                                    label = row.label
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.label,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight(550),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    row.packageName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight(550),
                                    color = colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatTime(row.lastTime),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Text(
                                "×${row.count}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
                item(key = "footnote") {
                    Text(
                        text = stringResource(R.string.stats_footnote),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsScreenMaterial(uiState: StatsUiState, onBack: () -> Unit) {
    // 清空统计：对标日志清空，二次确认
    val viewModel = viewModel<StatsViewModel>()
    val clearDialog = rememberConfirmDialog(onConfirm = viewModel::clearStats)
    val clearTitle = stringResource(R.string.stats_clear)
    val clearMessage = stringResource(R.string.stats_clear_confirm)
    val confirmText = stringResource(R.string.confirm)
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {
                    IconButton(
                        onClick = {
                            clearDialog.showConfirm(
                                title = clearTitle,
                                content = clearMessage,
                                confirm = confirmText,
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = clearTitle,
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            uiState.rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.stats_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                // 列表与标题拉开一点距离（与 Miuix 同值）
                item(key = "top_spacing") { Spacer(Modifier.height(8.dp)) }
                item {
                    SegmentedColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        content = uiState.rows.map { row ->
                            {
                                SegmentedListItem(
                                    onClick = {},
                                    headlineContent = { Text(row.label) },
                                    supportingContent = {
                                        Column {
                                            Text(
                                                row.packageName,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                formatTime(row.lastTime),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingContent = {
                                        if (row.info != null) {
                                            AppIconImage(
                                                modifier = Modifier.size(48.dp),
                                                applicationInfo = row.info,
                                                label = row.label
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Text(
                                            "×${row.count}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
                item(key = "footnote") {
                    Text(
                        text = stringResource(R.string.stats_footnote),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
