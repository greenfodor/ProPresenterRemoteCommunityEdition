package com.greenfodor.ppremotece.core.data.network

import com.greenfodor.ppremotece.core.domain.model.IconPath
import com.greenfodor.ppremotece.core.domain.model.ServerIcon
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

private val DECLARATION = Regex("<!(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
private val STYLE_FILL = Regex("(?:^|;)\\s*fill\\s*:\\s*([^;]+)")
private val STYLE_FILL_RULE = Regex("(?:^|;)\\s*fill-rule\\s*:\\s*([^;]+)")
private val NOT_DRAWN = setOf("defs", "clipPath", "mask", "symbol", "pattern", "marker")
private val NUMBERS = Regex("[\\s,]+")
private const val VIEWBOX_PARTS = 4

/**
 * The filled `<path>` elements of an SVG icon with the `fill` and `fill-rule` they set (as
 * attributes or in `style`) or inherit, in the `viewBox` (else `width` × `height`). Paths inside
 * definitions, clip paths, masks, symbols, patterns and markers are not drawn. Null when the icon
 * cannot be read, has a document type declaration, a `transform`, a `viewBox` not at 0,0, or no
 * filled path.
 */
internal fun parseSvgIcon(svg: String): ServerIcon.Vector? =
    svgRoot(svg)?.let { root ->
        viewportOf(root)?.let { (width, height) ->
            val paths = buildList { collectPaths(root, filled = true, evenOdd = false, into = this) }
            ServerIcon.Vector(width, height, paths).takeIf { paths.isNotEmpty() }
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
                .takeIf { root -> root.tagName == "svg" && !root.hasTransform() }
        }
    } catch (_: SAXException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: ParserConfigurationException) {
        null
    }

private fun Element.hasTransform(): Boolean {
    val elements = getElementsByTagName("*")
    return hasAttribute("transform") ||
        (0 until elements.length).any { (elements.item(it) as Element).hasAttribute("transform") }
}

private fun viewportOf(root: Element): Pair<Float, Float>? {
    val viewBox = root.getAttribute("viewBox").trim().split(NUMBERS).mapNotNull { it.toFloatOrNull() }
    if (viewBox.take(2).any { it != 0f }) return null
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
    if (element.tagName in NOT_DRAWN) return
    val isFilled = element.paint("fill", STYLE_FILL)?.let { it != "none" } ?: filled
    val isEvenOdd = element.paint("fill-rule", STYLE_FILL_RULE)?.let { it == "evenodd" } ?: evenOdd
    val pathData = element.getAttribute("d")
    if (element.tagName == "path" && isFilled && pathData.isNotBlank()) into += IconPath(pathData, isEvenOdd)
    val children = element.childNodes
    for (i in 0 until children.length) {
        (children.item(i) as? Element)?.let { collectPaths(it, isFilled, isEvenOdd, into) }
    }
}

/** The value of [attribute] set in `style`, else as an attribute, else null. */
private fun Element.paint(attribute: String, inStyle: Regex): String? =
    inStyle.find(getAttribute("style"))?.groupValues?.get(1)?.trim()
        ?: getAttribute(attribute).takeIf { it.isNotEmpty() }
