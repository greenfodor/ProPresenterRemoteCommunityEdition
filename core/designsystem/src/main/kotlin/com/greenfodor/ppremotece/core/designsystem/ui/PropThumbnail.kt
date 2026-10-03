package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_ASPECT = 16f / 9f

/** The [version] each prop thumbnail key was last loaded with. */
internal class PropThumbnailVersions {
    private val loaded = ConcurrentHashMap<String, String>()

    /** Whether [key] was loaded with another version than [version]; false before its first load. */
    fun needsRefresh(key: String, version: String): Boolean = loaded[key]?.let { it != version } ?: false

    fun loaded(key: String, version: String) {
        loaded[key] = version
    }
}

private val Versions = PropThumbnailVersions()

@Volatile
private var lastAspect: Float? = null

/**
 * A prop's image over black, in a box of the image's own aspect and never cropped; until it is
 * loaded the box has the aspect of the prop image loaded last (16:9 before any). [url] is loaded
 * into the memory cache under [cacheKey] and never stored on disk. It is read again when
 * [cacheKey] was last loaded with another [version]; nothing but black shows while it loads, when
 * it fails and when [url] is null.
 */
@Composable
fun PropThumbnail(url: String?, cacheKey: String?, version: String, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(context, url, cacheKey, version) {
        val refresh = cacheKey != null && Versions.needsRefresh(cacheKey, version)
        propThumbnailImageRequest(context, url, cacheKey, refresh)
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsStateWithLifecycle()
    val size = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
    val aspect = size?.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }?.let { it.width / it.height }
    if (aspect != null && cacheKey != null) {
        SideEffect {
            Versions.loaded(cacheKey, version)
            lastAspect = aspect
        }
    }
    Box(modifier = modifier.aspectRatio(aspect ?: lastAspect ?: DEFAULT_ASPECT).background(Color.Black)) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The request [PropThumbnail] loads: [url] kept in memory under [cacheKey], with the disk cache
 * off; with [refresh] the memory entry is not read, only written.
 */
internal fun propThumbnailImageRequest(
    context: PlatformContext,
    url: String?,
    cacheKey: String?,
    refresh: Boolean
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(url)
        .memoryCacheKey(cacheKey)
        .memoryCachePolicy(if (refresh) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(false)
        .build()

@Preview
@Composable
private fun PropThumbnailPreview() {
    PPRemoteTheme {
        SyntheticThumbnails {
            PropThumbnail(
                url = "http://192.0.2.14:60113/v1/prop/p-0/thumbnail?quality=400",
                cacheKey = "prop:preview:p-0:w400",
                version = "Prop 01",
                modifier = Modifier.width(200.dp)
            )
        }
    }
}
