package com.greenfodor.ppremotece.feature.clear

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.model.ServerIcon
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
    fun `groups lists the clear all group when it is the only one`() = runTest(dispatcher) {
        val viewModel = openedWith(CLEAR_ALL)

        viewModel.state.test {
            assertThat(awaitItem().groups).containsExactly(CLEAR_ALL)
        }
    }

    @Test
    fun `groups lists every group when there are several`() = runTest(dispatcher) {
        val viewModel = openedWith(CLEAR_ALL, LYRICS)

        viewModel.state.test {
            assertThat(awaitItem().groups).containsExactly(CLEAR_ALL, LYRICS)
        }
    }

    @Test
    fun `one tap on any pill triggers its group`() = runTest(dispatcher) {
        val viewModel = openedWith(CLEAR_ALL, LYRICS)

        viewModel.onAction(ClearAction.OnGroupClick(CLEAR_ALL.uuid))
        viewModel.onAction(ClearAction.OnGroupClick(LYRICS.uuid))

        assertThat(client.triggeredGroups).containsExactly(CLEAR_ALL.uuid, LYRICS.uuid)
        assertThat(client.clearedLayers).isEmpty()
    }

    @Test
    fun `each group's icon is read once and missing icons are left out`() = runTest(dispatcher) {
        val icon = ServerIcon.Vector(18f, 18f, listOf(IconPath("M0,0 L1,1", evenOdd = false)))
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
    fun `a failed group clear is reported`() = runTest(dispatcher) {
        client.failingGroups += CLEAR_ALL.uuid
        val viewModel = openedWith(CLEAR_ALL)

        viewModel.events.test {
            viewModel.onAction(ClearAction.OnGroupClick(CLEAR_ALL.uuid))
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
