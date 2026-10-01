package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide

/**
 * A song with Verse 1 (2 slides), Chorus (2 slides) and Bridge (1 slide) and these arrangements:
 * "Long" V C V C B C (11 cues: chorus at 2, 6 and 9), "Short" C V C (6 cues) and an empty
 * "Placeholder"; its current arrangement is the placeholder.
 */
object SongFixtures {
    const val SONG = "song"
    val chorusColor = GroupColor(red = 0.2f, green = 0.4f, blue = 0.8f, alpha = 1f)

    val song = Presentation(
        uuid = SONG,
        name = "Song",
        groups = listOf(
            Group("v", "Verse 1", null, List(2) { Slide("Verse 1 · ${it + 1}") }),
            Group("c", "Chorus", chorusColor, List(2) { Slide("Chorus · ${it + 1}") }),
            Group("b", "Bridge", null, listOf(Slide("Bridge · 1")))
        ),
        arrangements = listOf(
            Arrangement("long", "Long", listOf("v", "c", "v", "c", "b", "c"), totalCues = 11),
            Arrangement("short", "Short", listOf("c", "v", "c"), totalCues = 6),
            Arrangement("placeholder", "Placeholder", emptyList(), totalCues = 0)
        ),
        currentArrangementUuid = "placeholder"
    )

    fun key(index: Int) = PlaylistItemKey("pl", index)

    fun ref(arrangementUuid: String) = PresentationRef(SONG, arrangementUuid, arrangementName = "")

    fun cueList(arrangementUuid: String): CueList = ArrangementExpander.expand(song, ref(arrangementUuid))
}
