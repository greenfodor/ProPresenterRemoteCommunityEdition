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
 * presentation's groups in their stored order. Group uuids that match no group are skipped, and
 * [CueList.countMismatch] is set when the expanded count differs from [Arrangement.totalCues].
 * Disabled slides keep their cue index.
 */
object ArrangementExpander {
    fun expand(presentation: Presentation, ref: PresentationRef): CueList {
        val arrangement = resolve(presentation, ref)
        return if (arrangement == null || arrangement.groupUuids.isEmpty()) {
            CueList(ArrangementChoice.SongOrder, cuesOf(presentation.groups))
        } else {
            val groupsByUuid = presentation.groups.associateBy { it.uuid }
            val cues = cuesOf(arrangement.groupUuids.mapNotNull { groupsByUuid[it] })
            CueList(
                ArrangementChoice.Resolved(arrangement),
                cues,
                countMismatch = cues.size != arrangement.totalCues
            )
        }
    }

    internal fun resolve(presentation: Presentation, ref: PresentationRef): Arrangement? {
        if (ref.arrangementUuid.isEmpty()) return null
        return presentation.arrangements.firstOrNull { it.uuid == ref.arrangementUuid }
            ?: ref.arrangementName.takeIf { it.isNotEmpty() }?.let { name ->
                presentation.arrangements.firstOrNull { it.name == name }
            }
    }

    private fun cuesOf(groups: List<Group>): List<Cue> =
        groups
            .flatMap { group -> group.slides.mapIndexed { slideIndex, slide -> Triple(group, slideIndex, slide) } }
            .mapIndexed { index, (group, slideIndex, slide) ->
                Cue(
                    index = index,
                    groupUuid = group.uuid,
                    groupName = group.name,
                    groupColor = group.color,
                    slideIndexInGroup = slideIndex,
                    slideText = slide.text,
                    enabled = slide.enabled,
                    size = slide.size,
                    slideLabel = slide.label
                )
            }
}
