package com.greenfodor.ppremotece.core.domain.live

import com.greenfodor.ppremotece.core.domain.model.OutputLayer
import com.greenfodor.ppremotece.core.domain.result.Result

/** Clears every output layer, in [OutputLayer] order, even after a failure, and returns how many failed. */
suspend fun ProPresenterClient.clearAll(): Int = OutputLayer.entries.count { clearLayer(it) is Result.Failure }
