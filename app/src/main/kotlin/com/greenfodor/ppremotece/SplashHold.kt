package com.greenfodor.ppremotece

/** The shortest the splash is kept. */
internal const val SPLASH_MIN_MILLIS = 600L

/** The longest the splash is kept while start-up is unresolved. */
internal const val SPLASH_CAP_MILLIS = 2_000L

/**
 * Whether the splash stays on screen [elapsedMillis] after the activity was created: for its first
 * [SPLASH_MIN_MILLIS], and after that while start-up is unresolved and less than
 * [SPLASH_CAP_MILLIS] have passed.
 */
internal fun keepSplash(startupResolved: Boolean, elapsedMillis: Long): Boolean =
    elapsedMillis < SPLASH_MIN_MILLIS || (!startupResolved && elapsedMillis < SPLASH_CAP_MILLIS)
