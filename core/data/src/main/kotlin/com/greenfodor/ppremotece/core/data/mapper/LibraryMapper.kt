package com.greenfodor.ppremotece.core.data.mapper

import com.greenfodor.ppremotece.core.data.dto.IdDto
import com.greenfodor.ppremotece.core.data.dto.LibraryResponseDto
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry

fun IdDto.toLibrary(): Library = Library(uuid = uuid, name = name, index = index)

fun LibraryResponseDto.toDomain(): List<LibraryEntry> =
    items.map { LibraryEntry(uuid = it.uuid, name = it.name, index = it.index) }
