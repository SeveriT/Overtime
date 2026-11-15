package fi.severi.overtime.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.request.crossfade
import fi.severi.overtime.AppViewModel
import fi.severi.overtime.model.AppSettings
import okio.Path.Companion.toPath

/** Sidebar width limits; the default is the M3 standard navigation drawer width. */
private const val SIDEBAR_MIN_DP = 280f
private const val SIDEBAR_MAX_DP = 720f

/** Below this window width the sidebar hides automatically. */
private val NARROW_WINDOW = 1000.dp
private const val SIDEBAR_MAX_SHARE = 0.6f
private val DEFAULT_SIDEBAR_DP = AppSettings().sidebarWidth.toFloat()
private const val IMAGE_CACHE_BYTES = 300L * 1024 * 1024

@Composable
fun App(vm: AppViewModel) {
    // Posters and logos are cached on disk so library rows don't re-download artwork every visit.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory("${vm.dataDirectory}/image-cache".toPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }
    val settings by vm.settings.collectAsState()
    OvertimeTheme(accent = accentColorOf(settings.accentColor)) {
        val sources by vm.sources.collectAsState()
        val fullscreen by vm.fullscreen.collectAsState()
        val sidebarVisible by vm.sidebarVisible.collectAsState()
        val guideVisible by vm.guideVisible.collectAsState()
        val libraryVisible by vm.library.visible.collectAsState()
        val vodPlaying = vm.currentVod.collectAsState().value != null
        var showSources by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        // First launch: nothing configured yet, open the sources dialog straight away.
        LaunchedEffect(sources.isEmpty()) { if (sources.isEmpty()) showSources = true }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (libraryVisible) {
                LibraryScreen(vm)
            } else if (guideVisible) {
                GuideScreen(vm)
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val narrow = maxWidth < NARROW_WINDOW
                    LaunchedEffect(narrow) { vm.setNarrowWindow(narrow) }
                    val density = LocalDensity.current
                    val maxSidebar = minOf(SIDEBAR_MAX_DP, with(density) { maxWidth.value } * SIDEBAR_MAX_SHARE)
                        .coerceAtLeast(SIDEBAR_MIN_DP)
                    // Follows the drag live; saved to settings when the drag ends.
                    var sidebarDp by remember { mutableFloatStateOf(settings.sidebarWidth.toFloat()) }
                    val shownDp = sidebarDp.coerceIn(SIDEBAR_MIN_DP, maxSidebar)

                    Row(Modifier.fillMaxSize()) {
                        if (sidebarVisible) {
                            SidebarTheme(sidebarColorOf(settings.sidebarColor)) {
                                // A movie or episode replaces the channel list with its own panel.
                                if (vodPlaying) {
                                    VodSidebar(
                                        vm = vm,
                                        onOpenSettings = { showSettings = true },
                                        modifier = Modifier.width(shownDp.dp).fillMaxHeight(),
                                    )
                                } else {
                                    ChannelSidebar(
                                        vm = vm,
                                        onOpenSettings = { showSettings = true },
                                        onAddSource = { showSources = true },
                                        modifier = Modifier.width(shownDp.dp).fillMaxHeight(),
                                    )
                                }
                                PaneDragHandle(
                                    onDrag = { deltaPx ->
                                        // Build on the live value: several deltas can arrive between frames.
                                        sidebarDp = (sidebarDp.coerceIn(SIDEBAR_MIN_DP, maxSidebar) + deltaPx / density.density)
                                            .coerceIn(SIDEBAR_MIN_DP, maxSidebar)
                                    },
                                    onDragStopped = {
                                        vm.updateSettings { it.copy(sidebarWidth = sidebarDp.toInt()) }
                                    },
                                    onReset = {
                                        sidebarDp = DEFAULT_SIDEBAR_DP
                                        vm.updateSettings { it.copy(sidebarWidth = DEFAULT_SIDEBAR_DP.toInt()) }
                                    },
                                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                            }
                        }
                        PlayerPane(
                            vm = vm,
                            fullscreen = fullscreen,
                            sidebarVisible = sidebarVisible,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (showSources) SourcesDialog(vm, onDismiss = { showSources = false })
            if (showSettings) {
                SettingsDialog(
                    vm,
                    onDismiss = { showSettings = false },
                    onOpenSources = {
                        showSettings = false
                        showSources = true
                    },
                )
            }
        }
    }
}
