# YTDLnis Extended v1.9.3

Welcome to **YTDLnis Extended v1.9.3**! This release brings a completely reimagined **Audio Player screen**, full **Material 3 Expressive design** integration, interactive **Video Chapters** with scrubber milestones, and significant under-the-hood audio/video decoding performance boosts.

---

### 📦 Standalone Co-Installation
* **Package Name:** `com.deniscerri.ytdl.extended`
* **App Label:** `YTDLnis Extended`
* **Side-by-Side Operation:** Installs independently alongside official YTDLnis (`com.deniscerri.ytdl`) without overwriting your settings, databases, or download libraries.

---

### 🌟 What's New in v1.9.3

#### 🎵 1. Reimagined Audio Player Screen & Dynamic Artwork Glow
* **Dynamic Artwork Glow:** Real-time color extraction from album art casts an ambient, contrast-clamped glow behind artwork that subtly breathes with playback.
* **Apple Music-Style Tactile Physics:** Responsive scale-bounce animations on Play/Pause, tactile press states across controls, and smooth crossfade track transitions.
* **Springy Swipe-to-Skip Gestures:** Natural physics-driven swipe gestures to skip to previous or next tracks in the queue.
* **Persistent Mini Player:** Dockable floating mini-player accessible across all screens, backed by Android's `MediaSessionService` for seamless background listening.
* **High-Performance Artwork Caching:** Downsampled 256px disk & memory caching prevents UI jank even in massive playlists.

#### 🎨 2. Material 3 Expressive & Material You Aesthetics
* **Material 3 Expressive Styling:** Redesigned cards, chips, modal bottom sheets, seekbars, and switches aligned with the latest Material 3 Expressive design language.
* **Dynamic Palette Tinting:** Fluid Android 12+ wallpaper color-matching across all screens, dialogs, and navigation components.
* **Tactile List & Press Animations:** Refined touch feedback and container transforms that elevate navigation throughout the entire application.

#### 📑 3. Interactive Video Chapters & Smart Navigation
* **Timeline Chapter Markers:** Visual chapter milestones placed directly onto the video scrubber bar for effortless orientation.
* **Interactive Chapter Drawer:** Jump to any chapter with titles, start timestamps, and durations in a clean bottom drawer.
* **Smart Chapter Extraction:** Automatically parses chapter metadata via yt-dlp for YouTube and supported media sources.
* **Quick Chapter Skipping:** One-tap chapter seek buttons to jump forward or backward through sections without manual scrubbing.

#### 🎤 4. Time-Synced & Word-by-Word Karaoke Lyrics Engine
* **Interactive Karaoke View:** Synchronized word-by-word syllable lighting, active line glow, and tap-any-line seeking.
* **Urdu & Arabic RTL Support:** Full Right-to-Left alignment for Urdu, Arabic, Persian, and Hebrew lyrics with inverted progressive wipe animations.
* **Multi-Provider Search:** Live lyrics querying across **BetterLyrics** (word-level TTML), **LyricsPlus** (synced line and word lyrics), **LRCLIB**, and **YouTube Subtitles/Tags** with customizable draggable priority order.
* **Universal Embedded Tag Reader:** Demuxes ID3 (`USLT` & `SYLT`), MP4 (`©lyr`), FLAC (`LYRICS`), and raw Ogg/Opus vorbis tags directly from local files.
* **Multi-Language Romanization:** Real-time transliteration of Japanese (Romaji), Korean, and Chinese (Pinyin) characters into Latin script.

#### 🔐 5. Zero-Knowledge Encrypted Private Vault
* **Direct-to-Vault Downloads:** Download streams directly into an encrypted `.vault` container on-the-fly without plaintext ever touching disk.
* **Hybrid Cryptography:** AES-256-GCM chunked file cipher paired with RSA-2048 OAEP asymmetric public-key sealing for locked background downloads.
* **Hardware Biometrics:** Secure biometric unlock with Android Keystore-backed `CryptoObject`.
* **Anti-Forensics Protection:** Decoy vault with alternative PIN, auto-wipe on consecutive failed attempts, panic lock on screen-off, and `FLAG_SECURE` screenshot prevention.
* **In-Memory Streaming:** Plays back media directly from memory with zero unencrypted disk cache.

#### 🍪 6. Persistent YouTube Session Auto-Healing & PO Token Coordination
* **Background Session Engine:** Off-screen WebView engine rotates short-lived Google authentication tokens (`__Secure-1PSIDTS`, `__Secure-3PSIDTS`) before expiration.
* **Zero MicroG / GMS Dependency:** Fully self-contained without Google Play Services or account managers.
* **Automated PO Token Coordination:** Synchronizes visitor data and Proof-of-Origin (PO) tokens with `yt-dlp` and `NewPipe` extractor to prevent 403 Forbidden errors.

#### 🎬 7. Video Player Gesture Controls & Native AV1
* **Vertical Gestures:** Left edge adjusts screen brightness; right edge adjusts media volume.
* **2x Speed & Hold-to-Boost:** Long-press anywhere on video for instant 2x fast-forward (YouTube style); release to resume normal speed.
* **Native AV1 Decoder (dav1d):** Built-in native C++ dav1d decoder for buttery-smooth AV1 video and opus audio rendering.

---

### 🙏 Credits & Acknowledgements
Special thanks to:
* **Denis Çerri** ([YTDLnis](https://github.com/deniscerri/ytdlnis)) for the exceptional foundation.
* **yt-dlp Team & Contributors** for the media extraction engine.
* **youtubedl-android** for porting yt-dlp to Android.
* **JunkFood02 / Seal** for Material You design inspiration.
* **Team NewPipe** for NewPipe Extractor.
* **VideoLAN / dav1d** for native AV1 decoding.
* **decipher3114** for the application icon design.
