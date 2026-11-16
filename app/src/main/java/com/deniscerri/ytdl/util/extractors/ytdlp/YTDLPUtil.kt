package com.deniscerri.ytdl.util.extractors.ytdlp

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.anggrayudi.storage.extension.count
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.YoutubeGeneratePoTokenItem
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import com.deniscerri.ytdl.util.EphemeralCookieExporter
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.Extensions.getIntByAny
import com.deniscerri.ytdl.util.Extensions.getStringByAny
import com.deniscerri.ytdl.util.Extensions.isSoundCloudURL
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.Extensions.isYoutubeWatchVideosURL
import com.deniscerri.ytdl.util.Extensions.readJsonValue
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.playback.model.PlaybackChapter
import com.deniscerri.ytdl.playback.model.PlaybackExtrasCodec
import com.deniscerri.ytdl.playback.model.PlaybackSubtitle
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenBundle
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenCoordinator
import com.deniscerri.ytdl.util.extractors.newpipe.potoken.PoTokenFormatting
import com.google.gson.Gson
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.StringJoiner
import java.util.UUID
import java.util.zip.CRC32

/**
 * Output of a playback-only yt-dlp resolve. Either [singleUrl] is set (progressive/HLS/muxed or
 * audio-only) or [videoUrl] + [audioUrl] are set (separate streams that need MergingMediaSource).
 */
data class PlaybackStreamInfo(
    val singleUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val isLive: Boolean = false,
    val chapters: List<ChapterItem> = emptyList(),
    val playbackChapters: List<PlaybackChapter> = emptyList(),
    val subtitles: List<PlaybackSubtitle> = emptyList()
) {
    val isEmpty: Boolean
        get() = singleUrl.isNullOrBlank() && videoUrl.isNullOrBlank() && audioUrl.isNullOrBlank()
}

class YTDLPUtil(private val context: Context, private val commandTemplateDao: CommandTemplateDao) {
    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val formatUtil = FormatUtil(context)
    private val handler = Handler(Looper.getMainLooper())

