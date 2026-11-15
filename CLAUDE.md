# Overtime – IPTV player for Windows

Kotlin Multiplatform project (JVM target only for now, structured so an Android target can be added later).
Compose Multiplatform UI, libVLC playback through vlcj 4.x (VLC 3.x must be installed on the machine).

## Layout

- `composeApp/src/commonMain` – models, M3U/Xtream/EPG data layer, `AppViewModel`, all UI.
- `composeApp/src/jvmMain` – `Main.kt` (window + shortcuts), `VlcPlayer` (frames rendered to an `ImageBitmap`
  via a vlcj callback surface, no AWT interop), StAX XMLTV parser, file store in `%APPDATA%\Overtime`.
- Platform hooks are `expect`/`actual` declarations in `data/Platform.kt`.

## Conventions

- Material 3, dark theme only (`ui/Theme.kt`). Dialogs go through `DialogFrame` in `ui/Dialogs.kt`.
- State lives in `AppViewModel` as `StateFlow`s; composables only `collectAsState()`.
- Persisted JSON uses kotlinx.serialization with `ignoreUnknownKeys` so old files keep loading.
- Build: `.\gradlew :composeApp:run`. Package: `.\gradlew :composeApp:packageMsi`.
