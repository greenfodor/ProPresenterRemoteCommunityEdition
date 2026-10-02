package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.CountDownTarget
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.Macro
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.SlideText
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType
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
    fun `status layers decodes the layers with content`() {
        val frame = """{"url":"status/layers","data":{"video_input":false,"media":true,"slide":true,""" +
            """"announcements":false,"props":false,"messages":false,"audio":false}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.Layers(setOf(OutputLayer.SLIDE, OutputLayer.MEDIA)))
    }

    @Test
    fun `status layers without an object decodes to unknown`() {
        assertThat(
            parser.decode("""{"url":"status/layers","data":null}""")
        ).isEqualTo(StatusEvent.Unknown("status/layers"))
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

    @Test
    fun `timers decodes each timer with its type`() {
        val frame = """{"url":"timers","data":[""" +
            """{"id":{"name":"Timer 01","index":0,"uuid":"t-0"},"allows_overrun":true,""" +
            """"count_down_to_time":{"time_of_day":30600,"period":"pm"}},""" +
            """{"id":{"name":"Timer 02","index":1,"uuid":"t-1"},"allows_overrun":false,""" +
            """"countdown":{"duration":300}},""" +
            """{"id":{"name":"Timer 03","index":2,"uuid":"t-2"},"allows_overrun":false,"elapsed":{"start_time":0}}""" +
            """]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.Timers(
                listOf(
                    Timer(
                        "t-0",
                        "Timer 01",
                        0,
                        TimerType.COUNTDOWN_TO_TIME,
                        allowsOverrun = true,
                        target = CountDownTarget(timeOfDaySeconds = 30600, period = "pm")
                    ),
                    Timer("t-1", "Timer 02", 1, TimerType.COUNTDOWN, allowsOverrun = false),
                    Timer("t-2", "Timer 03", 2, TimerType.ELAPSED, allowsOverrun = false)
                )
            )
        )
    }

    @Test
    fun `a timer of an unknown type is kept as unknown`() {
        val frame = """{"url":"timers","data":[{"id":{"name":"Timer 01","index":0,"uuid":"t-0"},"lap":{}}]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.Timers(listOf(Timer("t-0", "Timer 01", 0, TimerType.UNKNOWN, allowsOverrun = false)))
        )
    }

    @Test
    fun `looks decode each look with its uuid, name and index`() {
        val frame = """{"url":"looks","data":[""" +
            """{"id":{"uuid":"l-0","name":"Look 01","index":0},"screens":[{"slide":true,"presentation":""}]},""" +
            """{"id":{"uuid":"l-1","name":"Look 02","index":1},"screens":[]}""" +
            """]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.Looks(listOf(Look("l-0", "Look 01", 0), Look("l-1", "Look 02", 1)))
        )
    }

    @Test
    fun `the current look decodes to the live look's own uuid with its name and index`() {
        val frame = """{"url":"look/current","data":{"id":{"uuid":"live","name":"Look 02","index":1},"screens":[]}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.CurrentLook(Look("live", "Look 02", 1)))
    }

    @Test
    fun `looks without a list decode to unknown`() {
        assertThat(parser.decode("""{"url":"looks","data":{}}""")).isEqualTo(StatusEvent.Unknown("looks"))
    }

    @Test
    fun `an error frame decodes to the url it rejects`() {
        assertThat(parser.decode("""["URL: stage/layouts. Error: 404 Not Found","URL: looks. Error: 404 Not Found"]"""))
            .isEqualTo(
                StatusEvent.Rejected(
                    listOf("URL: stage/layouts. Error: 404 Not Found", "URL: looks. Error: 404 Not Found")
                )
            )
    }

    @Test
    fun `current timers decode each reading with the time as sent`() {
        val frame = """{"url":"timers/current","data":[""" +
            """{"id":{"uuid":"t-0","name":"Timer 01","index":0},"time":"17:05:22","state":"running"},""" +
            """{"id":{"uuid":"t-1","name":"Timer 02","index":1},"time":"-00:00:02","state":"overrunning"}""" +
            """]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.TimerReadings(
                listOf(
                    TimerReading("t-0", "17:05:22", TimerState.RUNNING),
                    TimerReading("t-1", "-00:00:02", TimerState.OVERRUNNING)
                )
            )
        )
    }

    @Test
    fun `each timer state is read, overrun as overran and anything else as unknown`() {
        val states = listOf("stopped", "running", "complete", "overrunning", "overran", "overrun", "paused")
        val frame = """{"url":"timers/current","data":[""" +
            states.joinToString(",") { """{"id":{"uuid":"t-$it"},"time":"00:00:01.00","state":"$it"}""" } + "]}"

        val readings = (parser.decode(frame) as StatusEvent.TimerReadings).readings

        assertThat(readings.map { it.state }).containsExactly(
            TimerState.STOPPED,
            TimerState.RUNNING,
            TimerState.COMPLETE,
            TimerState.OVERRUNNING,
            TimerState.OVERRAN,
            TimerState.OVERRAN,
            TimerState.UNKNOWN
        )
    }

    @Test
    fun `timers without a list decode to unknown`() {
        assertThat(parser.decode("""{"url":"timers","data":{}}""")).isEqualTo(StatusEvent.Unknown("timers"))
        assertThat(parser.decode("""{"url":"timers/current","data":null}"""))
            .isEqualTo(StatusEvent.Unknown("timers/current"))
    }

    @Test
    fun `macro collections decode each collection with its macros and colours`() {
        val frame = """{"url":"macro_collections","data":{"collections":[""" +
            """{"id":{"uuid":"c-0","name":"Collection 01","index":0},"macros":[""" +
            """{"id":{"uuid":"m-0","name":"Macro 01","index":0},""" +
            """"color":{"red":1.0,"green":0.5,"blue":0.0,"alpha":1.0},"image_type":"Default","actions":[]},""" +
            """{"id":{"uuid":"m-1","name":"Macro 02","index":1},"image_type":"Custom","actions":[]}""" +
            """]}]}}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.MacroCollections(
                listOf(
                    MacroCollection(
                        uuid = "c-0",
                        name = "Collection 01",
                        index = 0,
                        macros = listOf(
                            Macro(
                                "m-0",
                                "Macro 01",
                                0,
                                GroupColor(red = 1f, green = 0.5f, blue = 0f, alpha = 1f),
                                imageType = "Default"
                            ),
                            Macro("m-1", "Macro 02", 1, color = null, imageType = "Custom")
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `macro collections without a list decode to unknown`() {
        assertThat(parser.decode("""{"url":"macro_collections","data":[]}"""))
            .isEqualTo(StatusEvent.Unknown("macro_collections"))
    }

    private fun bytes(text: String) = text.encodeToByteArray()

    private companion object {
        const val SEPARATOR = "\r\n\r\n"
    }
}
