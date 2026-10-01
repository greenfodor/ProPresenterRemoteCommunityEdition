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
        val content = "${cue.slideText}\u0000$size"
        val quality = boxQuality?.let { ":q$it" }.orEmpty()
        return "thumb:v1:$hostInstanceName:$presentationUuid:${cue.groupUuid}:${cue.slideIndexInGroup}:${sha1(
            content
        )}$quality"
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.encodeToByteArray()).joinToString("") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }

    private const val BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16
}
