package com.greenfodor.ppremotece.feature.playlist.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.feature.playlist.R
import com.greenfodor.ppremotece.feature.playlist.tree.ExpandableRow
import com.greenfodor.ppremotece.feature.playlist.tree.TreeError
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val RowHeight = 56.dp
private val SectionHeight = 48.dp
private val DepthIndent = 16.dp

/**
 * Library mode's list: a search field above the libraries, or above the search results grouped
 * under their library names; the presentation [openPresentation] is highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryList(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
    openPresentation: String? = null,
    bottomPadding: Dp = 0.dp
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(LibraryAction.OnQueryChange(it)) },
            placeholder = { Text(stringResource(R.string.library_search)) },
            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(LibraryAction.OnRefresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> TreeError(state.error, onRetry = { onAction(LibraryAction.OnRetryClick) })
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.noResults) {
                        item {
                            Text(
                                text = stringResource(R.string.library_no_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(state.rows, key = { it.id }) { row ->
                        LibraryRow(row = row, openPresentation = openPresentation, onAction = onAction)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(row: LibraryRowUi, openPresentation: String?, onAction: (LibraryAction) -> Unit) {
    when (row) {
        is LibraryRowUi.Library -> ExpandableRow(
            depth = 0,
            name = row.name,
            expanded = row.expanded,
            isLoading = row.isLoading,
            onClick = { onAction(LibraryAction.OnLibraryClick(row.id)) }
        )
        is LibraryRowUi.Section -> Text(
            text = row.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SectionHeight)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        is LibraryRowUi.Presentation -> PresentationRow(
            name = row.name,
            selected = row.uuid == openPresentation,
            onClick = { onAction(LibraryAction.OnPresentationClick(row.uuid)) }
        )
    }
}

@Composable
private fun PresentationRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(start = DepthIndent + 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    )
}

private val PreviewLibraries = listOf(
    LibraryRowUi.Library("l-1", "Library 01", expanded = true, isLoading = false),
    LibraryRowUi.Presentation("l-1/p-1", "p-1", "Song A"),
    LibraryRowUi.Presentation("l-1/p-2", "p-2", "Song B"),
    LibraryRowUi.Library("l-2", "Library 02", expanded = false, isLoading = false),
    LibraryRowUi.Library("l-3", "Library 03", expanded = false, isLoading = true)
)

@Preview(widthDp = 360)
@Composable
private fun LibraryListCollapsedPreview() {
    PPRemoteTheme {
        Surface {
            LibraryList(
                state = LibraryState(
                    isLoading = false,
                    rows = PreviewLibraries.filterIsInstance<LibraryRowUi.Library>().map { it.copy(expanded = false) }
                ),
                onAction = {}
            )
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun LibraryListExpandedPreview() {
    PPRemoteTheme {
        Surface {
            LibraryList(state = LibraryState(isLoading = false, rows = PreviewLibraries), onAction = {
            }, openPresentation = "p-2")
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun LibraryListSearchResultsPreview() {
    PPRemoteTheme {
        Surface {
            LibraryList(
                state = LibraryState(
                    query = "song",
                    isLoading = false,
                    rows = listOf(
                        LibraryRowUi.Section("section/l-1", "Library 01"),
                        LibraryRowUi.Presentation("l-1/p-1", "p-1", "Song A"),
                        LibraryRowUi.Section("section/l-2", "Library 02"),
                        LibraryRowUi.Presentation("l-2/p-3", "p-3", "Song C")
                    )
                ),
                onAction = {}
            )
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun LibraryListNoResultsPreview() {
    PPRemoteTheme {
        Surface {
            LibraryList(state = LibraryState(query = "qqq", isLoading = false, noResults = true), onAction = {})
        }
    }
}
