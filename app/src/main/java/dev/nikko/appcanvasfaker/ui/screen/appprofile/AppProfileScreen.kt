package dev.nikko.appcanvasfaker.ui.screen.appprofile

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
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.ui.LocalUiMode
import dev.nikko.appcanvasfaker.ui.UiMode
import dev.nikko.appcanvasfaker.ui.component.dialog.rememberConfirmDialog
import dev.nikko.appcanvasfaker.ui.navigation3.LocalNavigator
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
    val deleteText = stringResource(R.string.delete)
    val successText = stringResource(R.string.randomize_success)
    val ssaidRandomizeConfirmText = stringResource(R.string.ssaid_randomize_confirm)
    val ssaidDeleteConfirmText = stringResource(R.string.ssaid_delete_confirm)
    val ssaidRandomizeSuccessText = stringResource(R.string.ssaid_randomize_success)
    val ssaidDeleteSuccessText = stringResource(R.string.ssaid_delete_success)
    val ssaidFailedText = stringResource(R.string.ssaid_operation_failed)

    // 统一结果提示：Material 走 snackbar，Miuix 走 toast（沿用既有页面约定）
    fun showResult(successMessage: String, ok: Boolean) {
        if (ok) {
            if (uiMode == UiMode.Material) {
                scope.launch { snackbarHost.showSnackbar(successMessage) }
            } else {
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }
        } else {
            if (uiMode == UiMode.Material) {
                scope.launch { snackbarHost.showSnackbar(ssaidFailedText) }
            } else {
                Toast.makeText(context, ssaidFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                if (viewModel.randomize(packageName)) {
                    showResult(successText, true)
                }
            }
        }
    )

    val ssaidRandomizeDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch { showResult(ssaidRandomizeSuccessText, viewModel.randomizeSsaid(packageName)) }
        }
    )

    val ssaidDeleteDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch { showResult(ssaidDeleteSuccessText, viewModel.deleteSsaid(packageName)) }
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
        onRandomizeSsaid = {
            ssaidRandomizeDialog.showConfirm(
                title = confirmTitle,
                content = ssaidRandomizeConfirmText,
                confirm = actionText,
            )
        },
        onDeleteSsaid = {
            ssaidDeleteDialog.showConfirm(
                title = confirmTitle,
                content = ssaidDeleteConfirmText,
                confirm = deleteText,
            )
        },
        onLaunchApp = { scope.launch { viewModel.launchApp(packageName) } },
        onForceStopApp = { scope.launch { viewModel.forceStopApp(packageName) } },
        onRestartApp = { scope.launch { viewModel.restartApp(packageName) } },
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
