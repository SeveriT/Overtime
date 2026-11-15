package fi.severi.overtime.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.severi.overtime.AppViewModel

/** M3 standard navigation drawer width. */
private val SidebarWidth = 360.dp

@Composable
fun App(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    OvertimeTheme(accent = accentColorOf(settings.accentColor)) {
        val sources by vm.sources.collectAsState()
        val fullscreen by vm.fullscreen.collectAsState()
        val sidebarVisible by vm.sidebarVisible.collectAsState()
        var showSources by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        // First launch: nothing configured yet, open the sources dialog straight away.
        LaunchedEffect(sources.isEmpty()) { if (sources.isEmpty()) showSources = true }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                if (sidebarVisible) {
                    SidebarTheme(sidebarColorOf(settings.sidebarColor)) {
                        ChannelSidebar(
                            vm = vm,
                            onOpenSettings = { showSettings = true },
                            onAddSource = { showSources = true },
                            modifier = Modifier.width(SidebarWidth).fillMaxHeight(),
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
