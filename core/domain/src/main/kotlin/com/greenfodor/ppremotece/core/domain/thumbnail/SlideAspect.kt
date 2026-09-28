package com.greenfodor.ppremotece.core.domain.thumbnail

import com.greenfodor.ppremotece.core.domain.model.Presentation

private const val MIN_ASPECT = 4f / 3f
private const val MAX_ASPECT = 3f
private const val DEFAULT_ASPECT = 16f / 9f

/** Width / height of the presentation's first sized slide, clamped to 4:3 … 3:1; 16:9 when no slide has a size. */
fun slideAspect(presentation: Presentation): Float =
    presentation.groups
        .asSequence()
        .flatMap { it.slides }
        .mapNotNull { it.size?.takeIf { size -> size.width > 0 && size.height > 0 } }
        .firstOrNull()
        ?.let { (it.width.toFloat() / it.height).coerceIn(MIN_ASPECT, MAX_ASPECT) }
        ?: DEFAULT_ASPECT
