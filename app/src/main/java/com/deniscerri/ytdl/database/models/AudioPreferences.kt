package com.deniscerri.ytdl.database.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioPreferences(
    var embedThumb: Boolean = true,
    var cropThumb: Boolean? = null,
    var splitByChapters: Boolean = false,
    var sponsorBlockFilters: ArrayList<String> = arrayListOf(),
    var bitrate: String = "",
    var embedLyrics: Boolean = true,
    /** Write TTML into the lyrics tag instead of LRC when [embedLyrics] is on. */
    var embedTtml: Boolean = true,
    var saveLrc: Boolean = false,
    /** lrclib track id picked by the user, 0 = match automatically */
    var lyricsId: Long = 0,
    /** milliseconds added to synced lyrics timestamps */
    var lyricsOffset: Long = 0,
    /** Path to pre-fetched or custom lyrics file (TTML/LRC) to embed */
    var customLyricsFile: String = ""
) : Parcelable
