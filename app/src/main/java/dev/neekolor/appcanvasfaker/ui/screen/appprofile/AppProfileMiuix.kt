package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.ListPopupDefaults
import dev.neekolor.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.neekolor.appcanvasfaker.ui.theme.isInDarkTheme
import dev.neekolor.appcanvasfaker.ui.util.BlurredBar
import dev.neekolor.appcanvasfaker.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AppProfileScreenMiuix(
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.profile),
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
                                tint = colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        ProfileOverflowMenu(actions = actions)
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 16.dp)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                item {
                    AppProfileContent(
                        state = state,
                        actions = actions,
                    )
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

@Composable
private fun AppProfileContent(
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            insideMargin = PaddingValues(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.applicationInfo != null) {
                    AppIconImage(
                        applicationInfo = state.applicationInfo,
                        label = state.displayLabel,
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colorScheme.secondaryContainer)
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = state.displayLabel,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight(550),
                        modifier = Modifier.basicMarquee(),
                        maxLines = 1,
                        softWrap = false
                    )
                    if (state.versionName != null) {
                        Text(
                            text = "${state.versionName} (${state.versionCode})",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.basicMarquee(),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = state.packageName,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.basicMarquee(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        ) {
            SwitchPreference(
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                        tint = colorScheme.onBackground
                    )
                },
                title = stringResource(id = R.string.enable_feature),
                summary = stringResource(id = R.string.enable_feature_summary),
                checked = state.enabled,
                onCheckedChange = actions.onSetEnabled,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            tint = colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.randomize_fingerprint),
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.randomize_seed_label),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.width(12.dp))
                ExecuteButton(onClick = actions.onRandomize)
            }
        }

        SmallTitle(
            text = stringResource(R.string.randomized_values),
            modifier = Modifier.padding(top = 4.dp)
        )
        if (state.fingerprints.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.log_empty),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                Spacer(Modifier.height(3.dp))
                state.fingerprints.forEach { fp ->
                    BasicComponent(
                        startAction = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.secondaryContainer.copy(alpha = 0.8f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = fp.method,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight(750),
                                    color = colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        },
                        title = fp.title,
                        summary = fp.hash,
                        insideMargin = PaddingValues(start = 11.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

/** 顶栏 MoreCircle 菜单：启动 / 强制停止 / 重启（与原版 KSU 的 App Profile 一致）。 */
@Composable
private fun ProfileOverflowMenu(actions: AppProfileActions) {
    val showPopup = remember { mutableStateOf(false) }
    OverlayListPopup(
        show = showPopup.value,
        popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = { showPopup.value = false },
        content = {
            ListPopupColumn {
                DropdownImpl(
                    text = stringResource(R.string.launch_app),
                    isSelected = false,
                    optionSize = 3,
                    onSelectedIndexChange = {
                        showPopup.value = false
                        actions.onLaunchApp()
                    },
                    index = 0
                )
                DropdownImpl(
                    text = stringResource(R.string.force_stop_app),
                    isSelected = false,
                    optionSize = 3,
                    onSelectedIndexChange = {
                        showPopup.value = false
                        actions.onForceStopApp()
                    },
                    index = 1
                )
                DropdownImpl(
                    text = stringResource(R.string.restart_app),
                    isSelected = false,
                    optionSize = 3,
                    onSelectedIndexChange = {
                        showPopup.value = false
                        actions.onRestartApp()
                    },
                    index = 2
                )
            }
        }
    )
    IconButton(
        onClick = { showPopup.value = true },
        holdDownState = showPopup.value
    ) {
        Icon(
            imageVector = MiuixIcons.MoreCircle,
            tint = colorScheme.onSurface,
            contentDescription = null
        )
    }
}

@Composable
private fun ExecuteButton(
    onClick: () -> Unit,
) {
    val isDark = isInDarkTheme()
    val tint = colorScheme.onSurface.copy(alpha = if (isDark) 0.7f else 0.9f)
    Row(
        modifier = Modifier
            .heightIn(min = 35.dp)
            .widthIn(min = 35.dp)
            .clip(CircleShape)
            .background(colorScheme.secondaryContainer.copy(alpha = 0.8f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = Icons.Rounded.PlayArrow,
            tint = tint,
            contentDescription = stringResource(R.string.action)
        )
        Text(
            modifier = Modifier.padding(start = 3.dp, end = 4.dp),
            text = stringResource(R.string.action),
            color = tint,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}