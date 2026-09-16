package digital.demilich.grimoire.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

private val PANEL_X_KEY = intPreferencesKey("panel_position_x")
private val PANEL_Y_KEY = intPreferencesKey("panel_position_y")

/**
 * The floating tool panel's last dragged position, in raw screen pixels under
 * TOP|START gravity - matches how OverlayService's WindowManager.LayoutParams
 * already tracks it, so no unit conversion is needed on either side of
 * storage. Null (either key missing) means "never dragged yet, use the
 * default centered position".
 */
object PanelPosition {
    suspend fun get(context: Context): Pair<Int, Int>? {
        val prefs = context.appDataStore.data.first()
        val x = prefs[PANEL_X_KEY] ?: return null
        val y = prefs[PANEL_Y_KEY] ?: return null
        return x to y
    }

    suspend fun save(context: Context, x: Int, y: Int) {
        context.appDataStore.edit { prefs ->
            prefs[PANEL_X_KEY] = x
            prefs[PANEL_Y_KEY] = y
        }
    }
}
