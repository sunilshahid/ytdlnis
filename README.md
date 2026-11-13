<h1 align="center">
	<img src="fastlane/metadata/android/en-US/images/icon.png" width="25%" /> <br>
	YTDLnis Extended
</h1>

<div align="center">
	English
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-sq.md">Shqip</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-az.md">Azərbaycanca</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-tr.md">Türkçe</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-id.md">Indonesia</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-pt.md">Português</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-es.md">Español</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ja.md">Japanese</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-zh_CN.md">简体中文</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ro.md">Română</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ar.md">العربية</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ru.md">Русский</a>
</div>

<h3 align="center">
	YTDLnis Extended is a powerful, privacy-first video/audio downloader and media suite using yt-dlp for Android 7.0 and above.
</h3>
<h4 align="center">
	Original by Denis Çerri &nbsp;•&nbsp; Extended Edition
</h4>

<div align="center">

[![GitHub Releases](https://custom-icon-badges.herokuapp.com/badge/Download-blue?style=for-the-badge&logo=download&logoColor=white)](https://github.com/deniscerri/ytdlnis/releases/latest)
[![F-Droid](https://custom-icon-badges.herokuapp.com/badge/FDroid-violet?style=for-the-badge&logo=download&logoColor=white)](https://f-droid.org/en/packages/com.deniscerri.ytdl)
[![IzzyOnDroid repository](https://custom-icon-badges.herokuapp.com/badge/IzzyOnDroid%20Repo-red?style=for-the-badge&logo=download&logoColor=white)](https://apt.izzysoft.de/packages/com.deniscerri.ytdl)
[![Uptodown](https://custom-icon-badges.herokuapp.com/badge/UpToDown-green?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.en.uptodown.com/android/download)

![CI](https://github.com/deniscerri/ytdlnis/actions/workflows/android.yml/badge.svg?branch=main&event=pull)
[![Preview release](https://img.shields.io/github/release/deniscerri/ytdlnis.svg?maxAge=3600&include_prereleases&label=preview)](https://github.com/deniscerri/ytdlnis/releases) 
[![Downloads](https://img.shields.io/github/downloads/deniscerri/ytdlnis/total?style=flat-square)](https://github.com/deniscerri/ytdlnis/releases) 
[![Translation status](https://hosted.weblate.org/widgets/ytdlnis/-/svg-badge.svg)](https://hosted.weblate.org/engage/ytdlnis/?utm_source=widget) 
[![community](https://img.shields.io/badge/Discord-YTDLnis-blueviolet?style=flat-square&logo=discord)](https://discord.gg/WW3KYWxAPm) 
[![community](https://img.shields.io/badge/Telegram-YTDLnis-blue?style=flat-square&logo=telegram)](https://t.me/ytdlnis)
[![community](https://img.shields.io/badge/Telegram-Updates-red?style=flat-square&logo=telegram)](https://t.me/ytdlnis_updates)
[![website](https://img.shields.io/badge/Website-orange?style=flat-square&logo=youtube)](https://ytdlnis.org)
![GitHub Sponsor](https://img.shields.io/github/sponsors/deniscerri?label=Sponsor&logo=GitHub)

</div>

---

## 🌟 Extended Highlights & New Features

YTDLnis Extended supercharges the application with a zero-knowledge encrypted vault, a full-fledged offline media player with karaoke lyrics, local device files library integration, persistent background YouTube cookie healing, automated PO token coordination, and an adaptive sorting and view management system.

### 🔐 1. Zero-Knowledge Encrypted Private Vault
* **End-to-End Authenticated Encryption:** AES-256-GCM chunked streaming encryption (`.vault` payload, `.meta.enc`, `.thumb.enc`) with per-item derived root keys via HKDF-SHA256 and salted PBKDF2/Argon2id key derivation.
* **Hardware Biometrics:** Unlock securely via `BiometricPrompt` with an Android Keystore-backed `CryptoObject` bound directly to cipher operations.
* **Direct-to-Vault Downloads:** Stream downloads directly into the encrypted vault on-the-fly without intermediate unencrypted files ever touching device storage.
* **Duress & Anti-Forensics Protection:**
  * **Decoy Vault:** A secondary password unlocks an isolated, believable decoy vault in memory.
  * **Stealth Mode:** Disguises the vault interface; unlockable only via a secret duress/stealth trigger PIN.
  * **Auto-Wipe:** Configurable threshold of consecutive failed unlock attempts permanently obliterates the vault.
  * **Panic Lock:** Screen-off and background broadcast triggers immediately lock the vault and zeroize cryptographic keys in RAM.
  * **Anti-Screen Capture:** Enforcement of `FLAG_SECURE` prevents screenshots, screen recording, and system recents previews.
* **In-App Vault Player & Import Picker:** Decrypts audio/video directly in memory during playback with zero plaintext disk caching, paired with a multi-select in-app device video picker supporting batch SAF moves and deletions.

### 🎵 2. Next-Gen Playback & Karaoke Lyrics
* **High-Performance Audio/Video Engine:** Backed by `MediaSessionService` + ExoPlayer with native **dav1d AV1 software rendering**.
* **Immersive Now Playing Screen:**
  * **Dynamic Artwork Glow:** Real-time color extraction from album art with contrast clamping for an ambient visualizer glow.
  * **Gesture Navigation:** Smooth horizontal swipe-to-skip on album artwork.
  * **Playback Control:** Pitch, tempo/speed adjustment, sleep timer, persistent audio queue, and quick audio/video stream toggle.
* **Time-Synced Karaoke Lyrics:**
  * Interactive lyrics sheet with smooth line-by-line scrolling, active lyric glow, and tap-to-seek playback.
  * Universal embedded lyrics tag extraction: ID3 (USLT & SYLT), MP4 (`©lyr`), FLAC (`LYRICS` / `UNSYNCEDLYRICS`), and raw Ogg/Opus vorbis comment page demuxing.

### 📁 3. Device Files & Complete Play Hub
* **Unified Play Tab:** Centralized dashboard for Continue Playing cards, Up Next queue, Liked tracks, Recently Played history, and local Device Files.
* **Deep Device Media Scanner:** Fast MediaStore indexing combined with deep filesystem recursive scanning for audio and video files not indexed by Android (such as `.opus` and `.ogg` downloads).
* **Folder Filter & Scope:** Browse media by individual directory trees or across all storage folders.
* **Multi-Select Batch Actions:** Select multiple device files simultaneously to play, queue, favorite, move into the encrypted Private Vault, or delete.

### 🍪 4. Persistent YouTube Cookie Engine & Auto-Healing
* **Background WebView Session Engine:** Keeps an off-screen Chromium instance active in the background sharing persistent WebView storage with the in-app login sheet.
* **Silent Cookie Rotation:** Automatically executes Google's own authentication routines to rotate short-lived, sensitive tokens (such as `__Secure-1PSIDTS` and `__Secure-3PSIDTS`) before they expire, keeping logins valid indefinitely.
* **Zero MicroG / GMS Dependency:** Operates 100% self-contained without Google Play Services, AccountManager, or microG hacks.
* **Pre-Flight Cookie Health Checks:** Probes YouTube session health on app launch and before queued download batches, healing stale cookies seamlessly in the background.
* **Ephemeral Cookie Exporter:** Safely extracts Netscape-format cookie jars for `yt-dlp` and `NewPipe` without exposing or leaking unnecessary tracking data.

### 🛡️ 5. Coordinated Automatic PO Token System
* **Automated Visitor Data & PO Token Coordination:** Synchronizes token coordination between `yt-dlp` and `NewPipe` extractor to prevent YouTube bot detection, 403 Forbidden errors, and signature throttles.
* **Configurable Coordination Policy:** Choose between **Auto** (session-bound visitor data if logged in, fallback to anonymous WebView), **Signed-in session only**, or **Anonymous WebView only**.

### 🎛️ 6. Universal Dynamic Sorting & View Options
* **Adaptive Order Labels:** Dynamic direction toggles that change text based on the chosen sort attribute:
  * **Date / Played time:** `Newest first` / `Oldest first` *(Default)*
  * **Title / Artist / Author / Path / Website:** `A → Z` / `Z → A`
  * **File Size:** `Largest first` / `Smallest first`
  * **Length / Duration:** `Longest first` / `Shortest first`
  * **Resolution / Frame rate:** `Highest first` / `Lowest first`
  * **Status:** `Active first` / `New first`
  * **Type:** `Videos first` / `Audio first`
* **Consistent Defaults:** All libraries (Device Files, Play tab, Vault gallery, Vault Import picker, Downloads history) default to **Date · Newest first**.
* **Custom View Layouts & Chips:** Switch between **2-column Grid** and **List** layouts across all tabs, with customizable metadata chips (Artist, Source, Date, Duration, Resolution, Size, Path).

---

## 💡 Features:

- Download audio/video files from more than <a href="https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md">1000 websites</a>
- Process playlists
	- Edit every playlist item separately just like in a normal download item
	- Select a common format for all items and/or select multiple audio formats in case you are downloading them as a video
	- Select a download path for all items
	- Select a filename template for all items
	- Batch update download type to audio/video/custom command in one click
- Queue downloads and schedule them by date and time
	- You can also schedule multiple items at the same time
- Download multiple items at the same time
- Use custom commands and templates or use yt-dlp with the built-in terminal
	- You can backup and restore templates so you can share them with your buddies
- Supports cookies. Log in with your accounts and download private/unavailable videos, unlock premium formats etc.
- Cut videos based on timestamps and video chapters (experimental yt-dlp feature)
	- You can make unlimited cuts
- Remove SponsorBlock elements from downloaded items
	- Embed them as a chapters in your video 
- Embed subtitles/metadata/chapters etc
- Modify metadata such as title and author
- Split item into separate files depending on its chapters
- Select different download formats
- Bottom card right from the share menu, no need to open the app 
	- You can create a txt file and fill it with links/playlists/search queries separate by a new line and the app will process them
- Search or insert a link from the app
	- You can stack searches so you can process them at the same time
- Log downloads in case of problems
- Re-download cancelled or failed downloads
	- You can use gestures to swipe left to redownload and right to delete
	- You can long click the redownload button in the details sheet to show the download card for more functionality
- Incognito mode when you don't want to save a download history or logs
- Quick download mode
	- Download immediately without having to wait for data to process. Turn off the bottom card and it will instantly start
- Open / share downloaded files right from the finished notification
- Most yt-dlp features are implemented, suggestions are welcome
- Material You interface
- Theming options
- Backup and restore features
- MVVM architecture with WorkManager

## 🧩 Plugin Support

YTDLnis orchestrates plugins so users can freely upgrade and downgrade components such as:
- Python
- JS Runtimes (NodeJS, Deno)
- FFmpeg
- Aria2c

You can install ytdlnis packages from this repository [ytdlnis-packages](https://github.com/deniscerri/ytdlnis-packages/) or through the updating section in the application.
<br>For more information refer to the repo's README.

## 📲 Screenshots

<div>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width="90%" />
</div>

## 💬 Contact

Join our [Discord](https://discord.gg/WW3KYWxAPm) or [Telegram channel](https://t.me/ytdlnis) for announcements, discussion and releases.

## 😇 Contributing

Please read the [contributing](CONTRIBUTING.MD) section if you would like to contribute.

## 📝 Help translate on Weblate
<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/strings/open-graph.png" alt="Translation status" />
</a>


<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/multi-auto.svg" alt="Translation status" />
</a>

## 🔑 Connect with third-party apps using the package name

The app's package name is "com.deniscerri.ytdl".

## 🔍 Verify application signature

The app should contain the signature below. The github workflow action uses it, and the releases are based on it to make it a reproducible build.
If the signature is different, your third party distributor has modified the application. Please use the app with the original signature.
```
Signer #1 certificate DN: CN=Denis Cerri, OU=Personal, O=Personal, L=Albania, ST=Albania, C=AL
Signer #1 certificate SHA-256 digest: 263645cb5272eb290759fe1f59149ae24df6ce171e9f6666eead981d3fc64c95
Signer #1 certificate SHA-1 digest: 2fec9c2fcef68d29a60857e185c795fec5f56fb6
Signer #1 certificate MD5 digest: 429d0c6315d2f99650f66cc44cf5a794
```


## 🤖 Connect with third-party apps using intents

You can use intents to push commands to the app to run downloads without user interaction.
Accepted variables:

<b>TYPE</b> -> it can be: audio,video,command <br/>
<b>BACKGROUND</b> -> it can be: true,false. If its true the app won't show the download card no matter what and run the download in the background <br/>

### An example of downloading an audio item in the background with Tasker
1. Create Send Intent task
2. Action: android.intent.action.SEND
3. Cat: Default
4. Mime Type: text/*
5. Extra: android.intent.extra.TEXT:url (instead of "url" write the URL of the video you want to download)
6. Extra: TYPE:audio
7. Extra: BACKGROUND:true

## 📄 License

[GNU GPL v3.0](https://github.com/deniscerri/ytdlnis/blob/main/LICENSE)

Except for the source code licensed under the GPLv3 license, all other parties are prohibited from using the "YTDLnis" name as a downloader app, and the same is true for its derivatives. Derivatives include but are not limited to forks and unofficial builds.

## 😁 Donate


[<img src="https://raw.githubusercontent.com/WSTxda/WSTxda/main/images/BMC.svg"
alt='Donate with BMC'
height="80">](https://www.buymeacoffee.com/deniscerri)

## 🙏 Thanks

- [decipher3114](https://github.com/decipher3114) for the app's icon
- [dvd](https://github.com/yausername/dvd) for being an example youtubedl-android implementation
- [seal](https://github.com/JunkFood02/Seal) for certain design elements and features I wanted to have in this app when I started developing it
- [youtubedl-android](https://github.com/yausername/youtubedl-android) for porting yt-dlp to Android
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) and its contributors for making this tool possible. Without it this app wouldn't exist


and to a lot of other people, such as contributors.
