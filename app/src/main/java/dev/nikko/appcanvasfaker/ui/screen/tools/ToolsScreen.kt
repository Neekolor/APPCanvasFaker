package dev.nikko.appcanvasfaker.ui.screen.tools

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.ui.LocalUiMode
import dev.nikko.appcanvasfaker.ui.UiMode
import dev.nikko.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.nikko.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.nikko.appcanvasfaker.ui.component.material.SegmentedSwitchItem
import dev.nikko.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.nikko.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import dev.nikko.appcanvasfaker.ui.navigation3.LocalNavigator
import dev.nikko.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.nikko.appcanvasfaker.ui.util.BlurredBar
import dev.nikko.appcanvasfaker.ui.util.rememberBlurBackdrop
import dev.nikko.appcanvasfaker.ui.viewmodel.SettingsViewModel
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
 * （E1 文本度量 / D1 GL 直读）。A2 单点读取为常开链，不提供开关。
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
    uiState: dev.nikko.appcanvasfaker.ui.screen.settings.SettingsUiState,
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    val textMetrics = stringResource(id = R.string.settings_hook_text_metrics)
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
                }
            }
        }
    }
}

@Composable
private fun ToolsScreenMaterial(
    uiState: dev.nikko.appcanvasfaker.ui.screen.settings.SettingsUiState,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    }
                )
            )
        }
    }
}
