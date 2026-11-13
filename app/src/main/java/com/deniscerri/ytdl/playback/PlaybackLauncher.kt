package com.deniscerri.ytdl.playback

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.playback.model.PlaybackItem
import com.deniscerri.ytdl.playback.model.PlaybackType
import com.deniscerri.ytdl.ui.play.AudioPlayerActivity
import com.deniscerri.ytdl.ui.play.VideoPlayerActivity

/** Starts playback and opens the matching full-player surface. */
object PlaybackLauncher {

    fun play(context: Context, item: PlaybackItem, queue: List<PlaybackItem> = listOf(item)) {
        PlaybackController.init(context)
        val index = queue.indexOfFirst { it.mediaId == item.mediaId }.coerceAtLeast(0)
        PlaybackController.playNow(queue, index, autoPlay = true)
        openPlayer(context, item)
    }

    fun playHistory(context: Context, item: HistoryItem) {
        play(context, PlaybackItem.fromHistory(item))
    }

    fun playAll(context: Context, items: List<PlaybackItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        PlaybackController.init(context)
        PlaybackController.playNow(items, startIndex, autoPlay = true)
        openPlayer(context, items[startIndex.coerceIn(items.indices)])
    }

    fun openPlayer(context: Context, item: PlaybackItem) {
        val intent = if (item.type == PlaybackType.VIDEO) {
            Intent(context, VideoPlayerActivity::class.java)
        } else {
            Intent(context, AudioPlayerActivity::class.java)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openPlayer(context: Context) {
        val item = PlaybackController.state.value.currentItem
        if (item != null) {
            openPlayer(context, item)
        } else {
            val intent = Intent(context, AudioPlayerActivity::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
