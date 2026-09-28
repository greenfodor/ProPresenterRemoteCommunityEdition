package com.greenfodor.ppremotece.core.data.mapper

import com.greenfodor.ppremotece.core.data.dto.SlideIndexResponseDto
import com.greenfodor.ppremotece.core.data.dto.VersionDto
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.ProPresenterVersion

fun VersionDto.toDomain(): ProPresenterVersion =
    ProPresenterVersion(name = name, hostDescription = hostDescription, apiVersion = apiVersion)

fun SlideIndexResponseDto.toDomain(): LiveSlide? =
    presentationIndex?.let {
        LiveSlide(presentationUuid = it.presentationId.uuid, index = it.index, totalCues = it.totalCues)
    }
