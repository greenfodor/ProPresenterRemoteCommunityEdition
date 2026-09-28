package com.greenfodor.ppremotece.core.domain.arrangement

import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.Cue
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.PresentationRef

/**
 * Expands a playlist item's arrangement into its cue list.
 *
 * The arrangement is looked up in [Presentation.arrangements] by uuid, then by name when the uuid
 * is not found. An empty uuid, a missing arrangement, or an arrangement with no groups expands the
 * presentation's groups in their stored order.
 */
object ArrangementExpander {
    fun expand(presentation: Presentation, ref: PresentationRef): CueList {
        val arrangement = resolve(presentation, ref)
        return if (arrangement == null || arrangement.groupUuids.isEmpty()) {
            CueList(ArrangementChoice.SongOrder, cuesOf(presentation.groups))
        } else {
            val groupsByUuid = presentation.groups.associateBy { it.uuid }
            CueList(
                ArrangementChoice.Resolved(arrangement),
                cuesOf(arrangement.groupUuids.mapNotNull { groupsByUuid[it] })
            )
        }
    }

    private fun resolve(presentation: Presentation, ref: PresentationRef): Arrangement? {
        if (ref.arrangementUuid.isEmpty()) return null
        return presentation.arrangements.firstOrNull { it.uuid == ref.arrangementUuid }
            ?: ref.arrangementName.takeIf { it.isNotEmpty() }?.let { name ->
                presentation.arrangements.firstOrNull { it.name == name }
            }
    }

    private fun cuesOf(groups: List<Group>): List<Cue> =
        groups
            .flatMap { group -> group.slides.map { slide -> group to slide } }
            .mapIndexed { index, (group, slide) ->
                Cue(index = index, groupName = group.name, groupColor = group.color, slideText = slide.text)
            }
}
