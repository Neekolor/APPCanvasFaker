package dev.nikko.appcanvasfaker.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import dev.nikko.appcanvasfaker.BuildConfig
import dev.nikko.appcanvasfaker.R
import dev.nikko.appcanvasfaker.acfApp
import dev.nikko.appcanvasfaker.core.ConfigRepository
import dev.nikko.appcanvasfaker.ui.LocalUiMode
import dev.nikko.appcanvasfaker.ui.UiMode
import dev.nikko.appcanvasfaker.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
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
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}