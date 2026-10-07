package com.greenfodor.ppremotece.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.TabTitle
import com.greenfodor.ppremotece.core.designsystem.ui.scrollInsets
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

/** A destination listed under More: its icon, its label and the route a tap opens. */
data class MoreEntry(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    val route: NavKey
)

/** The More list: one 56 dp row per entry; a tap opens the entry's route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(entries: List<MoreEntry>, onOpen: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { TabTitle(DesignR.drawable.ic_more_horiz, stringResource(R.string.more_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { padding ->
        val insets = scrollInsets(padding)
        LazyColumn(
            contentPadding = PaddingValues(bottom = insets.scrollBottom),
            modifier = Modifier.fillMaxSize().padding(insets.frame)
        ) {
            items(entries, key = { it.route.toString() }) { entry ->
                ListItem(
                    headlineContent = { Text(stringResource(entry.label)) },
                    leadingContent = { Icon(painterResource(entry.icon), contentDescription = null) },
                    modifier = Modifier.clickable { onOpen(entry.route) }
                )
            }
        }
    }
}

@Preview
@Composable
private fun MoreScreenPreview() {
    PPRemoteTheme {
        MoreScreen(
            entries = listOf(MoreEntry(DesignR.drawable.ic_settings, R.string.settings_title, SettingsRoute)),
            onOpen = {}
        )
    }
}
