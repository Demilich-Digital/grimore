package digital.demilich.grimoire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// digital.demilich - dark ink / cream duotone with a purple accent, matched to
// the demilich.digital site's own light/dark theme tokens.
//
// `background` matches the tool pages' own CSS --bg (see assets/tools/), so the
// overlay panel's Surface never flashes a mismatched color before the WebView
// paints. `surface` is the distinct, deliberately-off native-window tone
// MainActivity uses for the launcher screen - brighter than the panel
// background in dark mode, darker in light mode - so the two windows read as
// visually separate rather than identical.
private val LightColors = lightColorScheme(
    primary = Color(0xFF170022),
    onPrimary = Color(0xFFF4F0F2),
    background = Color(0xFFF4F0F2),
    surface = Color(0xFFEAE2E7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDE7EA),
    onPrimary = Color(0xFF100016),
    background = Color(0xFF100016),
    surface = Color(0xFF150019),
)

@Composable
fun GrimoireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
