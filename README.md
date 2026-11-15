# Overtime

IPTV player for Windows, built with Kotlin, Compose Multiplatform (Material 3 Expressive) and libVLC.

## Features

### Live TV
- M3U / M3U8 playlists from a URL or a local file, and Xtream Codes logins
- Channel groups (playlist order or A–Z), search and favourites; the sidebar can be resized or hidden
- Channels and guides refresh in the background when the app opens (can be turned off)
- Resumes the last channel on start
- **Live rewind**: pause and rewind up to 5 minutes, with a seek bar and a Go live button

### Programme guide
- XMLTV guides (plain or gzip), several per source; M3U header guides (`x-tvg-url`, `url-tvg`) are picked up automatically
- Now / next and progress in the channel list
- Full EPG grid (G) with a details sheet; move through time with ← → (Shift for 3 hours)

### Movies & series (Xtream)
- Home rows per category, poster grid and search
- Continue watching, resume where you left off, and Next up for series
- Optional rated poster artwork from a configurable template, plus poster card size and corner settings
- Now playing sidebar with episode list, and a mini player while browsing

### Player
- GPU colour conversion, so 4K HEVC at 50 fps plays without dropped frames
- Audio and subtitle track picker (B / V)
- Subtitle style: font, size, outline / shadow / box, colour and bold, with a live preview
- Picture settings: brightness, contrast and saturation
- Stream info panel: resolution, frame rate, codec, bitrate, dropped frames and audio, plus a button to copy the stream URL
- Fullscreen, Windows-style volume popup, and auto-hiding controls
- Remembers the window size and position

### Look
- Material 3 Expressive design with custom accent and sidebar / title bar colours

## Requirements

- Windows 10/11, 64-bit
- [VLC 3.x 64-bit](https://www.videolan.org/) installed (the app uses its libVLC)
- JDK 17 or newer to build

## Build & run

```powershell
.\gradlew :composeApp:run          # run from sources
.\gradlew :composeApp:jvmTest      # unit tests
.\gradlew :composeApp:packageMsi   # installer in composeApp\build\compose\binaries\main\msi
```

### Install with shortcuts

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

This builds a standalone copy, installs it to `%LOCALAPPDATA%\Programs\Overtime` and creates
`Overtime` shortcuts on the desktop and in the Start Menu, so it also shows up in Windows search.
Run it again after pulling changes to update; it closes a running copy first.

## Keyboard shortcuts

| Key | Action |
| --- | --- |
| Space | Pause / resume |
| ← / → | Previous / next channel; skip 10 s while a movie or episode plays |
| Shift + ← / → | Rewind / forward 10 s on live TV |
| End | Back to live |
| Page Up / Page Down | Previous / next channel |
| ↑ / ↓ | Volume |
| M | Mute |
| B | Next audio track |
| V | Next subtitle track (or off) |
| I | Stream info |
| S | Show / hide the sidebar |
| G | Programme guide (← → move through time, Home jumps to now) |
| L | Movies & series |
| F / F11 | Toggle fullscreen |
| Esc | Close the guide or library, leave fullscreen, back to groups |
| Double-click video | Toggle fullscreen |

## Data

Settings, sources, watch progress and cached playlists and guides live in `%APPDATA%\Overtime`.
The live rewind buffer is kept in `%TEMP%\overtime-timeshift` while a channel plays and deleted afterwards.
