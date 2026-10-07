package com.greenfodor.ppremotece.core.domain.live

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val FIRST_RETRY_DELAY = 2.seconds
private val MAX_RETRY_DELAY = 30.seconds
private const val DOUBLINGS_TO_MAX = 4

/**
 * How long a playlist's change connection waits before it is reopened for the [attempt]th time in a
 * row, counted from 0: 2, 4, 8, 16, then 30 s.
 */
fun changeConnectionRetryDelay(attempt: Int): Duration =
    if (attempt >= DOUBLINGS_TO_MAX) MAX_RETRY_DELAY else FIRST_RETRY_DELAY * (1 shl attempt.coerceAtLeast(0))