    private companion object {
        const val PLAYBACK_META_MARKER = "YTDLNIS_META:"
        const val PLAYBACK_FORMATS_MARKER = "YTDLNIS_FORMATS:"

        fun parsePlaybackSubtitles(
            subtitlesObj: JSONObject?,
            autoCaptionsObj: JSONObject?
        ): List<PlaybackSubtitle> {
            val deviceLang = Locale.getDefault().language.lowercase(Locale.ROOT)
            val preferredExts = listOf("vtt", "srt", "ttml")
            val result = ArrayList<PlaybackSubtitle>()
            val seenKeys = HashSet<String>()

            fun collect(obj: JSONObject?, isAuto: Boolean, maxEntries: Int) {
                if (obj == null) return
                val keys = mutableListOf<String>()
                obj.keys().forEach { k ->
                    if (!k.equals("live_chat", ignoreCase = true) && k.isNotBlank()) {
                        keys.add(k)
                    }
                }
                val sortedKeys = keys.sortedWith(
                    compareByDescending<String> { it.substringBefore('-').lowercase(Locale.ROOT) == deviceLang }
                        .thenByDescending { it.startsWith("en", ignoreCase = true) }
                        .thenBy { it }
                )
                var count = 0
                for (langKey in sortedKeys) {
                    if (count >= maxEntries) break
                    val variants = obj.optJSONArray(langKey) ?: continue
                    var chosenObj: JSONObject? = null
                    for (preferredExt in preferredExts) {
                        for (i in 0 until variants.length()) {
                            val candidate = variants.optJSONObject(i) ?: continue
                            val ext = candidate.optString("ext").lowercase(Locale.ROOT)
                            val url = candidate.optString("url")
                            if (ext == preferredExt && url.isNotBlank()) {
                                chosenObj = candidate
                                break
                            }
                        }
                        if (chosenObj != null) break
                    }
                    chosenObj ?: continue
                    val subUrl = chosenObj.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val ext = chosenObj.optString("ext").lowercase(Locale.ROOT)
                    val dedupeKey = "${langKey.lowercase(Locale.ROOT)}:$isAuto"
                    if (!seenKeys.add(dedupeKey)) continue

                    val rawName = chosenObj.optString("name").takeIf { it.isNotBlank() }
                    val displayName = rawName ?: runCatching {
                        Locale.forLanguageTag(langKey.replace('_', '-')).displayName
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: langKey.uppercase(Locale.ROOT)

                    result.add(
                        PlaybackSubtitle(
                            uri = subUrl,
                            mimeType = PlaybackExtrasCodec.inferSubtitleMimeType(ext),
                            language = langKey,
                            label = if (isAuto && !displayName.contains("auto", ignoreCase = true)) {
                                "$displayName (auto)"
                            } else {
                                displayName
                            },
                            isAutoGenerated = isAuto,
                            isLocal = false
                        )
                    )
                    count++
                }
            }

            collect(subtitlesObj, isAuto = false, maxEntries = 20)
            collect(autoCaptionsObj, isAuto = true, maxEntries = 8)
            return result
        }
    }

    private fun YTDLRequest.applyDefaultOptionsForFetchingData(
        url: String?,
        additionalCookieFile: File? = null
    ) {
        addOption("--skip-download")
        addOption("--quiet")
        addOption("--ignore-errors")
        addOption("--no-warnings")
        addOption("-R", "1")

        val compatOptions = mutableListOf("manifest-filesize-approx")
        if (url?.isYoutubeURL() == true) {
            compatOptions.add("no-youtube-unavailable-videos")
        }
        addOption("--compat-options", compatOptions.joinToString(","))

        val socketTimeout = sharedPreferences.getString("socket_timeout", "5")!!.ifEmpty { "5" }
        addOption("--socket-timeout", socketTimeout)

        if (sharedPreferences.getBoolean("force_ipv4", false)){
            addOption("-4")
        }

        if (additionalCookieFile != null) {
            // A read-only per-resolve snapshot keeps concurrent playback resolutions from
            // sharing (or corrupting) one mutable cookie file.
            addOption("--cookies", additionalCookieFile.absolutePath)
        } else if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context){
                addOption("--cookies", it)
            }

            val useHeader = sharedPreferences.getBoolean("use_header", false)
            val header = sharedPreferences.getString("useragent_header", "")
            if (useHeader && !header.isNullOrBlank()){
                addOption("--add-header","User-Agent:${header}")
            }
        }

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            addOption("--proxy", proxy)
        }
        addOption("-P", FileUtil.getCachePath(context) + "/tmp")

        if (sharedPreferences.getBoolean("no_check_certificates", false)) {
            addOption("--no-check-certificates")
        }

        var extraCommands = commandTemplateDao.getAllTemplatesAsDataFetchingExtraCommands()

        extraCommands = if (url != null) {
            extraCommands.filter {
                it.urlRegex.any { u -> Regex(u).containsMatchIn(url) } ||
                it.urlRegex.isEmpty()
            }
        }else{
            extraCommands.filter { it.urlRegex.isEmpty() }
        }

        if (extraCommands.isNotEmpty()){
            //addCommands(extraCommands.split(" ", "\t", "\n"))
            addConfig(extraCommands.joinToString(" ") { it.content })
        }
    }

    private fun canApplyFlatPlaylist(url: String) : Boolean {
        var noFlatPlaylist = sharedPreferences.getBoolean("no_flat_playlist", false)
        if (url.isSoundCloudURL()) {
            noFlatPlaylist = true
        }

        return !noFlatPlaylist
    }

    @SuppressLint("RestrictedApi")
    suspend fun getFromYTDL(query: String, singleItem: Boolean = false, resultsGenerated: suspend (pagedResults: List<ResultItem>) -> Unit): List<ResultItem> {
        val searchEngine = sharedPreferences.getString("search_engine", "ytsearch")

        val request : YTDLRequest
        if (query.contains("http")){
            if (query.isYoutubeWatchVideosURL()) {
                request = YTDLRequest(emptyList())
                val config =
                    File(context.cacheDir.absolutePath + "/config" + System.currentTimeMillis() + "##url.txt")
                config.writeText(query)
                request.addOption("--config", config.absolutePath)
            }else{
                request = YTDLRequest(query)
            }
            request.addWriteInfoJson(query)
        }else{
            request = YTDLRequest(emptyList())
            when (searchEngine){
                "ytsearchmusic" -> {
                    request.addOption("--default-search", "https://music.youtube.com/search?q=")
                    request.addOption("ytsearch25:\"${query}\"")
                }
                else -> {
                    request.addOption("${searchEngine}25:\"${query}\"")
                }
            }
        }
        if (searchEngine == "ytsearch" || query.isYoutubeURL()) {
            val poBundle = PoTokenCoordinator.getOrMintForYtDlp(context, if (query.isURL()) query else null)
            request.setYoutubeExtractorArgs(query, poBundle)
        }

        if (canApplyFlatPlaylist(query)) {
            request.addOption("--flat-playlist")
        }

        request.addOption("--lazy-playlist")
        request.addOption(if (singleItem) "-J" else "-j")
        request.applyDefaultOptionsForFetchingData(if (query.isURL()) query else null)
        val usePlaylistOriginalURL = sharedPreferences.getBoolean("use_original_url_playlist", false)

        val finalResults = mutableListOf<ResultItem>()
        var postedProgress = false
        // callbacks run on the stdout reader thread inside runBlocking, which doesn't see the caller being cancelled
        val callerJob = currentCoroutineContext().job
        RuntimeManager.getInstance().runKillable { processId ->
        RuntimeManager.getInstance().execute(request, processId) { progress, _, line ->
            if (!callerJob.isActive) return@execute
            runCatching {
                val generatedResults = parseYTDLPListResults(listOf(line))
                if (generatedResults.isNotEmpty()) {

                    generatedResults.forEach {
                        if (!it.playlistURL.isNullOrBlank() && usePlaylistOriginalURL && query.contains("http")) {
                            it.playlistURL = query
                        }
                    }

                    finalResults.addAll(generatedResults)
                    if (!postedProgress) {
                        if (finalResults.size > 1) {
                            runBlocking {
                                resultsGenerated(finalResults)
                            }
                            postedProgress = true
                        }
                    }else{
                        runBlocking {
                            resultsGenerated(generatedResults)
                        }
                    }
                }
            }
        }
        }

        currentCoroutineContext().ensureActive()
        if (!postedProgress) resultsGenerated(finalResults)
        return finalResults
    }

    private fun parseYTDLPListResults(results: List<String?>, query: String = "") : ArrayList<ResultItem> {
        val items = arrayListOf<ResultItem>()

        for (result in results) {
            if (result.isNullOrBlank()) continue
            val jsonObject = JSONObject(result)
            var title = jsonObject.getStringByAny("alt_title", "title", "webpage_url_basename")
            if (title == "[Private video]" || title == "[Deleted video]") continue


            var playlistTitle = jsonObject.getStringByAny("playlist_title")
            var playlistURL: String? = ""
            var playlistIndex: Int? = null

            if(playlistTitle.removeSurrounding("\"") == query) playlistTitle = ""

            if (playlistTitle.isNotBlank()){
                playlistURL = jsonObject.getStringByAny("playlist_webpage_url").ifEmpty { query }
                runCatching { playlistIndex = jsonObject.getInt("playlist_index") }
            }
            val url = if (jsonObject.has("url") && results.size > 1){
                jsonObject.getString("url")
            }else{
                if (Patterns.WEB_URL.matcher(query).matches() && playlistURL?.isEmpty() == true){
                    query
                }else{
                    jsonObject.getStringByAny("webpage_url", "original_url", "url")
                }
            }

            val authorTags = mutableListOf("uploader", "channel", "playlist_uploader", "uploader_id")
            if (url.isYoutubeURL()) {
                authorTags.addAll(0, listOf("artists", "artist", "creators"))
            }
            var author = jsonObject.getStringByAny(*authorTags.map { it }.toTypedArray()).removeSuffix(" - Topic")
            var duration = jsonObject.getIntByAny("duration").toString()
            if (duration != "-1"){
                duration = jsonObject.getInt("duration").toStringDuration(Locale.US)
            }

            var thumb: String? = ""
            if (jsonObject.has("thumbnail")) {
                thumb = jsonObject.getString("thumbnail")
            } else if (jsonObject.has("thumbnails")) {
                val thumbs = jsonObject.getJSONArray("thumbnails")
                if (thumbs.length() > 0){
                    thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                }
            }

            if(author.isEmpty()){
                runCatching {
                    val firstEntry = jsonObject.getJSONArray("entries").getJSONObject(0)
                    author = firstEntry.getStringByAny("uploader", "channel", "playlist_uploader", "uploader_id")
                    val thumbs = firstEntry.getJSONArray("thumbnails")
                    if (thumbs.length() > 0){
                        thumb = thumbs.getJSONObject(thumbs.length() - 1).getString("url")
                    }
                }
            }

            var website = jsonObject.getStringByAny("extractor_key", "extractor","ie_key")
            if (website == "Generic" || website == "HTML5MediaEmbed") website = jsonObject.getStringByAny("webpage_url_domain")


            val formatsInJSON = if (jsonObject.has("formats") && jsonObject.get("formats") is JSONArray) jsonObject.getJSONArray("formats") else null
            val formats : ArrayList<Format> = parseYTDLFormats(formatsInJSON)

            val chaptersInJSON = if (jsonObject.has("chapters") && jsonObject.get("chapters") is JSONArray) jsonObject.getJSONArray("chapters") else null
            val listType: Type = object : TypeToken<List<ChapterItem>>() {}.type
            var chapters : ArrayList<ChapterItem> = arrayListOf()

            if (chaptersInJSON != null){
                chapters = Gson().fromJson(chaptersInJSON.toString(), listType)
            }

            var urls = "";
            if(jsonObject.has("requested_formats")) {
                val requestedFormats = jsonObject.getJSONArray("requested_formats")
                val urlList = mutableListOf<String>()
                val length = requestedFormats.length()-1
                for (i in length downTo 0) {
                    urlList.add(requestedFormats.getJSONObject(i).getString("url"))
                }

                urls = urlList.joinToString("\n")
            }



            val type = jsonObject.getStringByAny("_type")
            if (type == "playlist") {
                if (playlistTitle.isEmpty()) {
                    playlistTitle = title
                }
                title = ""
                author = ""
            }


            val availableSubtitles = mutableListOf<String>()
            if (jsonObject.has("subtitles")) {
                runCatching {
                    availableSubtitles.addAll(jsonObject.getJSONObject("subtitles").keys().asSequence().toList())
                }
            }

            val res = ResultItem(
                0,
                url = url,
                title = title,
                author = author,
                duration = duration,
                thumb = thumb!!,
                website = website,
                playlistTitle = playlistTitle,
                formats = formats,
                urls = urls,
                chapters = chapters,
                playlistURL = playlistURL,
                playlistIndex = playlistIndex,
                availableSubtitles = availableSubtitles
            )

            items.add(res)
        }

        return items
    }

    suspend fun getYoutubeWatchLater() : ArrayList<ResultItem> {
        val request = YTDLRequest(listOf())
        request.setYoutubeExtractorArgs(null, PoTokenCoordinator.getOrMintForYtDlp(context, null))
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ytwatchlater")
        val ytdlResponse = RuntimeManager.getInstance().runKillable { processId ->
            RuntimeManager.getInstance().execute(request, processId)
        }
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            ytdlResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(ytdlResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    suspend fun getYoutubeRecommendations() : ArrayList<ResultItem> {
        val request = YTDLRequest(listOf())
        request.setYoutubeExtractorArgs(null, PoTokenCoordinator.getOrMintForYtDlp(context, null))
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ytrec")
        val ytdlResponse = RuntimeManager.getInstance().runKillable { processId ->
            RuntimeManager.getInstance().execute(request, processId)
        }
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            ytdlResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(ytdlResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    suspend fun getYoutubeLikedVideos() : ArrayList<ResultItem> {
        val request = YTDLRequest(listOf())
        request.setYoutubeExtractorArgs(null, PoTokenCoordinator.getOrMintForYtDlp(context, null))
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ytfav")
        val ytdlResponse = RuntimeManager.getInstance().runKillable { processId ->
            RuntimeManager.getInstance().execute(request, processId)
        }
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            ytdlResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(ytdlResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }

    suspend fun getYoutubeWatchHistory() : ArrayList<ResultItem> {
        val request = YTDLRequest(listOf())
        request.setYoutubeExtractorArgs(null, PoTokenCoordinator.getOrMintForYtDlp(context, null))
        request.addOption( "-j")
        request.addOption("--flat-playlist")
        request.applyDefaultOptionsForFetchingData(null)
        request.addOption(":ythis")
        val ytdlResponse = RuntimeManager.getInstance().runKillable { processId ->
            RuntimeManager.getInstance().execute(request, processId)
        }
        val results: List<String?> = try {
            val lineSeparator = System.getProperty("line.separator")
            ytdlResponse.out.split(lineSeparator!!)
        } catch (e: Exception) {
            listOf(ytdlResponse.out)
        }.filter { it.isNotBlank() }.apply {
            if (this.isEmpty()) return arrayListOf()
        }
        return parseYTDLPListResults(results)
    }


    suspend fun getFormatsForAll(urls: List<String>, progress: (progress: ResultViewModel.MultipleFormatProgress) -> Unit) : Result<MutableList<MutableList<Format>>>  {
        val formatCollection = mutableListOf<MutableList<Format>>()

        val urlsFile = File(context.cacheDir, "urls.txt")
        urlsFile.delete()
        withContext(Dispatchers.IO) {
            urlsFile.createNewFile()
        }
        urls.forEach {
            urlsFile.appendText(it+"\n")
        }

        try {
            val request = YTDLRequest(emptyList())
            request.addOption("--print", "formats")
            request.addOption("-a", urlsFile.absolutePath)
            request.applyDefaultOptionsForFetchingData(urls.firstOrNull { it.isURL() })
            if (urls.all { it.isYoutubeURL() }) {
                request.setYoutubeExtractorArgs(urls[0], PoTokenCoordinator.getOrMintForYtDlp(context, urls[0]))
            }
            if (!request.hasOption("--no-check-certificates")) request.addOption("--no-check-certificates")

            val txt = parseYTDLRequestString(request)
            println(txt)

            var urlIdx = 0
            val callerJob = currentCoroutineContext().job
            RuntimeManager.getInstance().runKillable { processId ->
            RuntimeManager.getInstance().execute(request, processId){ progress, _, line ->
                if (!callerJob.isActive) return@execute
                try{
                    if (line.isNotBlank()){
                        val url = urls[urlIdx]
                        println(line)
                        println(url)

                        if (line.contains("unavailable")) {
                            progress(ResultViewModel.MultipleFormatProgress(url, listOf(), true, line))
                        }else{
                            val formatsJSON = JSONArray(line)
                            val formats = parseYTDLFormats(formatsJSON)

                            formatCollection.add(formats)
                            progress(ResultViewModel.MultipleFormatProgress(url, formats))
                        }
                    }

                }catch (e: Exception){
                    Log.e("GET MULTIPLE FORMATS", e.toString())
                }
                urlIdx++
            }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.message?.split(System.lineSeparator())?.onEach { line ->
                println(line)
                if (line.contains("unavailable")) {
                    runCatching {
                        val id = Regex("""\[.*?\] (\w+):""").find(line)!!.groupValues[1]
                        val url = urls.first { it.contains(id) }
                        progress(
                            ResultViewModel.MultipleFormatProgress(
                                url,
                                listOf(),
                                true,
                                line
                            )
                        )
                        delay(500)
                    }
                }

            }

            e.message?.apply {
                handler.post {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }
            }

            e.printStackTrace()
            return Result.failure(e)
        } finally {
            urlsFile.delete()
        }

        return Result.success(formatCollection)
    }

    suspend fun getFormats(url: String, ignoreInfoJSON: Boolean) : List<Format> {
        val request = YTDLRequest(url)
        request.addOption("--print", "%(formats)j")
        request.addOption("--print", "%(duration)s")
        request.applyDefaultOptionsForFetchingData(url)
        if (url.isYoutubeURL()) {
            request.setYoutubeExtractorArgs(url, PoTokenCoordinator.getOrMintForYtDlp(context, url))
        }
        if (!request.hasOption("--no-check-certificates")) request.addOption("--no-check-certificates")

        val canUseWriteInfoJson =
            !ignoreInfoJSON &&
            !sharedPreferences.getBoolean("disable_write_info_json", false) &&
            !request.toString().contains("--download-sections")

        if (canUseWriteInfoJson) {
            val infoJsonFile = getInfoJsonFile(url)
            //ignore info file if its older than 5 hours. puny measure to prevent expired formats in some cases
            if (infoJsonFile != null && System.currentTimeMillis() - infoJsonFile.lastModified() <= (1000 * 60 * 60 * 5)) {
                request.addOption("--load-info-json", infoJsonFile.absolutePath)
            }else {
                request.addWriteInfoJson(url)
            }
        }

        val formats = try {
            RuntimeManager.getInstance().runKillable { processId ->
                RuntimeManager.getInstance().executeStreaming(request, processId) { stream ->
                    JsonReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                        reader.strictness = Strictness.LENIENT
                        readFormatsList(reader)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            arrayListOf()
        }

        if (formats.isEmpty()) {
            runCatching {
                getInfoJsonFile(url)?.apply { this.delete() }
            }
        }

        return formats
    }

    fun readFormatsList(reader: JsonReader): ArrayList<Format> {
        val formats = arrayListOf<Format>()
        val seenFormatIds = HashSet<String>()

        reader.beginArray()
        while (reader.hasNext()) {
            val obj = readFormatObject(reader) // builds one JSONObject, keep-list filtered

            val id = obj.optString("format_id").ifBlank { obj.optString("itag") }
            if (id.isNotBlank() && !seenFormatIds.add(id)) {
                continue // duplicate - discard immediately, never converted to Format
            }

            val formatProper = parseOneFormat(obj) ?: continue
            formats.add(formatProper)
            // `obj` falls out of scope here - eligible for GC immediately,
            // nothing keeps N of them alive at once
        }
        reader.endArray()

        formats.reverse()
        return formats
    }

    private fun readFormatObject(reader: JsonReader): JSONObject {
        val obj = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            obj.put(name, readJsonValue(reader))
        }
        reader.endObject()
        return obj
    }

    private fun parseOneFormat(format: JSONObject): Format? {
        runCatching {
            if (format.get("filesize").toString() == "None") format.remove("filesize")
        }
        runCatching {
            if (format.get("filesize_approx").toString() == "None") format.remove("filesize_approx")
        }

        val exactSize = format.optLong("filesize").takeIf { it > 0L }
        val reportedApprox = format.optLong("filesize_approx").takeIf { it > 0L }

        runCatching {
            if (format.get("format_note").toString() == "null") format.remove("format_note")
        }

        val formatProper = Gson().fromJson(format.toString(), Format::class.java)
        if (formatProper.format_note == null) formatProper.format_note = ""

        formatProper.filesize = exactSize ?: reportedApprox ?: 0
        formatProper.isFilesizeApprox = exactSize == null

        val resolution = format.optString("resolution")
        if (format.has("format_note")) {
            if (!formatProper.format_note.contains("audio only", true)) {
                formatProper.format_note = format.getString("format_note")
            } else {
                if (!formatProper.format_note.endsWith("audio", true)) {
                    formatProper.format_note = format.getString("format_note").uppercase().removeSuffix("AUDIO").trim() + " AUDIO"
                }
            }
            if (!resolution.isNullOrBlank() && resolution != "audio only") {
                formatProper.format_note = "${formatProper.format_note} (${resolution})"
            }
        }

        if (formatProper.format_note.contains("storyboard", ignoreCase = true)) return null

        formatProper.format_note = formatProper.format_note.trim()
        formatProper.container = format.getString("ext")
        if (formatProper.tbr == "None") formatProper.tbr = ""
        if (!formatProper.tbr.isNullOrBlank()) {
            formatProper.tbr += "k"
        }

        if (formatProper.vcodec.isNullOrEmpty() || formatProper.vcodec == "null") {
            if (formatProper.acodec.isNullOrEmpty() || formatProper.acodec == "null") {
                formatProper.vcodec = format.getStringByAny("video_ext", "ext").ifEmpty { "unknown" }
            }
        }

        return formatProper
    }


    private fun parseYTDLFormats(formatsInJSON: JSONArray?) : ArrayList<Format> {
        val formats = arrayListOf<Format>()

        if (formatsInJSON != null) {
            for (f in formatsInJSON.length() - 1 downTo 0){
                val formatRaw = formatsInJSON.getJSONObject(f)
                val format = parseOneFormat(formatRaw) ?: continue
                formats.add(format)
            }
        }
        return formats
    }


    /**
     * Resolves direct stream URLs for the in-app player without downloading.
     *
     * Uses an isolated cookie snapshot per resolve, asks yt-dlp for the requested_formats object
     * (never relies on positional ordering), and returns separate video/audio URLs when the
     * selected format requires merging.
     */
    suspend fun resolvePlaybackStream(
        url: String,
        audioOnly: Boolean = false,
        videoHeightCap: Int = 720,
        preferHlsForLive: Boolean = true
    ): Result<PlaybackStreamInfo> {
        val cookieJobId = "playback-${System.currentTimeMillis()}"
        var cookieFile: File? = null
        try {
            val request = YTDLRequest(url)
            val useCookies = sharedPreferences.getBoolean("use_cookies", false)
            if (useCookies) {
                cookieFile = EphemeralCookieExporter.createEphemeralCookieFile(context, cookieJobId)
            }
            request.applyDefaultOptionsForFetchingData(url, additionalCookieFile = cookieFile)
            request.addOption("--no-playlist")
            // requested_formats is printed in full (not field-projected) so its entries keep
            // vcodec/acodec. Older yt-dlp builds ignored the projection anyway; both shapes are
            // handled by parsePlaybackOutput().
            request.addOption("--print", "$PLAYBACK_META_MARKER%(.{url,duration,is_live,protocol,vcodec,acodec,chapters,subtitles,automatic_captions})j")
            request.addOption("--print", "$PLAYBACK_FORMATS_MARKER%(requested_formats)j")

            if (audioOnly) {
                // Prefer AAC/M4A for broad device decoder support; Opus/WebM remains the fallback.
                request.addOption("-f", "ba[acodec^=mp4a]/ba/b")
            } else {
                // H.264/AAC first, then any codec at the requested height. A muxed format is
                // the next fallback and HLS is only the final live-stream fallback. Never sort
                // every VOD resolve towards m3u8: YouTube's HLS variants are lower quality and
                // can fail on devices without the right HLS/AAC combination.
                val selector = buildString {
                    append("bv*[height<=$videoHeightCap][vcodec^=avc1]+ba[acodec^=mp4a]")
                    append("/bv*[height<=$videoHeightCap]+ba")
                    append("/b[height<=$videoHeightCap]")
                    if (preferHlsForLive) append("/b*[protocol^=m3u8]")
                    append("/b")
                }
                request.addOption("-f", selector)
            }

            if (url.isYoutubeURL()) {
                request.setYoutubeExtractorArgs(url, PoTokenCoordinator.getOrMintForYtDlp(context, url))
            }

            val response = RuntimeManager.getInstance().runKillable { processId ->
                RuntimeManager.getInstance().execute(request, processId)
            }
            val output = parsePlaybackOutput(response.out)
                ?: return Result.failure(Exception("yt-dlp returned no stream information"))

            val meta = output.meta
            val requestedFormats = output.requestedFormats
            var video: String? = null
            var audio: String? = null
            var single: String? = null

            if (requestedFormats != null && requestedFormats.length() > 0) {
                val entries = (0 until requestedFormats.length()).mapNotNull { index ->
                    requestedFormats.optJSONObject(index)?.let { format ->
                        val formatUrl = format.optString("url").takeIf { it.isNotBlank() }
                        if (formatUrl == null) null else RequestedFormat(
                            url = formatUrl,
                            hasVideo = format.optString("vcodec").let {
                                it.isNotBlank() && it != "none"
                            } || format.optString("resolution").let {
                                it.isNotBlank() && it != "audio only"
                            },
                            hasAudio = format.optString("acodec").let {
                                it.isNotBlank() && it != "none"
                            }
                        )
                    }
                }

                val classifiedVideo = entries.firstOrNull { it.hasVideo && !it.hasAudio }
                val classifiedAudio = entries.firstOrNull { it.hasAudio && !it.hasVideo }
                if (audioOnly) {
                    single = classifiedAudio?.url ?: meta?.optString("url")
                        ?.takeIf { it.isNotBlank() } ?: entries.first().url
                } else if (classifiedVideo != null && classifiedAudio != null) {
                    video = classifiedVideo.url
                    audio = classifiedAudio.url
                } else if (entries.size == 2) {
                    // Field projection stripped the codec metadata: yt-dlp preserves selector
                    // order, so the video stream was requested first and audio second.
                    if (entries[0].hasAudio && !entries[0].hasVideo &&
                        entries[1].hasVideo && !entries[1].hasAudio
                    ) {
                        audio = entries[0].url
                        video = entries[1].url
                    } else {
                        video = entries[0].url
                        audio = entries[1].url
                    }
                } else if (entries.size == 1) {
                    single = entries.first().url
                } else {
                    video = entries.firstOrNull { it.hasVideo }?.url ?: entries.first().url
                    audio = entries.firstOrNull { it.hasAudio }?.url
                    single = meta?.optString("url")?.takeIf { it.isNotBlank() }
                }
            } else {
                single = meta?.optString("url")?.takeIf { it.isNotBlank() }
            }

            if (!audioOnly && single == null && (video == null || audio == null)) {
                single = meta?.optString("url")?.takeIf { it.isNotBlank() }
                if (single != null) {
                    video = null
                    audio = null
                }
            }

            val chapters = mutableListOf<ChapterItem>()
            meta?.optJSONArray("chapters")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let {
                        runCatching {
                            chapters.add(Gson().fromJson(it.toString(), ChapterItem::class.java))
                        }
                    }
                }
            }

            val durationMs = meta?.optDouble("duration", 0.0)
                ?.takeIf { it > 0 }
                ?.let { (it * 1000).toLong() }

            val protocol = meta?.optString("protocol").orEmpty()
            val effectiveUrl = single ?: video ?: audio
            val playbackChapters = PlaybackExtrasCodec.parseYtDlpChapters(meta?.optJSONArray("chapters"))
            val subtitles = parsePlaybackSubtitles(
                meta?.optJSONObject("subtitles"),
                meta?.optJSONObject("automatic_captions")
            )
            val info = PlaybackStreamInfo(
                singleUrl = single,
                videoUrl = video,
                audioUrl = audio,
                mimeType = if (single != null) playbackMimeType(protocol, effectiveUrl) else null,
                durationMs = durationMs,
                isLive = meta?.optBoolean("is_live", false) ?: false,
                chapters = chapters,
                playbackChapters = playbackChapters,
                subtitles = subtitles
            )
            return if (info.isEmpty) {
                Result.failure(Exception("No playable stream found"))
            } else {
                Result.success(info)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(e)
        } finally {
            EphemeralCookieExporter.deleteEphemeralCookieFile(cookieFile)
        }
    }

    private data class RequestedFormat(
        val url: String,
        val hasVideo: Boolean,
        val hasAudio: Boolean
    )

    private data class ParsedPlaybackOutput(
        val meta: JSONObject?,
        val requestedFormats: JSONArray?
    )

    /**
     * Reads the marker lines emitted by [resolvePlaybackStream]. Falls back to the last JSON
     * object on stdout so output from older yt-dlp builds keeps working.
     */
    private fun parsePlaybackOutput(raw: String): ParsedPlaybackOutput? {
        var meta: JSONObject? = null
        var formats: JSONArray? = null
        var fallback: JSONObject? = null

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith(PLAYBACK_META_MARKER) -> {
                    val json = trimmed.removePrefix(PLAYBACK_META_MARKER).trim()
                    meta = runCatching { JSONObject(json) }.getOrNull()
                }

                trimmed.startsWith(PLAYBACK_FORMATS_MARKER) -> {
                    val json = trimmed.removePrefix(PLAYBACK_FORMATS_MARKER).trim()
                    formats = runCatching { JSONArray(json) }.getOrNull()
                }

                trimmed.startsWith("{") && trimmed.endsWith("}") -> {
                    fallback = runCatching { JSONObject(trimmed) }.getOrNull()
                }
            }
        }

        if (meta == null && formats == null && fallback == null) return null
        return ParsedPlaybackOutput(
            meta = meta ?: fallback,
            requestedFormats = formats ?: (meta ?: fallback)?.optJSONArray("requested_formats")
        )
    }

    private fun playbackMimeType(protocol: String, url: String?): String? {
        val p = protocol.lowercase(Locale.US)
        val u = url?.lowercase(Locale.US).orEmpty()
        return when {
            p.contains("m3u8") || u.contains(".m3u8") -> "application/x-mpegURL"
            p.contains("dash") -> "application/dash+xml"
            else -> null
        }
    }

    suspend fun getStreamingUrlAndChapters(url: String) : Result<Pair<List<String>, List<ChapterItem>?>> {
        try {
            val request = YTDLRequest(url)
            //request.addOption("--get-url")
            request.addOption("--print", "%(.{urls,chapters})s")
            request.addOption("-S", "res:720,+proto:m3u8")
            request.applyDefaultOptionsForFetchingData(url)
            if (url.isYoutubeURL()) {
                request.setYoutubeExtractorArgs(url, PoTokenCoordinator.getOrMintForYtDlp(context, url))
            }

            val ytdlResponse = RuntimeManager.getInstance().runKillable { processId ->
                RuntimeManager.getInstance().execute(request, processId)
            }
            val json = JSONObject(ytdlResponse.out)
            val urls = if (json.has("urls")) {
                json.getString("urls").split("\n")
            }else{
                listOf()
            }

            val chapters = if (json.has("chapters")) {
                val arr = json.getJSONArray("chapters")
                val list = mutableListOf<ChapterItem>()
                for (i in 0 until arr.length()) {
                    list.add(
                        Gson().fromJson(arr.getJSONObject(i).toString(), ChapterItem::class.java)
                    )
                }

                list
            }else{
                listOf()
            }

            return Result.success(Pair(urls, chapters))

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(e)
        }
    }
    fun getVersion(context: Context, channel: String) : String {
        val prefVersion = if (listOf("stable", "nightly", "master").contains(channel)) {
            RuntimeManager.getInstance().version(context) ?: ""
        } else ""

        if (prefVersion.isNotBlank()) return prefVersion

        val req = YTDLRequest(emptyList())
        req.addOption("--version")
        return RuntimeManager.getInstance().execute(req).out.trim()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun YTDLRequest.addWriteInfoJson(url: String) {
        if (url.isYoutubeURL() && url.contains("playlist?list=")) return

        val cachePath = FileUtil.getInfoJsonPath(context)
        File(cachePath).mkdirs()

        var id = url
        if (url.isYoutubeURL()) {
            id = url.getIDFromYoutubeURL() ?: url
        }

        val infoJsonName = hash(id)
        //yt-dlp doesnt overwrite info json, so delete manually
        File("${cachePath}/${infoJsonName}video.info.json").delete()
        this.addOption("--no-clean-info-json")
        this.addCommands(listOf("--print-to-file", "video:%()j", "${cachePath}/${infoJsonName}${System.currentTimeMillis()}video.info.json"))
    }

    fun hash(text: String): String {
        val crc = CRC32()
        crc.update(text.toByteArray())
        return crc.value.toString(16) // 8 hex chars max
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getInfoJsonFile(url: String): File? {
        val cachePath = FileUtil.getInfoJsonPath(context)

        var id = url
        if (url.isYoutubeURL()) {
            id = url.getIDFromYoutubeURL() ?: url
        }

        val infoJsonName = hash(id)
        val infoJsonFile : File? =
            File(cachePath)
                .walkBottomUp()
                .sortedByDescending { it.lastModified() }
                .filter { it.name.startsWith(infoJsonName) }
                .firstOrNull()

        return infoJsonFile
    }

    fun getFilenameTemplatePreview(item: DownloadItem, filenameTemplate: String): String {
        item.customFileNameTemplate = filenameTemplate
        val request = buildYTDLRequest(item)
        request.addOption("--print", "%(filename)s")
        request.addOption("--skip-download")
        request.addOption("--simulate")
        request.addOption("--no-check-formats")
        request.addOption("--quiet")

        try {
            val response = RuntimeManager.getInstance().execute(request)
            return response.out.replace(FileUtil.getCacheDownloadsPath(context) + "/${item.id}/", "").trim()
        } catch (ex: Exception) {
            return ex.message ?: ""
        }
    }

    fun parseYTDLRequestString(request : YTDLRequest) : String {
        val arr = request.buildCommand().toMutableList()
        for (i in arr.indices) {
            if (!arr[i].startsWith("-")) {
                arr[i] = "\"${arr[i]}\""
            }
        }

        var final = java.lang.String.join(" ", arr).replace("\"\"", "\" \"")
        val ppas = "--config(-locations)? \"(.*?)\"".toRegex().findAll(final)
        ppas.forEach {res ->
            val path = "\"(.*?)\"".toRegex().find(res.value)?.value?.replace("\"", "")
            val newVal = runCatching {
                File(path ?: "").readText()
            }.onFailure {
                res.value
            }.getOrDefault("")

            final = final.replace(res.value, newVal)

        }

        return final
    }

    private fun MutableList<String>.addOption(vararg options: String) {
        options.forEach {
            this.add(it)
        }
    }


    private fun YTDLRequest.setYoutubeExtractorArgs(url: String?, poTokenBundle: PoTokenBundle? = null) {
        val extractorArgs = mutableListOf<String>()
        val playerClients = mutableSetOf<String>()
        val poTokens = mutableListOf<String>()

        val configuredPlayerClientsRaw = sharedPreferences.getString("youtube_player_clients", "[]")!!.ifEmpty { "[]" }
        kotlin.runCatching {
            val configuredPlayerClients = Gson().fromJson(configuredPlayerClientsRaw, Array<YoutubePlayerClientItem>::class.java).toMutableList()

            for (value in configuredPlayerClients) {
                if (value.enabled) {
                    if (!value.useOnlyPoToken) {
                        playerClients.add(value.playerClient)
                    }

                    var canUsePoToken = true
                    if (value.urlRegex.isNotEmpty() && url != null) {
                        canUsePoToken = value.urlRegex.any { url.matches(it.toRegex()) }
                    }

                    if (canUsePoToken) {
                        value.poTokens.forEach { pt ->
                            poTokens.add("${value.playerClient}.${pt.context}+${pt.token}")
                        }
                    }
                }
            }
        }

//        val dataSyncID = sharedPreferences.getString("youtube_data_sync_id", "")!!
//        if (dataSyncID.isNotBlank()) {
//            extractorArgs.add("player_skip=webpage,configs")
//            extractorArgs.add("data_sync_id=${dataSyncID}")
//        }

        val generatedPoTokensRaw = sharedPreferences.getString("youtube_generated_po_tokens", "[]")!!.ifEmpty { "[]" }
        kotlin.runCatching {
            val generatedPoTokens = Gson().fromJson(generatedPoTokensRaw,Array<YoutubeGeneratePoTokenItem>::class.java).toMutableList()
            if (generatedPoTokens.isNotEmpty()) {
                for (value in generatedPoTokens) {
                    if (value.enabled) {
                        for (cl in value.clients) {
                            playerClients.add(cl)
                            for (pt in value.poTokens) {
                                if (pt.token.isNotBlank()) {
                                    poTokens.add("${cl}.${pt.context}+${pt.token}")
                                }
                            }
                        }

                        if (value.useVisitorData && value.visitorData.isNotBlank()) {
                            extractorArgs.add("visitor_data=${value.visitorData}")
                        }

                    }
                }
            }
        }

        if (playerClients.isNotEmpty()){
            extractorArgs.add("player_client=${playerClients.joinToString(",")}")
        }else{
            //TODO DISCUSS
            //extractorArgs.add("player_client=default,-web,-web-safari,-android,-ios,-tv")
        }

        if (poTokenBundle != null) {
            extractorArgs.removeAll { it.startsWith("visitor_data=") }
            val (visitorArg, poTokenArg) = PoTokenFormatting.buildYtDlpPoTokenArgs(
                poTokenBundle,
                PoTokenFormatting.DEFAULT_YTDLP_WEB_CLIENTS
            )
            extractorArgs.add(visitorArg)
            val generatedEntries = poTokenArg.removePrefix("po_token=").split(",").filter { it.isNotBlank() }
            val generatedClients = generatedEntries.mapTo(mutableSetOf()) { it.substringBefore('.') }
            poTokens.removeAll { it.substringBefore('.') in generatedClients }
            poTokens.addAll(generatedEntries)
        }

        if (poTokens.isNotEmpty()) {
            extractorArgs.add("po_token=${poTokens.joinToString(",")}")
        }

        //extractorArgs.add("skip=translated_subs")

        val useLanguageForMetadata = sharedPreferences.getBoolean("use_app_language_for_metadata", false)
        if (useLanguageForMetadata) {
            val lang = java.util.Locale.getDefault().language
            val langTag = java.util.Locale.getDefault().toLanguageTag()
            if (context.getStringArray(R.array.subtitle_langs).contains(lang)) {
                extractorArgs.add("lang=$lang")
            }else if (context.getStringArray(R.array.subtitle_langs).contains(langTag)) {
                extractorArgs.add("lang=$langTag")
            }
        }

        val otherArgs = sharedPreferences.getString("youtube_other_extractor_args", "")!!
        if (otherArgs.isNotBlank()) {
            extractorArgs.add(otherArgs)
        }

        val extArgs = extractorArgs.joinToString(";")
        if (extractorArgs.isNotEmpty()) {
            this.addOption("--extractor-args", "youtube:${extArgs}")
        }

        val useBgUtils = sharedPreferences.getBoolean("use_bgutils_potoken_generator", false)
        val bgUtilsMethod = sharedPreferences.getString("bgutils_potoken_method", "server")
        if (useBgUtils && bgUtilsMethod == "generation_script") {
            val serverPath = File(BgUtilsPoTokenGeneratorUtil.getServerFolder(context), "server").absolutePath
            this.addOption("--extractor-args", "youtubepot-bgutilscript:server_home=${serverPath}")
        }
    }

    private fun YTDLRequest.addConfig(commandString: String) {
        this.addOption(
            "--config-locations",
            File(context.cacheDir.absolutePath + "/${System.currentTimeMillis()}${java.util.UUID.randomUUID()}.txt").apply {
                writeText(commandString)
            }.absolutePath
        )
    }

    private fun StringJoiner.addOption(vararg elements: Any) {
        this.add(elements.first().toString())
        if (elements.size > 1) {
            for (el in elements.drop(1)) {
                val arg = el.toString()
                    //.replace("\\", "\\\\")
                    .replace("\"", "\\\"")

                this.add("\"$arg\"")
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun buildYTDLRequest(downloadItem: DownloadItem) : YTDLRequest {
        var useItemURL = sharedPreferences.getBoolean("use_itemurl_instead_playlisturl", false)
        // for /releases youtube channel playlists that have playlists inside of them, cant use indexing or match filter id, so download on its own
        if (downloadItem.url.isYoutubeURL() && downloadItem.url.getIDFromYoutubeURL() == null) {
            useItemURL = true
        }

        var isPlaylistItem = false

        val request = StringJoiner(" ")

        val ytDlRequest = if (downloadItem.url.endsWith(".txt")) {
            YTDLRequest(listOf()).apply {
                request.addOption("-a", downloadItem.url)
            }
        }else if (downloadItem.playlistURL.isNullOrBlank() || downloadItem.playlistTitle.isBlank() || useItemURL){
            if (downloadItem.url.isBlank()) {
                YTDLRequest(listOf())
            }else{
                YTDLRequest(downloadItem.url)
            }
        }else{
            isPlaylistItem = true
            YTDLRequest(downloadItem.playlistURL!!).apply {
                val ytVideoID = downloadItem.url.getIDFromYoutubeURL()
                if(downloadItem.playlistIndex == null || (downloadItem.url.isYoutubeURL() && ytVideoID != null)){
                    request.addOption("--match-filter", "id~='${ytVideoID}'")
                }else{
                    request.addOption("-I", "${downloadItem.playlistIndex!!}")
                }
            }
        }

        request.addOption("--newline")
        request.addOption("--plugin-dirs", FileUtil.getBundledYTDLPPluginsPath(context))

        val metadataCommands = StringJoiner(" ")

        if (downloadItem.playlistIndex != null && useItemURL) {
            metadataCommands.addOption("--parse-metadata", " ${downloadItem.playlistIndex}: %(playlist_index)s")
        }

        if (downloadItem.playlistTitle.isNotBlank() && useItemURL) {
            metadataCommands.addOption("--parse-metadata", " :%(playlist_title)s")
            metadataCommands.addOption("--parse-metadata", "%(playlist_title)s:%(playlist)s")
            metadataCommands.addOption("--replace-in-metadata", "playlist,playlist_title", "^.*$", downloadItem.playlistTitle)
        }

        if (downloadItem.playlistIndex != null) {
            metadataCommands.addOption("--parse-metadata", "%(playlist_index)s:playlist_index")
        }

        downloadItem.rowNumber.apply {
            if (this > 0) {
                request.addOption("--parse-metadata", " ${downloadItem.rowNumber}: %(rownumber)s")
            }
        }

        val type = downloadItem.type

        val targetDir = File(FileUtil.formatPath(downloadItem.downloadPath))
        val cacheDownloads = sharedPreferences.getBoolean("cache_downloads", true)
        val writtenPath = type == DownloadType.command && downloadItem.format.format_note.contains("-P ")

        val writeDirectly = !downloadItem.vaultMode && (writtenPath || (!cacheDownloads && targetDir.canWrite()))
        val downDir: File
        if (downloadItem.vaultMode) {
            downDir = com.deniscerri.ytdl.vault.VaultStorage.tempDirForJob(context, downloadItem.vaultJobUuid).apply {
                mkdirs()
            }
        } else if (writeDirectly) {
            downDir = targetDir
            request.addOption("--no-quiet")
            request.addOption("--no-simulate")
            request.addOption("--print", "after_move:'%(filepath,_filename)s'")
        } else {
            downDir = File(FileUtil.getCacheDownloadsPath(context), downloadItem.id.toString()).apply {
                deleteRecursively()
                mkdirs()
            }
        }

        val aria2 = sharedPreferences.getBoolean("aria2", false)
        if (aria2) {
            ytDlRequest.addOption("--downloader", "libaria2c.so")
            //request.addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")
            //ytDlRequest.addOption("--no-check-certificates")
            //request.addOption("--external-downloader-args", "aria2c:\"--check-certificate=false\"")
        }

        val concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1)
        if (concurrentFragments > 1) request.addOption("-N", concurrentFragments)

        val retries = sharedPreferences.getString("retries", "")!!
        val fragmentRetries = sharedPreferences.getString("fragment_retries", "")!!

        if(retries.isNotEmpty()) request.addOption("--retries", retries)
        if(fragmentRetries.isNotEmpty()) request.addOption("--fragment-retries", fragmentRetries)

        val limitRate = sharedPreferences.getString("limit_rate", "")!!
        if (limitRate.isNotBlank()) request.addOption("-r", limitRate)

        val bufferSize = sharedPreferences.getString("buffer_size", "")!!
        if (bufferSize.isNotBlank()){
            request.addOption("--buffer-size", bufferSize)
            request.addOption("--no-resize-buffer")
        }

        val socketTimeout = sharedPreferences.getString("socket_timeout", "")!!
        if (socketTimeout.isNotBlank()) {
            request.addOption("--socket-timeout", socketTimeout)
        }

        if (sharedPreferences.getBoolean("restrict_filenames", true)) request.addOption("--restrict-filenames")
        if (sharedPreferences.getBoolean("force_ipv4", false)){
            request.addOption("-4")
        }
        if (sharedPreferences.getBoolean("use_cookies", false)){
            FileUtil.getCookieFile(context, true){
                // Downloads create a per-job snapshot before the request is built. When it is
                // present, prefer it over the shared cookies.txt file. The live session export
                // lives under auth_cookies/, while the fallback copy lives in cacheDir/.
                val preferredFile =
                    com.deniscerri.ytdl.util.EphemeralCookieExporter.getEphemeralCookieFile(
                        context,
                        downloadItem.id.toString()
                    )
                val fallbackFile = File(context.cacheDir, "cookies_job_${downloadItem.id}.txt")
                val ephemeralFile = when {
                    preferredFile.exists() -> preferredFile
                    fallbackFile.exists() -> fallbackFile
                    else -> null
                }

                if (ephemeralFile != null) {
                    request.addOption("--cookies", ephemeralFile.absolutePath)
                } else {
                    request.addOption("--cookies", it)
                }
            }
        }

        if (sharedPreferences.getBoolean("no_check_certificates", false) && !request.toString().contains("--no-check-certificates")) {
            request.addOption("--no-check-certificates")
        }

        val proxy = sharedPreferences.getString("proxy", "")
        if (proxy!!.isNotBlank()){
            request.addOption("--proxy", proxy)
        }

        val keepCache = sharedPreferences.getBoolean("keep_cache", false)
        if(keepCache && !downloadItem.vaultMode){
            request.addOption("--keep-fragments")
        }

        val embedMetadata = sharedPreferences.getBoolean("embed_metadata", true)
        val thumbnailFormat = sharedPreferences.getString("thumbnail_format", "jpg")
        var filenameTemplate = downloadItem.customFileNameTemplate

        if(downloadItem.type != DownloadType.command){
            if (sharedPreferences.getBoolean("no_part", false)){
                request.addOption("--no-part")
            }

            if (sharedPreferences.getBoolean("trim_filenames", true)) {
                request.addOption("--trim-filenames",  254 - downDir.absolutePath.length)
            }

            if (downloadItem.SaveThumb && !downloadItem.vaultMode) {
                request.addOption("--write-thumbnail")
                request.addOption("--convert-thumbnails", thumbnailFormat!!)
            }
            if (!sharedPreferences.getBoolean("mtime", false)){
                request.addOption("--no-mtime")
            }else{
                request.addOption("--mtime")
            }

            val sponsorBlockFilters : ArrayList<String> = when(downloadItem.type) {
                DownloadType.audio -> {
                    downloadItem.audioPreferences.sponsorBlockFilters
                }
                //video
                else -> {
                    downloadItem.videoPreferences.sponsorBlockFilters
                }
            }


            if (sharedPreferences.getBoolean("use_sponsorblock", true)){
                if (sponsorBlockFilters.isNotEmpty()) {
                    val filters = java.lang.String.join(",", sponsorBlockFilters.filter { it.isNotBlank() })
                    if (filters.isNotBlank()) {
                        request.addOption("--sponsorblock-remove", filters)
                        if (sharedPreferences.getBoolean("force_keyframes", false)){
                            request.addOption("--force-keyframes-at-cuts")
                        }
                    }
                }
            }

            if(downloadItem.title.isNotBlank()){
                metadataCommands.addOption("--replace-in-metadata", "title", """^.*$""", downloadItem.title.replace("""\""", """\\"""))
                metadataCommands.addOption("--parse-metadata", "%(title)s:%(meta_title)s")
            }

            val useArtistTags = if (downloadItem.url.isYoutubeURL()) "artists,artist,creators," else ""
            metadataCommands.addOption("--parse-metadata", """%(${useArtistTags}uploader,channel,creator|)l:^(?P<uploader>.*?)(?:(?= - Topic)|$)""")

            if (downloadItem.author.isNotBlank()){
                metadataCommands.addOption("--replace-in-metadata", "uploader", """^.*$""", downloadItem.author.replace("""\""", """\\"""))
                metadataCommands.addOption("--parse-metadata", "%(uploader)s:%(artist)s")
            }

            if (downloadItem.downloadSections.isNotBlank()){
                val sections = downloadItem.downloadSections.split(";").filter { it.isNotBlank() }
                sections.forEach {
                    request.addOption("--download-sections", "*${it.split(" ")[0]}")
                }

                if (sections.isNotEmpty()) {
                    if (sharedPreferences.getBoolean("force_keyframes", false) && !request.toString().contains("--force-keyframes-at-cuts")){
                        request.addOption("--force-keyframes-at-cuts")
                    }
                }

                if (sections.size > 1){
                    filenameTemplate = "%(autonumber)d. $filenameTemplate [%(section_start>%H∶%M∶%S)s]"
                    if (downloadItem.mergeCuts) {
                        request.addOption("--use-postprocessor", "SectionMerge:when=post_process;count=${sections.size}")
                    }
                }
            }

            if (!downloadItem.vaultMode && sharedPreferences.getBoolean("write_description", false)){
                request.addOption("--write-description")
            }

            if (downloadItem.url.isYoutubeURL()) {
                ytDlRequest.setYoutubeExtractorArgs(
                    downloadItem.url,
                    PoTokenCoordinator.getCachedOrMintOnWorker(context, downloadItem.url)
                )
            }

            /*
            * https://github.com/yt-dlp/yt-dlp/issues/11947
            * Cant use info json when using download sections
            * */
            val canUseWriteInfoJson =
                !downloadItem.vaultMode &&
                !sharedPreferences.getBoolean("disable_write_info_json", false) &&
                !request.toString().contains("--download-sections")

            if (canUseWriteInfoJson && downloadItem.playlistURL.isNullOrBlank()) {
                val infoJsonURL = downloadItem.url
                val infoJsonFile = getInfoJsonFile(infoJsonURL)
                //ignore info file if its older than 5 hours. puny measure to prevent expired formats in some cases
                if (infoJsonFile != null && System.currentTimeMillis() - infoJsonFile.lastModified() <= (1000 * 60 * 60 * 5)) {
                    request.addOption("--load-info-json", infoJsonFile.absolutePath)
                }else {
                    ytDlRequest.addWriteInfoJson(infoJsonURL)
                }
            }

            val subDirectories = sharedPreferences.getStringSet("save_subdirectory", setOf())!!
            if (!downloadItem.vaultMode && subDirectories.contains("website")) {
                metadataCommands.addOption("--parse-metadata", "%(extractor_key,extractor,ie_key)s:^(?P<no_GH5ME>(?!Generic|HTML5MediaEmbed).*)$")
                metadataCommands.addOption("--parse-metadata", "%(no_GH5ME,webpage_url_domain)s:%(fldr_website)s")
            }
        }

        if (!downloadItem.vaultMode && sharedPreferences.getString("prevent_duplicate_downloads", "")!! == "download_archive"){
            request.addOption("--download-archive", FileUtil.getDownloadArchivePath(context))
        }

        val internalPluginFFmpegPreset = sharedPreferences.getString("internal_plugin_ffmpeg_preset", "")

        val preferredAudioCodec = sharedPreferences.getString("audio_codec", "")!!
        val aCodecPrefIndex = context.getStringArray(R.array.audio_codec_values).indexOf(preferredAudioCodec)
        var aCodecPref = runCatching { context.getStringArray(R.array.audio_codec_values_ytdlp)[aCodecPrefIndex] }.getOrElse { "" }

        when(type){
            DownloadType.audio -> {
                val supportedContainers = context.resources.getStringArray(R.array.audio_containers)
                val preferredLanguage = sharedPreferences.getString("audio_language","")!!
                val formatImportance = formatUtil.getAudioFormatImportance(false)

                var abrSort = ""

                var audioQualityId : String = downloadItem.format.format_id
                if (audioQualityId.isBlank() || listOf("0", context.getString(R.string.best_quality), "ba", "best", "", context.getString(
                        R.string.worst_quality), "wa", "worst").contains(audioQualityId)){
                    audioQualityId = "ba/b"
                }else if(audioQualityId.contains("kbps_ytdlnisgeneric")){
                    abrSort = audioQualityId.split("kbps")[0]
                    audioQualityId = ""
                }else{
                    audioQualityId += "/ba/b"
                }

                if ((audioQualityId.isBlank() || audioQualityId == "ba/b") && formatImportance.contains("prefer_drc")) {
                    audioQualityId = "ba[format_id$=-drc]/${audioQualityId.ifEmpty { "ba/b" }}"
                }

                if ((audioQualityId.isBlank() || audioQualityId == "ba/b") && preferredLanguage.isNotBlank()) {
                    audioQualityId = "ba[language^=$preferredLanguage]/${audioQualityId.ifEmpty { "ba/b" }}"
                }

                if (audioQualityId.isNotBlank()) {
                    if (audioQualityId.matches(".*-[0-9]+.*".toRegex())) {
                        audioQualityId = if(!downloadItem.format.lang.isNullOrBlank() && downloadItem.format.lang != "None"){
                            "ba[format_id~='^(${audioQualityId.split("-")[0]})'][language^=${downloadItem.format.lang}]/ba/b"
                        }else{
                            "$audioQualityId/${audioQualityId.split("-")[0]}"
                        }
                    }

                    request.addOption("-f", audioQualityId)
                }

                request.addOption("-x")
                val ext = downloadItem.container

                val formatSorting = mutableListOf<String>()
                val useCustomFormatSorting = sharedPreferences.getBoolean("use_format_sorting", false)
                if (useCustomFormatSorting) {
                    formatSorting.add("hasaud")
                }

                for(order in formatImportance) {
                    when(order) {
                        "smallsize" -> {
                            formatSorting.add("+size")
                        }
                        "file_size" -> {
                            formatSorting.add("size")
                        }
//                        "language" -> {
//                            if (preferredLanguage.isNotBlank()) {
//                                formatSorting.add("lang:${preferredLanguage}")
//                            }
//                        }
                        "codec" -> {
                            if (aCodecPref.isNotBlank()){
                                formatSorting.add("acodec:$aCodecPref")
                            }
                        }
                        "container" -> {
                            if(ext.isNotBlank()){
                                if(!ext.matches("(webm)|(Default)|(${context.getString(R.string.defaultValue)})".toRegex()) && supportedContainers.contains(ext)){
                                    request.addOption("--audio-format", ext)
                                    formatSorting.add("aext:$ext")
                                }
                            }
                        }
                    }
                }

                if (downloadItem.format.format_id == context.resources.getString(R.string.worst_quality) || downloadItem.format.format_id == "wa" || downloadItem.format.format_id == "worst") {
                    formatSorting.remove("size")
                    formatSorting.remove("+size")
                    formatSorting.addAll(0,listOf("+abr"))
                }

                if (abrSort.isNotBlank()){
                    formatSorting.add(0, "abr:${abrSort}")
                }

                if (downloadItem.downloadSections.isNotBlank()) {
                    formatSorting.add(0, "proto:https")
                }

                if(formatSorting.isNotEmpty()) {
                    request.addOption("-S", formatSorting.joinToString(","))
                }

                if (downloadItem.vaultMode) {
                    request.addOption("-P", "home:${downDir.absolutePath}")
                    request.addOption("-P", "temp:${downDir.absolutePath}")
                } else {
                    request.addOption("-P", downDir.absolutePath)
                }

                if (!downloadItem.vaultMode && downloadItem.audioPreferences.splitByChapters && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_number)d - %(section_title)s.%(ext)s")
                }else{
                    if (embedMetadata){
                        metadataCommands.addOption("--embed-metadata")

                        val emptyAuthor = downloadItem.author.isEmpty()
                        val usePlaylistMetadata = sharedPreferences.getBoolean("playlist_as_album", true)

                        if (emptyAuthor) {
                            if (usePlaylistMetadata) {
                                metadataCommands.addOption("--parse-metadata", "%(playlist_uploader,artist,uploader|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }else{
                                metadataCommands.addOption("--parse-metadata", "%(artist,uploader|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }
                        }else{
                            if (usePlaylistMetadata) {
                                metadataCommands.addOption("--parse-metadata", "%(playlist_uploader,artist|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }else{
                                metadataCommands.addOption("--parse-metadata", "%(artist|)s:^(?P<first_artist>.*?)(?:(?=,\\s+)|$)")
                            }
                        }

                        if (usePlaylistMetadata) {
                            metadataCommands.addOption("--parse-metadata", "%(album,playlist_title,playlist|)s:%(meta_album)s")
                        }


                        metadataCommands.addOption("--parse-metadata", "%(album_artist,first_artist|)s:%(album_artist)s")
                        metadataCommands.addOption("--parse-metadata", "%(release_year,release_date>%Y,upload_date>%Y)s:(?P<meta_date>\\d+)")

                        if (isPlaylistItem) {
                            metadataCommands.addOption("--parse-metadata", "%(track_number,playlist_index)d:(?P<track_number>\\d+)")
                        }
                    }

                    val cropThumb = downloadItem.audioPreferences.cropThumb ?: sharedPreferences.getBoolean("crop_thumbnail", true)
                    if (downloadItem.audioPreferences.embedThumb){
                        metadataCommands.addOption("--embed-thumbnail")
                        if (!request.toString().contains("--convert-thumbnails")) metadataCommands.addOption("--convert-thumbnails", thumbnailFormat!!)

                        val thumbnailConfig = StringBuilder("")
                        val cropConfig = """-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"""""
                        if (thumbnailFormat == "jpg")  thumbnailConfig.append("""--ppa "ThumbnailsConvertor:-qmin 1 -q:v 1"""")
                        if (cropThumb){
                            if (thumbnailFormat == "jpg") {
                                thumbnailConfig.deleteCharAt(thumbnailConfig.length - 1)
                                thumbnailConfig.append(""" $cropConfig""")
                            }
                            else thumbnailConfig.append("""--ppa "ThumbnailsConvertor:$cropConfig""")
                        }

                        if (thumbnailConfig.isNotBlank()){
                            request.addOption(thumbnailConfig.toString())
                        }

                    }

                    if (downloadItem.vaultMode) {
                        request.addOption("-o", "${downloadItem.vaultJobUuid}.%(ext)s")
                    } else {
                        if (filenameTemplate.isNotBlank()){
                            request.addOption("-o", "${filenameTemplate.removeSuffix(".%(ext)s")}.%(ext)s")
                        }

                        //lyrics timing and duration only make sense for the full track
                        val audioPrefs = downloadItem.audioPreferences
                        if ((audioPrefs.embedLyrics || audioPrefs.saveLrc) && downloadItem.downloadSections.isBlank()) {
                            val args = mutableListOf(
                                //after_move so it runs once audio extraction, metadata and thumbnail are done
                                "when=after_move",
                                "embed=${if (audioPrefs.embedLyrics) 1 else 0}",
                                "ttml=${if (audioPrefs.embedTtml) 1 else 0}",
                                "lrc=${if (audioPrefs.saveLrc) 1 else 0}",
                                "offset=${audioPrefs.lyricsOffset}"
                            )
                            if (audioPrefs.customLyricsFile.isNotBlank()) {
                                args.add("file=${audioPrefs.customLyricsFile}")
                            } else if (audioPrefs.lyricsId > 0) {
                                args.add("id=${audioPrefs.lyricsId}")
                            }
                            request.addOption("--use-postprocessor", "Lyrics:${args.joinToString(";")}")
                        }
                    }
                }

                if (downloadItem.audioPreferences.bitrate.isNotBlank()) {
                    request.addOption("--audio-quality", downloadItem.audioPreferences.bitrate)
                }

            }
            DownloadType.video -> {
                val supportedContainers = context.resources.getStringArray(R.array.video_containers)

                if (downloadItem.videoPreferences.addChapters && downloadItem.downloadSections.isBlank()) {
                    if (sharedPreferences.getBoolean("use_sponsorblock", true)){
                        request.addOption("--sponsorblock-mark", "all")
                    }
                    request.addOption("--embed-chapters")
                }

                if (embedMetadata) {
                    metadataCommands.add("--embed-metadata")
                }

                if (!downloadItem.vaultMode && downloadItem.videoPreferences.writeSubs){
                    request.addOption("--write-subs")
                }

                if(!downloadItem.vaultMode && downloadItem.videoPreferences.writeAutoSubs){
                    request.addOption("--write-auto-subs")
                }

                if (downloadItem.videoPreferences.embedSubs) {
                    if (downloadItem.vaultMode || (sharedPreferences.getBoolean("no_keep_subs", false) && (downloadItem.videoPreferences.writeSubs || downloadItem.videoPreferences.writeAutoSubs))) {
                        request.addOption("--compat-options", "no-keep-subs")
                    }

                    request.addOption("--embed-subs")
                }

                if (downloadItem.videoPreferences.embedSubs || (!downloadItem.vaultMode && (downloadItem.videoPreferences.writeSubs || downloadItem.videoPreferences.writeAutoSubs))){
                    val subFormat = sharedPreferences.getString("sub_format", "")
                    if(subFormat!!.isNotBlank()){
                        request.addOption("--sub-format", "${subFormat}/best")
                        request.addOption("--convert-subtitles", subFormat)
                    }
                    request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages.ifEmpty { ".*-orig" })
                }

                if (downloadItem.videoPreferences.burnSubs) {
                    request.addOption("--use-postprocessor", "BurnSubs:preset=$internalPluginFFmpegPreset")
                }

                var cont = ""
                val outputContainer = downloadItem.container

                if(
                    outputContainer.isNotEmpty() &&
                    outputContainer != "Default" &&
                    outputContainer != context.getString(R.string.defaultValue) &&
                    supportedContainers.contains(outputContainer)
                ){
                    cont = outputContainer

                    val cantRecode = listOf("avi")
                    if (downloadItem.videoPreferences.recodeVideo && !cantRecode.contains(cont)) {
                        request.addOption("--recode-video", outputContainer.lowercase())
                    }else if (!downloadItem.videoPreferences.compatibilityMode){
                        request.addOption("--merge-output-format", outputContainer.lowercase())
                    }

                    if (!listOf("webm", "avi", "flv", "gif").contains(outputContainer.lowercase())) {
                        if (downloadItem.videoPreferences.embedThumbnail) {
                            metadataCommands.addOption("--embed-thumbnail")
                            if (!request.toString().contains("--convert-thumbnails")) request.addOption("--convert-thumbnails", thumbnailFormat!!)
                        }
                    }
                }
                var acont = sharedPreferences.getString("audio_format", "")!!
                if (acont == "Default") acont = ""

                //format logic
                var videoF = downloadItem.format.format_id
                var altAudioF = ""
                var audioF = downloadItem.videoPreferences.audioFormatIDs.joinToString("+") { f ->
                    val format = downloadItem.allFormats.find { it.format_id == f }
                    format?.run {
                        if (this.format_id.matches(".*-[0-9]+".toRegex())) {
                            if (!this.lang.isNullOrBlank() && this.lang != "None") {
                                "ba[format_id~='^(${this.format_id.split("-")[0]})'][language^=${this.lang}]"
                            } else {
                                altAudioF = this.format_id.split("-")[0]
                                this.format_id
                            }
                        } else {
                            this.format_id
                        }
                    } ?: if (f == "wa") "" else f
                }.ifBlank { "ba" }
                val preferredAudioLanguage = sharedPreferences.getString("audio_language", "")!!
                val preferDRCAudio = sharedPreferences.getBoolean("prefer_drc_audio", false)
                val usingWorstAudio = downloadItem.videoPreferences.audioFormatIDs.any { it == "wa" }

                if (downloadItem.videoPreferences.removeAudio) audioF = ""

                var abrSort = ""
                if(audioF.contains("kbps_ytdlnisgeneric")){
                    abrSort = audioF.split("kbps")[0]
                    audioF = ""
                }

                val f = StringBuilder()

                val preferredCodec = sharedPreferences.getString("video_codec", "")
                val vCodecPrefIndex = context.getStringArray(R.array.video_codec_values).indexOf(preferredCodec)
                var vCodecPref = context.getStringArray(R.array.video_codec_values_ytdlp)[vCodecPrefIndex]

                if (downloadItem.videoPreferences.compatibilityMode) {
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("--remux-video", "mp4")
                    request.addOption("--use-postprocessor", "CompatibleRecoder:preset=$internalPluginFFmpegPreset")
                    vCodecPref = "h264"
                    aCodecPref = "aac"
                }

                val defaultFormats = context.resources.getStringArray(R.array.video_formats_values)
                val usingGenericFormat = defaultFormats.contains(videoF) || downloadItem.allFormats.isEmpty() || downloadItem.allFormats == formatUtil.getGenericVideoFormats(context.resources)
                var hasGenericResulutionFormat = ""
                if (!usingGenericFormat){
                    // with audio removed
                    if (audioF.isBlank()){
                        f.append("$videoF/bv/b")
                    }else{
                        //with audio
                        f.append("$videoF+$audioF/")
                        if (altAudioF.isNotBlank()){
                            f.append("$videoF+$altAudioF/")
                        }
                        if (!f.contains("$videoF+ba")) {
                            if (preferredAudioLanguage.isNotEmpty()) {
                                f.append("$videoF+ba[language^=$preferredAudioLanguage]/")
                            }else if (preferDRCAudio) {
                                f.append("$videoF+ba[format_id$=-drc]/")
                            }else {
                                f.append("$videoF+ba/")
                            }
                        }
                        f.append("$videoF/b")


                        if (audioF.count("+") > 0){
                            request.addOption("--audio-multistreams")
                        }
                    }
                }else{
                    if (videoF == context.resources.getString(R.string.best_quality) || videoF == "best" || videoF == context.resources.getString(
                            R.string.worst_quality) || videoF == "worst") {
                        videoF = "bv"
                    }else if (defaultFormats.contains(videoF)) {
                        hasGenericResulutionFormat = videoF.split("_")[0].dropLast(1)
                        videoF = "bv"
                    }

                    val preferredFormatIDs = sharedPreferences.getString("format_id", "").toString()
                        .split(",")
                        .filter { it.isNotEmpty() }
                        .ifEmpty { listOf(videoF) }.toMutableList()
                    if (!preferredFormatIDs.contains(videoF)){
                        preferredFormatIDs.add(0, videoF)
                    }
                    // ^ [videoF, preferredID1, preferredID2...]

                    val preferredAudioFormatIDs = sharedPreferences.getString("format_id_audio", "")
                        .toString()
                        .split(",")
                        .filter { it.isNotBlank() }
                        .ifEmpty {
                            val list = mutableListOf<String>()
                            if (preferredAudioLanguage.isNotEmpty() && !downloadItem.videoPreferences.removeAudio) list.add("ba[language^=$preferredAudioLanguage]")
                            if (preferDRCAudio) list.add("ba[format_id$=-drc]")
                            list
                        }.apply {

                        }.toMutableList()
                    if (!preferredAudioFormatIDs.contains(audioF) && audioF != "ba"){
                        preferredAudioFormatIDs.add(0, audioF)
                    }

                    if (preferredAudioFormatIDs.contains("wa")) {
                        preferredFormatIDs.remove("wa")
                    }

                    // ^ [audioF, preferredAID1, preferredAID2, ....]
                    if (preferredAudioFormatIDs.any{it.contains("+")}){
                        request.addOption("--audio-multistreams")
                    }


                    preferredFormatIDs.forEach { v ->
                        preferredAudioFormatIDs.filter { it.isNotBlank() }.forEach { a ->
                            f.append("$v+$a/")
                        }
                        //build format with just videoformatid and audio if remove audio is not checked
                        if (!downloadItem.videoPreferences.removeAudio){
                            //build format with audio with preferred language
                            if (preferredAudioLanguage.isNotBlank()){
                                val al = if (v == "wv"){
                                    "$v+wa[language^=$preferredAudioLanguage]/"
                                }else{
                                    "$v+ba[language^=$preferredAudioLanguage]/"
                                }
                                if (!f.contains(al)) f.append(al)
                            }
                            //build format with best audio
                            if (!f.contains("$v+ba/") && !f.contains("wa")) f.append("$v+ba/")
                            //build format with standalone video
                            if (v == "wv"){
                                f.append("w/")
                            }else{
                                if (v != "bv") f.append("$v/")
                            }
                        }
                    }

                    if(!f.endsWith("b/")){
                        //last fallback
                        if (f.isBlank()) {
                            f.append("bv/")
                        }

                        f.append("b")
                    }

                }

                val formatImportance = formatUtil.getVideoFormatImportance().toMutableList()
                val formatSorting = mutableListOf<String>()

                for(order in formatImportance) {
                    when(order) {
                        "smallsize" -> {
                            formatSorting.add("+size")
                        }
                        "file_size" -> {
                            formatSorting.add("size")
                        }
                        "no_audio" -> {
                            formatSorting.add("+hasaud")
                        }
                        "codec" -> {
                            if (vCodecPref.isNotBlank()) formatSorting.add("vcodec:$vCodecPref")
                            if (aCodecPref.isNotBlank()) formatSorting.add("acodec:$aCodecPref")
                        }
                        "resolution" -> {
                            if (hasGenericResulutionFormat.isNotBlank()) {
                                formatSorting.add("res:${hasGenericResulutionFormat}")
                            }
                        }
                        "container" -> {
                            if (!downloadItem.videoPreferences.compatibilityMode) {
                                if (cont.isNotBlank()) formatSorting.add("vext:$cont")
                                if (acont.isNotBlank()) formatSorting.add("aext:$acont")
                            }
                        }
                    }
                }

                if (downloadItem.format.format_id == context.resources.getString(R.string.worst_quality) || downloadItem.format.format_id == "worst") {
                    formatSorting.remove("+size")
                    formatSorting.remove("size")
                    formatSorting.addAll(0, listOf("+br","+res","+fps"))
                }

                if (abrSort.isNotBlank()) {
                    formatSorting.add("abr:${abrSort}")
                } else if (usingWorstAudio) {
                    formatSorting.add("+abr")
                }

//                if (preferredLanguage.isNotBlank()) {
//                    formatSorting.add("lang:${preferredLanguage}")
//                }

                if (downloadItem.downloadSections.isNotBlank()) {
                    formatSorting.add(0, "proto:https")
                }

                if (formatSorting.isNotEmpty()) {
                    request.addOption("-S", formatSorting.joinToString(","))
                }

                request.addOption("-f", f.toString().replace("/$".toRegex(), ""))

                var copyStream = ""

                if (downloadItem.videoPreferences.cropValues.isNotBlank()){
                    val parts = downloadItem.videoPreferences.cropValues.split(":")
                    if (parts.size >= 6) {
                        val x = parts[0].toIntOrNull() ?: 0
                        val y = parts[1].toIntOrNull() ?: 0
                        val w = parts[2].toIntOrNull() ?: 0
                        val h = parts[3].toIntOrNull() ?: 0
                        val refW = parts[4].toIntOrNull() ?: 0
                        val refH = parts[5].toIntOrNull() ?: 0

                        if (w > 0 && h > 0) {
                            val setting = when {
                                downloadItem.format.vcodec.contains("vp9", true) -> "-c:v libvpx-vp9"
                                downloadItem.format.vcodec.contains("av1", true) -> "-c:v libaom-av1"
                                downloadItem.format.vcodec.contains("hevc", true) -> "-c:v libx265 -preset veryfast"
                                else -> "-c:v libx264 -preset veryfast"
                            }

                            copyStream = "$setting -vf crop=($w/$refW)*iw:($h/$refH)*ih:($x/$refW)*iw:($y/$refH)*ih"
                        }
                    }
                }

                if (downloadItem.videoPreferences.removeAudio && outputContainer != "gif") {
                    if (copyStream.isBlank()) {
                        copyStream = "-c copy"
                    }
                    copyStream = "$copyStream -an"
                }

                if (copyStream.isNotBlank()) {
                    request.addOption("--use-postprocessor", "FFmpegCopyStream")
                    request.addOption("--ppa", "CopyStream:$copyStream")
                }

                if (downloadItem.vaultMode) {
                    request.addOption("-P", "home:${downDir.absolutePath}")
                    request.addOption("-P", "temp:${downDir.absolutePath}")
                } else {
                    request.addOption("-P", downDir.absolutePath)
                }

                if (!downloadItem.vaultMode && downloadItem.videoPreferences.splitByChapters  && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-o", "chapter:%(section_number)d - %(section_title)s.%(ext)s")
                }else{
                    if (downloadItem.vaultMode) {
                        request.addOption("-o", "${downloadItem.vaultJobUuid}.%(ext)s")
                    } else if (filenameTemplate.isNotBlank()){
                        request.addOption("-o", "${filenameTemplate.removeSuffix(".%(ext)s")}.%(ext)s")
                    }
                }

                if (downloadItem.videoPreferences.liveFromStart) {
                    request.addOption("--live-from-start")
                }

                downloadItem.videoPreferences.waitForVideoMinutes.apply {
                    if (this > 0) {
                        request.addOption("--wait-for-video", this * 60)
                    }
                }

            }
            DownloadType.command -> {
                if (downloadItem.vaultMode) {
                    request.addOption("-P", "home:${downDir.absolutePath}")
                    request.addOption("-P", "temp:${downDir.absolutePath}")
                    request.addOption("-o", "${downloadItem.vaultJobUuid}.%(ext)s")
                } else if (!writtenPath) {
                    request.addOption("-P", downDir.absolutePath)
                }

                request.addOption(downloadItem.format.format_note)
            }

            else -> {}
        }

        request.merge(metadataCommands)

        // Vault jobs must be fully controlled by the app: user-supplied extraCommands could
        // re-enable `-P`/`-o`/`--write-*` plaintext side outputs outside vault_tmp.
        if (downloadItem.extraCommands.isNotBlank() && !downloadItem.vaultMode && downloadItem.type != DownloadType.command){
            request.addOption(downloadItem.extraCommands)
        }

        if (request.toString().contains("sponsorblock")) {
            val sponsorBlockURL = sharedPreferences.getString("sponsorblock_url", "")!!
            if (sponsorBlockURL.isNotBlank()) request.addOption("--sponsorblock-api", sponsorBlockURL)
        }

        val cache = if (downloadItem.vaultMode) {
            downDir
        } else {
            File(FileUtil.getCacheDownloadsPath(context))
        }
        cache.mkdirs()
        val conf = File(cache.absolutePath + "/${System.currentTimeMillis()}${UUID.randomUUID()}.txt")
        conf.createNewFile()
        conf.writeText(request.toString())
        val tmp = mutableListOf<String>()
        tmp.addOption("--config-locations", conf.absolutePath)
        ytDlRequest.addCommands(tmp)

        if (downloadItem.vaultMode) {
            ytDlRequest.addOption("--no-cache-dir")
        } else {
            val ytdlpCache = File(FileUtil.getCacheYTDLPPath(context))
            ytDlRequest.addOption("--cache-dir", ytdlpCache.absolutePath)
        }
        return ytDlRequest
    }
}