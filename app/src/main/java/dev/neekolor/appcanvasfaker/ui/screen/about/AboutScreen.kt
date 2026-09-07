package dev.neekolor.appcanvasfaker.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neekolor.appcanvasfaker.BuildConfig
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.acfApp
import dev.neekolor.appcanvasfaker.core.ConfigRepository
import dev.neekolor.appcanvasfaker.core.UpdateCenter
import kotlinx.coroutines.launch
import dev.neekolor.appcanvasfaker.ui.LocalUiMode
import dev.neekolor.appcanvasfaker.ui.UiMode
import dev.neekolor.appcanvasfaker.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val versionName = BuildConfig.VERSION_NAME
    val htmlString = stringResource(
        id = R.string.about_source_code,
        "<b><a href=\"https://github.com\">GitHub</a></b>"
    )
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = versionName,
        description = stringResource(R.string.about_description),
        license = stringResource(R.string.about_license),
        links = extractLinks(htmlString),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = uriHandler::openUri,
        onCheckUpdate = {
            scope.launch {
                UpdateCenter.check(acfApp, manual = true)
            }
        },
    )
    // 更新弹窗由 MainActivity 根统一渲染（自动/手动共用同一状态），此处只触发检查。
    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}