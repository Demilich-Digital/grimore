package digital.demilich.grimoire

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import digital.demilich.grimoire.overlay.OverlayService
import digital.demilich.grimoire.ui.theme.GrimoireTheme

class MainActivity : ComponentActivity() {

    private var canDrawOverlays by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrimoireTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    val overlayRunning by OverlayService.isRunning.collectAsState()
                    if (!canDrawOverlays) {
                        OverlayPermissionScreen(onOpenSettings = ::openOverlaySettings)
                    } else {
                        StartStopScreen(
                            running = overlayRunning,
                            onStart = { OverlayService.start(this) },
                            onStop = { OverlayService.stop(this) },
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
private fun StartStopScreen(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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
        }
    }
}
