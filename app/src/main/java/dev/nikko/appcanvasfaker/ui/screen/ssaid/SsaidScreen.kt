package dev.nikko.appcanvasfaker.ui.screen.ssaid

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.ui.LocalUiMode
import dev.nikko.appcanvasfaker.ui.UiMode
import dev.nikko.appcanvasfaker.ui.component.AppIconImage
import dev.nikko.appcanvasfaker.ui.component.dialog.rememberConfirmDialog
import dev.nikko.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.nikko.appcanvasfaker.ui.component.material.SnackBarHost
import dev.nikko.appcanvasfaker.ui.component.material.TonalCard
import dev.nikko.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.nikko.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import dev.nikko.appcanvasfaker.ui.navigation3.LocalNavigator
import dev.nikko.appcanvasfaker.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** SSAID 管理页：settings_ssaid.xml 全部条目的列表 + 逐条随机化/删除（root，真写系统文件）。 */
@Composable
fun SsaidScreen() {
    val uiMode = LocalUiMode.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewModel = viewModel<SsaidViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val confirmTitle = stringResource(R.string.confirm)
    val actionText = stringResource(R.string.action)
    val deleteText = stringResource(R.string.delete)
    val randomizeConfirm = stringResource(R.string.ssaid_randomize_confirm)
    val deleteConfirm = stringResource(R.string.ssaid_delete_confirm)
    val randomizeSuccess = stringResource(R.string.ssaid_randomize_success)
    val deleteSuccess = stringResource(R.string.ssaid_delete_success)
    val reloadFailed = stringResource(R.string.ssaid_reload_failed)
    val operationFailed = stringResource(R.string.ssaid_operation_failed)

    fun showResult(message: String) {
        if (uiMode == UiMode.Material) {
            scope.launch { snackbarHost.showSnackbar(message) }
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 确认弹窗按包名分派：showConfirm 前记录目标条目
    var pendingRandomize by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val randomizeDialog = rememberConfirmDialog(onConfirm = {
        val pkg = pendingRandomize ?: return@rememberConfirmDialog
        scope.launch {
            viewModel.setBusy(pkg)
            val (written, reloaded) = viewModel.randomize(pkg)
            viewModel.setBusy(null)
            showResult(
                when {
                    written && reloaded -> randomizeSuccess
                    written -> reloadFailed
                    else -> operationFailed
                }
            )
        }
    })
    val deleteDialog = rememberConfirmDialog(onConfirm = {
        val pkg = pendingDelete ?: return@rememberConfirmDialog
        scope.launch {
            viewModel.setBusy(pkg)
            val (written, reloaded) = viewModel.delete(pkg)
            viewModel.setBusy(null)
            showResult(
                when {
                    written && reloaded -> deleteSuccess
                    written -> reloadFailed
                    else -> operationFailed
                }
            )
        }
    })

    val actions = SsaidActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onRandomize = { pkg ->
            pendingRandomize = pkg
            randomizeDialog.showConfirm(title = confirmTitle, content = randomizeConfirm, confirm = actionText)
        },
        onDelete = { pkg ->
            pendingDelete = pkg
            deleteDialog.showConfirm(title = confirmTitle, content = deleteConfirm, confirm = deleteText)
        },
        onRetry = viewModel::refresh,
    )

    when (uiMode) {
        UiMode.Miuix -> SsaidScreenMiuix(state, actions)
        UiMode.Material -> SsaidScreenMaterial(state, actions, snackbarHost)
    }
}

/** 操作按钮互斥（ 的 UI 面）：有任一操作进行中时全部按钮禁用。 */
private fun buttonsEnabled(busyPkg: String?): Boolean = busyPkg == null

// ========================= Miuix 皮肤 =========================

@Composable
private fun SsaidScreenMiuix(
    state: SsaidUiState,
    actions: SsaidActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                title = stringResource(R.string.ssaid_title),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground,
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
        when (state.loadState) {
            SsaidLoadState.LOADING -> LoadingBox(Modifier.padding(innerPadding))
            SsaidLoadState.UNAVAILABLE -> UnavailableBox(Modifier.padding(innerPadding), onRetry = actions.onRetry)
            SsaidLoadState.FAILED -> FailedBox(Modifier.padding(innerPadding), onRetry = actions.onRetry)
            SsaidLoadState.READY -> {
                if (state.items.isEmpty()) {
                    EmptyBox(Modifier.padding(innerPadding))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        ),
                    ) {
                        items(state.items, key = { it.packageName }) { item ->
                            SsaidItemCard(
                                item = item,
                                enabled = buttonsEnabled(state.busyPkg),
                                onRandomize = { actions.onRandomize(item.packageName) },
                                onDelete = { actions.onDelete(item.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========================= Material 皮肤 =========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SsaidScreenMaterial(
    state: SsaidUiState,
    actions: SsaidActions,
    snackbarHost: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.ssaid_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHost) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        when (state.loadState) {
            SsaidLoadState.LOADING -> LoadingBox(Modifier.padding(paddingValues))
            SsaidLoadState.UNAVAILABLE -> UnavailableBox(Modifier.padding(paddingValues), onRetry = actions.onRetry)
            SsaidLoadState.FAILED -> FailedBox(Modifier.padding(paddingValues), onRetry = actions.onRetry)
            SsaidLoadState.READY -> {
                if (state.items.isEmpty()) {
                    EmptyBox(Modifier.padding(paddingValues))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    ) {
                        items(state.items, key = { it.packageName }) { item ->
                            SsaidItemCard(
                                item = item,
                                enabled = buttonsEnabled(state.busyPkg),
                                onRandomize = { actions.onRandomize(item.packageName) },
                                onDelete = { actions.onDelete(item.packageName) },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// ========================= 共享组件 =========================

@Composable
private fun SsaidItemCard(
    item: SsaidItemUi,
    enabled: Boolean,
    onRandomize: () -> Unit,
    onDelete: () -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.applicationInfo != null) {
                AppIconImage(
                    applicationInfo = item.applicationInfo,
                    label = item.displayName,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.displayName.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.label != null) {
                    Text(
                        text = item.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.value,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp, bottom = 12.dp, top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeleteButton(enabled = enabled, onClick = onDelete)
            Spacer(Modifier.width(8.dp))
            RandomizeButton(enabled = enabled, onClick = onRandomize)
        }
    }
}

@Composable
private fun RandomizeButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonDefaults.filledTonalShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = Modifier.heightIn(min = 34.dp),
    ) {
        Text(text = stringResource(R.string.action_randomize), fontSize = 13.sp)
    }
}

@Composable
private fun DeleteButton(enabled: Boolean, onClick: () -> Unit) {
    val isDark = isInDarkTheme()
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonDefaults.filledTonalShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF4A2222) else Color(0xFFFBE9E9),
            contentColor = if (isDark) Color(0xFFF2B8B5) else Color(0xFFB3261E),
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = Modifier.heightIn(min = 34.dp),
    ) {
        Text(text = stringResource(R.string.delete), fontSize = 13.sp)
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.ssaid_list_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun UnavailableBox(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    StateMessageBox(
        modifier = modifier,
        message = stringResource(R.string.ssaid_root_unavailable),
        retryText = stringResource(R.string.network_retry),
        onRetry = onRetry,
    )
}

@Composable
private fun FailedBox(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    StateMessageBox(
        modifier = modifier,
        message = stringResource(R.string.ssaid_read_failed),
        retryText = stringResource(R.string.network_retry),
        onRetry = onRetry,
    )
}

@Composable
private fun StateMessageBox(modifier: Modifier, message: String, retryText: String, onRetry: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, shape = ButtonDefaults.filledTonalShape) {
                Text(retryText)
            }
        }
    }
}
