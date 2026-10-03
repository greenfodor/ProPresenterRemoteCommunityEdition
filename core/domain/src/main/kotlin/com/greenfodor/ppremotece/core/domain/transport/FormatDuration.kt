package com.greenfodor.ppremotece.core.domain.transport

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

/** [seconds] as `m:ss`, or `h:mm:ss` from one hour; a negative value reads as zero. */
fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = (total % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$rest" else "$minutes:$rest"
}
