package dev.nikko.appcanvasfaker.ui.screen.cfc

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.scanner.fingerprint.FingerprintResult
import dev.nikko.appcanvasfaker.ui.LocalUiMode
import dev.nikko.appcanvasfaker.ui.UiMode
import dev.nikko.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.nikko.appcanvasfaker.ui.component.material.TonalCard
import dev.nikko.appcanvasfaker.ui.component.material.expressiveTopAppBarColors
import dev.nikko.appcanvasfaker.ui.theme.LocalEnableBlur
import dev.nikko.appcanvasfaker.ui.util.BlurredBar
import dev.nikko.appcanvasfaker.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class CfcActions(
    val onCollectAll: () -> Unit,
    val onRunC1: () -> Unit,
    val onRunC2: () -> Unit,
    val onRunD1: () -> Unit,
    val onRandomizeSelf: () -> Unit,
)

@Composable
fun CfcScreen(
    bottomInnerPadding: Dp,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean,
) {
    val viewModel = viewModel<CfcViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val context = LocalContext.current
    val randomizedToast = stringResource(R.string.cfc_randomized_toast)

    val actions = CfcActions(
        onCollectAll = { activity?.applicationContext?.let(viewModel::collectAll) },
        onRunC1 = { activity?.let(viewModel::runC1) },
        onRunC2 = { activity?.let(viewModel::runC2) },
        onRunD1 = viewModel::runD1,
        onRandomizeSelf = {
            viewModel.randomizeSelf {
                android.widget.Toast.makeText(context, randomizedToast, android.widget.Toast.LENGTH_SHORT).show()
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> CfcScreenMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> CfcScreenMaterial(uiState, actions, bottomInnerPadding)
    }
}

// ========================= Miuix 皮肤 =========================

@Composable
private fun CfcScreenMiuix(
    state: CfcUiState,
    actions: CfcActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.cfc_title),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        RandomizePill(actions.onRandomizeSelf)
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = bottomInnerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(12.dp)
            ButtonsRowMiuix(state, actions)
            CaptureRowsMiuix(state)
            LazyColumnResults(state, Modifier.weight(1f))
            Spacer(bottomInnerPadding)
        }
    }
}

// ========================= Material 皮肤 =========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CfcScreenMaterial(
    state: CfcUiState,
    actions: CfcActions,
    bottomInnerPadding: Dp,
) {
    ExpressiveScaffold(
        topBar = {
            androidx.compose.material3.LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.cfc_title)) },
                actions = {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = actions.onRandomizeSelf,
                        modifier = Modifier.height(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    ) {
                        Text(stringResource(R.string.cfc_randomize), maxLines = 1)
                    }
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
                .padding(bottom = bottomInnerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(12.dp)
            ButtonsRowMaterial(state, actions)
            CaptureRowsMaterial(state)
            LazyColumnResults(state, Modifier.weight(1f))
            Spacer(bottomInnerPadding)
        }
    }
}

/** 顶栏"随机化"按钮（Miuix）：胶囊样式，与 App Profile 的执行按钮一致。 */
@Composable
private fun RandomizePill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 35.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.cfc_randomize),
            tint = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
        Text(
            text = stringResource(R.string.cfc_randomize),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
        )
    }
}

// ========================= 共享内容块 =========================

