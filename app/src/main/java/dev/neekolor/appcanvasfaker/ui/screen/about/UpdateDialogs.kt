package dev.neekolor.appcanvasfaker.ui.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.UpdateCenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import androidx.compose.material3.TextButton as MaterialTextButton

/**
 * 检查更新结果弹窗（双皮肤）：关于页手动触发与启动自动检查共用。
 * 状态机由 UpdateCenter 单例驱动；下载中允许关闭弹窗（下载继续，
 * 完成后转 ReadyToInstall 会再次弹出安装确认）。
 */
@Composable
fun UpdateDialogMiuix(
    state: UpdateCenter.UiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is UpdateCenter.UiState.Idle) return
    val title = stringResource(R.string.settings_check_update)
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column {
                when (state) {
                    is UpdateCenter.UiState.Idle -> Unit
                    is UpdateCenter.UiState.Checking -> {
                        MiuixText(text = stringResource(R.string.update_checking))
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    is UpdateCenter.UiState.Latest -> {
                        MiuixText(text = stringResource(R.string.update_latest, state.current))
                    }
                    is UpdateCenter.UiState.Available -> {
                        MiuixText(text = stringResource(R.string.update_found, state.tag, state.sizeText))
                    }
                    is UpdateCenter.UiState.Failed -> {
                        MiuixText(text = stringResource(R.string.update_failed, state.reason))
                    }
                    is UpdateCenter.UiState.Downloading -> {
                        val percent = (state.progress * 100).toInt()
                        MiuixText(text = stringResource(R.string.update_downloading, percent))
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdateCenter.UiState.ReadyToInstall -> {
                        MiuixText(text = stringResource(R.string.update_install_now))
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (state) {
                    is UpdateCenter.UiState.Available -> {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.update_download),
                            onClick = onDownload,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss,
                        )
                    }
                    is UpdateCenter.UiState.Failed -> {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.network_retry),
                            onClick = onRetry,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss,
                        )
                    }
                    is UpdateCenter.UiState.ReadyToInstall -> {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.update_install),
                            onClick = onInstall,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss,
                        )
                    }
                    else -> {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = if (state is UpdateCenter.UiState.Checking) {
                                stringResource(android.R.string.cancel)
                            } else {
                                stringResource(android.R.string.ok)
                            },
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun UpdateDialogMaterial(
    state: UpdateCenter.UiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is UpdateCenter.UiState.Idle) return
    val title = stringResource(R.string.settings_check_update)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when (state) {
                    is UpdateCenter.UiState.Idle -> Unit
                    is UpdateCenter.UiState.Checking -> {
                        Text(stringResource(R.string.update_checking))
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    is UpdateCenter.UiState.Latest -> {
                        Text(stringResource(R.string.update_latest, state.current))
                    }
                    is UpdateCenter.UiState.Available -> {
                        Text(stringResource(R.string.update_found, state.tag, state.sizeText))
                    }
                    is UpdateCenter.UiState.Failed -> {
                        Text(stringResource(R.string.update_failed, state.reason))
                    }
                    is UpdateCenter.UiState.Downloading -> {
                        val percent = (state.progress * 100).toInt()
                        Text(stringResource(R.string.update_downloading, percent))
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdateCenter.UiState.ReadyToInstall -> {
                        Text(stringResource(R.string.update_install_now))
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateCenter.UiState.Available -> {
                    MaterialTextButton(onClick = onDownload) {
                        Text(stringResource(R.string.update_download))
                    }
                }
                is UpdateCenter.UiState.Failed -> {
                    MaterialTextButton(onClick = onRetry) {
                        Text(stringResource(R.string.network_retry))
                    }
                }
                is UpdateCenter.UiState.ReadyToInstall -> {
                    MaterialTextButton(onClick = onInstall) {
                        Text(stringResource(R.string.update_install))
                    }
                }
                else -> {
                    MaterialTextButton(onClick = onDismiss) {
                        Text(
                            if (state is UpdateCenter.UiState.Checking) {
                                stringResource(android.R.string.cancel)
                            } else {
                                stringResource(android.R.string.ok)
                            }
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (state is UpdateCenter.UiState.Available ||
                state is UpdateCenter.UiState.Failed ||
                state is UpdateCenter.UiState.ReadyToInstall
            ) {
                MaterialTextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

/** 弹窗各按钮的标准接线（调用方只需给 scope + app）：下载/安装/重试/关闭。 */
class UpdateDialogActions(
    val onDownload: () -> Unit,
    val onInstall: () -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun rememberUpdateDialogActions(
    check: () -> Unit,
): UpdateDialogActions {
    return UpdateDialogActions(
        onDownload = {
            val s = UpdateCenter.ui.value
            if (s is UpdateCenter.UiState.Available) {
                MainScope().launch {
                    UpdateCenter.downloadGuarded(acfApp, s.apkUrl, s.tag)
                }
            }
        },
        onInstall = {
            val s = UpdateCenter.ui.value
            if (s is UpdateCenter.UiState.ReadyToInstall) UpdateCenter.install(acfApp, s.file)
        },
        onRetry = { check() },
        onDismiss = { UpdateCenter.dismiss() },
    )
}
