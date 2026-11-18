package com.deniscerri.ytdl.ui.compose.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.deniscerri.ytdl.R
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

/**
 * Image loader for Compose using Picasso (Phase 4b).
 *
 * Avoids introducing an external image loader dependency like Coil while the project is in
 * offline mode, loading bitmaps directly into a remember-backed ImageBitmap state.
 */
@Composable
fun PicassoImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    @DrawableRes placeholderRes: Int? = null,
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

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

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                // Keep previous or wait for load
            }
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

    val currentBitmap = bitmap
    if (currentBitmap != null) {
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