@Composable
private fun ButtonsRowMiuix(state: CfcUiState, actions: CfcActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = actions.onCollectAll,
            enabled = !state.collectingAll,
            modifier = Modifier.weight(2f),
        ) {
            Text(
                text = if (state.collectingAll) stringResource(R.string.cfc_collecting) else stringResource(R.string.cfc_collect_all),
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
        TextButton(text = "C1", onClick = actions.onRunC1, modifier = Modifier.weight(1f))
        TextButton(text = "C2", onClick = actions.onRunC2, modifier = Modifier.weight(1f))
        TextButton(text = "D1", onClick = actions.onRunD1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ButtonsRowMaterial(state: CfcUiState, actions: CfcActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Button(
            onClick = actions.onCollectAll,
            enabled = !state.collectingAll,
            modifier = Modifier.weight(2f),
        ) {
            Text(
                text = if (state.collectingAll) stringResource(R.string.cfc_collecting) else stringResource(R.string.cfc_collect_all),
                maxLines = 1,
            )
        }
        androidx.compose.material3.OutlinedButton(onClick = actions.onRunC1, modifier = Modifier.weight(1f)) { Text("C1") }
        androidx.compose.material3.OutlinedButton(onClick = actions.onRunC2, modifier = Modifier.weight(1f)) { Text("C2") }
        androidx.compose.material3.OutlinedButton(onClick = actions.onRunD1, modifier = Modifier.weight(1f)) { Text("D1") }
    }
}

@Composable
private fun CaptureRowsMiuix(state: CfcUiState) {
    CaptureRowMiuix("C1", "buildDrawingCache", state.c1)
    CaptureRowMiuix("C2", "PixelCopy.request", state.c2)
    CaptureRowMiuix("D1", "glReadPixels", state.d1)
}

@Composable
private fun CaptureRowsMaterial(state: CfcUiState) {
    CaptureRowMaterial("C1", "buildDrawingCache", state.c1)
    CaptureRowMaterial("C2", "PixelCopy.request", state.c2)
    CaptureRowMaterial("D1", "glReadPixels", state.d1)
}

@Composable
private fun CaptureRowMiuix(id: String, method: String, capture: CfcCapture) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$id $method" + (capture.elapsedMs?.let { " ${it}ms" } ?: ""),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            HashTextMiuix(capture.hash)
        }
    }
}

@Composable
private fun CaptureRowMaterial(id: String, method: String, capture: CfcCapture) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(
                text = "$id $method" + (capture.elapsedMs?.let { " ${it}ms" } ?: ""),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            HashTextMaterial(capture.hash)
        }
    }
}

@Composable
private fun LazyColumnResults(state: CfcUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.syncResults, key = { it.item.id }) { result ->
            ResultCard(result)
        }
        item {
            Spacer(24.dp)
        }
    }
}

@Composable
private fun ResultCard(result: FingerprintResult) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ResultCardMiuix(result)
        UiMode.Material -> ResultCardMaterial(result)
    }
}

@Composable
private fun ResultCardMiuix(result: FingerprintResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${result.item.id} ${result.item.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${result.elapsedMs}ms",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            HashTextMiuix(result.hash)
        }
    }
}

@Composable
private fun ResultCardMaterial(result: FingerprintResult) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                androidx.compose.material3.Text(
                    text = "${result.item.id} ${result.item.name}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Text(
                    text = "${result.elapsedMs}ms",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            HashTextMaterial(result.hash)
        }
    }
}

@Composable
private fun HashTextMiuix(hash: String?) {
    if (hash == null) {
        Text(
            text = stringResource(R.string.cfc_not_collected),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    } else {
        Text(
            text = dev.nikko.appcanvasfaker.util.HashUtils.foldHash16(hash),
            fontSize = 13.sp,
            color = hashDisplayColor(hash),
        )
    }
}

@Composable
private fun HashTextMaterial(hash: String?) {
    if (hash == null) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.cfc_not_collected),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        androidx.compose.material3.Text(
            text = dev.nikko.appcanvasfaker.util.HashUtils.foldHash16(hash),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = hashDisplayColor(hash),
        )
    }
}

/** 折叠哈希派生颜色（与独立扫描器一致）：非法/异常文本回退主色。 */
private fun hashDisplayColor(fullHash: String): Color {
    val folded = dev.nikko.appcanvasfaker.util.HashUtils.foldHash16(fullHash)
    if (folded.length != 16 || folded.any { it !in "0123456789abcdef" }) {
        return Color(0xFFB3261E)
    }
    return try {
        val v = java.lang.Long.parseUnsignedLong(folded, 16)
        val ul = v.toULong()
        val base = (ul % 360000uL).toLong()
        val hue = ((base * 1375077L / 10000L).mod(360L)).toFloat()
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.78f)))
    } catch (_: NumberFormatException) {
        Color(0xFFB3261E)
    }
}

@Composable
private fun Spacer(height: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}
