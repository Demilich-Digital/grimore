package digital.demilich.grimoire.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single shared DataStore instance backing all of this app's local
 * persistence (bridge-exposed macros, recent-tool tracking, ...). Jetpack's
 * `preferencesDataStore` delegate throws at runtime if two separate delegate
 * properties are created for the same file name within one process, so this
 * must be the only declaration of it - everything else imports this property
 * rather than declaring its own.
 */
val Context.appDataStore by preferencesDataStore(name = "grimoire_settings")
