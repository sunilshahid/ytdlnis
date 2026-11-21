package com.deniscerri.ytdl.ui.compose.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.deniscerri.ytdl.ui.play.PlaybackArtworkLoader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image and artwork loader for Compose.
 *
 * - Remote URLs (http/https) are loaded via Picasso.
 * - Local media files, content:// URIs, and audio/video files are loaded via PlaybackArtworkLoader
 *   with a 2-tier caching system: low-quality persistent WebP disk cache for fast list rendering,
 *   and high-resolution original decoding when fullQuality = true.
 */
@Composable
fun PicassoImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    @DrawableRes placeholderRes: Int? = null,
    fullQuality: Boolean = false,
) {
    val context = LocalContext.current
    var bitmap by remember(url, fullQuality) { mutableStateOf<Bitmap?>(null) }

    val isRemote = remember(url) {
        url?.startsWith("http://", ignoreCase = true) == true ||
        url?.startsWith("https://", ignoreCase = true) == true
    }

    if (isRemote) {
        DisposableEffect(url) {
            if (url.isNullOrBlank()) {
                bitmap = null
                return@DisposableEffect onDispose {}
            }

            val target = object : Target {
                override fun onBitmapLoaded(loadedBitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                    bitmap = loadedBitmap
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    bitmap = null
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            }

            try {
                Picasso.get().load(url).into(target)
            } catch (_: Exception) {
                bitmap = null
            }

            onDispose {
                try {
                    Picasso.get().cancelRequest(target)
                } catch (_: Exception) {
                }
            }
        }
    } else {
        LaunchedEffect(url, fullQuality) {
            if (url.isNullOrBlank()) {
                bitmap = null
                return@LaunchedEffect
            }
            bitmap = withContext(Dispatchers.IO) {
                PlaybackArtworkLoader.loadUriBitmap(context, url, fullQuality = fullQuality)
            }
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null && !currentBitmap.isRecycled) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else if (placeholderRes != null) {
        Image(
            painter = painterResource(placeholderRes),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier = modifier)
    }
}
