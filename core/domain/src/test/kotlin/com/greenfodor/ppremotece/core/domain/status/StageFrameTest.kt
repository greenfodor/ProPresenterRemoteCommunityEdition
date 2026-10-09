package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.StageLayout
import com.greenfodor.ppremotece.core.domain.model.StageScreen
import org.junit.jupiter.api.Test

class StageFrameTest {
    private val parser = StatusFrameParser()

    @Test
    fun `stage screens decode each screen with its uuid and name in order`() {
        val frame = """{"url":"stage/screens","data":[""" +
            """{"uuid":"s-0","name":"Stage Screen 01","index":0},""" +
            """{"uuid":"s-1","name":"Stage Screen 02","index":1}]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.StageScreens(
                listOf(StageScreen("s-0", "Stage Screen 01"), StageScreen("s-1", "Stage Screen 02"))
            )
        )
    }

    @Test
    fun `stage layouts decode each layout from its id`() {
        val frame = """{"url":"stage/layouts","data":[""" +
            """{"id":{"uuid":"l-0","name":"Layout 01","index":0}},""" +
            """{"id":{"uuid":"l-1","name":"Layout 02","index":1}}]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.StageLayouts(listOf(StageLayout("l-0", "Layout 01"), StageLayout("l-1", "Layout 02")))
        )
    }

    @Test
    fun `the layout map decodes each screen's uuid with its layout's uuid`() {
        val frame = """{"url":"stage/layout_map","data":[""" +
            """{"screen":{"uuid":"s-0","name":"Stage Screen 01","index":0},""" +
            """"layout":{"uuid":"l-0","name":"Layout 01","index":0}},""" +
            """{"screen":{"uuid":"s-1","name":"Stage Screen 02","index":1},""" +
            """"layout":{"uuid":"l-7","name":"Layout 08","index":7}}]}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.StageLayoutMap(mapOf("s-0" to "l-0", "s-1" to "l-7")))
    }

    @Test
    fun `empty stage lists decode to empty values`() {
        assertThat(parser.decode("""{"url":"stage/screens","data":[]}"""))
            .isEqualTo(StatusEvent.StageScreens(emptyList()))
        assertThat(parser.decode("""{"url":"stage/layouts","data":[]}"""))
            .isEqualTo(StatusEvent.StageLayouts(emptyList()))
        assertThat(parser.decode("""{"url":"stage/layout_map","data":[]}"""))
            .isEqualTo(StatusEvent.StageLayoutMap(emptyMap()))
    }

    @Test
    fun `a screen without a layout is absent from the map`() {
        val frame = """{"url":"stage/layout_map","data":[""" +
            """{"screen":{"uuid":"s-0","name":"Stage Screen 01","index":0},""" +
            """"layout":{"uuid":"l-0","name":"Layout 01","index":0}},""" +
            """{"screen":{"uuid":"s-1","name":"Stage Screen 02","index":1},"layout":null}]}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.StageLayoutMap(mapOf("s-0" to "l-0")))
    }

    @Test
    fun `stage entries without a uuid are left out`() {
        assertThat(parser.decode("""{"url":"stage/screens","data":[{"name":"Stage Screen 01","index":0}]}"""))
            .isEqualTo(StatusEvent.StageScreens(emptyList()))
        assertThat(parser.decode("""{"url":"stage/layouts","data":[{"id":{"name":"Layout 01","index":0}}]}"""))
            .isEqualTo(StatusEvent.StageLayouts(emptyList()))
    }
}
