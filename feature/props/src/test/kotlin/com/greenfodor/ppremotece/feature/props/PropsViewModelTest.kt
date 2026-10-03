package com.greenfodor.ppremotece.feature.props

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.model.Prop
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequest
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailRequests
import com.greenfodor.ppremotece.core.domain.props.PropThumbnailSource
import com.greenfodor.ppremotece.core.domain.props.PropsRepository
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PropsViewModelTest {
    private val off = Prop("p-0", "Prop 01", 0, isActive = false, transitionName = null)
    private val on = Prop("p-1", "Prop 02", 1, isActive = true, transitionName = "Transition 01")
    private val first = PropCollection("c-0", "Collection 01", 0, listOf(off, on))
    private val second = PropCollection("c-1", "Collection 02", 1, listOf(Prop("p-2", "Prop 03", 0, false, null)))
    private val repository = object : PropsRepository {
        override val propCollections = MutableStateFlow<Loadable<List<PropCollection>>>(Loadable.Loaded(listOf(first)))
    }
    private val thumbnails = object : PropThumbnailSource {
        override val propThumbnailRequests = MutableStateFlow<PropThumbnailRequests?>(
            PropThumbnailRequests { uuid, px -> PropThumbnailRequest("http://host/$uuid?w=$px", "k:$uuid:$px") }
        )
    }
    private val client = FakePropClient()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PropsViewModel(repository, thumbnails, client)

    private val PropsState.loaded: List<PropSectionUi> get() = (sections as Loadable.Loaded).value

    @Test
    fun `a single collection is one section without a header, each prop with its active state`() =
        runTest(dispatcher) {
            viewModel().state.test {
                val state = expectMostRecentItem()
                assertThat(state.showHeaders).isFalse()
                assertThat(state.loaded.single().props.map { it.name to it.active })
                    .containsExactly("Prop 01" to false, "Prop 02" to true)
            }
        }

    @Test
    fun `each collection with props is a section with a header and empty collections are hidden`() =
        runTest(dispatcher) {
            repository.propCollections.value =
                Loadable.Loaded(listOf(first, PropCollection("c-9", "Collection 09", 2, emptyList()), second))

            viewModel().state.test {
                val state = expectMostRecentItem()
                assertThat(state.showHeaders).isTrue()
                assertThat(state.loaded.map { it.name }).containsExactly("Collection 01", "Collection 02")
            }
        }

    @Test
    fun `a tap shows an inactive prop and clears an active one`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(PropsAction.OnPropClick("p-0"))
        viewModel.onAction(PropsAction.OnPropClick("p-1"))

        assertThat(client.calls).containsExactly("trigger p-0", "clear p-1")
    }

    @Test
    fun `a tap follows the prop's state in the latest frame`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(PropsAction.OnPropClick("p-0"))
        repository.propCollections.value =
            Loadable.Loaded(listOf(first.copy(props = listOf(off.copy(isActive = true), on))))
        viewModel.onAction(PropsAction.OnPropClick("p-0"))

        assertThat(client.calls).containsExactly("trigger p-0", "clear p-0")
    }

    @Test
    fun `taps on a prop are ignored until its state changes after a successful tap`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(PropsAction.OnPropClick("p-0"))
        viewModel.onAction(PropsAction.OnPropClick("p-0"))

        assertThat(client.calls).containsExactly("trigger p-0")
    }

    @Test
    fun `a prop takes taps again when no frame follows a successful tap within two seconds`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PropsAction.OnPropClick("p-0"))
            advanceTimeBy(2_001)
            viewModel.onAction(PropsAction.OnPropClick("p-0"))

            assertThat(client.calls).containsExactly("trigger p-0", "trigger p-0")
        }

    @Test
    fun `collections and props that repeat a uuid are shown once`() = runTest(dispatcher) {
        repository.propCollections.value = Loadable.Loaded(listOf(first.copy(props = listOf(off, off, on)), first))

        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.loaded.map { it.uuid }).containsExactly("c-0")
            assertThat(state.loaded.single().props.map { it.uuid }).containsExactly("p-0", "p-1")
        }
    }

    @Test
    fun `taps on a prop are ignored while its request is in flight`() = runTest(dispatcher) {
        client.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(PropsAction.OnPropClick("p-0"))
        viewModel.onAction(PropsAction.OnPropClick("p-0"))
        viewModel.onAction(PropsAction.OnPropClick("p-1"))
        client.gate.complete(Unit)
        repository.propCollections.value =
            Loadable.Loaded(listOf(first.copy(props = listOf(off.copy(isActive = true), on.copy(isActive = false)))))
        viewModel.onAction(PropsAction.OnPropClick("p-0"))

        assertThat(client.calls).containsExactly("trigger p-0", "clear p-1", "clear p-0")
    }

    @Test
    fun `a failed show and a failed clear name the prop`() = runTest(dispatcher) {
        client.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(PropsAction.OnPropClick("p-0"))
            val show = (awaitItem() as PropsEvent.ShowError).message as UiText.StringResource
            viewModel.onAction(PropsAction.OnPropClick("p-1"))
            val clear = (awaitItem() as PropsEvent.ShowError).message as UiText.StringResource

            assertThat(show.id to show.args).isEqualTo(R.string.props_error_show to listOf<Any>("Prop 01"))
            assertThat(clear.id to clear.args).isEqualTo(R.string.props_error_clear to listOf<Any>("Prop 02"))
        }
    }

    @Test
    fun `props not loaded or unavailable pass through`() = runTest(dispatcher) {
        repository.propCollections.value = Loadable.NotLoaded

        viewModel().state.test {
            assertThat(expectMostRecentItem().sections).isEqualTo(Loadable.NotLoaded)
            repository.propCollections.value = Loadable.Unavailable
            assertThat(awaitItem().sections).isEqualTo(Loadable.Unavailable)
        }
    }

    @Test
    fun `each prop carries its thumbnail version and the host's thumbnail requests`() = runTest(dispatcher) {
        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.loaded.single().props.map { it.thumbnailVersion })
                .containsExactly("Prop 01\u0000", "Prop 02\u0000Transition 01")
            assertThat(state.thumbnails?.request("p-0", 350))
                .isEqualTo(PropThumbnailRequest("http://host/p-0?w=350", "k:p-0:350"))
        }
    }

    /** Records prop triggers and clears; every other call is not served. */
    private class FakePropClient : ProPresenterClient by notServed() {
        val calls = mutableListOf<String>()
        var gate = CompletableDeferred(Unit)
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun triggerProp(uuid: String): EmptyResult<DataError.Network> = record("trigger $uuid")

        override suspend fun clearProp(uuid: String): EmptyResult<DataError.Network> = record("clear $uuid")

        private suspend fun record(call: String): EmptyResult<DataError.Network> {
            calls += call
            gate.await()
            return result
        }
    }

    private companion object {
        fun notServed(): ProPresenterClient =
            Proxy.newProxyInstance(
                ProPresenterClient::class.java.classLoader,
                arrayOf(ProPresenterClient::class.java)
            ) {
                _,
                method,
                _
                ->
                throw UnsupportedOperationException(method.name)
            } as ProPresenterClient
    }
}
