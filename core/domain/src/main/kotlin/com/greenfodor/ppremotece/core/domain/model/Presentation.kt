package com.greenfodor.ppremotece.core.domain.model

data class Presentation(
    val uuid: String,
    val name: String,
    val groups: List<Group>,
    val arrangements: List<Arrangement>
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
    val text: String
)

/** An ordered list of group uuids; a group may appear more than once. */
data class Arrangement(
    val uuid: String,
    val name: String,
    val groupUuids: List<String>,
    val totalCues: Int
)

/** One triggerable slide position of a playlist item, numbered from 0 in arrangement order. */
data class Cue(
    val index: Int,
    val groupName: String,
    val groupColor: GroupColor?,
    val slideText: String
)
