package com.greenfodor.ppremotece.core.domain.transport

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.PlaylistItem
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.PresentationRef
import com.greenfodor.ppremotece.core.domain.model.Transport
import org.junit.jupiter.api.Test

class ItemLiveTest {
    private fun item(type: PlaylistItemType, target: String? = TARGET) =
        PlaylistItem(
            key = PlaylistItemKey("pl", 4),
            name = "Media 01",
            type = type,
            presentation = null,
            targetUuid = target
        )

    private fun playing(uuid: String, isPlaying: Boolean = true) =
        Transport(
            isPlaying = isPlaying,
            uuid = uuid,
            name = "Media 01",
            artist = "",
            audioOnly = false,
            durationSeconds = 20.0
        )

    @Test
    fun `a media item is live while the presentation transport plays its target`() {
        assertThat(itemLive(item(PlaylistItemType.MEDIA), presentation = playing(TARGET), audio = null)).isTrue()
    }

    @Test
    fun `an audio item is live while the audio transport plays its target`() {
        assertThat(itemLive(item(PlaylistItemType.AUDIO), presentation = null, audio = playing(TARGET))).isTrue()
    }

    @Test
    fun `an item is matched on its own layer only`() {
        assertThat(itemLive(item(PlaylistItemType.MEDIA), presentation = null, audio = playing(TARGET))).isFalse()
        assertThat(itemLive(item(PlaylistItemType.AUDIO), presentation = playing(TARGET), audio = null)).isFalse()
    }

    @Test
    fun `a transport that is not playing marks nothing live`() {
        val paused = playing(TARGET, isPlaying = false)

        assertThat(itemLive(item(PlaylistItemType.MEDIA), presentation = paused, audio = null)).isFalse()
        assertThat(itemLive(item(PlaylistItemType.AUDIO), presentation = null, audio = paused)).isFalse()
    }

    @Test
    fun `a transport playing another uuid marks nothing live`() {
        assertThat(itemLive(item(PlaylistItemType.MEDIA), presentation = playing("other"), audio = null)).isFalse()
        assertThat(itemLive(item(PlaylistItemType.AUDIO), presentation = null, audio = playing("other"))).isFalse()
    }

    @Test
    fun `an item without a target is never live`() {
        val empty = playing("")

        assertThat(itemLive(item(PlaylistItemType.MEDIA, target = null), presentation = empty, audio = null)).isFalse()
        assertThat(itemLive(item(PlaylistItemType.MEDIA, target = ""), presentation = empty, audio = null)).isFalse()
    }

    @Test
    fun `a live video item is never live`() {
        val item = item(PlaylistItemType.LIVE_VIDEO)

        assertThat(itemLive(item, presentation = playing(TARGET), audio = playing(TARGET))).isFalse()
    }

    @Test
    fun `a presentation item is never live by a transport`() {
        val item = item(PlaylistItemType.PRESENTATION).copy(presentation = PresentationRef(TARGET, "", ""))

        assertThat(itemLive(item, presentation = playing(TARGET), audio = playing(TARGET))).isFalse()
    }

    private companion object {
        const val TARGET = "2ca780f1-d07f-40c3-b855-5ade46ba2eb9"
    }
}
