package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef

/** The cue list of [presentation]'s current arrangement, which ProPresenter plays outside a playlist. */
fun currentCueList(presentation: Presentation): CueList =
    ArrangementExpander.expand(
        presentation,
        PresentationRef(presentation.uuid, arrangementUuid = presentation.currentArrangementUuid, arrangementName = "")
    )
