package com.greenfodor.ppremotece.feature.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.LoadableList
import com.greenfodor.ppremotece.core.designsystem.ui.PropThumbnail
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailRequest
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailRequests
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

internal val GridPadding = 8.dp
internal val GridGap = 10.dp
internal val BottomClearance = 88.dp
internal const val THUMBNAIL_ASPECT = 16f / 9f
private val MinCardWidth = 320.dp
private val CardMinHeight = 72.dp
private val CardThumbnailWidth = 144.dp

@Composable
fun StageRoot(
    reconnecting: Boolean,
    onOpenScreen: (String) -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: StageViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StageScreen(
        state = state,
        onOpenScreen = onOpenScreen,
        reconnecting = reconnecting,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Stage tab under a `podium` title: one card per stage screen in an adaptive grid of 320 dp
 * cells, with 88 dp below the last row while the Clear FAB shows; a tap opens the screen's layouts.
 * A spinner until the screens are loaded, "No stage screens in ProPresenter" when there are none,
 * and "Not available on this ProPresenter" when the server rejected them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageScreen(
    state: StageState,
    onOpenScreen: (String) -> Unit,
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
                title = { TabTitle(DesignR.drawable.ic_podium, stringResource(R.string.stage_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Column(modifier = Modifier.fillMaxSize().padding(insets.frame)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.screens, emptyText = stringResource(R.string.stage_empty)) { screens ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(MinCardWidth),
                    contentPadding = PaddingValues(
                        start = GridPadding,
                        top = GridPadding,
                        end = GridPadding,
                        bottom = insets.scrollBottom +
                            if (floatingActionButton != null) BottomClearance else 0.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(screens, key = { it.uuid }) { screen ->
                        StageScreenCard(
                            screen = screen,
                            thumbnails = state.thumbnails,
                            onClick = { onOpenScreen(screen.uuid) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A stage screen's card on `surfaceContainerHigh`: its layout's thumbnail 144 dp wide, its name
 * over the layout's name, or "No layout" without one, and a chevron.
 */
@Composable
private fun StageScreenCard(screen: StageScreenUi, thumbnails: StageLayoutThumbnailRequests?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = CardMinHeight).padding(12.dp)
        ) {
            StageLayoutThumbnail(
                layoutUuid = screen.layoutUuid,
                version = screen.layoutName.orEmpty(),
                thumbnails = thumbnails,
                modifier = Modifier.width(CardThumbnailWidth).clip(MaterialTheme.shapes.small)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = screen.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = screen.layoutName ?: stringResource(R.string.stage_no_layout),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painter = painterResource(DesignR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Stage layout [layoutUuid]'s thumbnail, asked at the width the box measures, in a black 16:9 box;
 * the box alone without a layout.
 */
@Composable
internal fun StageLayoutThumbnail(
    layoutUuid: String?,
    version: String,
    thumbnails: StageLayoutThumbnailRequests?,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val request = remember(thumbnails, layoutUuid, widthPx) {
        layoutUuid?.takeIf { widthPx > 0 }?.let { thumbnails?.request(it, widthPx) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(THUMBNAIL_ASPECT)
            .background(Color.Black)
            .onSizeChanged { widthPx = it.width }
    ) {
        PropThumbnail(url = request?.url, cacheKey = request?.cacheKey, version = version)
    }
}

internal val PreviewThumbnails = StageLayoutThumbnailRequests { uuid, px ->
    StageLayoutThumbnailRequest(
        "http://192.0.2.14:60113/v1/stage/layout/$uuid/thumbnail?quality=$px",
        "stage-layout:preview:$uuid:w$px"
    )
}

private val PreviewScreens = listOf(
    StageScreenUi("s-0", "Stage Screen 01", "l-0", "Layout 01"),
    StageScreenUi("s-1", "Stage Screen 02", "l-7", "Layout 08"),
    StageScreenUi("s-2", "Stage Screen 03", "l-8", "Layout 09 with a long name that is cut"),
    StageScreenUi("s-3", "Stage Screen 04", null, null)
)

@Preview(widthDp = 411, heightDp = 520)
@Composable
private fun StageScreenPreview() {
    PPRemoteTheme {
        StageScreen(
            state = StageState(Loadable.Loaded(PreviewScreens), PreviewThumbnails),
            onOpenScreen = {}
        )
    }
}

@Preview(widthDp = 1100, heightDp = 400)
@Composable
private fun StageScreenWidePreview() {
    PPRemoteTheme {
        StageScreen(
            state = StageState(Loadable.Loaded(PreviewScreens), PreviewThumbnails),
            onOpenScreen = {}
        )
    }
}

@Preview(widthDp = 411, heightDp = 300)
@Composable
private fun StageScreenEmptyPreview() {
    PPRemoteTheme {
        StageScreen(state = StageState(Loadable.Loaded(emptyList())), onOpenScreen = {})
    }
}

@Preview(widthDp = 411, heightDp = 300)
@Composable
private fun StageScreenUnavailablePreview() {
    PPRemoteTheme {
        StageScreen(state = StageState(Loadable.Unavailable), onOpenScreen = {})
    }
}
