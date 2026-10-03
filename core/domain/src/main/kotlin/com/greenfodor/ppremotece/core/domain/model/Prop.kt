package com.greenfodor.ppremotece.core.domain.model

/** A prop collection configured in ProPresenter, with its [props] in order. */
data class PropCollection(
    val uuid: String,
    val name: String,
    val index: Int,
    val props: List<Prop>
)

/** A prop; [isActive] while it shows, [transitionName] when it has a transition. */
data class Prop(
    val uuid: String,
    val name: String,
    val index: Int,
    val isActive: Boolean,
    val transitionName: String?
)
