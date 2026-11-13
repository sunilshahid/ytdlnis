package com.deniscerri.ytdl.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.play.AudioPlayerActivity
import com.deniscerri.ytdl.ui.play.VideoPlayerActivity
import com.deniscerri.ytdl.util.VideoPlayerUtil
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the single ExoPlayer + MediaSession for the whole app. Every surface (miniplayer, full
 * audio player, video activity, notification, Bluetooth) attaches to this one session.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    /** True once a dav1d failure forced us onto MediaCodec-only renderers. */
    private var extensionsDisabled = false

    /** Shared by every player build so cached connections and headers stay identical. */
    private lateinit var dataSourceFactory: DefaultDataSource.Factory

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(AppMediaNotificationProvider(this))

        dataSourceFactory = DefaultDataSource.Factory(this)
        // MediaSession needs its own bitmap loader to resolve MediaMetadata.artworkUri for the
        // notification and lock screen. Without this the artwork URI is ignored.
        val artworkBitmapLoader = DataSourceBitmapLoader.Builder(this)
            .setDataSourceFactory(dataSourceFactory)
            .build()
        val exoPlayer = createPlayer(useExtensions = !extensionsDisabled)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AudioPlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopCommand = SessionCommand(ACTION_STOP_PLAYER, Bundle.EMPTY)
        val stopButton = CommandButton.Builder()
            .setSessionCommand(stopCommand)
            .setIconResId(R.drawable.baseline_close_24)
            .setDisplayName(getString(R.string.close))
            .build()

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(SessionCallback())
            .setCustomLayout(ImmutableList.of(stopButton))
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(artworkBitmapLoader)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // The vendored dav1d software decoder fails on some devices ("Playback error
                // Libdav1dVideoRenderer"). Rebuild the player once with extension renderers
                // off so MediaCodec (hardware or its own software fallback) takes over, then
                // resume the exact queue/position/playback state.
                if (extensionsDisabled) return
                val isDav1dRendererError = error is ExoPlaybackException &&
                        error.type == ExoPlaybackException.TYPE_RENDERER &&
                        error.rendererName?.contains("dav1d", ignoreCase = true) == true
                if (isDav1dRendererError) {
                    Log.w(TAG, "dav1d renderer failed — rebuilding player without extensions")
                    rebuildPlayerWithoutExtensions()
                }
            }
        })
    }

    private fun createPlayer(useExtensions: Boolean): ExoPlayer =
        VideoPlayerUtil.buildPlayer(
            this,
            PlaybackMediaSourceFactory(this, dataSourceFactory),
            extensionsEnabled = useExtensions
        ).apply {
            setWakeMode(C.WAKE_MODE_NETWORK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateSessionActivity(mediaItem)
                }
            })
        }

    /**
     * Swaps in a player built without the compiled-in extension renderers, carrying over the
     * full playback state so the user sees a seamless retry instead of a dead error screen.
     */
    private fun rebuildPlayerWithoutExtensions() {
        val old = player ?: return
        val oldSession = mediaSession ?: return
        extensionsDisabled = true
        val newItemIndex = old.currentMediaItemIndex
        val positionMs = old.currentPosition
        val playWhenReady = old.playWhenReady
        val repeatMode = old.repeatMode
        val shuffle = old.shuffleModeEnabled
        val mediaItems = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        try {
            val newPlayer = createPlayer(useExtensions = false)
            newPlayer.repeatMode = repeatMode
            newPlayer.shuffleModeEnabled = shuffle
            if (mediaItems.isNotEmpty()) {
                newPlayer.setMediaItems(mediaItems, newItemIndex, positionMs)
            }
            newPlayer.playWhenReady = playWhenReady
            oldSession.player = newPlayer
            player = newPlayer
            newPlayer.prepare()
            old.release()
            Log.i(TAG, "Player rebuilt without extension renderers; playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Player rebuild failed", e)
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(ACTION_STOP_PLAYER, Bundle.EMPTY))
                .build()
            val allCommands = Player.Commands.Builder().addAllCommands().build()
            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                .addAll(allCommands)
                .build()
            return MediaSession.ConnectionResult.accept(
                availableSessionCommands,
                availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_STOP_PLAYER) {
                // This is the notification's close action — the only true "dismissed" stop.
                PlaybackController.stop(dismissed = true)
                session.player.stop()
                session.player.clearMediaItems()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            if (playerCommand == Player.COMMAND_SEEK_TO_NEXT) {
                if (!session.player.hasNextMediaItem()) {
                    PlaybackController.next()
                    return SessionResult.RESULT_SUCCESS
                }
            } else if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS) {
                if (!session.player.hasPreviousMediaItem()) {
                    PlaybackController.previous()
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    private class AppMediaNotificationProvider(
        private val context: Context
    ) : DefaultMediaNotificationProvider(context) {

        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showWhenCompact: Boolean
        ): ImmutableList<CommandButton> {
            val list = ImmutableList.builder<CommandButton>()

            // 1. Previous
            list.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .setIconResId(androidx.media3.session.R.drawable.media3_icon_previous)
                    .setDisplayName(context.getString(R.string.previous))
                    .build()
            )

            // 2. Play / Pause
            val isPlaying = session.player.isPlaying
            val playPauseIcon = if (isPlaying) {
                androidx.media3.session.R.drawable.media3_icon_pause
            } else {
                androidx.media3.session.R.drawable.media3_icon_play
            }
            list.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setIconResId(playPauseIcon)
                    .setDisplayName(context.getString(if (isPlaying) R.string.pause else R.string.play))
                    .build()
            )

            // 3. Next
            list.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    .setIconResId(androidx.media3.session.R.drawable.media3_icon_next)
                    .setDisplayName(context.getString(R.string.next))
                    .build()
            )

            // 4. Close (✕)
            list.add(
                CommandButton.Builder()
                    .setSessionCommand(SessionCommand(ACTION_STOP_PLAYER, Bundle.EMPTY))
                    .setIconResId(R.drawable.baseline_close_24)
                    .setDisplayName(context.getString(R.string.close))
                    .build()
            )

            return list.build()
        }

        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            builder: NotificationCompat.Builder,
            actionFactory: MediaNotification.ActionFactory
        ): IntArray {
            super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
            return intArrayOf(0, 1, 2)
        }
    }

    /** Opens the matching full player (audio or video) from the notification/lockscreen. */
    private fun updateSessionActivity(mediaItem: MediaItem?) {
        val type = mediaItem?.let { PlaybackItem.fromMediaItem(it)?.type }
        val target = if (type == PlaybackType.VIDEO) {
            VideoPlayerActivity::class.java
        } else {
            AudioPlayerActivity::class.java
        }
        val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession?.setSessionActivity(pending)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val exoPlayer = player
        if (exoPlayer == null || !exoPlayer.playWhenReady || exoPlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_PLAYER = "com.deniscerri.ytdl.playback.ACTION_STOP_PLAYER"
        private const val TAG = "PlaybackService"
    }
}
