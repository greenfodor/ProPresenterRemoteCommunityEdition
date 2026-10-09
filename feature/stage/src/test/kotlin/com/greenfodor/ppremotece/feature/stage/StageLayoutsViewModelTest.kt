package com.greenfodor.ppremotece.feature.stage

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.orEmpty
import com.greenfodor.ppremotece.core.domain.model.StageScreen
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CompletableDeferred
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
class StageLayoutsViewModelTest {
    private val repository = FakeStageRepository()
    private val thumbnails = FakeStageThumbnails()
    private val messages = UiMessages()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(screenUuid: String = "s-0") =
        StageLayoutsViewModel(screenUuid, repository, thumbnails, messages)

    @Test
    fun `the tiles are the layouts in order with the screen's layout live`() = runTest(dispatcher) {
        viewModel().state.test {
            val state = expectMostRecentItem()

            assertThat(state.screenName).isEqualTo("Stage Screen 01")
            assertThat(state.layouts.orEmpty().map { it.name }).containsExactly("Layout 01", "Layout 02", "Layout 03")
            assertThat(state.layouts.orEmpty().map { it.live }).containsExactly(true, false, false)
        }
    }

    @Test
    fun `a screen the map does not hold has no live tile`() = runTest(dispatcher) {
        viewModel("s-2").state.test {
            assertThat(expectMostRecentItem().layouts.orEmpty().map { it.live }).containsExactly(false, false, false)
        }
    }

    @Test
    fun `a tap sets the layout with both uuids and does not move the live tile`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-1"))

            assertThat(repository.sets).containsExactly("s-0" to "l-1")
            expectNoEvents()
            assertThat(viewModel.state.value.layouts.orEmpty().map { it.live }).containsExactly(true, false, false)
        }
    }

    @Test
    fun `a later layout map moves the live tile`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            expectMostRecentItem()
            viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-1"))
            repository.layoutMap.value = Loadable.Loaded(mapOf("s-0" to "l-1", "s-1" to "l-2"))

            assertThat(awaitItem().layouts.orEmpty().map { it.live }).containsExactly(false, true, false)
        }
    }

    @Test
    fun `a failed set shows its message with the layout's name and leaves the live tile`() = runTest(dispatcher) {
        repository.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        messages.messages.test {
            viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-2"))
            val message = awaitItem() as UiText.StringResource

            assertThat(message.id).isEqualTo(R.string.stage_error_set)
            assertThat(message.args).isEqualTo(listOf<Any>("Layout 03"))
        }
        viewModel.state.test {
            assertThat(expectMostRecentItem().layouts.orEmpty().map { it.live }).containsExactly(true, false, false)
        }
    }

    @Test
    fun `taps on a layout are ignored while its set is in flight`() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-1"))
        viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-1"))
        repository.gate.complete(Unit)
        viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-1"))

        assertThat(repository.sets).containsExactly("s-0" to "l-1", "s-0" to "l-1")
    }

    @Test
    fun `a tap on another layout is ignored while a set for the screen is in flight`() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-1"))
        viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-2"))
        repository.gate.complete(Unit)
        viewModel.onAction(StageLayoutsAction.OnLayoutClick("l-2"))

        assertThat(repository.sets).containsExactly("s-0" to "l-1", "s-0" to "l-2")
    }

    @Test
    fun `a tap on an unknown layout sends nothing`() = runTest(dispatcher) {
        viewModel().onAction(StageLayoutsAction.OnLayoutClick("nope"))

        assertThat(repository.sets).isEmpty()
    }

    @Test
    fun `a screen that leaves the loaded screens is reported as gone`() = runTest(dispatcher) {
        viewModel().state.test {
            assertThat(expectMostRecentItem().screenGone).isFalse()
            repository.screens.value = Loadable.Loaded(listOf(StageScreen("s-1", "Stage Screen 02")))

            assertThat(awaitItem().screenGone).isTrue()
        }
    }

    @Test
    fun `screens that are not loaded do not report the screen as gone`() = runTest(dispatcher) {
        repository.screens.value = Loadable.NotLoaded

        viewModel().state.test {
            val state = expectMostRecentItem()

            assertThat(state.screenGone).isFalse()
            assertThat(state.layouts).isEqualTo(Loadable.NotLoaded)
            assertThat(state.thumbnails).isNotNull()
        }
    }
}
