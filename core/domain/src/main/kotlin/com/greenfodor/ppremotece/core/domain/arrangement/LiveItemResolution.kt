package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef

/**
 * What ProPresenter plays for a live slide: the [presentation], the [arrangement] it plays in and
 * that arrangement's expanded [cueList]. [arrangementResolved] is false when [arrangement] names an
 * arrangement the presentation does not have.
 */
data class LiveItemResolution(
    val presentation: Presentation,
    val arrangement: PresentationRef,
    val cueList: CueList,
    val arrangementResolved: Boolean
)

/**
 * The live slide of [presentation] played through a playlist item with [itemRef], or, when
 * [itemRef] is null, outside a playlist in the presentation's current arrangement. Null when
 * [itemRef] is of another presentation.
 */
fun liveItemResolution(presentation: Presentation, itemRef: PresentationRef?): LiveItemResolution? {
    val ref = itemRef ?: PresentationRef(presentation.uuid, presentation.currentArrangementUuid, arrangementName = "")
    if (ref.presentationUuid != presentation.uuid) return null
    return LiveItemResolution(
        presentation = presentation,
        arrangement = ref,
        cueList = ArrangementExpander.expand(presentation, ref),
        arrangementResolved = ref.arrangementUuid.isEmpty() || ArrangementExpander.resolve(presentation, ref) != null
    )
}
