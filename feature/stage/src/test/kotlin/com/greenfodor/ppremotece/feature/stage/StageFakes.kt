package com.greenfodor.ppremotece.feature.stage

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.StageLayout
import com.greenfodor.ppremotece.core.domain.model.StageScreen
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.core.domain.result.EmptyResult
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailRequest
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailRequests
import com.greenfodor.ppremotece.core.domain.stage.StageLayoutThumbnailSource
import com.greenfodor.ppremotece.core.domain.stage.StageRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** A stage repository over three state flows that records each layout set. */
internal class FakeStageRepository : StageRepository {
    val stageScreens = listOf(
        StageScreen("s-0", "Stage Screen 01"),
        StageScreen("s-1", "Stage Screen 02"),
        StageScreen("s-2", "Stage Screen 03")
    )
    val stageLayouts =
        listOf(StageLayout("l-0", "Layout 01"), StageLayout("l-1", "Layout 02"), StageLayout("l-2", "Layout 03"))

    override val screens = MutableStateFlow<Loadable<List<StageScreen>>>(Loadable.Loaded(stageScreens))
    override val layouts = MutableStateFlow<Loadable<List<StageLayout>>>(Loadable.Loaded(stageLayouts))
    override val layoutMap =
        MutableStateFlow<Loadable<Map<String, String>>>(Loadable.Loaded(mapOf("s-0" to "l-0", "s-1" to "l-2")))

    val sets = mutableListOf<Pair<String, String>>()
    var gate = CompletableDeferred(Unit)
    var result: EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun setLayout(screenUuid: String, layoutUuid: String): EmptyResult<DataError.Network> {
        sets += screenUuid to layoutUuid
        gate.await()
        return result
    }
}

internal class FakeStageThumbnails : StageLayoutThumbnailSource {
    override val stageLayoutThumbnailRequests = MutableStateFlow<StageLayoutThumbnailRequests?>(
        StageLayoutThumbnailRequests {
            uuid,
            px
            ->
            StageLayoutThumbnailRequest("http://host/$uuid?w=$px", "k:$uuid:$px")
        }
    )
}
