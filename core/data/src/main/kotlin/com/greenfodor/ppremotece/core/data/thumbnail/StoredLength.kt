package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.disk.DiskCache
import okio.IOException

private const val METADATA_LINES_BEFORE_HEADERS = 4
private const val CONTENT_LENGTH = "content-length"

/**
 * Whether the body of [snapshot] is as long as the `content-length` header in the metadata Coil's
 * network fetcher stored with it; true when no length is stored or the metadata can't be read.
 */
fun DiskCache.holdsWholeBody(snapshot: DiskCache.Snapshot): Boolean {
    val expected = storedContentLength(snapshot) ?: return true
    return fileSystem.metadata(snapshot.data).size == expected
}

private fun DiskCache.storedContentLength(snapshot: DiskCache.Snapshot): Long? =
    try {
        fileSystem.read(snapshot.metadata) {
            generateSequence { readUtf8Line() }
                .drop(METADATA_LINES_BEFORE_HEADERS)
                .map { it.substringBefore(':') to it.substringAfter(':', "") }
                .firstOrNull { (name, _) -> name.trim().equals(CONTENT_LENGTH, ignoreCase = true) }
                ?.second
                ?.trim()
                ?.toLongOrNull()
        }
    } catch (_: IOException) {
        null
    }
