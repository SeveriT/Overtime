# Overtime

IPTV player for Windows, built with Kotlin, Compose Multiplatform (Material 3 Expressive) and libVLC.

## Features

### Live TV
- M3U / M3U8 playlists (URL or local file) and Xtream Codes logins
- Channel groups, search and favourite channels
- Favourite groups pinned to the top of the group list
- Live rewind: pause and rewind up to 5 minutes
- Automatic channel and guide refresh

### Programme guide
- XMLTV guides, including guides named in M3U playlists
- Now / next in the channel list
- Full programme guide grid

### Movies & series (Xtream)
- Browse by category, search and poster grid
- Continue watching, resume and Next up for series
- Next episode button, and Up next with autoplay during the end credits
- Rated poster artwork
- Poster size options (small, medium, large)
- Mini player while browsing

### Player
- Smooth 4K playback
- Audio and subtitle track selection
- Subtitle style: font, size, edge, colour and bold
- Picture settings: brightness, contrast and saturation
- Stream info: resolution, frame rate, codec, bitrate and more, with copy stream URL
- Fullscreen and customisable keyboard shortcuts

### Look
- Material 3 Expressive design
- Custom accent, sidebar and title bar colours
- Compact layout for narrow windows
- Scrollbars on long sidebar lists

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

Defaults; every shortcut can be changed in Settings → Keyboard shortcuts.

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
| N | Next episode |
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
