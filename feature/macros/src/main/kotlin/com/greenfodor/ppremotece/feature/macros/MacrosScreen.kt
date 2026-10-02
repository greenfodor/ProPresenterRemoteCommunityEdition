package com.greenfodor.ppremotece.feature.macros

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.LocalGroupColors
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.LoadableList
import com.greenfodor.ppremotece.core.designsystem.ui.ObserveAsEvents
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.ServerIconImage
import com.greenfodor.ppremotece.core.designsystem.ui.toColor
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.ServerIcon
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val GridPadding = 8.dp
private val GridGap = 8.dp
private val BottomClearance = 88.dp
private val HeaderHeight = 48.dp
private val TilePadding = 12.dp
private val IconSize = 48.dp
private const val CHECK_SCRIM_ALPHA = 0.45f

@Composable
fun MacrosRoot(
    widthClass: WidthClass,
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: MacrosViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MacrosEvent.ShowError -> scope.launch { snackbarHostState.showSnackbar(event.message.asString(context)) }
        }
    }
    MacrosScreen(
        state = state,
        onAction = viewModel::onAction,
        widthClass = widthClass,
        reconnecting = reconnecting,
        snackbarHostState = snackbarHostState,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Macros tab: one section per collection in an adaptive grid of square tiles (160 / 200 /
 * 240 dp cells by width class), each section under a 48 dp header while there are several, with
 * 88 dp below the last row; a spinner until the collections are loaded, "No macros in
 * ProPresenter" when there are none, and "Not available on this ProPresenter" when the server
 * rejected them ([LoadableList]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen(
    state: MacrosState,
    onAction: (MacrosAction) -> Unit,
    widthClass: WidthClass,
    modifier: Modifier = Modifier,
    reconnecting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { floatingActionButton?.invoke(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.macros_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.sections, emptyText = stringResource(R.string.macros_empty)) { sections ->
                MacroGrid(
                    sections = sections,
                    showHeaders = state.showHeaders,
                    widthClass = widthClass,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun MacroGrid(
    sections: List<MacroSectionUi>,
    showHeaders: Boolean,
    widthClass: WidthClass,
    onAction: (MacrosAction) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth(widthClass)),
        contentPadding = PaddingValues(
            start = GridPadding,
            top = GridPadding,
            end = GridPadding,
            bottom = BottomClearance
        ),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridGap),
        modifier = Modifier.fillMaxSize()
    ) {
        sections.forEach { section ->
            if (showHeaders) {
                item(key = "header:${section.uuid}", span = { GridItemSpan(maxLineSpan) }) {
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.height(HeaderHeight)) {
                        Text(
                            text = section.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = GridPadding)
                        )
                    }
                }
            }
            items(section.macros, key = { "${section.uuid}:${it.uuid}" }) { macro ->
                MacroTile(macro = macro, onClick = { onAction(MacrosAction.OnMacroClick(macro.uuid)) })
            }
        }
    }
}

private fun minCellWidth(widthClass: WidthClass) =
    when (widthClass) {
        WidthClass.COMPACT -> 160.dp
        WidthClass.MEDIUM -> 200.dp
        WidthClass.EXPANDED -> 240.dp
    }

/**
 * A square tile with 16 dp corners filled with the macro's colour (`surfaceContainerHigh` without
 * one): the served icon centred at 48 dp in white, the name below in `titleSmall` with the label
 * colour that contrasts with the fill, up to two lines, and a check over the tile while [MacroUi.confirmed].
 */
@Composable
private fun MacroTile(macro: MacroUi, onClick: () -> Unit) {
    val fill = macro.color?.toColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = fill,
        contentColor = LocalGroupColors.current.labelOn(fill),
        modifier = Modifier.aspectRatio(1f)
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(TilePadding)
            ) {
                macro.icon?.let {
                    ServerIconImage(icon = it, tint = Color.White, size = IconSize)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = macro.name,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (macro.confirmed) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CHECK_SCRIM_ALPHA))
                ) {
                    Icon(
                        painterResource(DesignR.drawable.ic_check),
                        contentDescription = stringResource(R.string.macros_ran, macro.name),
                        tint = Color.White,
                        modifier = Modifier.size(IconSize)
                    )
                }
            }
        }
    }
}

private val PreviewIcon = ServerIcon.Vector(
    viewportWidth = 18f,
    viewportHeight = 18f,
    paths = listOf(IconPath("M0,0 L18,0 L18,18 L0,18 Z M6,6 L6,12 L12,12 L12,6 Z", evenOdd = true))
)
private val PreviewMacros = listOf(
    MacroUi("m-0", "Macro 01", GroupColor(0.09f, 0.5f, 1f, 1f), PreviewIcon, confirmed = false),
    MacroUi("m-1", "Macro 02 with a name long enough for two lines", GroupColor(1f, 0f, 0f, 1f), PreviewIcon, false),
    MacroUi("m-2", "Macro 03", color = null, icon = PreviewIcon, confirmed = false),
    MacroUi("m-3", "Macro 04", GroupColor(0.24f, 0.7f, 0.44f, 1f), icon = null, confirmed = false),
    MacroUi("m-4", "Macro 05", GroupColor(0.9f, 0.9f, 0.3f, 1f), PreviewIcon, confirmed = true)
)

@Preview(widthDp = 411, heightDp = 640)
@Composable
private fun MacrosScreenPreview() {
    PPRemoteTheme {
        MacrosScreen(
            state = MacrosState(Loadable.Loaded(listOf(MacroSectionUi("c-0", "Collection 01", PreviewMacros)))),
            onAction = {},
            widthClass = WidthClass.COMPACT
        )
    }
}

@Preview(widthDp = 411, heightDp = 800)
@Composable
private fun MacrosScreenTwoCollectionsPreview() {
    PPRemoteTheme {
        MacrosScreen(
            state = MacrosState(
                sections = Loadable.Loaded(
                    listOf(
                        MacroSectionUi("c-0", "Collection 01", PreviewMacros.take(3)),
                        MacroSectionUi("c-1", "Collection 02", PreviewMacros.drop(3))
                    )
                ),
                showHeaders = true
            ),
            onAction = {},
            widthClass = WidthClass.COMPACT
        )
    }
}

@Preview(widthDp = 411, heightDp = 320)
@Composable
private fun MacrosScreenEmptyPreview() {
    PPRemoteTheme {
        MacrosScreen(
            state = MacrosState(Loadable.Loaded(emptyList())),
            onAction = {},
            widthClass = WidthClass.COMPACT,
            reconnecting = true
        )
    }
}
