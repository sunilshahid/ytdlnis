package com.deniscerri.ytdl.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Replaces default preference values previously loaded from XML via [PreferenceManager.setDefaultValues].
 */
object DefaultPreferences {

    fun apply(context: Context, force: Boolean = false) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        apply(prefs, force)
    }

    fun apply(prefs: SharedPreferences, force: Boolean = false) {
        val editor = prefs.edit()
        applyRoot(prefs, editor, force)
        applyAppearance(prefs, editor, force)
        applyGeneral(prefs, editor, force)
        applyPlayer(prefs, editor, force)
        applyFolders(prefs, editor, force)
        applyTemporaryFiles(prefs, editor, force)
        applyDownloading(prefs, editor, force)
        applyProcessing(prefs, editor, force)
        applyUpdating(prefs, editor, force)
        applyYtdlpUpdate(prefs, editor, force)
        applyAppUpdate(prefs, editor, force)
        applyAdvanced(prefs, editor, force)
        editor.apply()
    }

    fun applyRoot(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("automatic_backup")) editor.putBoolean("automatic_backup", true)
    }

    fun resetRoot(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyRoot(prefs, editor, force = true)
        editor.apply()
    }

    fun applyAppearance(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("ytdlnis_theme")) editor.putString("ytdlnis_theme", "System")
        if (force || !prefs.contains("ytdlnis_icon")) editor.putString("ytdlnis_icon", "default")
        if (force || !prefs.contains("theme_accent")) editor.putString("theme_accent", "blue")
        if (force || !prefs.contains("high_contrast")) editor.putBoolean("high_contrast", false)
        if (force || !prefs.contains("modify_download_card")) editor.putStringSet("modify_download_card", setOf("schedule", "title", "author", "container", "adjust_audio", "adjust_video", "adjust_templates", "url"))
        if (force || !prefs.contains("display_over_apps")) editor.putBoolean("display_over_apps", false)
    }

    fun resetAppearance(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyAppearance(prefs, editor, force = true)
        editor.apply()
    }

    fun applyGeneral(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("history_quick_action")) editor.putString("history_quick_action", "share")
        if (force || !prefs.contains("locale")) editor.putString("locale", "")
        if (force || !prefs.contains("start_destination")) editor.putString("start_destination", "Home")
        if (force || !prefs.contains("navigation_bar")) editor.putString("navigation_bar", "Home,History,Queue,More")
        if (force || !prefs.contains("label_visibility")) editor.putString("label_visibility", "always")
        if (force || !prefs.contains("search_engine")) editor.putString("search_engine", "ytsearch")
        if (force || !prefs.contains("check_clipboard_home")) editor.putBoolean("check_clipboard_home", true)
        if (force || !prefs.contains("search_suggestions")) editor.putBoolean("search_suggestions", false)
        if (force || !prefs.contains("swipe_gesture")) editor.putStringSet("swipe_gesture", setOf("history", "queued", "cancelled", "scheduled", "errored", "saved", "templates", "cookies", "multipledownloadcard"))
        if (force || !prefs.contains("swipe_gestures_download_card")) editor.putBoolean("swipe_gestures_download_card", true)
        if (force || !prefs.contains("show_terminal")) editor.putBoolean("show_terminal", false)
        if (force || !prefs.contains("show_quick_download_share")) editor.putBoolean("show_quick_download_share", false)
        if (force || !prefs.contains("show_count_downloads")) editor.putBoolean("show_count_downloads", true)
        if (force || !prefs.contains("write_ytdlp_terminal")) editor.putBoolean("write_ytdlp_terminal", true)
        if (force || !prefs.contains("recommendations_home")) editor.putString("recommendations_home", "")
        if (force || !prefs.contains("youtube_data_fetching_extractor")) editor.putString("youtube_data_fetching_extractor", "YT_DLP")
        if (force || !prefs.contains("formats_source")) editor.putString("formats_source", "yt-dlp")
        if (force || !prefs.contains("use_itemurl_instead_playlisturl")) editor.putBoolean("use_itemurl_instead_playlisturl", false)
    }

    fun resetGeneral(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyGeneral(prefs, editor, force = true)
        editor.apply()
    }

    fun applyPlayer(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("scrub_preview_mini")) editor.putBoolean("scrub_preview_mini", false)
        if (force || !prefs.contains("show_device_files")) editor.putBoolean("show_device_files", false)
        if (force || !prefs.contains("lyrics_text_color")) editor.putString("lyrics_text_color", "default")
        if (force || !prefs.contains("lyrics_prefer_word_sync")) editor.putBoolean("lyrics_prefer_word_sync", true)
        if (force || !prefs.contains("lyrics_provider_betterlyrics")) editor.putBoolean("lyrics_provider_betterlyrics", true)
        if (force || !prefs.contains("lyrics_provider_lyricsplus")) editor.putBoolean("lyrics_provider_lyricsplus", true)
        if (force || !prefs.contains("lyrics_provider_lrclib")) editor.putBoolean("lyrics_provider_lrclib", true)
        if (force || !prefs.contains("lyrics_provider_youtube_subs")) editor.putBoolean("lyrics_provider_youtube_subs", true)
    }

    fun resetPlayer(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyPlayer(prefs, editor, force = true)
        editor.apply()
    }

    fun applyFolders(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("music_path")) editor.putString("music_path", "")
        if (force || !prefs.contains("video_path")) editor.putString("video_path", "")
        if (force || !prefs.contains("command_path")) editor.putString("command_path", "")
        if (force || !prefs.contains("cache_path")) editor.putString("cache_path", "")
        if (force || !prefs.contains("check_available_storage")) editor.putBoolean("check_available_storage", true)
        if (force || !prefs.contains("cache_downloads")) editor.putBoolean("cache_downloads", true)
        if (force || !prefs.contains("no_part")) editor.putBoolean("no_part", false)
        if (force || !prefs.contains("keep_cache")) editor.putBoolean("keep_cache", false)
        if (force || !prefs.contains("file_name_template")) editor.putString("file_name_template", "%(uploader).30B - %(title).170B")
        if (force || !prefs.contains("file_name_template_audio")) editor.putString("file_name_template_audio", "%(uploader).30B - %(title).170B")
        if (force || !prefs.contains("trim_filenames")) editor.putBoolean("trim_filenames", true)
        if (force || !prefs.contains("restrict_filenames")) editor.putBoolean("restrict_filenames", false)
    }

    fun resetFolders(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyFolders(prefs, editor, force = true)
        editor.apply()
    }

    fun applyTemporaryFiles(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        // No default values in temporary_files_preferences.xml
    }

    fun resetTemporaryFiles(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyTemporaryFiles(prefs, editor, force = true)
        editor.apply()
    }

    fun applyDownloading(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("incognito")) editor.putBoolean("incognito", false)
        if (force || !prefs.contains("download_card")) editor.putBoolean("download_card", true)
        if (force || !prefs.contains("quick_download")) editor.putBoolean("quick_download", false)
        if (force || !prefs.contains("prevent_duplicate_downloads")) editor.putString("prevent_duplicate_downloads", "url_type")
        if (force || !prefs.contains("download_delay")) editor.putString("download_delay", "0-0")
        if (force || !prefs.contains("download_archive_path")) editor.putString("download_archive_path", "")
        if (force || !prefs.contains("metered_networks")) editor.putBoolean("metered_networks", true)
        if (force || !prefs.contains("force_ipv4")) editor.putBoolean("force_ipv4", false)
        if (force || !prefs.contains("use_cookies")) editor.putBoolean("use_cookies", false)
        if (force || !prefs.contains("proxy")) editor.putString("proxy", "")
        if (force || !prefs.contains("preferred_download_type")) editor.putString("preferred_download_type", "auto")
        if (force || !prefs.contains("remember_download_type")) editor.putBoolean("remember_download_type", false)
        if (force || !prefs.contains("last_used_download_type")) editor.putString("last_used_download_type", "video")
        if (force || !prefs.contains("concurrent_fragments")) editor.putInt("concurrent_fragments", 1)
        if (force || !prefs.contains("concurrent_downloads")) editor.putInt("concurrent_downloads", 1)
        if (force || !prefs.contains("aria2")) editor.putBoolean("aria2", false)
        if (force || !prefs.contains("log_downloads")) editor.putBoolean("log_downloads", false)
        if (force || !prefs.contains("cleanup_leftover_downloads")) editor.putString("cleanup_leftover_downloads", "")
        if (force || !prefs.contains("retries")) editor.putString("retries", "")
        if (force || !prefs.contains("fragment_retries")) editor.putString("fragment_retries", "")
        if (force || !prefs.contains("use_alarm_for_scheduling")) editor.putBoolean("use_alarm_for_scheduling", false)
        if (force || !prefs.contains("use_scheduler")) editor.putBoolean("use_scheduler", false)
        if (force || !prefs.contains("schedule_start")) editor.putString("schedule_start", "00:00")
        if (force || !prefs.contains("schedule_end")) editor.putString("schedule_end", "05:00")
        if (force || !prefs.contains("useragent_header")) editor.putString("useragent_header", "")
    }

    fun resetDownloading(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyDownloading(prefs, editor, force = true)
        editor.apply()
    }

    fun applyProcessing(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("use_sponsorblock")) editor.putBoolean("use_sponsorblock", true)
        if (force || !prefs.contains("sponsorblock_url")) editor.putString("sponsorblock_url", "")
        if (force || !prefs.contains("mtime")) editor.putBoolean("mtime", false)
        if (force || !prefs.contains("write_description")) editor.putBoolean("write_description", false)
        if (force || !prefs.contains("use_extra_commands")) editor.putBoolean("use_extra_commands", true)
        if (force || !prefs.contains("force_keyframes")) editor.putBoolean("force_keyframes", false)
        if (force || !prefs.contains("embed_metadata")) editor.putBoolean("embed_metadata", true)
        if (force || !prefs.contains("audio_bitrate")) editor.putString("audio_bitrate", "")
        if (force || !prefs.contains("embed_thumbnail")) editor.putBoolean("embed_thumbnail", true)
        if (force || !prefs.contains("crop_thumbnail")) editor.putBoolean("crop_thumbnail", true)
        if (force || !prefs.contains("embed_lyrics")) editor.putBoolean("embed_lyrics", true)
        if (force || !prefs.contains("auto_embed_lyrics")) editor.putBoolean("auto_embed_lyrics", true)
        if (force || !prefs.contains("save_lrc")) editor.putBoolean("save_lrc", false)
        if (force || !prefs.contains("embed_ttml")) editor.putBoolean("embed_ttml", true)
        if (force || !prefs.contains("playlist_as_album")) editor.putBoolean("playlist_as_album", true)
        if (force || !prefs.contains("audio_codec")) editor.putString("audio_codec", "")
        if (force || !prefs.contains("prefer_container_over_codec_audio")) editor.putBoolean("prefer_container_over_codec_audio", false)
        if (force || !prefs.contains("audio_format")) editor.putString("audio_format", "")
        if (force || !prefs.contains("format_id_audio")) editor.putString("format_id_audio", "")
        if (force || !prefs.contains("prefer_drc_audio")) editor.putBoolean("prefer_drc_audio", false)
        if (force || !prefs.contains("embed_subtitles")) editor.putBoolean("embed_subtitles", true)
        if (force || !prefs.contains("write_subtitles")) editor.putBoolean("write_subtitles", false)
        if (force || !prefs.contains("write_auto_subtitles")) editor.putBoolean("write_auto_subtitles", false)
        if (force || !prefs.contains("no_keep_subs")) editor.putBoolean("no_keep_subs", true)
        if (force || !prefs.contains("subs_lang")) editor.putString("subs_lang", ".*-orig")
        if (force || !prefs.contains("sub_format")) editor.putString("sub_format", "")
        if (force || !prefs.contains("write_thumbnail")) editor.putBoolean("write_thumbnail", false)
        if (force || !prefs.contains("video_embed_thumbnail")) editor.putBoolean("video_embed_thumbnail", false)
        if (force || !prefs.contains("thumbnail_format")) editor.putString("thumbnail_format", "jpg")
        if (force || !prefs.contains("add_chapters")) editor.putBoolean("add_chapters", true)
        if (force || !prefs.contains("video_format")) editor.putString("video_format", "")
        if (force || !prefs.contains("recode_video")) editor.putBoolean("recode_video", false)
        if (force || !prefs.contains("compatible_video")) editor.putBoolean("compatible_video", false)
        if (force || !prefs.contains("video_codec")) editor.putString("video_codec", "")
        if (force || !prefs.contains("video_quality")) editor.putString("video_quality", "best")
        if (force || !prefs.contains("format_id")) editor.putString("format_id", "")
        if (force || !prefs.contains("internal_plugin_ffmpeg_preset")) editor.putString("internal_plugin_ffmpeg_preset", "")
        if (force || !prefs.contains("remove_audio")) editor.putBoolean("remove_audio", false)
        if (force || !prefs.contains("also_download_audio")) editor.putBoolean("also_download_audio", false)
        if (force || !prefs.contains("preferred_format_size")) editor.putString("preferred_format_size", "")
        if (force || !prefs.contains("preferred_format_size_audio")) editor.putString("preferred_format_size_audio", "")
    }

    fun resetProcessing(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyProcessing(prefs, editor, force = true)
        editor.apply()
    }

    fun applyUpdating(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("apk_install_method")) editor.putString("apk_install_method", "system")
        if (force || !prefs.contains("format_order")) editor.putString("format_order", "filesize")
        if (force || !prefs.contains("format_filter")) editor.putString("format_filter", "ALL")
        if (force || !prefs.contains("update_formats")) editor.putBoolean("update_formats", true)
    }

    fun resetUpdating(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyUpdating(prefs, editor, force = true)
        editor.apply()
    }

    fun applyYtdlpUpdate(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("auto_update_ytdlp")) editor.putBoolean("auto_update_ytdlp", true)
        if (force || !prefs.contains("update_ytdlp_while_downloading")) editor.putBoolean("update_ytdlp_while_downloading", true)
    }

    fun resetYtdlpUpdate(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyYtdlpUpdate(prefs, editor, force = true)
        editor.apply()
    }

    fun applyAppUpdate(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("update_app")) editor.putBoolean("update_app", true)
        if (force || !prefs.contains("skip_updates")) editor.putString("skip_updates", "")
        if (force || !prefs.contains("update_beta")) editor.putBoolean("update_beta", false)
    }

    fun resetAppUpdate(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyAppUpdate(prefs, editor, force = true)
        editor.apply()
    }

    fun applyAdvanced(prefs: SharedPreferences, editor: SharedPreferences.Editor, force: Boolean = false) {
        if (force || !prefs.contains("yt_player_client")) editor.putString("yt_player_client", "")
        if (force || !prefs.contains("generate_po_tokens")) editor.putString("generate_po_tokens", "")
        if (force || !prefs.contains("use_app_language_for_metadata")) editor.putBoolean("use_app_language_for_metadata", false)
        if (force || !prefs.contains("youtube_other_extractor_args")) editor.putString("youtube_other_extractor_args", "")
        if (force || !prefs.contains("use_format_sorting")) editor.putBoolean("use_format_sorting", false)
        if (force || !prefs.contains("no_check_certificates")) editor.putBoolean("no_check_certificates", false)
        if (force || !prefs.contains("use_header")) editor.putBoolean("use_header", false)
    }

    fun resetAdvanced(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        applyAdvanced(prefs, editor, force = true)
        editor.apply()
    }

}