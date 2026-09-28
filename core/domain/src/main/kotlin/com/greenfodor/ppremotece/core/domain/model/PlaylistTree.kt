package com.greenfodor.ppremotece.core.domain.model

/** A node of the ProPresenter playlist library: a folder or a playlist. */
sealed interface PlaylistTreeNode {
    val uuid: String
    val name: String
}

data class PlaylistFolder(
    override val uuid: String,
    override val name: String,
    val children: List<PlaylistTreeNode>
) : PlaylistTreeNode

data class PlaylistLeaf(
    override val uuid: String,
    override val name: String
) : PlaylistTreeNode
