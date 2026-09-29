package com.greenfodor.ppremotece.feature.clear

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.ClearGroup
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
        client.groups = listOf(ClearGroup("g-1", "Clear Group 01"))
        val viewModel = viewModel()

        viewModel.state.test {
            assertThat(awaitItem().groups).isEmpty()
            assertThat(client.groupReads).isEqualTo(0)
            viewModel.onAction(ClearAction.OnSheetOpen)
            assertThat(awaitItem().groups).containsExactly(ClearGroup("g-1", "Clear Group 01"))
        }
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
    fun `clear all needs a second tap within three seconds`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            assertThat(client.clearedLayers).isEmpty()

            advanceTimeBy(2_900)
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isFalse()
            assertThat(client.clearedLayers).containsExactly(
                OutputLayer.SLIDE,
                OutputLayer.MEDIA,
                OutputLayer.VIDEO_INPUT,
                OutputLayer.PROPS,
                OutputLayer.MESSAGES,
                OutputLayer.ANNOUNCEMENTS,
                OutputLayer.AUDIO
            )
        }
    }

    @Test
    fun `clear all disarms after three seconds`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            advanceTimeBy(3_001)
            assertThat(awaitItem().armed).isFalse()

            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem().armed).isTrue()
            assertThat(client.clearedLayers).isEmpty()
        }
    }

    @Test
    fun `clear all reports how many layers failed`() = runTest(dispatcher) {
        client.failingLayers += setOf(OutputLayer.MEDIA, OutputLayer.AUDIO)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ClearAction.OnClearAllClick)
            viewModel.onAction(ClearAction.OnClearAllClick)
            assertThat(awaitItem()).isEqualTo(ClearEvent.LayersFailed(2))
        }
        assertThat(client.clearedLayers.size).isEqualTo(7)
    }
}
