package com.deniscerri.ytdl.ui.play.lyrics

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.lyrics.LyricsDocument
import com.deniscerri.ytdl.lyrics.LyricsRepository
import com.deniscerri.ytdl.lyrics.model.LyricsCandidate
import com.deniscerri.ytdl.lyrics.model.LyricsQuery
import com.deniscerri.ytdl.playback.model.PlaybackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class LyricsLoadStatus {
    IDLE,
    LOADING_LOCAL,
    LOADING_NETWORK,
    READY,
    NOT_FOUND
}

data class LyricsUiState(
    val item: PlaybackItem? = null,
    val document: LyricsDocument? = null,
    val status: LyricsLoadStatus = LyricsLoadStatus.IDLE
)

/**
 * Single owner of the currently displayed lyrics.
 *
 * The screen and the sheet both observe this view model, so a document loaded from embedded tags,
 * a sidecar, the cache or a manual import is shared without parsing it twice. Opening the sheet
 * never touches the network: online lookup only happens after an explicit search action.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LyricsRepository(application)
    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var current: PlaybackItem? = null
    private var durationMs = 0L
    private var loadJob: Job? = null
    private var applyJob: Job? = null

    fun onItemChanged(item: PlaybackItem?, durationMs: Long) {
        if (durationMs > 0L) this.durationMs = durationMs
        if (item == null) {
            if (current != null) {
                current = null
                loadJob?.cancel()
                applyJob?.cancel()
                _state.value = LyricsUiState()
            }
            return
        }
        if (item.mediaId == current?.mediaId) return
        current = item
        loadJob?.cancel()
        applyJob?.cancel()
        _state.value = LyricsUiState(item = item, status = LyricsLoadStatus.LOADING_LOCAL)
        loadLocal(item)
    }

    private fun loadLocal(item: PlaybackItem) {
        loadJob = viewModelScope.launch {
            val document = repository.load(
                item = item,
                durationMs = durationMs,
                forceRefresh = false,
                allowNetwork = false
            )
            if (current?.mediaId != item.mediaId) return@launch
            if (document != null && document.lines.isNotEmpty()) {
                _state.value = LyricsUiState(item, document, LyricsLoadStatus.READY)
            } else {
                _state.value = LyricsUiState(item, null, LyricsLoadStatus.NOT_FOUND)
            }
        }
    }

    /** Explicit online lookup. Never called when the sheet is merely opened. */
    fun searchOnline() {
        val item = current ?: return
        val previous = _state.value.document
        loadJob?.cancel()
        _state.update { it.copy(status = LyricsLoadStatus.LOADING_NETWORK) }
        loadJob = viewModelScope.launch {
            val document = repository.load(
                item = item,
                durationMs = durationMs,
                forceRefresh = true,
                allowNetwork = true
            )
            if (current?.mediaId != item.mediaId) return@launch
            if (document != null && document.lines.isNotEmpty()) {
                _state.value = LyricsUiState(item, document, LyricsLoadStatus.READY)
            } else {
                if (previous != null) {
                    _state.value = LyricsUiState(item, previous, LyricsLoadStatus.READY)
                } else {
                    _state.update { it.copy(status = LyricsLoadStatus.NOT_FOUND) }
                }
                _messages.emit(R.string.lyrics_not_found)
            }
        }
    }

    suspend fun findCandidates(customTitle: String? = null, customArtist: String? = null): List<LyricsCandidate> {
        val item = current ?: return emptyList()
        val query = LyricsQuery(
            mediaId = item.mediaId,
            title = customTitle?.takeIf { it.isNotBlank() } ?: item.title,
            artist = customArtist?.takeIf { it.isNotBlank() } ?: item.artist,
            album = item.album,
            durationMs = durationMs,
            localUri = item.localUri
        )
        return repository.searchCandidates(query)
    }

    fun applyCandidate(candidate: LyricsCandidate) {
        val item = current ?: return
        loadJob?.cancel()
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            val document = repository.applyCandidate(item, candidate, durationMs)
            if (document != null && document.lines.isNotEmpty()) {
                if (current?.mediaId != item.mediaId) return@launch
                _state.value = LyricsUiState(item, document, LyricsLoadStatus.READY)
                _messages.emit(R.string.lyrics_imported)
            } else {
                if (current?.mediaId != item.mediaId) return@launch
                _messages.emit(R.string.lyrics_not_found)
            }
        }
    }

    fun importLyrics(uri: Uri) {
        val item = current ?: return
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { readText(uri) }
            if (raw.isNullOrBlank()) {
                _messages.emit(R.string.lyrics_import_failed)
                return@launch
            }
            val document = repository.saveUserLyrics(item, raw, durationMs)
            if (document == null || document.lines.isEmpty()) {
                _messages.emit(R.string.lyrics_import_failed)
                return@launch
            }
            if (current?.mediaId != item.mediaId) return@launch
            _state.value = LyricsUiState(item, document, LyricsLoadStatus.READY)
            _messages.emit(R.string.lyrics_imported)
        }
    }

    fun changeOffset(deltaMs: Long) {
        val ui = _state.value
        val document = ui.document ?: return
        val updated = document.withOffset(
            (document.offsetMs + deltaMs).coerceIn(-60_000L, 60_000L)
        )
        _state.value = ui.copy(document = updated)
        viewModelScope.launch { repository.updateOffset(document.mediaId, updated.offsetMs) }
    }

    fun resetOffset() {
        val document = _state.value.document ?: return
        if (document.offsetMs == 0L) return
        changeOffset(-document.offsetMs)
    }

    private fun readText(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_LYRICS_BYTES) return@use null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }.getOrNull() ?: return null
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val MAX_LYRICS_BYTES = 2 * 1024 * 1024
    }
}
