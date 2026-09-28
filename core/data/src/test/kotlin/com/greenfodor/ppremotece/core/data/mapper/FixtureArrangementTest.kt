package com.greenfodor.ppremotece.core.data.mapper

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.greenfodor.ppremotece.core.data.Fixtures
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementExpander
import com.greenfodor.ppremotece.core.domain.arrangement.CueList
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.model.Presentation
import org.junit.jupiter.api.Test

class FixtureArrangementTest {
    private val songA = Fixtures.presentation(Fixtures.SONG_A).toDomain()
    private val songB = Fixtures.presentation(Fixtures.SONG_B).toDomain()
    private val arrangementTest = Fixtures.playlist(Fixtures.ARRANGEMENT_TEST_PLAYLIST).toDomain()
    private val service = Fixtures.playlist(Fixtures.SERVICE_PLAYLIST).toDomain()

    @Test
    fun `song A items expand to the cue counts of their own arrangements`() {
        val cueLists = (0..2).map { expand(arrangementTest, it, songA) }

        assertThat(cueLists.map { it.arrangementName() }).containsExactly("Full", "Chorus Only", "Short")
        assertThat(cueLists.map { it.cues.size }).containsExactly(26, 7, 18)
    }

    @Test
    fun `chorus only cues are labelled in arrangement order`() {
        val cueList = expand(arrangementTest, 1, songA)

        assertThat(cueList.cues.map { it.groupName }).containsExactly(
            "Loop",
            "Chorus 1",
            "Chorus 1",
            "Chorus 2",
            "Chorus 1",
            "Chorus 1",
            "Chorus 2"
        )
        assertThat(cueList.cues.map { it.index }).containsExactly(0, 1, 2, 3, 4, 5, 6)
    }

    @Test
    fun `song B items expand to full and bridge plus chorus`() {
        val full = expand(service, 4, songB)
        val bridgeAndChorus = expand(service, 6, songB)

        assertThat(full.arrangementName()).isEqualTo("Full")
        assertThat(full.cues.size).isEqualTo(20)
        assertThat(bridgeAndChorus.arrangementName()).isEqualTo("Bridge + Chorus")
        assertThat(bridgeAndChorus.cues.size).isEqualTo(4)
    }

    @Test
    fun `item pointing at the placeholder arrangement expands in song order`() {
        val placeholderSong = Fixtures.presentation(Fixtures.PLACEHOLDER_SONG).toDomain()

        val cueList = expand(arrangementTest, 5, placeholderSong)

        assertThat(cueList.choice).isEqualTo(ArrangementChoice.SongOrder)
        assertThat(cueList.cues.size).isEqualTo(11)
    }

    @Test
    fun `service items that share one item uuid resolve by index`() {
        val dto = Fixtures.playlist(Fixtures.SERVICE_PLAYLIST)
        assertThat(dto.items.orEmpty()[4].id.uuid).isEqualTo(dto.items.orEmpty()[6].id.uuid)

        val itemsByKey = service.items.associateBy { it.key }
        val fourth = requireNotNull(itemsByKey[PlaylistItemKey(service.uuid, 4)])
        val sixth = requireNotNull(itemsByKey[PlaylistItemKey(service.uuid, 6)])

        assertThat(itemsByKey.size).isEqualTo(dto.items.orEmpty().size)
        assertThat(fourth.presentation?.arrangementName).isEqualTo("Full")
        assertThat(sixth.presentation?.arrangementName).isEqualTo("Bridge + Chorus")
    }

    @Test
    fun `playlist tree keeps nested folders`() {
        val tree = Fixtures.playlistTree().map { it.toDomain() }

        val folderA = tree.filterIsInstance<PlaylistFolder>().single { it.name == "Folder A" }
        assertThat(folderA.children.filterIsInstance<PlaylistLeaf>().map { it.name }).contains("Arrangement Test")
        val nested = folderA.children.filterIsInstance<PlaylistFolder>().flatMap { it.children }
        assertThat(nested.single { it.name == "Service Playlist" })
            .isInstanceOf<PlaylistLeaf>()
            .prop(PlaylistLeaf::uuid)
            .isEqualTo(service.uuid)
    }

    private fun expand(playlist: Playlist, index: Int, presentation: Presentation): CueList {
        val item = playlist.items.single { it.key == PlaylistItemKey(playlist.uuid, index) }
        return ArrangementExpander.expand(presentation, requireNotNull(item.presentation))
    }

    private fun CueList.arrangementName(): String =
        (choice as ArrangementChoice.Resolved).arrangement.name
}
