package com.greenfodor.ppremotece.core.data.network

import com.greenfodor.ppremotece.core.domain.model.ClearGroupIcon
import com.greenfodor.ppremotece.core.domain.model.IconPath
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

private val DECLARATION = Regex("<!(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
private val NUMBERS = Regex("[\\s,]+")
private const val VIEWBOX_PARTS = 4

/**
 * The filled `<path>` elements of an SVG icon with the `fill` and `fill-rule` they set or inherit,
 * in the `viewBox` (else `width` × `height`), or null when it cannot be read, has a document type
 * declaration or has no filled path.
 */
internal fun parseSvgIcon(svg: String): ClearGroupIcon.Vector? =
    svgRoot(svg)?.let { root ->
        viewportOf(root)?.let { (width, height) ->
            val paths = buildList { collectPaths(root, filled = true, evenOdd = false, into = this) }
            ClearGroupIcon.Vector(width, height, paths).takeIf { paths.isNotEmpty() }
        }
    }

private fun svgRoot(svg: String): Element? =
    try {
        svg.takeUnless { DECLARATION.containsMatchIn(it) }?.let {
            DocumentBuilderFactory.newInstance()
                .apply { isExpandEntityReferences = false }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(it)))
                .documentElement
                .takeIf { root -> root.tagName == "svg" }
        }
    } catch (_: SAXException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: ParserConfigurationException) {
        null
    }

private fun viewportOf(root: Element): Pair<Float, Float>? {
    val viewBox = root.getAttribute("viewBox").trim().split(NUMBERS).mapNotNull { it.toFloatOrNull() }
    val width = (viewBox.getOrNull(2) ?: root.getAttribute("width").removeSuffix("px").toFloatOrNull())
        ?.takeIf { it > 0f }
    val height = (
        viewBox.getOrNull(
            VIEWBOX_PARTS - 1
        ) ?: root.getAttribute("height").removeSuffix("px").toFloatOrNull()
    )
        ?.takeIf { it > 0f }
    return if (width != null && height != null) width to height else null
}

private fun collectPaths(element: Element, filled: Boolean, evenOdd: Boolean, into: MutableList<IconPath>) {
    val isFilled = element.getAttribute("fill").takeIf { it.isNotEmpty() }?.let { it != "none" } ?: filled
    val isEvenOdd = element.getAttribute("fill-rule").takeIf { it.isNotEmpty() }?.let { it == "evenodd" } ?: evenOdd
    val pathData = element.getAttribute("d")
    if (element.tagName == "path" && isFilled && pathData.isNotBlank()) into += IconPath(pathData, isEvenOdd)
    val children = element.childNodes
    for (i in 0 until children.length) {
        (children.item(i) as? Element)?.let { collectPaths(it, isFilled, isEvenOdd, into) }
    }
}
