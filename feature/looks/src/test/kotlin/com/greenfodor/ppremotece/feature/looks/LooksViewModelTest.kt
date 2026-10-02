package com.greenfodor.ppremotece.feature.looks

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.looks.LooksRepository
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class LooksViewModelTest {
    private val looks = listOf(Look("l-0", "Look 01", 0), Look("l-1", "Look 02", 1), Look("l-2", "Look 03", 2))
    private val repository = object : LooksRepository {
        override val looks = MutableStateFlow<Loadable<List<Look>>>(Loadable.Loaded(this@LooksViewModelTest.looks))
        override val currentLook = MutableStateFlow<Look?>(Look(LIVE_UUID, "Look 02", 1))
    }
    private val client = FakeLookClient()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LooksViewModel(repository, client)

    private val LooksState.loaded: List<LookUi> get() = (looks as Loadable.Loaded).value

    @Test
    fun `each look is listed with the live look selected`() = runTest(dispatcher) {
        viewModel().state.test {
            val looks = expectMostRecentItem().loaded
            assertThat(looks.map { it.name }).containsExactly("Look 01", "Look 02", "Look 03")
            assertThat(looks.map { it.live }).containsExactly(false, true, false)
        }
    }

    @Test
    fun `the selection follows the current look and clears without a match`() = runTest(dispatcher) {
        viewModel().state.test {
            expectMostRecentItem()
            repository.currentLook.value = Look(LIVE_UUID, "Look 01", 0)
            assertThat(awaitItem().loaded.map { it.live }).containsExactly(true, false, false)
            repository.currentLook.value = Look(LIVE_UUID, "Look 09", 0)
            assertThat(awaitItem().loaded.map { it.live }).containsExactly(false, false, false)
        }
    }

    @Test
    fun `a tap triggers the look and a tap on the live look triggers it again`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(LooksAction.OnLookClick("l-0"))
        viewModel.onAction(LooksAction.OnLookClick("l-1"))

        assertThat(client.triggers).containsExactly("l-0", "l-1")
    }

    @Test
    fun `taps on a look are ignored while its request is in flight`() = runTest(dispatcher) {
        client.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(LooksAction.OnLookClick("l-0"))
        viewModel.onAction(LooksAction.OnLookClick("l-0"))
        viewModel.onAction(LooksAction.OnLookClick("l-2"))
        client.gate.complete(Unit)
        viewModel.onAction(LooksAction.OnLookClick("l-0"))

        assertThat(client.triggers).containsExactly("l-0", "l-2", "l-0")
    }

    @Test
    fun `a failed trigger shows its message with the look's name`() = runTest(dispatcher) {
        client.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(LooksAction.OnLookClick("l-2"))
            val message = (awaitItem() as LooksEvent.ShowError).message as UiText.StringResource

            assertThat(message.id).isEqualTo(R.string.looks_error_switch)
            assertThat(message.args).isEqualTo(listOf<Any>("Look 03"))
        }
    }

    @Test
    fun `looks not loaded or unavailable pass through`() = runTest(dispatcher) {
        repository.looks.value = Loadable.NotLoaded

        viewModel().state.test {
            assertThat(expectMostRecentItem().looks).isEqualTo(Loadable.NotLoaded)
            repository.looks.value = Loadable.Unavailable
            assertThat(awaitItem().looks).isEqualTo(Loadable.Unavailable)
        }
    }

    /** Records look triggers; every other call is not served. */
    private class FakeLookClient : ProPresenterClient by notServed() {
        val triggers = mutableListOf<String>()
        var gate = CompletableDeferred(Unit)
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun triggerLook(uuid: String): EmptyResult<DataError.Network> {
            triggers += uuid
            gate.await()
            return result
        }
    }

    private companion object {
        const val LIVE_UUID = "live-look"

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
