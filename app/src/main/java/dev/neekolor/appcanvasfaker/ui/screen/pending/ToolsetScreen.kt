package dev.neekolor.appcanvasfaker.ui.screen.pending

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedListItem
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedSwitchItem
import dev.neekolor.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import dev.neekolor.appcanvasfaker.ui.navigation3.Navigator
import dev.neekolor.appcanvasfaker.ui.navigation3.Route
import dev.neekolor.appcanvasfaker.ui.screen.settings.SettingsUiState
import dev.neekolor.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.neekolor.appcanvasfaker.ui.util.BlurredBar
import dev.neekolor.appcanvasfaker.ui.util.rememberBlurBackdrop
import dev.neekolor.appcanvasfaker.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 底栏第三项"工具"页：伪装开关、SSAID 管理与日后新工具的集合。
 * 内容由设置页"实验性功能"二级页迁入（v0.8.5），设置页仅保留跳转入口。
 */
@Composable
fun ToolsetScreen(
    bottomInnerPadding: Dp,
    navigator: Navigator,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ToolsetScreenMiuix(uiState, viewModel, { navigator.push(Route.Ssaid) }, bottomInnerPadding)
        UiMode.Material -> ToolsetScreenMaterial(uiState, viewModel, { navigator.push(Route.Ssaid) }, bottomInnerPadding)
    }
}

@Composable
private fun ToolsetScreenMiuix(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenSsaid: () -> Unit,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.nav_pending),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = bottomInnerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    val textMetrics = stringResource(id = R.string.settings_hook_text_metrics)
                    SwitchPreference(
                        title = textMetrics,
                        summary = stringResource(id = R.string.settings_hook_text_metrics_summary),
                        startAction = {
                            MiuixIcon(
                                Icons.Rounded.TextFields,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = textMetrics,
                                tint = colorScheme.onBackground
                            )
                        },
                        checked = uiState.hookTextMetrics,
                        onCheckedChange = viewModel::setHookTextMetrics
                    )
                    val glReadPixels = stringResource(id = R.string.settings_hook_glreadpixels)
                    SwitchPreference(
                        title = glReadPixels,
                        summary = stringResource(id = R.string.settings_hook_glreadpixels_summary),
                        startAction = {
                            MiuixIcon(
                                Icons.Rounded.ViewInAr,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = glReadPixels,
                                tint = colorScheme.onBackground
                            )
                        },
                        checked = uiState.hookGlReadPixels,
                        onCheckedChange = viewModel::setHookGlReadPixels
                    )
                    val pixelCopy = stringResource(id = R.string.settings_hook_pixelcopy)
                    SwitchPreference(
                        title = pixelCopy,
                        summary = stringResource(id = R.string.settings_hook_pixelcopy_summary),
                        startAction = {
                            MiuixIcon(
                                Icons.Rounded.ContentCopy,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = pixelCopy,
                                tint = colorScheme.onBackground
                            )
                        },
                        checked = uiState.hookPixelCopy,
                        onCheckedChange = viewModel::setHookPixelCopy
                    )
                    val ssaidSwitch = stringResource(id = R.string.tools_ssaid_switch)
                    SwitchPreference(
                        title = ssaidSwitch,
                        summary = stringResource(id = R.string.tools_ssaid_switch_summary),
                        startAction = {
                            MiuixIcon(
                                Icons.Rounded.Badge,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = ssaidSwitch,
                                tint = colorScheme.onBackground
                            )
                        },
                        checked = uiState.ssaidEnabled,
                        onCheckedChange = viewModel::setSsaidEnabled
                    )
                }
                if (uiState.ssaidEnabled) {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val ssaid = stringResource(id = R.string.settings_ssaid)
                        ArrowPreference(
                            title = ssaid,
                            summary = stringResource(id = R.string.settings_ssaid_summary),
                            startAction = {
                                MiuixIcon(
                                    Icons.Rounded.Badge,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = ssaid,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = onOpenSsaid
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ToolsetScreenMaterial(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenSsaid: () -> Unit,
    bottomInnerPadding: Dp,
) {
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_pending)) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = bottomInnerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.TextFields,
                            title = stringResource(id = R.string.settings_hook_text_metrics),
                            summary = stringResource(id = R.string.settings_hook_text_metrics_summary),
                            checked = uiState.hookTextMetrics,
                            onCheckedChange = viewModel::setHookTextMetrics
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.ViewInAr,
                            title = stringResource(id = R.string.settings_hook_glreadpixels),
                            summary = stringResource(id = R.string.settings_hook_glreadpixels_summary),
                            checked = uiState.hookGlReadPixels,
                            onCheckedChange = viewModel::setHookGlReadPixels
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.ContentCopy,
                            title = stringResource(id = R.string.settings_hook_pixelcopy),
                            summary = stringResource(id = R.string.settings_hook_pixelcopy_summary),
                            checked = uiState.hookPixelCopy,
                            onCheckedChange = viewModel::setHookPixelCopy
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Badge,
                            title = stringResource(id = R.string.tools_ssaid_switch),
                            summary = stringResource(id = R.string.tools_ssaid_switch_summary),
                            checked = uiState.ssaidEnabled,
                            onCheckedChange = viewModel::setSsaidEnabled
                        )
                    }
                )
            )
            if (uiState.ssaidEnabled) {
                val ssaid = stringResource(id = R.string.settings_ssaid)
                SegmentedColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                onClick = onOpenSsaid,
                                headlineContent = { Text(ssaid) },
                                supportingContent = { Text(stringResource(id = R.string.settings_ssaid_summary)) },
                                leadingContent = { Icon(Icons.Filled.Badge, ssaid) },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        null
                                    )
                                }
                            )
                        }
                    )
                )
            }
        }
    }
}
