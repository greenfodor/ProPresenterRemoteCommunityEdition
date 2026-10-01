package com.greenfodor.ppremotece.feature.clear

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClearViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val client = FakeClearClient()
    private val live = FakeLiveStateRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ClearViewModel(client, live)

    @Test
    fun `a layer tap clears that layer`() = runTest(dispatcher) {
        viewModel().onAction(ClearAction.OnLayerClick(OutputLayer.MEDIA))

        assertThat(client.clearedLayers).containsExactly(OutputLayer.MEDIA)
    }

    @Test
    fun `a clear group tap triggers that group`() = runTest(dispatcher) {
        viewModel().onAction(ClearAction.OnGroupClick("g-1"))

        assertThat(client.triggeredGroups).containsExactly("g-1")
    }

    @Test
    fun `clear groups are read when the sheet opens`() = runTest(dispatcher) {
        client.groups = listOf(CLEAR_ALL, LYRICS)
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().groups).isEmpty()
            assertThat(client.groupReads).isEqualTo(0)
            viewModel.onAction(ClearAction.OnSheetOpen)
            assertThat(awaitItem().groups).containsExactly(CLEAR_ALL, LYRICS)
        }
    }

    @Test
    fun `only the clear all group gives the big button and no group pills`() = runTest(dispatcher) {
        client.groups = listOf(CLEAR_ALL)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnSheetOpen)
            val state = awaitItem()
            assertThat(state.clearAll).isEqualTo(CLEAR_ALL)
            assertThat(state.groups).isEmpty()
        }
    }

    @Test
    fun `with several groups clear all is one of the pills and there is no big button`() = runTest(dispatcher) {
        client.groups = listOf(CLEAR_ALL, LYRICS)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnSheetOpen)
            val state = awaitItem()
            assertThat(state.clearAll).isNull()
            assertThat(state.groups).containsExactly(CLEAR_ALL, LYRICS)
            viewModel.onAction(ClearAction.OnClearAllClick)
            expectNoEvents()
        }
        assertThat(client.triggeredGroups).isEmpty()
    }

    @Test
    fun `without a clear all group there is no big button`() = runTest(dispatcher) {
        client.groups = listOf(LYRICS)
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnSheetOpen)
            val state = awaitItem()
            assertThat(state.clearAll).isNull()
            assertThat(state.groups).containsExactly(LYRICS)
        }
    }

    @Test
    fun `each group's icon is read once and missing icons are left out`() = runTest(dispatcher) {
        val icon = ClearGroupIcon.Vector(18f, 18f, listOf(IconPath("M0,0 L1,1", evenOdd = false)))
        client.icons[LYRICS.uuid] = icon
        val viewModel = openedWith(CLEAR_ALL, LYRICS)

        viewModel.state.test {
            assertThat(awaitItem().icons).isEqualTo(mapOf(LYRICS.uuid to icon))
            viewModel.onAction(ClearAction.OnSheetOpen)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(client.iconReads.sorted()).containsExactly(CLEAR_ALL.uuid, CLEAR_ALL.uuid, LYRICS.uuid)
    }

    @Test
    fun `layer marks follow the live state`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().activeLayers).isEmpty()
            live.liveState.value = live.liveState.value.copy(layers = setOf(OutputLayer.SLIDE, OutputLayer.AUDIO))
            assertThat(awaitItem().activeLayers).isEqualTo(setOf(OutputLayer.SLIDE, OutputLayer.AUDIO))
        }
    }

    @Test
    fun `clear all needs a second tap within three seconds and triggers the clear all group`() = runTest(dispatcher) {
        val viewModel = openedWith(CLEAR_ALL)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            assertThat(client.triggeredGroups).isEmpty()

            advanceTimeBy(2_900)
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isFalse()
            assertThat(client.triggeredGroups).containsExactly(CLEAR_ALL.uuid)
            assertThat(client.clearedLayers).isEmpty()
        }
    }

    @Test
    fun `clear all disarms after three seconds`() = runTest(dispatcher) {
        val viewModel = openedWith(CLEAR_ALL)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            advanceTimeBy(3_001)
            assertThat(awaitItem().armed).isFalse()

            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            assertThat(client.triggeredGroups).isEmpty()
        }
    }

    @Test
    fun `closing the sheet disarms clear all`() = runTest(dispatcher) {
        val viewModel = openedWith(CLEAR_ALL)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            viewModel.onAction(ClearAction.OnSheetDismiss)
            assertThat(awaitItem().armed).isFalse()

            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            assertThat(client.triggeredGroups).isEmpty()
        }
    }

    @Test
    fun `a failed clear all is reported`() = runTest(dispatcher) {
        client.failingGroups += CLEAR_ALL.uuid
        val viewModel = openedWith(CLEAR_ALL)

        viewModel.events.test {
            viewModel.onAction(ClearAction.OnClearAllClick)
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem()).isInstanceOf(ClearEvent.ShowError::class)
        }
    }

    private fun openedWith(vararg groups: ClearGroup): ClearViewModel {
        client.groups = groups.toList()
        return viewModel().also { it.onAction(ClearAction.OnSheetOpen) }
    }

    private companion object {
        val CLEAR_ALL = ClearGroup("g-all", "Clear All")
        val LYRICS = ClearGroup("g-lyrics", "Clear Group 02")
    }
}
