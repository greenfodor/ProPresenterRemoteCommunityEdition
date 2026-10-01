package com.greenfodor.ppremotece.feature.settings

import androidx.compose.ui.platform.UriHandler
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class OpenUriTest {
    @Test
    fun `a link no app can open is ignored`() {
        val noBrowser = object : UriHandler {
            override fun openUri(uri: String) = throw IllegalArgumentException("Can't open $uri.")
        }

        assertThat(noBrowser.tryOpenUri(SOURCE_URL)).isFalse()
    }

    @Test
    fun `a link is opened with the uri handler`() {
        val opened = mutableListOf<String>()
        val browser = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }

        assertThat(browser.tryOpenUri(SOURCE_URL)).isTrue()
        assertThat(opened).isEqualTo(listOf(SOURCE_URL))
    }
}
