package com.greenfodor.ppremotece.core.domain.thumbnail

import com.greenfodor.ppremotece.core.domain.model.Cue
import java.security.MessageDigest

/**
 * Cache key of a cue's thumbnail:
 * `thumb:v1:{hostInstanceName}:{presentationUuid}:{groupUuid}:{slideIndexInGroup}:{sha1(text + width + height)}`.
 * Cues that repeat one group slide share a key.
 */
object ThumbnailKey {
    fun of(hostInstanceName: String, presentationUuid: String, cue: Cue): String {
        val content = cue.slideText + (cue.size?.width?.toString().orEmpty()) + (cue.size?.height?.toString().orEmpty())
        return "thumb:v1:$hostInstanceName:$presentationUuid:${cue.groupUuid}:${cue.slideIndexInGroup}:${sha1(content)}"
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.encodeToByteArray()).joinToString("") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }

    private const val BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16
}
