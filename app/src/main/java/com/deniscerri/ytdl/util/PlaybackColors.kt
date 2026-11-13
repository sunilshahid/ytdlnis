package com.deniscerri.ytdl.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.ui.play.PlaybackArtworkLoader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared artwork-derived colors for the audio player and lyrics surfaces.
 *
 * The "Default (from artwork)" lyrics color and the artwork glow both come from the same
 * dominant-color extraction: downscale the artwork, cluster its pixels, and pick the most
 * common cluster with a preference for vivid, mid-brightness colors.
 */
object PlaybackColors {

    data class LyricsPalette(val base: Int, val active: Int)

    /** Reads the `lyrics_text_color` preference. Returns null for "default (from artwork)". */
    fun configuredColor(context: Context): Int? {
        val value = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString("lyrics_text_color", "default") ?: "default"
        if (value.isBlank() || value == "default") return null
        return runCatching { Color.parseColor(value) }.getOrNull()
    }

    /**
     * Resolves the lyrics palette for an item: the user's fixed color when set, otherwise the
     * artwork's dominant color clamped for readability on the dark sheet background.
     */
    suspend fun lyricsPalette(context: Context, item: PlaybackItem?): LyricsPalette {
        val configured = configuredColor(context)
        if (configured != null) {
            val active = clampForContrast(configured, minLuminance = 0.38f)
            return LyricsPalette(base = soften(active), active = active)
        }
        val accent = item?.let { dominantArtworkColor(context, it) }
            ?: Color.WHITE
        // Same vivid treatment the system media-notification tint applies to the artwork.
        val active = clampForContrast(vibrant(accent), minLuminance = 0.42f)
        return LyricsPalette(base = soften(active), active = active)
    }

    /** Dominant artwork color for the glow behind the artwork card. */
    suspend fun artworkGlowColor(context: Context, item: PlaybackItem?): Int? {
        item ?: return null
        return dominantArtworkColor(context, item)
            ?.let { clampForContrast(vibrant(it), minLuminance = 0.34f) }
    }

    /**
     * Matches the boldness of the system notification tint: raises saturation so pale artwork
     * still yields a clearly visible accent, and keeps value in a mid-to-bright band.
     */
    fun vibrant(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.35f + 0.06f).coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(0.5f, 1f)
        return Color.HSVToColor(hsv)
    }

    private suspend fun dominantArtworkColor(context: Context, item: PlaybackItem): Int? {
        val bitmap = PlaybackArtworkLoader.bitmapFor(context, item) ?: return null
        return extractDominant(bitmap)
    }

    /**
     * Quantizes the bitmap to a 4-bit-per-channel grid and scores clusters by how often they
     * appear weighted by vividness (saturation) and mid-brightness. No androidx.palette needed.
     */
    fun extractDominant(bitmap: Bitmap): Int? {
        if (bitmap.isRecycled) return null
        val size = 48
        val scaled = runCatching {
            if (bitmap.width <= size && bitmap.height <= size) bitmap
            else Bitmap.createScaledBitmap(bitmap, size, size, true)
        }.getOrNull() ?: return null

        val counts = HashMap<Int, Int>(256)
        val sums = HashMap<Int, LongArray>(256)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        for (pixel in pixels) {
            if (Color.alpha(pixel) < 32) continue
            val bucket = quantize(pixel)
            counts[bucket] = (counts[bucket] ?: 0) + 1
            val sum = sums.getOrPut(bucket) { LongArray(4) }
            sum[0] += Color.red(pixel)
            sum[1] += Color.green(pixel)
            sum[2] += Color.blue(pixel)
            sum[3] += 1
        }
        if (scaled !== bitmap) scaled.recycle()

        var bestBucket = -1
        var bestScore = 0f
        for ((bucket, count) in counts) {
            val sum = sums.getValue(bucket)
            val r = (sum[0] / sum[3]).toInt()
            val g = (sum[1] / sum[3]).toInt()
            val b = (sum[2] / sum[3]).toInt()
            val hsv = FloatArray(3)
            Color.RGBToHSV(r, g, b, hsv)
            val vividness = hsv[1]
            val midBrightness = 1f - abs(hsv[2] - 0.5f) * 1.6f
            val score = count * vividness * max(0.12f, midBrightness)
            if (score > bestScore) {
                bestScore = score
                bestBucket = bucket
            }
        }
        if (bestBucket < 0) return null
        val sum = sums.getValue(bestBucket)
        val total = sum[3].toInt().coerceAtLeast(1)
        return Color.rgb(
            (sum[0] / total).toInt(),
            (sum[1] / total).toInt(),
            (sum[2] / total).toInt()
        )
    }

    private fun quantize(pixel: Int): Int {
        val r = (Color.red(pixel) shr 4) and 0xF
        val g = (Color.green(pixel) shr 4) and 0xF
        val b = (Color.blue(pixel) shr 4) and 0xF
        return (r shl 8) or (g shl 4) or b
    }

    /** Blends the color toward white until its relative luminance reaches [minLuminance]. */
    fun clampForContrast(color: Int, minLuminance: Float): Int {
        var current = color
        var blend = 0f
        while (luminance(current) < minLuminance && blend < 1f) {
            blend += 0.08f
            current = blendTowardWhite(color, blend)
        }
        return current
    }

    /** Mutes the color toward the surface color so inactive text stays secondary. */
    fun soften(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1] * 0.45f
        hsv[2] = max(hsv[2], 0.72f) * 0.92f
        return Color.HSVToColor(hsv)
    }

    private fun blendTowardWhite(color: Int, fraction: Float): Int {
        fun channel(c: Int) = c + ((255 - c) * fraction).toInt()
        return Color.rgb(
            channel(Color.red(color)).coerceIn(0, 255),
            channel(Color.green(color)).coerceIn(0, 255),
            channel(Color.blue(color)).coerceIn(0, 255)
        )
    }

    /** sRGB relative luminance, cheap variant. */
    fun luminance(color: Int): Float {
        fun channel(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4)
                .toFloat()
        }
        val r = channel(Color.red(color))
        val g = channel(Color.green(color))
        val b = channel(Color.blue(color))
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
