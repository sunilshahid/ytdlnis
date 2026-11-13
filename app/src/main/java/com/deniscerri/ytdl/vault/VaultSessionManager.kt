package com.deniscerri.ytdl.vault

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * Volatile in-memory session state for the unlocked Private Vault.
 *
 * Key material never touches Disk, SharedPreferences, Room, or String pools, and is
 * synchronously zeroed with `fill(0)` on every exit trigger (`Back`, `Home`, `Recents`,
 * `Power/Screen-Off`, `onPause()`, `onTrimMemory`, and `CrashListener`).
 */
object VaultSessionManager {

    private val lock = Any()

    @Volatile
    private var vaultKeyBytes: ByteArray? = null

    @Volatile
    var isDecoySession: Boolean = false
        private set

    @Volatile
    private var activePlayer: ExoPlayer? = null

    private val decryptedBitmaps =
        Collections.synchronizedList(mutableListOf<WeakReference<Bitmap>>())

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun unlockWithKey(dek: ByteArray, decoy: Boolean = false) {
        synchronized(lock) {
            vaultKeyBytes?.fill(0)
            vaultKeyBytes = dek.copyOf()
            dek.fill(0)
            isDecoySession = decoy
            _isUnlocked.value = true
        }
    }

    fun getKeyCopyOrNull(): ByteArray? {
        synchronized(lock) {
            val k = vaultKeyBytes ?: return null
            if (!_isUnlocked.value) return null
            return k.copyOf()
        }
    }

    fun requireKeyCopy(): ByteArray =
        getKeyCopyOrNull() ?: throw SecurityException("Private Vault is locked")

    fun registerBitmap(bitmap: Bitmap?) {
        if (bitmap == null) return
        decryptedBitmaps.add(WeakReference(bitmap))
    }

    fun registerPlayer(player: ExoPlayer?) {
        synchronized(lock) {
            activePlayer = player
        }
    }

    /**
     * Synchronously stops any vault player, recycles decrypted bitmaps, zeroes all key bytes,
     * and marks the vault locked. Safe to call from any thread or crash handler.
     */
    fun lockAndZeroize() {
        val playerToRelease: ExoPlayer?
        val isOnMainThread = Looper.myLooper() == Looper.getMainLooper()
        synchronized(lock) {
            _isUnlocked.value = false
            isDecoySession = false
            vaultKeyBytes?.fill(0)
            vaultKeyBytes = null
            playerToRelease = activePlayer
            // Field is cleared synchronously so a racing registerPlayer() can never resurrect it.
            activePlayer = null
        }

        if (playerToRelease != null) {
            val player = playerToRelease
            if (isOnMainThread) {
                releasePlayer(player)
            } else {
                // ExoPlayer may only be touched on the main thread. Crash handlers and
                // onTrimMemory can reach us from a background thread, so defer only the player
                // release to the main looper; all key material above was zeroed synchronously.
                Handler(Looper.getMainLooper()).post {
                    releasePlayer(player)
                }
            }
        }

        synchronized(decryptedBitmaps) {
            for (ref in decryptedBitmaps) {
                runCatching {
                    val bmp = ref.get()
                    if (bmp != null && !bmp.isRecycled) {
                        bmp.recycle()
                    }
                }
            }
            decryptedBitmaps.clear()
        }
    }

    private fun releasePlayer(player: ExoPlayer) {
        runCatching {
            player.playWhenReady = false
            player.volume = 0f
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }
}
