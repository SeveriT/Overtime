# Overtime

A lean IPTV player for Windows, built with Kotlin, Compose Multiplatform and libVLC.

## Features (0.1.0)

- M3U / M3U8 playlists from a URL or a local file
- Xtream Codes logins (live channels, categories, logos, guide)
- XMLTV programme guide (plain or gzip) with now/next and progress bars
- Groups, search, favorites, remembers the last channel
- Fullscreen player with hover controls and keyboard shortcuts

## Requirements

- Windows 10/11, 64-bit
- [VLC 3.x 64-bit](https://www.videolan.org/) installed (the app uses its libVLC)
- JDK 17 or newer to build

## Build & run

```powershell
.\gradlew :composeApp:run          # run from sources
.\gradlew :composeApp:packageMsi   # installer in composeApp\build\compose\binaries\main\msi
```

### Install with shortcuts

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

Builds a standalone copy, installs it to `%LOCALAPPDATA%\Programs\Overtime` and creates
`Overtime` shortcuts on the desktop and in the Start Menu, so it also shows up in Windows search.
Run it again after pulling changes to update.

## Shortcuts

| Key | Action |
| --- | --- |
| F / F11 | Toggle fullscreen |
| Esc | Leave fullscreen |
| Space | Pause / resume |
| M | Mute |
| Page Up / Page Down | Previous / next channel |
| Double-click video | Toggle fullscreen |

## Data

Settings, sources and cached playlists/guides live in `%APPDATA%\Overtime`.
