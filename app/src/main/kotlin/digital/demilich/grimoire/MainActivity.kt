package digital.demilich.grimoire

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import digital.demilich.grimoire.data.EnabledPacks
import digital.demilich.grimoire.data.ToolPack
import digital.demilich.grimoire.overlay.OverlayService
import digital.demilich.grimoire.ui.theme.GrimoireTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var canDrawOverlays by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrimoireTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    val overlayRunning by OverlayService.isRunning.collectAsState()
                    val enabledPacks by EnabledPacks.flow(this)
                        .collectAsState(initial = ToolPack.entries.filter { it.defaultEnabled }.toSet())
                    if (!canDrawOverlays) {
                        OverlayPermissionScreen(onOpenSettings = ::openOverlaySettings)
                    } else {
                        StartStopScreen(
                            running = overlayRunning,
                            onStart = { OverlayService.start(this) },
                            onStop = { OverlayService.stop(this) },
                            enabledPacks = enabledPacks,
                            onTogglePack = { pack, enabled ->
                                lifecycleScope.launch { EnabledPacks.setEnabled(this@MainActivity, pack, enabled) }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // No callback exists for "the user came back from the overlay-permission
        // settings screen" - re-check every time this Activity becomes visible
        // again, same as canDrawOverlays() is meant to be polled.
        canDrawOverlays = Settings.canDrawOverlays(this)
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}

@Composable
private fun OverlayPermissionScreen(onOpenSettings: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Turn on the floating overlay", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Demilich Digital Quick Grimoire requires the \"Display over other " +
                    "apps\" permission to float a dice roller over your note-taking " +
                    "space. Because this app completely lacks Internet permissions, " +
                    "it is technically impossible for this overlay to intercept, " +
                    "log, or transmit your keystrokes or data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Grant overlay permission")
            }
        }
    }
}

@Composable
private fun StartStopScreen(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    enabledPacks: Set<ToolPack>,
    onTogglePack: (ToolPack, Boolean) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Quick Grimoire", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A floating bubble that opens into your dice rollers and oracle " +
                    "tools, over any app. Drag it to the bottom of the screen to " +
                    "dismiss it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (running) {
                Text(
                    "The bubble is floating - look for it over your other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop floating tools")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start floating tools")
                }
            }
            HorizontalDivider()
            PackToggleList(enabledPacks = enabledPacks, onTogglePack = onTogglePack)
        }
    }
}

// The only place ToolPack's on/off state is edited - OverlayService's tool
// list only ever reads it (see EnabledPacks.flow there), so a pack toggled
// off here disappears from that list live, without restarting the overlay.
@Composable
private fun PackToggleList(enabledPacks: Set<ToolPack>, onTogglePack: (ToolPack, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tool packs", style = MaterialTheme.typography.titleMedium)
        Text(
            "Turn a pack off to shorten the tool list in the overlay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToolPack.entries.forEach { pack ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pack.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = pack in enabledPacks,
                    onCheckedChange = { onTogglePack(pack, it) },
                )
            }
        }
    }
}
