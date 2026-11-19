package com.deniscerri.ytdl.ui.play

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.deniscerri.ytdl.playback.LikedStore
import com.deniscerri.ytdl.playback.PlaybackController
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.compose.play.audio.AudioPlayerScreen
import com.deniscerri.ytdl.ui.compose.theme.YtdlnisTheme
import com.deniscerri.ytdl.ui.play.lyrics.LyricsSheetFragment

/**
 * Audio Player Activity (Phase 6b).
 * Migrated to Jetpack Compose from activity_audio_player.xml.
 */
class AudioPlayerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackController.init(this)
        LikedStore.init(this)

        setContent {
            YtdlnisTheme {
                AudioPlayerScreen(
                    onBackClick = { supportFinishAfterTransition() },
                    onOpenLyrics = { openLyricsSheet() }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val sheet = supportFragmentManager.findFragmentByTag(LYRICS_SHEET_TAG) as? LyricsSheetFragment
                if (sheet != null && sheet.isVisible) {
                    sheet.dismiss()
                    return
                }
                supportFinishAfterTransition()
            }
        })
    }

    private fun openLyricsSheet() {
        val currentItem = PlaybackController.state.value.currentItem ?: return
        if (supportFragmentManager.findFragmentByTag(LYRICS_SHEET_TAG) != null) return
        LyricsSheetFragment().show(supportFragmentManager, LYRICS_SHEET_TAG)
    }

    companion object {
        private const val LYRICS_SHEET_TAG = "lyrics_sheet"
    }
}
