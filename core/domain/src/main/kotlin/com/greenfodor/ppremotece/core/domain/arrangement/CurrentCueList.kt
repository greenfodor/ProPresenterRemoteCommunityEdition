package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Presentation

/** The cue list of [presentation]'s current arrangement, which ProPresenter plays outside a playlist. */
fun currentCueList(presentation: Presentation): CueList = checkNotNull(liveItemResolution(presentation, null)).cueList
