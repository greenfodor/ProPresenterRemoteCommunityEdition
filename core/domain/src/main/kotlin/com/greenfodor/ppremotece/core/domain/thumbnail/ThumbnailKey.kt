package com.greenfodor.ppremotece.core.domain.thumbnail

import com.greenfodor.ppremotece.core.domain.model.Cue
import java.security.MessageDigest

/**
 * Cache key of a cue's thumbnail:
 * `thumb:v1:{hostInstanceName}:{presentationUuid}:{groupUuid}:{slideIndexInGroup}:{sha1(text + "\u0000" + width + "x" + height)}`,
 * with an empty size part when the slide has no size; a box thumbnail's key appends `:q{quality}`.
 * Cues that repeat one group slide share a key.
 */
object ThumbnailKey {
    fun of(hostInstanceName: String, presentationUuid: String, cue: Cue, boxQuality: Int? = null): String {
        val size = cue.size?.let { "${it.width}x${it.height}" }.orEmpty()
        val digest = sha1("${cue.slideText}\u0000$size")
        val gridKey = "thumb:v1:$hostInstanceName:$presentationUuid:${cue.groupUuid}:${cue.slideIndexInGroup}:$digest"
        return boxQuality?.let { boxKey(gridKey, it) } ?: gridKey
    }

    /** The keys of every box thumbnail of the cue whose grid key is [gridKey]. */
    fun boxKeys(gridKey: String): List<String> = boxQualities().map { boxKey(gridKey, it) }

    /** The box keys of the same slide as [key] with a quality above [key]'s, smallest first; all box keys for a grid key. */
    fun largerKeys(key: String): List<String> {
        val match = BoxKeyPattern.matchEntire(key)
        val gridKey = match?.groupValues?.get(1) ?: key
        val quality = match?.groupValues?.get(2)?.toInt() ?: 0
        return boxQualities().filter { it > quality }.map { boxKey(gridKey, it) }
    }

    private fun boxKey(gridKey: String, quality: Int) = "$gridKey:q$quality"

    private val BoxKeyPattern = Regex("(.*):q(\\d+)")

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.encodeToByteArray()).joinToString("") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }

    private const val BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16
}
