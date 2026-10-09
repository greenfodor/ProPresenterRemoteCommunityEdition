package com.greenfodor.ppremotece.feature.timers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.LoadableList
import com.greenfodor.ppremotece.core.designsystem.ui.ReconnectingStrip
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.domain.layout.WidthClass
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.timers.TimerCard
import com.greenfodor.ppremotece.core.domain.timers.TimerIcon
import org.koin.compose.viewmodel.koinViewModel
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val GridPadding = 8.dp
private val GridGap = 8.dp
private val BottomClearance = 88.dp
private val CardPadding = 12.dp
private val ButtonSize = 48.dp
private val RunningBorder = 2.dp
private val MinReadoutSize = 16.sp
private const val COMPACT_COLUMNS = 2
private const val DIMMED_ALPHA = 0.38f

@Composable
fun TimersRoot(
    widthClass: WidthClass,
    reconnecting: Boolean,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable (SnackbarHostState) -> Unit)? = null,
    viewModel: TimersViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimersScreen(
        state = state,
        onAction = viewModel::onAction,
        widthClass = widthClass,
        reconnecting = reconnecting,
        floatingActionButton = floatingActionButton,
        modifier = modifier
    )
}

/**
 * The Timers tab: one card per timer in two columns on compact width and an adaptive grid of
 * 200 / 240 dp cells on medium / expanded width, with 88 dp below the last row; a spinner until the
 * timers are loaded, "No timers in ProPresenter" when there are none, and "Not available on this
 * ProPresenter" when the server rejected them ([LoadableList]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimersScreen(
    state: TimersState,
    onAction: (TimersAction) -> Unit,
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
                title = { TabTitle(DesignR.drawable.ic_timer, stringResource(R.string.timers_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        Column(modifier = Modifier.fillMaxSize().padding(insets.frame)) {
            ReconnectingStrip(visible = reconnecting)
            LoadableList(state.timers, emptyText = stringResource(R.string.timers_empty)) { timers ->
                LazyVerticalGrid(
                    columns = columnsOf(widthClass),
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
                    items(timers, key = { it.uuid }) { timer ->
                        TimerCardView(
                            timer = timer,
                            large = widthClass != WidthClass.COMPACT,
                            dimmed = state.dimmed,
                            onToggle = { onAction(TimersAction.OnToggleClick(timer.uuid)) },
                            onReset = { onAction(TimersAction.OnResetClick(timer.uuid)) }
                        )
                    }
                }
            }
        }
    }
}

private fun columnsOf(widthClass: WidthClass): GridCells =
    when (widthClass) {
        WidthClass.COMPACT -> GridCells.Fixed(COMPACT_COLUMNS)
        WidthClass.MEDIUM -> GridCells.Adaptive(200.dp)
        WidthClass.EXPANDED -> GridCells.Adaptive(240.dp)
    }

/**
 * A timer card: the type icon and name, the readout in tabular numerals (`displayMedium` when
 * [large], else `displaySmall`, shrunk to fit one line; `error` while overrun), or "Counts down to"
 * over the target, "Running" with a 2 dp `secondary` outline while running, and 48 dp Start/Stop
 * and Reset buttons. The readout and target are drawn at 38 % alpha while [dimmed].
 */
