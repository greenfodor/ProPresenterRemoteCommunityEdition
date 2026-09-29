package com.greenfodor.ppremotece.core.domain.remote

import com.greenfodor.ppremotece.core.domain.arrangement.ArrangementChoice
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Playlist
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemType
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.thumbnail.DEFAULT_SLIDE_ASPECT

/**
 * What the Remote tab is built from: the live state, the last live cue, the item cued with
 * ⏮/⏭ and the media item this app triggered.
 */
data class RemoteInputs(
    val live: LiveState,
    val lastLive: LiveCue?,
    val cued: PlaylistItemKey? = null,
    val mediaLive: PlaylistItemKey? = null
)

/** A request the Remote tab sends to ProPresenter. */
sealed interface RemoteCommand {
    data class TriggerCue(
        val item: PlaylistItemKey,
        val cueIndex: Int
    ) : RemoteCommand

    data class TriggerItem(
        val item: PlaylistItemKey
    ) : RemoteCommand

    data object TriggerNext : RemoteCommand

    data object TriggerPrevious : RemoteCommand
}

enum class BoxMark {
    NONE,
    LIVE,
    NEXT,
    CUED
}

/** The content of the current or the next box. */
sealed interface RemoteBox {
    /** A cue of [item]; [thumbnails] is false when the item's arrangement did not fully resolve. */
    data class Slide(
        val item: PlaylistItemKey,
        val presentationUuid: String,
        val cue: Cue,
        val mark: BoxMark,
        val thumbnails: Boolean
    ) : RemoteBox

    data class Text(
        val text: String
    ) : RemoteBox

    data class ItemCard(
        val name: String,
        val type: PlaylistItemType,
        val mark: BoxMark
    ) : RemoteBox

    data object Empty : RemoteBox
}

/** The shown item's name, its arrangement (null for no chip) and the 1-based cue number of the current box. */
data class RemoteHeader(
    val itemName: String,
    val arrangement: ArrangementChoice?,
    val cueNumber: Int?,
    val cueCount: Int?
)

/** The item ⏭ moves to, with its arrangement once its presentation is read. */
data class NextUp(
    val item: PlaylistItemKey,
    val name: String,
    val arrangement: ArrangementChoice?
)

/**
 * The cues of the shown item for the cue sidebar, with the mark of each marked cue; [thumbnails] is
 * false when the item's arrangement did not fully resolve.
 */
data class RemoteSidebar(
    val item: PlaylistItemKey,
    val presentationUuid: String,
    val cues: List<Cue>,
    val marks: Map<Int, BoxMark>,
    val thumbnails: Boolean
)

enum class RemoteStatus {
    NOTHING_LIVE,
    LOADING,
    SHOWING
}

/**
 * Everything the Remote tab shows and sends. Null commands and item targets are disabled.
 * [baseItem] is the live, remembered or app-triggered media item that the cued item replaces.
 */
data class RemoteDisplay(
    val status: RemoteStatus,
    val header: RemoteHeader? = null,
    val current: RemoteBox = RemoteBox.Empty,
    val next: RemoteBox = RemoteBox.Empty,
    val aspect: Float = DEFAULT_SLIDE_ASPECT,
    val cued: Boolean = false,
    val baseItem: PlaylistItemKey? = null,
    val previousItem: PlaylistItemKey? = null,
    val nextItem: PlaylistItemKey? = null,
    val nextUp: NextUp? = null,
    val endOfPlaylist: Boolean = false,
    val tapCurrent: RemoteCommand? = null,
    val tapNext: RemoteCommand? = null,
    val nextButton: RemoteCommand? = null,
    val previousButton: RemoteCommand? = null,
    val sidebar: RemoteSidebar? = null
) {
    companion object {
        /** The playlist [reduce] needs for [inputs], or null when it needs none. */
        fun playlistNeeded(inputs: RemoteInputs): String? = baseOf(inputs).item?.playlistUuid

        /** The presentations [reduce] needs for [inputs] once [playlist] is read. */
        fun presentationsNeeded(inputs: RemoteInputs, playlist: Playlist?): Set<String> {
            val focus = playlist?.let { focusOf(inputs, it) } ?: return emptySet()
            return listOfNotNull(focus, adjacentItem(playlist, focus, ItemDirection.NEXT))
                .mapNotNull { playlist.item(it)?.presentation?.presentationUuid }
                .toSet()
        }

        /**
         * What the Remote tab shows for [inputs], given the [playlist] and [presentations] read so far.
         *
         * The base is the media item this app triggered, else the live slide, else the last live
         * cue. A live slide without a playlist item, or of another presentation than its item's,
         * shows the `status/slide` text and steps with trigger next and previous. A cued item other
         * than the base item is shown in place of the base, from its first enabled cue.
         */
        fun reduce(inputs: RemoteInputs, playlist: Playlist?, presentations: Map<String, Presentation>): RemoteDisplay =
            when (val base = baseOf(inputs)) {
                Base.None -> RemoteDisplay(status = RemoteStatus.NOTHING_LIVE)
                Base.Text -> textDisplay(inputs.live.slideText)
                is Base.Cue, is Base.Media -> when {
                    playlist == null -> RemoteDisplay(status = RemoteStatus.LOADING)
                    !base.matches(playlist) -> textDisplay(inputs.live.slideText)
                    else -> itemDisplay(inputs, base, playlist, presentations)
                }
            }
    }
}
