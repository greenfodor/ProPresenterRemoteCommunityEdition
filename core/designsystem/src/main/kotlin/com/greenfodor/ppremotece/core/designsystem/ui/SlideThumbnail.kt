package com.greenfodor.ppremotece.core.designsystem.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.greenfodor.ppremotece.core.designsystem.theme.PPRemoteTheme
import okio.IOException

private const val PREVIEW_ASPECT = 1920f / 858f
private const val PREVIEW_IMAGE_WIDTH = 711
private const val PREVIEW_IMAGE_HEIGHT = 317
private val PreviewImageColor = Color(0xFF2E5E8C)

/**
 * A slide image in a black box of the slide's [aspect], fitted without cropping and without a
 * crossfade. [url] is loaded and cached under [cacheKey] (memory and disk); while it loads, the
 * memory-cache entry under [placeholderKey] is shown when there is one. While it loads without such
 * an entry, when it fails and when [url] is null, [fallbackText] is shown instead.
 */
@Composable
fun SlideThumbnail(
    url: String?,
    cacheKey: String?,
    aspect: Float,
    fallbackText: String,
    modifier: Modifier = Modifier,
    placeholderKey: String? = null
) {
    val context = LocalPlatformContext.current
    val request = remember(context, url, cacheKey, placeholderKey) {
        thumbnailImageRequest(context, url, cacheKey, placeholderKey)
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsStateWithLifecycle()
    Box(modifier = modifier.aspectRatio(aspect).background(Color.Black)) {
        val showsPlaceholder = state is AsyncImagePainter.State.Loading && state.painter != null
        if (state !is AsyncImagePainter.State.Success && !showsPlaceholder) {
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp)
            )
        }
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** The request [SlideThumbnail] loads: [url] cached under [cacheKey] in memory and on disk, without a crossfade. */
internal fun thumbnailImageRequest(
    context: PlatformContext,
    url: String?,
    cacheKey: String?,
    placeholderKey: String? = null
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(url)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .placeholderMemoryCacheKey(placeholderKey)
        .crossfade(false)
        .build()

/** Serves every [SlideThumbnail] inside [content] a solid synthetic image, for previews. */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun SyntheticThumbnails(content: @Composable () -> Unit) {
    val handler = AsyncImagePreviewHandler {
        ColorImage(PreviewImageColor.toArgb(), PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT)
    }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides handler, content = content)
}

@OptIn(ExperimentalCoilApi::class)
private object FailingPreviewHandler : AsyncImagePreviewHandler {
    override suspend fun handle(imageLoader: ImageLoader, request: ImageRequest): AsyncImagePainter.State =
        AsyncImagePainter.State.Error(
            painter = null,
            result = ErrorResult(image = null, request = request, throwable = IOException())
        )
}

@Preview
@Composable
private fun SlideThumbnailTextPreview() {
    PPRemoteTheme {
        SlideThumbnail(
            url = null,
            cacheKey = null,
            aspect = PREVIEW_ASPECT,
            fallbackText = "Chorus · 1",
            modifier = Modifier.width(200.dp)
        )
    }
}

@Preview
@Composable
private fun SlideThumbnailImagePreview() {
    PPRemoteTheme {
        SyntheticThumbnails {
            SlideThumbnail(
                url = "http://192.0.2.14:60113/v1/playlist/p/0/thumbnail/0?quality=400",
                cacheKey = "thumb:v1:preview",
                aspect = PREVIEW_ASPECT,
                fallbackText = "Chorus · 1",
                modifier = Modifier.width(200.dp)
            )
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun SlideThumbnailErrorPreview() {
    PPRemoteTheme {
        CompositionLocalProvider(
            LocalAsyncImagePreviewHandler provides FailingPreviewHandler
        ) {
            SlideThumbnail(
                url = "http://192.0.2.14:60113/v1/playlist/p/0/thumbnail/99?quality=400",
                cacheKey = "thumb:v1:preview-error",
                aspect = PREVIEW_ASPECT,
                fallbackText = "Verse 1 · 2",
                modifier = Modifier.width(200.dp)
            )
        }
    }
}
