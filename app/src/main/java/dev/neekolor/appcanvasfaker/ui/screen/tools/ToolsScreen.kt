package dev.neekolor.appcanvasfaker.ui.screen.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedSwitchItem
import dev.neekolor.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.neekolor.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import dev.neekolor.appcanvasfaker.ui.navigation3.LocalNavigator
import dev.neekolor.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.neekolor.appcanvasfaker.ui.util.BlurredBar
import dev.neekolor.appcanvasfaker.ui.util.rememberBlurBackdrop
import dev.neekolor.appcanvasfaker.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * "实验性功能"二级页：承载尚未稳定的 Hook 扩展开关
 * （H-05 文本度量 / H-02 GL 直读）。H-01 单点读取为常开链，不提供开关。
 */
@Composable
fun ToolsScreen() {
    val navigator = LocalNavigator.current
    val onBack = dropUnlessResumed { navigator.pop() }
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (LocalUiMode.current) {
        UiMode.Miuix -> ToolsScreenMiuix(uiState, viewModel, onBack)
        UiMode.Material -> ToolsScreenMaterial(uiState, viewModel, onBack)
    }
}

@Composable
private fun ToolsScreenMiuix(
    uiState: dev.neekolor.appcanvasfaker.ui.screen.settings.SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
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
                    title = stringResource(R.string.tools_title),
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
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
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                // 实验性功能说明（审计 W-06）
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tools_note),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    val textMetrics = stringResource(id = R.string.settings_hook_text_metrics)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = textMetrics,
                            summary = stringResource(id = R.string.settings_hook_text_metrics_summary),
                            startAction = {
                                Icon(
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
                                Icon(
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
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = pixelCopy,
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.hookPixelCopy,
                            onCheckedChange = viewModel::setHookPixelCopy
                        )
                        // 「启用随机化 SSAID」：控制设置页 SSAID 管理入口的显示（默认关）
                        val ssaid = stringResource(id = R.string.tools_ssaid_switch)
                        SwitchPreference(
                            title = ssaid,
                            summary = stringResource(id = R.string.tools_ssaid_switch_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Badge,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = ssaid,
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.ssaidEnabled,
                            onCheckedChange = viewModel::setSsaidEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreenMaterial(
    uiState: dev.neekolor.appcanvasfaker.ui.screen.settings.SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tools_title)) },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack)
                },
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
                .verticalScroll(rememberScrollState()),
        ) {
            // 实验性功能说明（审计 W-06）
            Text(
                text = stringResource(R.string.tools_note),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
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
        }
    }
}
