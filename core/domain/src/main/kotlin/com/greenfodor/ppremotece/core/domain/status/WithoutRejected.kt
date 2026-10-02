package com.greenfodor.ppremotece.core.domain.status

private val ErrorFrame = Regex("""^URL: (.+?)\. Error:""")

/** The url an error frame `URL: {url}. Error: …` names, or null for any other text. */
fun rejectedUrl(errorFrame: String): String? = ErrorFrame.find(errorFrame)?.groupValues?.get(1)

/** [urls] without the url [errorFrame] names ([rejectedUrl]); [urls] unchanged when it names none of them. */
fun withoutRejected(urls: List<String>, errorFrame: String): List<String> =
    rejectedUrl(errorFrame)?.let { url -> urls.filterNot { it == url } } ?: urls
