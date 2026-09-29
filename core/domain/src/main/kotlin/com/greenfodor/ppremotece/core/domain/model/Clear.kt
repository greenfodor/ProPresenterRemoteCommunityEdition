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

/** A clear group configured in ProPresenter. */
data class ClearGroup(
    val uuid: String,
    val name: String
)
