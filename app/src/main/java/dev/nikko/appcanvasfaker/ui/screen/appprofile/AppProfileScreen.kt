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
    val successText = stringResource(R.string.randomize_success)

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                if (viewModel.randomize(packageName)) {
                    if (uiMode == UiMode.Material) {
                        snackbarHost.showSnackbar(successText)
                    } else {
                        Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    }
                }
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