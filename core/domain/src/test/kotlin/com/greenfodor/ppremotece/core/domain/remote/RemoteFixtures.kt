package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideText

/**
 * A playlist shaped like the device test playlist:
 * 0 Song A "Full" (5 cues), 1 Song A "Chorus Only" (2 cues), 2 header, 3 placeholder, 4 media,
 * 5 Song C "A" (8 cues, cues 1 and 6 disabled), 6 audio, 7 Song C "Broken" (partly unresolved).
 */
object RemoteFixtures {
    const val PLAYLIST = "pl"
    const val SONG_A = "song-a"
    const val SONG_C = "song-c"

    fun key(index: Int) = PlaylistItemKey(PLAYLIST, index)

    val songA = Presentation(
        uuid = SONG_A,
        name = "Song A",
        groups = listOf(
            Group("verse", "Verse 1", null, List(3) { Slide("Verse 1 · ${it + 1}") }),
            Group("chorus", "Chorus", null, List(2) { Slide("Chorus · ${it + 1}") })
        ),
        arrangements = listOf(
            Arrangement("full", "Full", listOf("verse", "chorus"), totalCues = 5),
            Arrangement("chorus-only", "Chorus Only", listOf("chorus"), totalCues = 2)
        )
    )

    val songC = Presentation(
        uuid = SONG_C,
        name = "Song C",
        groups = listOf(
            Group(
                "v",
                "Verse 1",
                null,
                listOf(Slide("V1 A"), Slide("V1 B", enabled = false), Slide("V1 C"))
            ),
            Group("c", "Chorus", null, listOf(Slide("C a"), Slide("C b")))
        ),
        arrangements = listOf(
            Arrangement("a", "A", listOf("v", "c", "v"), totalCues = 8),
            Arrangement("broken", "Broken", listOf("v", "missing", "c"), totalCues = 7)
        )
    )

    val presentations = mapOf(SONG_A to songA, SONG_C to songC)

    val playlist = Playlist(
        uuid = PLAYLIST,
        name = "Arrangement Test",
        items = listOf(
            item(0, "Song A", PlaylistItemType.PRESENTATION, PresentationRef(SONG_A, "full", "Full")),
            item(1, "Song A", PlaylistItemType.PRESENTATION, PresentationRef(SONG_A, "chorus-only", "Chorus Only")),
            item(2, "Header", PlaylistItemType.HEADER, null),
            item(3, "Placeholder", PlaylistItemType.PLACEHOLDER, null),
            item(4, "Loop", PlaylistItemType.MEDIA, null),
            item(5, "Song C", PlaylistItemType.PRESENTATION, PresentationRef(SONG_C, "a", "A")),
            item(6, "Walk-in", PlaylistItemType.AUDIO, null),
            item(7, "Song C", PlaylistItemType.PRESENTATION, PresentationRef(SONG_C, "broken", "Broken"))
        )
    )

    private fun item(index: Int, name: String, type: PlaylistItemType, ref: PresentationRef?) =
        PlaylistItem(key(index), name, type, ref)

    fun liveAt(item: Int, presentation: String, cue: Int, text: SlideText? = null) =
        LiveState(
            connection = ConnectionStatus.CONNECTED,
            item = key(item),
            slide = LiveSlide(presentation, cue, totalCues = 0),
            slideText = text
        )

    /** A cleared output: nothing live, with the text of the last `status/slide` frame. */
    val cleared = LiveState(connection = ConnectionStatus.CONNECTED, item = null, slide = null)

    fun remembered(item: Int, presentation: String, cue: Int) = LiveCue(key(item), presentation, cue)
}