@Composable
private fun TimerCardView(
    timer: TimerUi,
    large: Boolean,
    dimmed: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    val card = timer.card
    val readoutStyle = (if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall)
        .copy(fontFeatureSettings = "tnum")
    val readoutAlpha = if (dimmed) DIMMED_ALPHA else 1f
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = if (card.running) BorderStroke(RunningBorder, MaterialTheme.colorScheme.secondary) else null
    ) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(card.icon.drawable()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val target = card.target
            if (target != null) {
                Text(
                    text = stringResource(R.string.timers_counts_down_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).alpha(readoutAlpha)
                )
                Text(
                    text = target,
                    style = readoutStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = MinReadoutSize,
                        maxFontSize = readoutStyle.fontSize
                    ),
                    modifier = Modifier.fillMaxWidth().alpha(readoutAlpha)
                )
            } else {
                Text(
                    text = card.readout,
                    style = readoutStyle,
                    color = if (card.overrun) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = MinReadoutSize,
                        maxFontSize = readoutStyle.fontSize
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).alpha(readoutAlpha)
                )
                Text(
                    text = if (card.running) stringResource(R.string.timers_running) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(ButtonSize)) {
                    Icon(
                        painterResource(
                            if (card.toggle ==
                                TimerOperation.STOP
                            ) {
                                DesignR.drawable.ic_stop
                            } else {
                                DesignR.drawable.ic_play_arrow
                            }
                        ),
                        contentDescription = stringResource(
                            if (card.toggle == TimerOperation.STOP) R.string.timers_stop else R.string.timers_start,
                            timer.name
                        )
                    )
                }
                IconButton(onClick = onReset, modifier = Modifier.size(ButtonSize)) {
                    Icon(
                        painterResource(DesignR.drawable.ic_restart_alt),
                        contentDescription = stringResource(R.string.timers_reset, timer.name)
                    )
                }
            }
        }
    }
}

private fun TimerIcon.drawable(): Int =
    when (this) {
        TimerIcon.TIMER -> DesignR.drawable.ic_timer
        TimerIcon.ALARM -> DesignR.drawable.ic_alarm
        TimerIcon.AVG_PACE -> DesignR.drawable.ic_avg_pace
    }

private val PreviewTimers = listOf(
    TimerUi(
        "t-0",
        "Timer 01",
        TimerCard(
            TimerIcon.ALARM,
            "08:23:24",
            running = false,
            overrun = false,
            TimerOperation.START,
            target = "8:30 PM"
        )
    ),
    TimerUi(
        "t-1",
        "Timer 02",
        TimerCard(TimerIcon.TIMER, "00:04:59", running = true, overrun = false, TimerOperation.STOP)
    ),
    TimerUi(
        "t-2",
        "Timer 03",
        TimerCard(TimerIcon.AVG_PACE, "00:12:07", running = true, overrun = false, TimerOperation.STOP)
    ),
    TimerUi(
        "t-3",
        "Timer 04",
        TimerCard(TimerIcon.TIMER, "-00:00:42", running = true, overrun = true, TimerOperation.STOP)
    ),
    TimerUi(
        "t-4",
        "Timer 05",
        TimerCard(TimerIcon.ALARM, "-00:34:47", running = false, overrun = true, TimerOperation.START)
    )
)

@Preview(widthDp = 411, heightDp = 640)
@Composable
private fun TimersScreenCompactPreview() {
    PPRemoteTheme {
        TimersScreen(
            state = TimersState(Loadable.Loaded(PreviewTimers)),
            onAction = {},
            widthClass = WidthClass.COMPACT
        )
    }
}

@Preview(widthDp = 411, heightDp = 640)
@Composable
private fun TimersScreenReconnectingPreview() {
    PPRemoteTheme {
        TimersScreen(
            state = TimersState(Loadable.Loaded(PreviewTimers), dimmed = true),
            onAction = {},
            widthClass = WidthClass.COMPACT,
            reconnecting = true
        )
    }
}

@Preview(widthDp = 840, heightDp = 480)
@Composable
private fun TimersScreenExpandedPreview() {
    PPRemoteTheme {
        TimersScreen(
            state = TimersState(Loadable.Loaded(PreviewTimers)),
            onAction = {},
            widthClass = WidthClass.EXPANDED
        )
    }
}

@Preview(widthDp = 411, heightDp = 320)
@Composable
private fun TimersScreenEmptyPreview() {
    PPRemoteTheme {
        TimersScreen(state = TimersState(Loadable.Loaded(emptyList())), onAction = {}, widthClass = WidthClass.COMPACT)
    }
}

@Preview(widthDp = 411, heightDp = 320)
@Composable
private fun TimersScreenUnavailablePreview() {
    PPRemoteTheme {
        TimersScreen(state = TimersState(Loadable.Unavailable), onAction = {}, widthClass = WidthClass.COMPACT)
    }
}
