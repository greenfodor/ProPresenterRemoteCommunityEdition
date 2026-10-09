package com.greenfodor.ppremotece.feature.stage

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StageViewModelTest {
    private val repository = FakeStageRepository()
    private val thumbnails = FakeStageThumbnails()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StageViewModel(repository, thumbnails)

    @Test
    fun `each stage screen is a card with the name of the layout it shows, in order`() = runTest(dispatcher) {
        viewModel().state.test {
            val cards = expectMostRecentItem().screens.orEmpty()

            assertThat(cards.map { it.name }).containsExactly("Stage Screen 01", "Stage Screen 02", "Stage Screen 03")
            assertThat(cards.map { it.layoutName }).containsExactly("Layout 01", "Layout 03", null)
            assertThat(cards.map { it.layoutUuid }).containsExactly("l-0", "l-2", null)
        }
    }

    @Test
    fun `a later layout map changes the card's layout`() = runTest(dispatcher) {
        viewModel().state.test {
            expectMostRecentItem()
            repository.layoutMap.value = Loadable.Loaded(mapOf("s-0" to "l-1", "s-1" to "l-2"))

            assertThat(awaitItem().screens.orEmpty().first().layoutName).isEqualTo("Layout 02")
        }
    }

    @Test
    fun `a layout the map names but the layouts do not hold shows no layout`() = runTest(dispatcher) {
        repository.layoutMap.value = Loadable.Loaded(mapOf("s-0" to "gone"))

        viewModel().state.test {
            val card = expectMostRecentItem().screens.orEmpty().first()

            assertThat(card.layoutUuid).isEqualTo(null)
            assertThat(card.layoutName).isEqualTo(null)
        }
    }

    @Test
    fun `the cards are not loaded until the screens, the layouts and the map are`() = runTest(dispatcher) {
        repository.layoutMap.value = Loadable.NotLoaded

        viewModel().state.test {
            assertThat(expectMostRecentItem().screens).isEqualTo(Loadable.NotLoaded)
            repository.layoutMap.value = Loadable.Loaded(emptyMap())
            assertThat(awaitItem().screens.orEmpty().size).isEqualTo(3)
        }
    }

    @Test
    fun `no stage screens load as an empty list and a rejected stage url as unavailable`() = runTest(dispatcher) {
        repository.screens.value = Loadable.Loaded(emptyList())

        viewModel().state.test {
            assertThat(expectMostRecentItem().screens).isEqualTo(Loadable.Loaded(emptyList()))
            repository.layouts.value = Loadable.Unavailable
            assertThat(awaitItem().screens).isEqualTo(Loadable.Unavailable)
        }
    }

    @Test
    fun `the thumbnail requests of the connected host reach the state`() = runTest(dispatcher) {
        viewModel().state.test {
            assertThat(expectMostRecentItem().thumbnails?.request("l-0", 640)?.cacheKey).isEqualTo("k:l-0:640")
        }
    }
}
