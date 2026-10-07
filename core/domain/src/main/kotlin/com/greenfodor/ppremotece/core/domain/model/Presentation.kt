package com.greenfodor.ppremotece.core.domain.model

/** A presentation; [currentArrangementUuid] is the arrangement it plays outside a playlist, empty when unknown. */
data class Presentation(
    val uuid: String,
    val name: String,
    val groups: List<Group>,
    val arrangements: List<Arrangement>,
    val currentArrangementUuid: String = ""
)

data class Group(
    val uuid: String,
    val name: String,
    val color: GroupColor?,
    val slides: List<Slide>
)

/** RGBA components in the 0..1 range. */
data class GroupColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
)

data class Slide(
    val text: String,
    val enabled: Boolean = true,
    val size: SlideSize? = null,
    val label: String = ""
)

/** A slide's size in pixels. */
data class SlideSize(
    val width: Int,
    val height: Int
)

/** An ordered list of group uuids; a group may appear more than once. */
data class Arrangement(
    val uuid: String,
    val name: String,
    val groupUuids: List<String>,
    val totalCues: Int
)

/**
 * One triggerable slide position of a playlist item, numbered from 0 in arrangement order.
 * [slideIndexInGroup] is the slide's position within its group, and [startsGroup] is true for the
 * first cue of each occurrence of a group.
 */
data class Cue(
    val index: Int,
    val groupUuid: String,
    val groupName: String,
    val groupColor: GroupColor?,
    val slideIndexInGroup: Int,
    val slideText: String,
    val enabled: Boolean,
    val size: SlideSize?,
    val slideLabel: String = "",
    val startsGroup: Boolean
)
