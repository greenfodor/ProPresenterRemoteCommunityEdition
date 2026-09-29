package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.SlideText
import org.junit.jupiter.api.Test

class StatusFrameParserTest {
    private val parser = StatusFrameParser()

    @Test
    fun `complete frame in one chunk is emitted`() {
        val frames = parser.feed(bytes("""{"url":"status/slide","data":{}}""" + SEPARATOR))

        assertThat(frames).containsExactly("""{"url":"status/slide","data":{}}""")
    }

    @Test
    fun `frame spanning chunks is emitted once it is complete`() {
        val frame = """{"url":"timer/system_time","data":1790534128}"""

        assertThat(parser.feed(bytes(frame.substring(0, 10)))).isEmpty()
        assertThat(parser.feed(bytes(frame.substring(10) + "\r\n"))).isEmpty()
        assertThat(parser.feed(bytes("\r\n"))).containsExactly(frame)
    }

    @Test
    fun `several frames in one chunk are emitted in order and the partial rest is kept`() {
        val first = """{"url":"status/slide","data":{}}"""
        val second = """{"url":"timer/system_time","data":1}"""
        val third = """{"url":"timer/system_time","data":2}"""

        val frames = parser.feed(bytes(first + SEPARATOR + second + SEPARATOR + third.take(5)))

        assertThat(frames).containsExactly(first, second)
        assertThat(parser.feed(bytes(third.drop(5) + SEPARATOR))).containsExactly(third)
    }

    @Test
    fun `multi-byte UTF-8 character split across chunks is decoded intact`() {
        val frame = """{"url":"status/slide","data":{"current":{"text":"Ș ă"}}}"""
        val encoded = bytes(frame + SEPARATOR)
        val splitInsideCharacter = frame.indexOf('Ș') + 1

        assertThat(parser.feed(encoded.copyOfRange(0, splitInsideCharacter))).isEmpty()
        assertThat(parser.feed(encoded.copyOfRange(splitInsideCharacter, encoded.size))).containsExactly(frame)
    }

    @Test
    fun `separator split across chunks still ends the frame`() {
        val frame = """{"url":"status/slide","data":{}}"""

        assertThat(parser.feed(bytes("$frame\r\n\r"))).isEmpty()
        assertThat(parser.feed(bytes("\n"))).containsExactly(frame)
    }

    @Test
    fun `unterminated input beyond the buffer limit is dropped`() {
        parser.feed(ByteArray(StatusFrameParser.MAX_PENDING_BYTES + 1) { 'x'.code.toByte() })

        assertThat(parser.feed(bytes("""{"url":"status/slide","data":{}}""" + SEPARATOR)))
            .containsExactly("""{"url":"status/slide","data":{}}""")
    }

    @Test
    fun `blank frames are skipped`() {
        assertThat(parser.feed(bytes(SEPARATOR + " " + SEPARATOR))).isEmpty()
    }

    @Test
    fun `status slide decodes to a slide change`() {
        assertThat(parser.decode("""{"url":"status/slide","data":{"current":{"uuid":"s-1"}}}"""))
            .isEqualTo(StatusEvent.SlideChanged(SlideText(current = "", next = "")))
    }

    @Test
    fun `status slide carries the current and next text`() {
        val frame = """{"url":"status/slide","data":{"current":{"text":"Text 03","notes":"n","uuid":"s-1"},""" +
            """"next":{"text":"Text 04","notes":"n","uuid":"s-2"}}}"""

        assertThat(
            parser.decode(frame)
        ).isEqualTo(StatusEvent.SlideChanged(SlideText(current = "Text 03", next = "Text 04")))
    }

    @Test
    fun `slide index decodes the live presentation position`() {
        val frame = """{"url":"presentation/slide_index","data":{"presentation_index":{"index":3,""" +
            """"presentation_id":{"uuid":"p-1","name":"Song","index":0},"total_cues":7,"remaining_cues":3}}}"""

        assertThat(parser.decode(frame))
            .isEqualTo(StatusEvent.SlideIndex(LiveSlide(presentationUuid = "p-1", index = 3, totalCues = 7)))
    }

    @Test
    fun `slide index without a presentation decodes to no live slide`() {
        assertThat(parser.decode("""{"url":"presentation/slide_index","data":{"presentation_index":null}}"""))
            .isEqualTo(StatusEvent.SlideIndex(null))
    }

    @Test
    fun `presentation active decodes the presentation uuid`() {
        val frame = """{"url":"presentation/active","data":{"presentation":""" +
            """{"id":{"uuid":"p-1","name":"Song","index":0}}}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.PresentationActive("p-1"))
    }

    @Test
    fun `playlist active decodes the live item key`() {
        val frame = """{"url":"playlist/active","data":{"presentation":{""" +
            """"playlist":{"uuid":"pl-1","name":"List","index":7},"item":{"uuid":"i-1","name":"Song","index":2}},""" +
            """"announcements":{"playlist":null,"item":null}}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.PlaylistActive(PlaylistItemKey("pl-1", 2)))
    }

    @Test
    fun `playlist active carries the presentation of the live item`() {
        val frame = """{"url":"playlist/active","data":{"presentation":{""" +
            """"playlist":{"uuid":"pl-1","name":"List","index":7},"item":{"uuid":"i-1","name":"Song","index":2},""" +
            """"playlist_item":{"id":{"uuid":"i-1","name":"Song","index":2},"type":"presentation",""" +
            """"presentation_info":{"presentation_uuid":"p-1","arrangement_name":"A","arrangement_uuid":"a-1"}}}}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.PlaylistActive(PlaylistItemKey("pl-1", 2), "p-1"))
    }

    @Test
    fun `playlist active without a presentation decodes to no live item`() {
        val frame = """{"url":"playlist/active","data":{"presentation":{"playlist":null,"item":null}}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.PlaylistActive(null))
    }

    @Test
    fun `system time decodes to a heartbeat`() {
        assertThat(parser.decode("""{"url":"timer/system_time","data":1790534128}"""))
            .isEqualTo(StatusEvent.Heartbeat(epochSeconds = 1790534128))
    }

    @Test
    fun `unknown url and malformed frames decode to unknown`() {
        assertThat(
            parser.decode("""{"url":"stage/message","data":{}}""")
        ).isEqualTo(StatusEvent.Unknown("stage/message"))
        assertThat(parser.decode("""{"url":""")).isEqualTo(StatusEvent.Unknown(null))
    }

    @Test
    fun `events feeds a chunk and decodes its complete frames`() {
        val chunk =
            """{"url":"status/slide","data":{}}""" + SEPARATOR + """{"url":"timer/system_time","data":5}""" + SEPARATOR

        assertThat(parser.events(bytes(chunk))).containsExactly(
            StatusEvent.SlideChanged(SlideText(current = "", next = "")),
            StatusEvent.Heartbeat(5)
        )
    }

    private fun bytes(text: String) = text.encodeToByteArray()

    private companion object {
        const val SEPARATOR = "\r\n\r\n"
    }
}
