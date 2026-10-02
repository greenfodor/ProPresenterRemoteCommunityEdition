package com.greenfodor.ppremotece.feature.macros

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.designsystem.ui.UiText
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.macros.MacrosRepository
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.Macro
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.ServerIcon
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
class MacrosViewModelTest {
    private val red = GroupColor(red = 1f, green = 0f, blue = 0f, alpha = 1f)
    private val first = MacroCollection(
        "c-0",
        "Collection 01",
        0,
        listOf(Macro("m-0", "Macro 01", 0, red), Macro("m-1", "Macro 02", 1, color = null))
    )
    private val second = MacroCollection("c-1", "Collection 02", 1, listOf(Macro("m-2", "Macro 03", 0, color = null)))
    private val icon = ServerIcon.Vector(18f, 18f, listOf(IconPath("M0,0 L18,18", evenOdd = true)))
    private val repository = object : MacrosRepository {
        override val collections = MutableStateFlow(listOf(first))
    }
    private val client = FakeMacroClient()
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MacrosViewModel(repository, client)

    @Test
    fun `a single collection is one section without a header`() = runTest(dispatcher) {
        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.showHeaders).isFalse()
            assertThat(state.sections.single().macros.map { it.name }).containsExactly("Macro 01", "Macro 02")
            assertThat(state.sections.single().macros.map { it.color }).containsExactly(red, null)
        }
    }

    @Test
    fun `each collection is a section with a header when there are several`() = runTest(dispatcher) {
        repository.collections.value = listOf(first, second)

        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.showHeaders).isTrue()
            assertThat(state.sections.map { it.name }).containsExactly("Collection 01", "Collection 02")
        }
    }

    @Test
    fun `each macro's icon is read once and an unreadable icon is left out`() = runTest(dispatcher) {
        client.icons["m-0"] = Result.Success(icon)
        val viewModel = viewModel()

        viewModel.state.test {
            val macros = expectMostRecentItem().sections.single().macros
            assertThat(macros.map { it.icon }).containsExactly(icon, null)
            repository.collections.value = listOf(first, second)
            repository.collections.value = listOf(first)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.state.test { cancelAndIgnoreRemainingEvents() }

        assertThat(client.iconReads).containsExactly("m-0", "m-1", "m-2")
    }

    @Test
    fun `a successful trigger shows the check for one second`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            viewModel.onAction(MacrosAction.OnMacroClick("m-0"))
            assertThat(expectMostRecentItem().macro("m-0").confirmed).isTrue()
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().macro("m-0").confirmed).isFalse()
        }
        assertThat(client.triggers).containsExactly("m-0")
    }

    @Test
    fun `taps on a macro are ignored while its request is in flight`() = runTest(dispatcher) {
        client.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onAction(MacrosAction.OnMacroClick("m-0"))
        viewModel.onAction(MacrosAction.OnMacroClick("m-0"))
        viewModel.onAction(MacrosAction.OnMacroClick("m-1"))
        client.gate.complete(Unit)

        assertThat(client.triggers).containsExactly("m-0", "m-1")
    }

    @Test
    fun `taps on a macro are ignored while its check shows`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(MacrosAction.OnMacroClick("m-0"))
        advanceTimeBy(500)
        viewModel.onAction(MacrosAction.OnMacroClick("m-0"))
        advanceTimeBy(501)
        viewModel.onAction(MacrosAction.OnMacroClick("m-0"))

        assertThat(client.triggers).containsExactly("m-0", "m-0")
    }

    @Test
    fun `a failed trigger shows its message with the macro's name and no check`() = runTest(dispatcher) {
        client.result = Result.Failure(DataError.Network.TIMEOUT)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(MacrosAction.OnMacroClick("m-1"))
            val message = (awaitItem() as MacrosEvent.ShowError).message as UiText.StringResource

            assertThat(message.id).isEqualTo(R.string.macros_error_run)
            assertThat(message.args).isEqualTo(listOf<Any>("Macro 02"))
        }
        viewModel.state.test {
            assertThat(expectMostRecentItem().macro("m-1").confirmed).isFalse()
        }
        viewModel.onAction(MacrosAction.OnMacroClick("m-1"))
        assertThat(client.triggers).containsExactly("m-1", "m-1")
    }

    @Test
    fun `no collections leave no sections`() = runTest(dispatcher) {
        repository.collections.value = emptyList()

        viewModel().state.test {
            assertThat(expectMostRecentItem().sections.firstOrNull()).isNull()
        }
    }

    private fun MacrosState.macro(uuid: String) = sections.flatMap { it.macros }.first { it.uuid == uuid }

    /** Records macro triggers and icon reads; every other call is not served. */
    private class FakeMacroClient : ProPresenterClient by notServed() {
        val triggers = mutableListOf<String>()
        val iconReads = mutableListOf<String>()
        val icons = mutableMapOf<String, Result<ServerIcon, DataError.Network>>()
        var gate = CompletableDeferred(Unit)
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)

        override suspend fun triggerMacro(uuid: String): EmptyResult<DataError.Network> {
            triggers += uuid
            gate.await()
            return result
        }

        override suspend fun macroIcon(uuid: String): Result<ServerIcon, DataError.Network> {
            iconReads += uuid
            return icons[uuid] ?: Result.Failure(DataError.Network.SERIALIZATION)
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
