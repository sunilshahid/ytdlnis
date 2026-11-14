<h1 align="center">
	<img src="fastlane/metadata/android/en-US/images/icon.png" width="120" /> <br>
	YTDLnis Extended
</h1>

<div align="center">
	<strong>The Privacy-First Media Suite & Powerful Video/Audio Downloader for Android (7.0+)</strong>
	<br>
	<em>Forked and extended from the renowned <a href="https://github.com/deniscerri/ytdlnis">YTDLnis</a> by Denis Çerri</em>
</div>

<br>

<div align="center">

[![Package](https://img.shields.io/badge/Package-com.deniscerri.ytdl.extended-brightgreen?style=flat-square&logo=android)](https://github.com/deniscerri/ytdlnis)
[![License](https://img.shields.io/badge/License-GPL%20v3.0-blue?style=flat-square)](https://github.com/deniscerri/ytdlnis/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green?style=flat-square&logo=android)](https://developer.android.com)
[![Engine](https://img.shields.io/badge/Engine-yt--dlp-red?style=flat-square&logo=youtube)](https://github.com/yt-dlp/yt-dlp)
[![ExoPlayer](https://img.shields.io/badge/Decoder-dav1d%20AV1%20Native-orange?style=flat-square)](https://code.videolan.org/videolan/dav1d)

</div>

<div align="center">
	<a href="#-co-existence--standalone-package">Co-Existence Package</a> &nbsp;•&nbsp;
	<a href="#-key-extended-features">Extended Features</a> &nbsp;•&nbsp;
	<a href="#-gallery--screenshots">Gallery</a> &nbsp;•&nbsp;
	<a href="#-installation">Installation</a> &nbsp;•&nbsp;
	<a href="#-credits--acknowledgements">Credits</a> &nbsp;•&nbsp;
	<a href="#-license">License</a>
</div>

---

## 🚀 Standalone Co-Existence Package

**YTDLnis Extended** is packaged under `com.deniscerri.ytdl.extended`.

> [!NOTE]
> **Install Alongside Official YTDLnis:**
> Because it utilizes its own distinct package identifier (`com.deniscerri.ytdl.extended`), you can install and run YTDLnis Extended **side-by-side** with the official YTDLnis app (`com.deniscerri.ytdl`) without any package collision, data loss, or overwriting of your existing configurations.

---

## 🌟 Key Extended Features

### 1. 🔐 Zero-Knowledge Encrypted Private Vault & Direct-to-Vault
* **Hybrid Cryptographic Architecture:** Bulk media payloads are protected with **AES-256-GCM** in 64 KiB authenticated chunks. Background downloads while the vault is locked are sealed using **RSA-2048 OAEP (SHA-256 / MGF1-SHA256)** public-key encryption so no master keys ever touch RAM.
* **Direct-to-Vault Downloading:** Download videos and audio streams straight into encrypted vault storage on-the-fly (`.vault` container, `.meta.enc`, `.thumb.enc`). Plaintext never touches disk.
* **In-Memory Streaming Decryption:** Decrypts audio/video on-demand during playback with zero plaintext disk caching or temporary file leaks.
* **Biometric Authentication:** Unlock seamlessly with fingerprint or face unlock backed by Android Keystore hardware-bound `CryptoObject`.
* **Anti-Forensics & Duress Defense:**
  * **Decoy Vault:** A secondary PIN unlocks a separate, believable decoy vault.
  * **Auto-Wipe & Panic Lock:** Configurable threshold of failed attempts permanently obliterates encrypted keys; background or screen-off triggers immediately flush cryptographic keys from memory.
  * **Anti-Screen Capture:** `FLAG_SECURE` blocks screenshots, screen recording, and system app switcher thumbnails.

<div align="center">
  <img src="screenshots/Private_Vault.png" width="23%" alt="Private Vault" />
  <img src="screenshots/Direct_To_Vault.png" width="23%" alt="Direct To Vault Dialog" />
  <img src="screenshots/Direct_To_Vault_Button_Highlighted.jpg" width="23%" alt="Direct To Vault Option" />
  <img src="screenshots/Save_To_Vault_Enabled.png" width="23%" alt="Save to Vault Toggle" />
</div>

---

### 2. 🎵 Immersive Next-Gen Audio Player & Mini Player
* **Dynamic Artwork Glow (`GlowView`):** Real-time dominant color extraction from album artwork with contrast-balanced ambient lighting.
* **Springy Swipe-to-Skip Gestures:** Natural physics-based spring swipe transitions between previous and next tracks.
* **Persistent Audio Queue & Mini Player:** Seamless floating mini player dockable across all tabs, backed by an Android `MediaSessionService`.
* **Native AV1 Decoder (dav1d):** Ultra-efficient software AV1 video and opus audio rendering built with native NDK C++ optimizations.
* **Background Audio Toggle:** Switch between full video playback and background-only audio playback with a single tap.

<div align="center">
  <img src="screenshots/Audio_Player.png" width="23%" alt="Audio Player" />
  <img src="screenshots/Audio_Player2.png" width="23%" alt="Audio Player Queue" />
  <img src="screenshots/Mini_Player.png" width="23%" alt="Floating Mini Player" />
  <img src="screenshots/Background_Player_ON.png" width="23%" alt="Background Player Mode" />
</div>

---

### 3. 🎤 Time-Synced & Word-by-Word Karaoke Lyrics Engine
* **Synchronized & Word-by-Word Karaoke:** Interactive lyrics sheet with real-time word highlighting, active row glowing, tap-to-seek, and fluid directional auto-hiding headers.
* **Urdu & Arabic RTL Support:** Native right-to-left layout and right-alignment for Urdu, Arabic, Persian, and Hebrew text with inverted gradient wipe animation for synchronized syllables.
* **Multi-Provider Online Lyrics Search:** Search and download word-synced and line-synced lyrics with automatic fallback and customizable provider priority across:
  * **BetterLyrics** (rich word-by-word synced TTML karaoke lyrics)
  * **LyricsPlus** (synced line-by-line & word-level lyrics across resilient mirror endpoints)
  * **LRCLIB** (high-quality open synchronized LRC lyrics)
  * **YouTube Subtitles / Tags** (automatic track-aligned subtitle caption streams)
* **Universal Embedded Tag Extraction:** Reads ID3 (`USLT` & `SYLT`), MP4 (`©lyr`), FLAC (`LYRICS` / `UNSYNCEDLYRICS`), and raw Ogg/Opus vorbis comment page demuxing.
* **Full-Screen Romanization:** Automatic transliteration of Japanese (Romaji), Korean, and Chinese (Pinyin) characters into Latin script.

<div align="center">
  <img src="screenshots/Lyrics_Screen.png" width="19%" alt="Lyrics Screen" />
  <img src="screenshots/Online_Lyrics_Search.png" width="19%" alt="Online Lyrics Search" />
  <img src="screenshots/Searching_Lyrics.png" width="19%" alt="Searching Lyrics" />
  <img src="screenshots/Search_Result_Lyrics.png" width="19%" alt="Lyrics Search Results" />
  <img src="screenshots/Lyrics_Search_Details.png" width="19%" alt="Lyrics Details" />
</div>

<br>

<div align="center">
  <img src="screenshots/Lyrcis_Providers.png" width="23%" alt="Lyrics Providers" />
  <img src="screenshots/Lyrics_Settings.png" width="23%" alt="Lyrics Settings" />
  <img src="screenshots/Lyrics_Settings_Provider_Priority.png" width="23%" alt="Provider Priority" />
  <img src="screenshots/Romanization_Full_Screen.png" width="23%" alt="Romanization Full Screen" />
</div>

---

### 4. 🎬 Intuitive Gesture Controls & In-App Video Player
* **Vertical Slide Gestures:** Left edge adjusts screen brightness; right edge adjusts media volume smoothly.
* **Quick 2x Speed & Hold-to-Boost:** Long-press anywhere on the video screen to temporarily boost playback speed to 2x (YouTube style); release to resume normal speed.
* **Double-Tap / Swipe Seeking:** Smooth forward and rewind skip animations.
* **PiP & Background Play:** Seamless Picture-in-Picture mode and background audio playback with lockscreen media controls.

<div align="center">
  <img src="screenshots/Video_Player.png" width="31%" alt="Video Player" />
  <img src="screenshots/Left_Swipe_Brightness_Up_Down.png" width="31%" alt="Brightness Swipe Gesture" />
  <img src="screenshots/Right_Swipe_Up_Down_Volume.png" width="31%" alt="Volume Swipe Gesture" />
</div>

<br>

<div align="center">
  <img src="screenshots/Hold_For_Speed_Option.png" width="31%" alt="Hold for Speed Option" />
  <img src="screenshots/2x_Speed.png" width="31%" alt="2x Speed Indicator" />
  <img src="screenshots/Forward.png" width="31%" alt="Forward Seeking Gesture" />
</div>

---

### 5. 📁 Device Files & Centralized Media Hub
* **Deep Device Media Scanner:** Fast MediaStore indexing coupled with recursive filesystem scanning for audio and video formats not indexed by standard Android galleries (including `.opus`, `.ogg`, and `.mkv`).
* **Folder Filter & Tree Browser:** Browse media organized by directories or unified across all storage locations.
* **Multi-Select Batch Operations:** Select multiple device media files at once to play, enqueue, add to favorites, move into the encrypted Private Vault, or safely delete.
* **Universal Dynamic Sorting:** Sort by Date, Title, Artist, File Size, Duration, or Format with intelligent adaptive labels (`Newest first`, `Oldest first`, `A → Z`, `Largest first`).

<div align="center">
  <img src="screenshots/Device_Files.png" width="23%" alt="Device Files Library" />
  <img src="screenshots/Device_Folder.png" width="23%" alt="Device Folder Tree" />
  <img src="screenshots/Side_bar.png" width="23%" alt="Navigation Sidebar" />
  <img src="screenshots/Player_Settings_Tab.png" width="23%" alt="Player Settings Tab" />
</div>

---

### 6. 🍪 Persistent YouTube Session Auto-Healing & PO Token Coordination
* **Background Session Engine:** Off-screen WebView engine rotates short-lived Google authentication tokens (`__Secure-1PSIDTS`, `__Secure-3PSIDTS`) before expiration, preventing login dropouts.
* **Zero MicroG / GMS Dependency:** Fully self-contained without Google Play Services or account managers.
* **Automated PO Token Coordination:** Synchronizes visitor data and Proof-of-Origin (PO) tokens with `yt-dlp` and `NewPipe` extractor to prevent 403 Forbidden errors and bot checks.

---

### 7. 📥 Full yt-dlp Power & Comprehensive Downloader Suite
* **1000+ Supported Sites:** Powered by `yt-dlp` with automatic updates and custom CLI command templates.
* **Batch & Playlist Processing:** Download full playlists or pick specific formats, audio tracks, and subtitles.
* **SponsorBlock Integration:** Skip or mark sponsored segments automatically.
* **Video Trimming & Chapter Splitting:** Cut videos by timestamps or chapters prior to saving.
* **Built-in Linux Terminal:** Full interactive terminal for running direct yt-dlp, ffmpeg, and python commands.

---

## 📦 Installation

1. Navigate to the [Releases](https://github.com/deniscerri/ytdlnis/releases) page.
2. Download the latest `YTDLnis-Extended-*-universal-release-github.apk` (or arm64-v8a).
3. Open the APK on your Android device (Android 7.0 / API 24 or newer).
4. Tap **Install** (or enable *Install unknown apps* if prompted).
5. Enjoy YTDLnis Extended alongside your existing apps!

---

## 🙏 Credits & Acknowledgements

YTDLnis Extended stands on the shoulders of giants. We express our deepest gratitude to:

* **[Denis Çerri](https://github.com/deniscerri)** — The visionary creator and lead maintainer of [YTDLnis](https://github.com/deniscerri/ytdlnis). This project would not exist without his incredible architecture and dedication to open source.
* **[yt-dlp Team & Contributors](https://github.com/yt-dlp/yt-dlp)** — The world-class media extraction engine powering video and audio downloads.
* **[youtubedl-android](https://github.com/yausername/youtubedl-android)** — Providing the Android port of yt-dlp and companion binaries.
* **[JunkFood02 / Seal](https://github.com/JunkFood02/Seal)** — Inspiration for Material You UI aesthetics and workflows.
* **[Team NewPipe](https://github.com/TeamNewPipe/NewPipeExtractor)** — The NewPipe Extractor and stream parsing engine.
* **[decipher3114](https://github.com/decipher3114)** — Original application icon designer.
* **[VideoLAN / dav1d](https://code.videolan.org/videolan/dav1d)** — High-performance AV1 software decoder library.
* **[LRCLIB](https://lrclib.net)** & open lyrics providers for making synchronized music lyrics accessible worldwide.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.  
See the [LICENSE](LICENSE) file for more details.
