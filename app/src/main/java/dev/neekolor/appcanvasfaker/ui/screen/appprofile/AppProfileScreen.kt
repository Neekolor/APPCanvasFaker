package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.component.dialog.rememberConfirmDialog
import dev.neekolor.appcanvasfaker.ui.navigation3.LocalNavigator
import kotlinx.coroutines.launch

@Composable
fun AppProfileScreen(packageName: String) {
    val uiMode = LocalUiMode.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewModel = viewModel<AppProfileViewModel>()

    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }
    // 管理器里勾完回来即重查门禁，否则警告条滞留
    LifecycleResumeEffect(packageName) {
        viewModel.refreshScope(packageName)
        onPauseOrDispose { }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val confirmTitle = stringResource(R.string.confirm)
    val confirmMessage = stringResource(R.string.randomize_confirm_message)
    val stopConfirmMessage = stringResource(R.string.force_stop_confirm)
    val restartConfirmMessage = stringResource(R.string.restart_app_confirm)
    val actionText = stringResource(R.string.action)
    val successText = stringResource(R.string.randomize_success)
    val failedText = stringResource(R.string.operation_failed)
    val scopeApprovedText = stringResource(R.string.scope_approved)
    val scopeFailedText = stringResource(R.string.scope_failed)

    fun showResult(message: String) {
        if (uiMode == UiMode.Material) {
            scope.launch { snackbarHost.showSnackbar(message) }
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                // 成功失败都弹结果：随机化是不可逆写操作，不能让用户猜
                val ok = viewModel.randomize(packageName)
                showResult(if (ok) successText else failedText)
            }
        }
    )

    // 溢出菜单的停/重启同样二次确认：误触代价是目标应用被杀
    var pendingMenuAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val menuDialog = rememberConfirmDialog(
        onConfirm = {
            pendingMenuAction?.let { action ->
                pendingMenuAction = null
                scope.launch { action() }
            }
        }
    )

    val actions = AppProfileActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetEnabled = { enabled -> viewModel.setEnabled(packageName, enabled) },
        onRandomize = {
            // 未启用时按动无效果：连确认弹窗都不弹
            if (viewModel.uiState.value.enabled) {
                confirmDialog.showConfirm(
                    title = confirmTitle,
                    content = confirmMessage,
                    confirm = actionText,
                )
            }
        },
        // 菜单操作同样给反馈：后台 shell 失败前台无感知，用户会以为没点上
        onLaunchApp = {
            scope.launch {
                if (!viewModel.launchApp(packageName)) showResult(failedText)
            }
        },
        onForceStopApp = {
            pendingMenuAction = {
                if (!viewModel.forceStopApp(packageName)) showResult(failedText)
            }
            menuDialog.showConfirm(
                title = confirmTitle,
                content = stopConfirmMessage,
                confirm = actionText,
            )
        },
        onRestartApp = {
            pendingMenuAction = {
                if (!viewModel.restartApp(packageName)) showResult(failedText)
            }
            menuDialog.showConfirm(
                title = confirmTitle,
                content = restartConfirmMessage,
                confirm = actionText,
            )
        },
        onOpenLogs = dropUnlessResumed { navigator.push(dev.neekolor.appcanvasfaker.ui.navigation3.Route.Log) },
        onRequestScope = {
            viewModel.requestScope(packageName) { approved ->
                showResult(if (approved) scopeApprovedText else scopeFailedText)
            }
        },
    )

    when (uiMode) {
        UiMode.Miuix -> AppProfileScreenMiuix(
            state = state,
            actions = actions,
        )

        UiMode.Material -> AppProfileScreenMaterial(
            state = state,
            actions = actions,
            snackBarHost = snackbarHost,
        )
    }
}
