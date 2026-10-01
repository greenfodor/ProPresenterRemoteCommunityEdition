package com.greenfodor.ppremotece.core.domain.model

/** A ProPresenter output layer, named as `status/layers` and `clear/layer/{layer}` name it. */
enum class OutputLayer(
    val apiName: String
) {
    SLIDE("slide"),
    MEDIA("media"),
    VIDEO_INPUT("video_input"),
    PROPS("props"),
    MESSAGES("messages"),
    ANNOUNCEMENTS("announcements"),
    AUDIO("audio");

    companion object {
        fun fromApiName(name: String): OutputLayer? = entries.firstOrNull { it.apiName == name }
    }
}

/** A clear group configured in ProPresenter, with the [tint] set for its icon. */
data class ClearGroup(
    val uuid: String,
    val name: String,
    val tint: GroupColor? = null
)

/** A clear group's icon: vector paths in a viewport, or the bytes of a PNG or JPEG image. */
sealed interface ClearGroupIcon {
    data class Vector(
        val viewportWidth: Float,
        val viewportHeight: Float,
        val paths: List<IconPath>
    ) : ClearGroupIcon

    class Image(
        val bytes: ByteArray
    ) : ClearGroupIcon
}

/** One filled path of a vector icon in SVG path syntax; [evenOdd] selects the even-odd fill rule. */
data class IconPath(
    val pathData: String,
    val evenOdd: Boolean
)
