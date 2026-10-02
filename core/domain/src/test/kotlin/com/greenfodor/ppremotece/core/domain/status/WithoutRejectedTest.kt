package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class WithoutRejectedTest {
    private val urls = listOf("status/slide", "timers", "timers/current", "macro_collections")

    @Test
    fun `the url an error frame names is left out`() {
        assertThat(withoutRejected(urls, "URL: macro_collections. Error: 404 Not Found"))
            .containsExactly("status/slide", "timers", "timers/current")
    }

    @Test
    fun `a url with dots and slashes is matched whole`() {
        assertThat(withoutRejected(urls, "URL: timers/current. Error: 400 Bad Request"))
            .containsExactly("status/slide", "timers", "macro_collections")
    }

    @Test
    fun `an error frame naming no subscribed url leaves the list as it is`() {
        assertThat(withoutRejected(urls, "URL: stage/layouts. Error: 404 Not Found")).isEqualTo(urls)
        assertThat(withoutRejected(urls, "Something else went wrong")).isEqualTo(urls)
    }

    @Test
    fun `the rejected url is read from the error frame`() {
        assertThat(rejectedUrl("URL: timers. Error: 404 Not Found")).isEqualTo("timers")
        assertThat(rejectedUrl("no url here")).isNull()
    }
}
