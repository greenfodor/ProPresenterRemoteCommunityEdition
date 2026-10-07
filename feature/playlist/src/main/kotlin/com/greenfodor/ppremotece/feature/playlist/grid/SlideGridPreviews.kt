package com.greenfodor.ppremotece.feature.playlist.grid

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import com.greenfodor.ppremotece.core.designsystem.ui.SyntheticThumbnails
import com.greenfodor.ppremotece.core.domain.live.CueStep
import com.greenfodor.ppremotece.core.domain.live.CueSteps
import com.greenfodor.ppremotece.core.domain.live.MarkedCue
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailRequest
import com.greenfodor.ppremotece.feature.playlist.ArrangementLabel

private const val PREVIEW_LIBRARY_CUES = 15

@Composable
private fun GridPreview(cleared: Boolean, steps: CueSteps) {
    val chorus = GroupColor(red = 0f, green = 0.47f, blue = 0.8f, alpha = 1f)

    fun thumbnail(cue: Int) = ThumbnailRequest(
        "http://192.0.2.14:60113/v1/playlist/p/6/thumbnail/$cue?quality=400",
        "k$cue"
    )
    PPRemoteTheme {
        SyntheticThumbnails {
            SlideGridScreen(
                state = SlideGridState(
                    title = "Song C",
                    label = ArrangementLabel.Named("A"),
                    cues = listOf(
                        CueUi(0, "Verse 1", null, "Verse 1 · 1", label = "", enabled = true, thumbnail = thumbnail(0)),
                        CueUi(
                            1,
                            "Verse 1",
                            null,
                            "Verse 1 · 2",
                            "",
                            enabled = false,
                            thumbnail(1),
                            startsGroup = false
                        ),
                        CueUi(2, "Verse 1", null, "Verse 1 · 3", "", enabled = true, thumbnail(2), startsGroup = false),
                        CueUi(3, "Chorus", chorus, "Chorus · 1", label = "Label 01", enabled = true),
                        CueUi(4, "Chorus", chorus, "", label = "", enabled = true, startsGroup = false)
                    ),
                    aspect = 1920f / 858f,
                    countMismatch = true,
                    marked = MarkedCue(index = 0, cleared = cleared, next = 2),
                    steps = steps,
                    isLoading = false
                ),
                onAction = {},
                onBack = {}
            )
        }
    }
}

@Preview
@Composable
private fun SlideGridScreenPreview() {
    GridPreview(cleared = false, steps = CueSteps(next = CueStep.Relative, previous = CueStep.Relative))
}

@Preview
@Composable
private fun SlideGridClearedPreview() {
    GridPreview(cleared = true, steps = CueSteps(next = CueStep.Explicit(2), previous = CueStep.Disabled))
}

@Preview
@Composable
private fun LibraryGridScreenPreview() {
    PPRemoteTheme {
        SlideGridScreen(
            state = SlideGridState(
                title = "Song A",
                label = null,
                cues = List(PREVIEW_LIBRARY_CUES) {
                    CueUi(it, "Verse 1", null, "Verse 1 · ${it + 1}", label = "", enabled = true, startsGroup = it == 0)
                },
                aspect = 1920f / 858f,
                marked = MarkedCue(index = 3, cleared = false, next = 4),
                steps = CueSteps(next = CueStep.Relative, previous = CueStep.Relative),
                isLoading = false
            ),
            onAction = {},
            onBack = {}
        )
    }
}
