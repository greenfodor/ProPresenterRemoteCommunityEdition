package com.greenfodor.ppremotece.core.data.mapper

import com.greenfodor.ppremotece.core.data.dto.ClearGroupDto
import com.greenfodor.ppremotece.core.domain.model.ClearGroup

fun ClearGroupDto.toDomain(): ClearGroup = ClearGroup(uuid = id.uuid, name = id.name, tint = tint?.toDomain())
