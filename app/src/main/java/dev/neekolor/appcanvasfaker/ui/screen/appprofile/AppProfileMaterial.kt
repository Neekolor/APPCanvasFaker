package dev.neekolor.appcanvasfaker.ui.screen.appprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.neekolor.appcanvasfaker.R
import dev.neekolor.appcanvasfaker.ui.component.AppIconImage
import dev.neekolor.appcanvasfaker.ui.component.material.ExpressiveScaffold
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedColumn
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedListItem
import dev.neekolor.appcanvasfaker.ui.component.material.SegmentedSwitchItem
import dev.neekolor.appcanvasfaker.ui.component.material.SnackBarHost
import dev.neekolor.appcanvasfaker.ui.component.material.TopBarBackButton
import dev.neekolor.appcanvasfaker.ui.component.material.expressiveTopAppBarColors

@Composable
fun AppProfileScreenMaterial(
    state: AppProfileUiState,
    actions: AppProfileActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    ProfileOverflowMenu(actions = actions)
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.safeDrawingPadding()) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        AppProfileContent(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxHeight()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            state = state,
            actions = actions,
        )
    }
}

@Composable
private fun AppProfileContent(
    modifier: Modifier = Modifier,
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    Column(modifier = modifier) {
        val header: @Composable () -> Unit = {
            SegmentedListItem(
                headlineContent = {
                    Text(
                        text = state.displayLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column {
                        if (state.versionName != null) {
                            Text(
                                text = "${state.versionName} (${state.versionCode})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = state.packageName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingContent = {
                    if (state.applicationInfo != null) {
                        AppIconImage(
                            applicationInfo = state.applicationInfo,
                            label = state.displayLabel,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(48.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        )
                    }
                },
            )
        }

        SegmentedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = listOf(
                header,
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.enable_feature),
                        summary = stringResource(R.string.enable_feature_summary),
                        checked = state.enabled,
                        onCheckedChange = actions.onSetEnabled,
                    )
                },
                {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.randomize_fingerprint))
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.randomize_seed_label),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        },
                        trailingContent = {
                            ExecuteButton(onClick = actions.onRandomize)
                        },
                    )
                }
            )
        )

        if (state.fingerprints.isEmpty()) {
            Text(
                text = stringResource(R.string.log_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            SegmentedColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(R.string.randomized_values),
                content = state.fingerprints.map { fp ->
                    @Composable {
                        SegmentedListItem(
                            headlineContent = {
                                Text(
                                    text = "${fp.method} · ${fp.title}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = fp.hash,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileOverflowMenu(actions: AppProfileActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null
            )
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.launch_app)) },
                    onClick = { expanded = false; actions.onLaunchApp() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.force_stop_app)) },
                    onClick = { expanded = false; actions.onForceStopApp() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.restart_app)) },
                    onClick = { expanded = false; actions.onRestartApp() }
                )
            }
        }
    }
}

@Composable
private fun ExecuteButton(
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier,
        shape = ButtonDefaults.filledTonalShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null
        )
        Text(
            modifier = Modifier.padding(start = 7.dp),
            text = stringResource(R.string.action),
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
        )
    }
}