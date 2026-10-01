package com.greenfodor.ppremotece.core.data.mapper

import com.greenfodor.ppremotece.core.data.dto.ArrangementDto
import com.greenfodor.ppremotece.core.data.dto.ColorDto
import com.greenfodor.ppremotece.core.data.dto.GroupDto
import com.greenfodor.ppremotece.core.data.dto.PresentationResponseDto
import com.greenfodor.ppremotece.core.data.dto.SlideDto
import com.greenfodor.ppremotece.core.domain.model.Arrangement
import com.greenfodor.ppremotece.core.domain.model.Group
import com.greenfodor.ppremotece.core.domain.model.GroupColor
import com.greenfodor.ppremotece.core.domain.model.Presentation
import com.greenfodor.ppremotece.core.domain.model.Slide
import com.greenfodor.ppremotece.core.domain.model.SlideSize

fun PresentationResponseDto.toDomain(): Presentation =
    Presentation(
        uuid = presentation.id.uuid,
        name = presentation.id.name,
        groups = presentation.groups.map { it.toDomain() },
        arrangements = presentation.arrangements.map { it.toDomain() },
        currentArrangementUuid = presentation.currentArrangement.orEmpty()
    )

private fun GroupDto.toDomain(): Group =
    Group(
        uuid = uuid,
        name = name,
        color = color?.toDomain(),
        slides = slides.map { it.toDomain() }
    )

private fun SlideDto.toDomain(): Slide =
    Slide(
        text = text,
        enabled = enabled,
        size = size?.let { SlideSize(width = it.width, height = it.height) },
        label = label
    )

internal fun ColorDto.toDomain(): GroupColor =
    GroupColor(red = red, green = green, blue = blue, alpha = alpha)

private fun ArrangementDto.toDomain(): Arrangement =
    Arrangement(uuid = id.uuid, name = id.name, groupUuids = groups, totalCues = totalCues)
