package com.greenfodor.ppremotece.core.domain.audio

private val AudioExtensions = setOf(
    "mp3",
    "m4a",
    "m4b",
    "aac",
    "wav",
    "wave",
    "aif",
    "aiff",
    "aifc",
    "flac",
    "ogg",
    "oga",
    "opus",
    "wma",
    "caf"
)

/**
 * The name an audio track is shown with: [name] without one trailing `.<extension>` when the extension is, ignoring
 * case, a known audio extension and something comes before it; every other name unchanged.
 */
fun trackDisplayName(name: String): String {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name
    return if (name.substring(dot + 1).lowercase() in AudioExtensions) name.substring(0, dot) else name
}
