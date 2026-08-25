package dev.nikko.appcanvasfaker.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.ui.UiMode
import dev.nikko.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.nikko.appcanvasfaker.ui.util.BlurredBar
import dev.nikko.appcanvasfaker.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
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
                    title = stringResource(R.string.settings),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.settings_check_update),
                            summary = stringResource(id = R.string.settings_check_update_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Update,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_check_update),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.checkUpdate,
                            onCheckedChange = actions.onSetCheckUpdate
                        )

                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_ui_mode),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                            onSelectedIndexChange = actions.onSetUiModeIndex
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_theme),
                            summary = stringResource(id = R.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_theme),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenTheme
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val sulog = stringResource(id = R.string.settings_sulog)
                        SwitchPreference(
                            title = sulog,
                            summary = stringResource(id = R.string.settings_sulog_summary),
                            startAction = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Article,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = sulog,
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.enableLogging,
                            onCheckedChange = actions.onSetEnableLogging
                        )
                    }

                    // v0.6.0 Hook 扩展：A2 单点读取 / E1 文本度量 / D1 GL 直读（默认关）
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val hookGetPixel = stringResource(id = R.string.settings_hook_getpixel)
                        SwitchPreference(
                            title = hookGetPixel,
                            summary = stringResource(id = R.string.settings_hook_getpixel_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Grain,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = hookGetPixel,
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.hookGetPixel,
                            onCheckedChange = actions.onSetHookGetPixel
                        )
                        val hookTextMetrics = stringResource(id = R.string.settings_hook_text_metrics)
                        SwitchPreference(
                            title = hookTextMetrics,
                            summary = stringResource(id = R.string.settings_hook_text_metrics_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.TextFields,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = hookTextMetrics,
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.hookTextMetrics,
                            onCheckedChange = actions.onSetHookTextMetrics
                        )
                        val hookGlReadPixels = stringResource(id = R.string.settings_hook_glreadpixels)
                        SwitchPreference(
                            title = hookGlReadPixels,
                            summary = stringResource(id = R.string.settings_hook_glreadpixels_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.ViewInAr,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = hookGlReadPixels,
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.hookGlReadPixels,
                            onCheckedChange = actions.onSetHookGlReadPixels
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val tools = stringResource(id = R.string.settings_profile_template)
                        ArrowPreference(
                            title = tools,
                            summary = stringResource(id = R.string.settings_profile_template_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Fence,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = tools,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenTools
                        )
                        val log = stringResource(id = R.string.settings_log)
                        ArrowPreference(
                            title = log,
                            summary = stringResource(id = R.string.settings_log_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.History,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = log,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenLog
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val about = stringResource(id = R.string.about)
                        ArrowPreference(
                            title = about,
                            startAction = {
                                Icon(
                                    Icons.Rounded.ContactPage,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = about,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenAbout,
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}