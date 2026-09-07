package dev.neekolor.appcanvasfaker.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedListItem
import dev.neekolor.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.neekolor.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import kotlinx.coroutines.launch

@Composable
fun AboutScreenMaterial(
    state: AboutUiState,
    actions: AboutScreenActions,
) {
    val eggHolder = rememberEasterEggHolder()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (eggHolder.currentRes == null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                contentScale = FixedScale(1f)
                            )
                        }
                    } else {
                        // 替换图激活：与原 logo 同款 80dp 白底圆角容器，Fit 恰好铺满
                        // （444×444 方图 → 80dp 完整显示，尺寸与原 logo 容器一致）
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                        ) {
                            EasterEggLogoImage(
                                holder = eggHolder,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = state.appName,
                        fontWeight = FontWeight.Medium,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize
                    )
                    Text(
                        text = state.versionName,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = state.description,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = state.license,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            }
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = buildList {
                        state.links.forEach { linkInfo ->
                            add {
                                SegmentedListItem(
                                    onClick = { actions.onOpenLink(linkInfo.url) },
                                    headlineContent = { Text(linkInfo.fullText) }
                                )
                            }
                        }
                        // 检查更新：同卡片样式，点按查 GitHub Releases（见 UpdateCenter）
                        add {
                            SegmentedListItem(
                                onClick = actions.onCheckUpdate,
                                headlineContent = { Text(stringResource(R.string.settings_check_update)) }
                            )
                        }
                    }
                )
                // 两版不等长：定宽右对齐，切换时后缀位置不动。
                // 宽度取值：Roboto 实测长串 147dp，取 160.dp 留余量；余量走左边，不影响后缀。
                // indication = null，避免整行按压背景。
                var showAcfCopyright by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showAcfCopyright = !showAcfCopyright },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        modifier = Modifier.width(160.dp),
                        text = if (showAcfCopyright)
                            buildAcfCopyrightText(baseFontSize = MaterialTheme.typography.bodySmall.fontSize)
                        else buildCopyrightText(baseFontSize = MaterialTheme.typography.bodySmall.fontSize),
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
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