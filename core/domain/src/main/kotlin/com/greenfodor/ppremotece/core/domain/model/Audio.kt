package com.greenfodor.ppremotece.core.domain.model

/** A node of the audio bin's tree: a folder or a playlist. */
sealed interface AudioNode {
    val uuid: String
    val name: String
}

/** A folder of the audio bin with its [children] in ProPresenter's order. */
data class AudioFolder(
    override val uuid: String,
    override val name: String,
    val children: List<AudioNode>
) : AudioNode

/** A playlist of the audio bin, addressed by its [uuid]. */
data class AudioPlaylist(
    override val uuid: String,
    override val name: String
) : AudioNode

/** The playlists of a tree, in tree order. */
fun List<AudioNode>.playlists(): List<AudioPlaylist> =
    flatMap { node ->
        when (node) {
            is AudioPlaylist -> listOf(node)
            is AudioFolder -> node.children.playlists()
        }
    }

/** A row of the audio playlist picker, [depth] folders deep. */
sealed interface AudioPickerRow {
    val depth: Int

    /** A folder's name, shown above its content. */
    data class Heading(
        val uuid: String,
        val name: String,
        override val depth: Int
    ) : AudioPickerRow

    data class Playlist(
        val playlist: AudioPlaylist,
        override val depth: Int
    ) : AudioPickerRow
}

/**
 * The picker's rows for a tree, in tree order: a heading per folder followed by its content one
 * level deeper. A folder with no playlist anywhere beneath it gives no row.
 */
fun List<AudioNode>.pickerRows(depth: Int = 0): List<AudioPickerRow> =
    flatMap { node ->
        when (node) {
            is AudioPlaylist -> listOf(AudioPickerRow.Playlist(node, depth))
            is AudioFolder -> node.children.pickerRows(depth + 1).let { content ->
                if (content.isEmpty()) {
                    content
                } else {
                    listOf(AudioPickerRow.Heading(node.uuid, node.name, depth)) +
                        content
                }
            }
        }
    }

/** A track of an audio playlist, triggered by its [index] within that playlist. */
data class AudioTrack(
    val uuid: String,
    val name: String,
    val index: Int,
    val artist: String,
    val durationSeconds: Int
)

/** The audio playlist track ProPresenter plays from the audio bin, named by that playlist's own uuids. */
data class ActiveAudio(
    val playlistUuid: String,
    val trackUuid: String,
    val trackIndex: Int
)
