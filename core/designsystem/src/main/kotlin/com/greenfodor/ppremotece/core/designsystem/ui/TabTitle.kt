package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.designsystem.R
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme

private val IconSize = 24.dp
private val IconGap = 12.dp

/** A tab root's title: the destination's 24 dp [icon], 12 dp, then [title] on one line, centred vertically. */
@Composable
fun TabTitle(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(IconSize))
        Spacer(modifier = Modifier.width(IconGap))
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(widthDp = 411)
@Composable
private fun TabTitlePreview() {
    PPRemoteTheme {
        TopAppBar(
            title = { TabTitle(icon = R.drawable.ic_bolt, title = "Macros") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        )
    }
}
