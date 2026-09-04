package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val confirmTitle = stringResource(R.string.confirm)
    val confirmMessage = stringResource(R.string.randomize_confirm_message)
    val actionText = stringResource(R.string.action)
    val successText = stringResource(R.string.randomize_success)
    val failedText = stringResource(R.string.operation_failed)

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
                // 审计 N-18：失败也要如实反馈，不再静默
                val ok = viewModel.randomize(packageName)
                showResult(if (ok) successText else failedText)
            }
        }
    )

    val actions = AppProfileActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetEnabled = { enabled -> viewModel.setEnabled(packageName, enabled) },
        onRandomize = {
            confirmDialog.showConfirm(
                title = confirmTitle,
                content = confirmMessage,
                confirm = actionText,
            )
        },
        // 审计 N-19：菜单操作失败同样给出反馈
        onLaunchApp = {
            scope.launch {
                if (!viewModel.launchApp(packageName)) showResult(failedText)
            }
        },
        onForceStopApp = {
            scope.launch {
                if (!viewModel.forceStopApp(packageName)) showResult(failedText)
            }
        },
        onRestartApp = {
            scope.launch {
                if (!viewModel.restartApp(packageName)) showResult(failedText)
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
