package dev.nikko.appcanvasfaker.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.core.FingerprintValue
import dev.nikko.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.nikko.appcanvasfaker.ui.component.material.TonalCard
import dev.nikko.appcanvasfaker.ui.component.material.expressiveTopAppBarColors

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
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

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = stringResource(R.string.reboot)
                )
            }
        },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val moduleActive = state.moduleActive

    // 首帧同步水合后 isLoading 仅覆盖指纹哈希计算窗口；服务绑定竞态下
    // 短暂未知时显示"检测中"而非误导性的"模块未激活"
    val checking = state.isLoading && !moduleActive

    val containerColor = when {
        moduleActive -> MaterialTheme.colorScheme.secondaryContainer
        checking -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

    val statusIcon = when {
        moduleActive -> Icons.Outlined.CheckCircle
        checking -> null
        else -> Icons.Outlined.Warning
    }
    val statusTitle = when {
        moduleActive -> stringResource(R.string.home_working)
        checking -> stringResource(R.string.home_checking)
        else -> stringResource(R.string.home_not_activated)
    }
    val statusSummary = if (moduleActive) {
        stringResource(R.string.home_working_version, state.versionName)
    } else {
        null
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            onClick = {},
        ) {
            ListItem(
                leadingContent = {
                    if (statusIcon != null) {
                        Icon(statusIcon, contentDescription = statusTitle)
                    } else {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                },
                supportingContent = statusSummary?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = contentColor,
                    leadingContentColor = contentColor,
                    supportingContentColor = contentColor.copy(alpha = 0.7f)
                ),
                elevation = ListItemDefaults.elevation(),
                content = {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                },
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            StatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                title = stringResource(R.string.hooked_apps),
                count = state.hookedAppCount.toString(),
                onClick = actions.onOpenHookedApps,
            )
            StatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                title = stringResource(R.string.hook_count),
                count = state.totalHookCount.toString(),
                onClick = actions.onOpenCfc,
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
    TonalCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = count,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InfoCard(state: HomeUiState) {
    TonalCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
        ) {
            @Composable
            fun InfoCardItem(label: String, content: String) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            InfoCardItem(
                stringResource(R.string.app_version),
                "${stringResource(R.string.version_channel)}/v${state.versionName}"
            )
            state.standardFingerprints.forEach { fingerprint ->
                Spacer(Modifier.height(16.dp))
                InfoCardItem(fingerprint.displayTitle(), fingerprint.hash)
            }
        }
    }
}

@Composable
private fun LearnMoreCard(onOpenUrl: (String) -> Unit) {
    val url = stringResource(R.string.home_learn_app_url)
    TonalCard(onClick = { onOpenUrl(url) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = stringResource(R.string.home_learn_app), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_app),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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