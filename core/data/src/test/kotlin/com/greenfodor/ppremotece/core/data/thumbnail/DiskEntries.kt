package com.greenfodor.ppremotece.core.data.thumbnail

import coil3.disk.DiskCache

/**
 * Stores [body] under [key] with the metadata Coil's network fetcher writes: status 200 and a
 * `content-length` header of [contentLength], or no headers when it is null.
 */
fun DiskCache.store(key: String, body: ByteArray, contentLength: Long? = body.size.toLong()) {
    val editor = checkNotNull(openEditor(key))
    fileSystem.write(editor.metadata) {
        writeUtf8("200\n0\n0\n")
        if (contentLength == null) {
            writeUtf8("0\n")
        } else {
            writeUtf8("2\ncontent-length:$contentLength\ncontent-type:image/jpeg\n")
        }
    }
    fileSystem.write(editor.data) { write(body) }
    editor.commit()
}

/** The size of the body stored under [key], or null when none is stored. */
fun DiskCache.storedSize(key: String): Long? = openSnapshot(key)?.use { fileSystem.metadata(it.data).size }
