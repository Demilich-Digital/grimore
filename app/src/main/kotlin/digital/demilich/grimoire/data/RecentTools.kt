package digital.demilich.grimoire.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

private const val MAX_STORED = 8
private val RECENT_TOOL_IDS_KEY = stringPreferencesKey("recent_tool_ids")

/**
 * Most-recently-opened tools, newest first. Capped at [MAX_STORED] as a
 * storage-size bound only - tool ids are stored comma-joined (they're always
 * lowercase-hyphenated, never containing a comma, so no escaping needed).
 * How many of these the bubble's fan-out actually displays at once is a
 * separate, screen-space-driven decision made where it's shown
 * (OverlayService), not here.
 */
object RecentTools {
    suspend fun get(context: Context): List<GrimoireTool> {
        val raw = context.appDataStore.data.first()[RECENT_TOOL_IDS_KEY] ?: return emptyList()
        return raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { id -> GrimoireTool.entries.find { it.id == id } }
    }

    suspend fun recordUsed(context: Context, tool: GrimoireTool) {
        context.appDataStore.edit { prefs ->
            val current = (prefs[RECENT_TOOL_IDS_KEY] ?: "").split(",").filter { it.isNotBlank() }
            val updated = (listOf(tool.id) + current.filter { it != tool.id }).take(MAX_STORED)
            prefs[RECENT_TOOL_IDS_KEY] = updated.joinToString(",")
        }
    }
}
