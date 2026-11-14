# YTDLnis Extended v1.9.1

**YTDLnis Extended** is an enhanced, privacy-focused media suite and downloader built on top of [YTDLnis](https://github.com/deniscerri/ytdlnis) by Denis Çerri.

---

### 📦 Standalone Co-Installation
* **Package Name:** `com.deniscerri.ytdl.extended`
* **App Label:** `YTDLnis Extended`
* Can be installed **side-by-side** with official YTDLnis (`com.deniscerri.ytdl`) without replacing or overwriting your existing settings, downloads, or configurations.

---

### 🌟 Key Highlights & New Features

#### 🔐 1. Zero-Knowledge Encrypted Private Vault
* **Direct-to-Vault Downloads:** Download streams directly into an encrypted `.vault` container on-the-fly without plaintext ever touching disk.
* **Hybrid Cryptography:** AES-256-GCM chunked file cipher paired with RSA-2048 OAEP asymmetric public-key sealing for locked background downloads.
* **Hardware Biometrics:** Secure biometric unlock with Android Keystore-backed `CryptoObject`.
* **Anti-Forensics Protection:** Decoy vault with alternative PIN, auto-wipe on consecutive failed attempts, panic lock on screen-off, and `FLAG_SECURE` screenshot prevention.
* **In-Memory Streaming:** Plays back media directly from memory with zero unencrypted disk cache.

#### 🎵 2. Immersive Next-Gen Audio Player & Mini Player
* **Dynamic Artwork Glow:** Real-time color extraction from album artwork with contrast-balanced ambient lighting.
* **Springy Swipe-to-Skip Gestures:** Natural physics-based spring swipe transitions between previous and next tracks.
* **Floating Mini Player:** Dockable across all tabs, backed by an Android `MediaSessionService`.
* **Native AV1 Decoder (dav1d):** High-performance software AV1 video and opus audio rendering built with native NDK C++ optimizations.

#### 🎤 3. Time-Synced & Word-by-Word Karaoke Lyrics Engine
* **Interactive Karaoke View:** Real-time word-by-word highlighted lyrics, active row glowing, tap-to-seek, and fluid directional auto-hiding headers.
* **Urdu & Arabic RTL Support:** Native right-to-left layout and right-alignment for Urdu, Arabic, Persian, and Hebrew text with inverted gradient wipe animation for synchronized syllables.
* **Multi-Provider Search:** Search and download lyrics across **BetterLyrics** (word-level TTML), **LyricsPlus** (synced line and word lyrics), **LRCLIB**, and **YouTube Subtitles/Tags** with custom draggable provider priority ordering.
* **Universal Embedded Tag Extraction:** Demuxes ID3 (`USLT` & `SYLT`), MP4 (`©lyr`), FLAC (`LYRICS` / `UNSYNCEDLYRICS`), and raw Ogg/Opus vorbis comments.
* **Full-Screen Romanization:** Automatic transliteration of Japanese (Romaji), Korean, and Chinese (Pinyin) characters into Latin script.

#### 🎬 4. Intuitive Gesture Controls & In-App Video Player
* **Vertical Gestures:** Left edge adjusts screen brightness; right edge adjusts media volume.
* **2x Speed & Hold-to-Boost:** Long-press anywhere on video for instant 2x fast-forward (YouTube style); release to resume normal speed.
* **Double-Tap / Swipe Seeking:** Smooth forward and rewind skip animations.
* **PiP & Background Play:** Picture-in-Picture mode and background audio playback with lockscreen media controls.

#### 📁 5. Device Files & Centralized Media Hub
* **Deep Media Indexing:** Comprehensive filesystem scanner for audio and video formats not indexed by standard Android galleries (including `.opus`, `.ogg`, and `.mkv`).
* **Folder Filter:** Browse media organized by directory trees or unified across all storage locations.
* **Batch Operations:** Select multiple device media files at once to play, enqueue, add to favorites, move into the encrypted Private Vault, or safely delete.
* **Universal Dynamic Sorting:** Sort by Date, Title, Artist, File Size, Duration, or Format with intelligent adaptive labels.

#### 🍪 6. Persistent YouTube Session Auto-Healing & PO Token Coordination
* **Background Session Engine:** Off-screen WebView engine rotates short-lived Google authentication tokens (`__Secure-1PSIDTS`, `__Secure-3PSIDTS`) before expiration.
* **Zero MicroG / GMS Dependency:** Fully self-contained without Google Play Services or account managers.
* **Automated PO Token Coordination:** Synchronizes visitor data and Proof-of-Origin (PO) tokens with `yt-dlp` and `NewPipe` extractor to prevent 403 Forbidden errors.

---

### 🙏 Credits & Acknowledgements
Special thanks to:
* **Denis Çerri** ([YTDLnis](https://github.com/deniscerri/ytdlnis)) for the incredible foundation.
* **yt-dlp Team & Contributors** for the media extraction engine.
* **youtubedl-android** for porting yt-dlp to Android.
* **JunkFood02 / Seal** for Material You design inspiration.
* **Team NewPipe** for NewPipe Extractor.
* **VideoLAN / dav1d** for native AV1 decoding.
* **decipher3114** for the application icon design.
