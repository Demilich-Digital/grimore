package digital.demilich.grimoire.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val ENABLED_PACK_IDS_KEY = stringPreferencesKey("enabled_pack_ids")

/**
 * Which [ToolPack]s are currently enabled. Unlike RecentTools/PanelPosition
 * (each only ever written by the one component that reads them), this can be
 * written by MainActivity's toggle screen while OverlayService's tool list -
 * a separate component, alive for as long as the overlay is running - needs
 * to reflect that change without a restart. [flow] is exposed as a live
 * DataStore Flow rather than a one-shot suspend read for exactly that reason.
 */
object EnabledPacks {
    fun flow(context: Context): Flow<Set<ToolPack>> =
        context.appDataStore.data.map { prefs -> parse(prefs[ENABLED_PACK_IDS_KEY]) }

    suspend fun setEnabled(context: Context, pack: ToolPack, enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            val current = parse(prefs[ENABLED_PACK_IDS_KEY])
            val updated = if (enabled) current + pack else current - pack
            prefs[ENABLED_PACK_IDS_KEY] = updated.joinToString(",") { it.id }
        }
    }

    // Null (never set - first launch, or an upgrade from before packs
    // existed) falls back to each pack's own defaultEnabled. A stored empty
    // string is NOT the same as null - it means the user deliberately
    // disabled every pack, and must stay empty rather than reverting to the
    // defaults on every read.
    private fun parse(raw: String?): Set<ToolPack> =
        if (raw == null) {
            ToolPack.entries.filter { it.defaultEnabled }.toSet()
        } else {
            raw.split(",").mapNotNull { ToolPack.fromId(it) }.toSet()
        }
}
