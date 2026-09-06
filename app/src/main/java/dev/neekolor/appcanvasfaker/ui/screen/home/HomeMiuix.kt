package dev.neekolor.appcanvasfaker.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import top.yukonga.miuix.kmp.icon.extended.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.core.FingerprintValue
import dev.neekolor.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.neekolor.appcanvasfaker.ui.theme.isInDarkTheme
import dev.neekolor.appcanvasfaker.ui.util.BlurredBar
import dev.neekolor.appcanvasfaker.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
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
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusCard(
                            state = state,
                            actions = actions,
                        )
                        InfoCard(state = state)
                        LearnMoreCard(onOpenUrl = actions.onOpenUrl)
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = MiuixIcons.Close2,
                        contentDescription = stringResource(R.string.reboot),
                        tint = colorScheme.onBackground
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.moduleActive) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.defaultColors(
                    color = when {
                        isDynamicColor -> colorScheme.secondaryContainer
                        isInDarkTheme() -> Color(0xFF1A3825)
                        else -> Color(0xFFDFFAE4)
                    }
                ),
                onClick = {},
                showIndication = false,
                pressFeedbackType = PressFeedbackType.Tilt
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(38.dp, 45.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Icon(
                            modifier = Modifier.size(170.dp),
                            imageVector = Icons.Rounded.CheckCircleOutline,
                            tint = if (isDynamicColor) {
                                colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                Color(0xFF36D167)
                            },
                            contentDescription = null
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(all = 16.dp)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.home_working),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_working_version, state.versionName),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = {},
                showIndication = false,
                pressFeedbackType = PressFeedbackType.Sink
            ) {
                BasicComponent(
                    title = if (state.isLoading) {
                        stringResource(R.string.home_checking)
                    } else {
                        stringResource(R.string.home_not_activated)
                    },
                    startAction = {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(24.dp),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                stringResource(R.string.home_not_activated),
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        }
                    }
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            StatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                title = stringResource(R.string.hooked_apps),
                count = state.hookedAppCount.toString(),
                onClick = actions.onOpenHookedApps,
            )
            Spacer(Modifier.height(12.dp))
            StatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                title = stringResource(R.string.hook_count),
                count = state.totalHookCount.toString(),
                onClick = actions.onOpenStats,
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    count: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Text(
                text = count,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InfoCard(state: HomeUiState) {
    @Composable
    fun InfoText(
        title: String,
        content: String,
        bottomPadding: Dp = 24.dp
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        Text(
            text = content,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding)
        )
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(
                title = stringResource(R.string.app_version),
                content = state.versionName
            )
            state.standardFingerprints.forEachIndexed { index, fingerprint ->
                InfoText(
                    title = fingerprint.displayTitle(),
                    content = fingerprint.hash,
                    bottomPadding = if (index == state.standardFingerprints.lastIndex) 0.dp else 24.dp
                )
            }
            // 模块自身不可被 Hook：此处恒为本机未污染基准；通道故障时明示
            Text(
                text = stringResource(R.string.home_baseline_note),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (state.moduleActive && !state.remoteChannelOk) {
                Text(
                    text = stringResource(R.string.home_channel_bad),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LearnMoreCard(
    onOpenUrl: (String) -> Unit,
) {
    val url = stringResource(R.string.home_learn_app_url)
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.home_learn_app),
            summary = stringResource(R.string.home_click_to_learn_app),
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    tint = colorScheme.onSurface,
                    contentDescription = null
                )
            },
            onClick = { onOpenUrl(url) }
        )
    }
}

private val previewState = HomeUiState(
    moduleActive = true,
    versionName = "0.3.0",
    hookedAppCount = 3,
    totalHookCount = 128L,
    standardFingerprints = listOf(
        FingerprintValue("A1", "像素直读（getPixels）", "9f86d081884c7d659a2feaa0c55ad015"),
        FingerprintValue("A3", "缓冲拷贝（copyPixelsToBuffer）", "60303ae22b998861bce3b28f33eec1be"),
        FingerprintValue("A4", "压缩读取（compress）", "fdbd8e75a67f29f701a4e040385e2e23"),
        FingerprintValue("A4b", "尺寸采样（getImageSizes）", "5d41402abc4b2a76b9719d911017c592"),
    ),
)

@Preview(name = "Home Activated", showBackground = true)
@Composable
private fun HomeActivatedPreview() {
    StatusCard(state = previewState, actions = HomeActions({}, {}, {}))
}

@Preview(name = "Home Not Activated", showBackground = true)
@Composable
private fun HomeNotActivatedPreview() {
    StatusCard(state = previewState.copy(moduleActive = false), actions = HomeActions({}, {}, {}))
}