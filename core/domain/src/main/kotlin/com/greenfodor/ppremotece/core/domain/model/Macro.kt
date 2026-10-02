package com.greenfodor.ppremotece.core.domain.model

/** A macro collection configured in ProPresenter, with its [macros] in order. */
data class MacroCollection(
    val uuid: String,
    val name: String,
    val index: Int,
    val macros: List<Macro>
)

/** A macro with its tile [color], null when it has none, and its icon's [imageType] as ProPresenter names it. */
data class Macro(
    val uuid: String,
    val name: String,
    val index: Int,
    val color: GroupColor?,
    val imageType: String? = null
)
