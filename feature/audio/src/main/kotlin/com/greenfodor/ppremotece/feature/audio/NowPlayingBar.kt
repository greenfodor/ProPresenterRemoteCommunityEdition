package com.greenfodor.ppremotece.feature.audio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenfodor.ppremotece.core.domain.audio.NowPlaying
import com.greenfodor.ppremotece.core.domain.audio.TransportButton
import com.greenfodor.ppremotece.core.designsystem.R as DesignR

private val ProgressHeight = 4.dp
private val ButtonSize = 48.dp
private const val DIMMED_ALPHA = 0.38f
private const val FRAME_MILLIS = 1_000

/**
 * [target] as a value that moves to each new target over [FRAME_MILLIS] while [playing], so the
 * line advances evenly between the position frames; it jumps to a lower target and to any target
 * while not playing.
 */
@Composable
private fun smoothProgress(target: Float, playing: Boolean): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(target) }
    LaunchedEffect(target, playing) {
        if (playing && target > progress.value) {
            progress.animateTo(target, tween(durationMillis = FRAME_MILLIS, easing = LinearEasing))
        } else {
            progress.snapTo(target)
        }
    }
    return progress
}

/**
 * The now-playing bar: a 4 dp progress line, moving evenly while the audio plays, over the loaded audio's name and its readout (drawn at
 * 38 % alpha while [dimmed]), then previous, Play or Pause, and next, each 48 dp. With nothing
 * loaded it reads "Nothing playing" and every button is disabled.
 */
@Composable
internal fun NowPlayingBar(
    bar: NowPlaying,
    dimmed: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
    ) {
        val progress = smoothProgress(target = bar.progress, playing = bar.button == TransportButton.PAUSE)
        LinearProgressIndicator(
            progress = { progress.value },
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(ProgressHeight)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bar.loaded) bar.name else stringResource(R.string.audio_nothing_playing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (bar.loaded) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (bar.loaded) {
                    Text(
                        text = bar.readout,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alpha(if (dimmed) DIMMED_ALPHA else 1f)
                    )
                }
            }
            IconButton(onClick = onPrevious, enabled = bar.skipEnabled, modifier = Modifier.size(ButtonSize)) {
                Icon(painterResource(DesignR.drawable.ic_skip_previous), stringResource(R.string.audio_previous))
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = bar.playPauseEnabled,
                modifier = Modifier.size(ButtonSize)
            ) {
                if (bar.button == TransportButton.PAUSE) {
                    Icon(painterResource(DesignR.drawable.ic_pause), stringResource(R.string.audio_pause))
                } else {
                    Icon(painterResource(DesignR.drawable.ic_play_arrow), stringResource(R.string.audio_play))
                }
            }
            IconButton(onClick = onNext, enabled = bar.skipEnabled, modifier = Modifier.size(ButtonSize)) {
                Icon(painterResource(DesignR.drawable.ic_skip_next), stringResource(R.string.audio_next))
            }
        }
    }
}
