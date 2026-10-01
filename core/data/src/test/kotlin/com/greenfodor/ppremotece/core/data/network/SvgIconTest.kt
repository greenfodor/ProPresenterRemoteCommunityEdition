package com.greenfodor.ppremotece.core.data.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.IconPath
import org.junit.jupiter.api.Test

class SvgIconTest {
    @Test
    fun `filled paths are read with the fill and fill rule they inherit`() {
        val svg = """<svg width="18px" height="18px" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg">""" +
            """<g stroke="none" fill="none" fill-rule="evenodd">""" +
            """<path d="M0,0 L1,1" fill="#FFFFFF" fill-rule="nonzero"/>""" +
            """<g fill="#FFFFFF"><path d="M2,2 L3,3"/></g>""" +
            """<path d="M4,4 L5,5"/>""" +
            """</g></svg>"""

        assertThat(parseSvgIcon(svg)).isEqualTo(
            ClearGroupIcon.Vector(
                viewportWidth = 18f,
                viewportHeight = 18f,
                paths = listOf(IconPath("M0,0 L1,1", evenOdd = false), IconPath("M2,2 L3,3", evenOdd = true))
            )
        )
    }

    @Test
    fun `the viewport falls back to the width and height`() {
        val svg = """<svg width="24" height="12" xmlns="http://www.w3.org/2000/svg"><path d="M0,0 L1,1"/></svg>"""

        assertThat(parseSvgIcon(svg)).isEqualTo(
            ClearGroupIcon.Vector(24f, 12f, listOf(IconPath("M0,0 L1,1", evenOdd = false)))
        )
    }

    @Test
    fun `malformed markup and document type declarations are not read`() {
        assertThat(parseSvgIcon("<svg><path d=")).isNull()
        assertThat(
            parseSvgIcon(
                """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY e "x">]>""" +
                    """<svg viewBox="0 0 1 1" xmlns="http://www.w3.org/2000/svg"><path d="M0,0"/></svg>"""
            )
        ).isNull()
    }
}
