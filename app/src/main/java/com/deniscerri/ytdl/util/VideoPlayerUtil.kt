package com.deniscerri.ytdl.util

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlin.math.max

object VideoPlayerUtil {
    private const val MINIMUM_BUFFER_DURATION = 1000 * 5 //exo default is 50s

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun buildPlayer(
        context: Context,
        mediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory? = null,
        extensionsEnabled: Boolean = true
    ) : ExoPlayer {
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            // cache the last three minutes
            .setBackBuffer(1000 * 60 * 3, true)
            .setBufferDurationsMs(
                MINIMUM_BUFFER_DURATION,
                max(50 * 1000, MINIMUM_BUFFER_DURATION),
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            // Only enable the compiled-in extension renderers (dav1d AV1 software
            // decoder) when this device has no hardware AV1 decoder. Hardware AV1
            // stays on MediaCodec; dav1d is purely the AV1 fallback.
            .setExtensionRendererMode(
                if (hasHardwareAv1Decoder() || !extensionsEnabled) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )

        return ExoPlayer.Builder(context, renderersFactory)
            .setUsePlatformDiagnostics(false)
            .setMediaSourceFactory(mediaSourceFactory ?: DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(DefaultTrackSelector(context))
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false)
            .build()
            .apply {
                skipSilenceEnabled = false
                playbackParameters = PlaybackParameters(1f, 1.0f)
            }
    }

    /**
     * Returns true when the device exposes a hardware-accelerated AV1 decoder.
     * Software codecs (e.g. c2.android.av1.decoder / OMX.google.*) do not count,
     * so dav1d is still used as the AV1 path on such devices.
     */
    private fun hasHardwareAv1Decoder(): Boolean {
        return try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
                if (info.isEncoder) return@any false
                val supportsAv1 = info.supportedTypes.any {
                    it.equals(MimeTypes.VIDEO_AV1, ignoreCase = true)
                }
                if (!supportsAv1) return@any false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    // Pre-Q has no isHardwareAccelerated(); fall back to the codec
                    // naming convention used by AOSP software decoders.
                    val name = info.name.lowercase()
                    !name.contains("software") && !name.contains("google") &&
                        !name.contains("sw.")
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}
