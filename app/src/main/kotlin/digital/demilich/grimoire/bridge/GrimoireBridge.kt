package digital.demilich.grimoire.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import digital.demilich.grimoire.data.appDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * Exposed to a tool's WebView as `window.AndroidBridge`. Covers the two things a
 * static local page can't do for itself: durable storage that survives a process
 * kill (localStorage inside a WebView is not guaranteed to survive Android
 * reclaiming the overlay's process the way it would in a normal browser tab), and
 * pushing a result onto the system clipboard so it can be pasted into whatever
 * note-taking app the overlay is floating over.
 *
 * Deliberately generic - not per-tool. None of the ported tools call this yet
 * (Custom Dice still uses its own localStorage - see assets/tools/custom-dice/
 * index.html); it's available for a tool to opt into.
 */
class GrimoireBridge(private val context: Context) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val macrosKey = stringPreferencesKey("user_macros")

    @JavascriptInterface
    fun saveMacrosState(jsonString: String) {
        runBlocking { context.appDataStore.edit { prefs -> prefs[macrosKey] = jsonString } }
    }

    @JavascriptInterface
    fun getMacrosState(): String {
        return runBlocking { context.appDataStore.data.first()[macrosKey] ?: "[]" }
    }

    @JavascriptInterface
    fun copyTextToClipboard(text: String) {
        val clip = ClipData.newPlainText("Grimoire Result", text)
        clipboardManager.setPrimaryClip(clip)
    }

    @JavascriptInterface
    fun copyImageToClipboard(base64Image: String) {
        try {
            val cleanBase64 = base64Image.substringAfter("base64,")
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (imageBytes.size > 3 * 1024 * 1024) return // 3MB safety limit

            val cachePath = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(cachePath, "grimoire_export.png")
            FileOutputStream(file).use { out -> out.write(imageBytes) }

            val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val clip = ClipData.newUri(context.contentResolver, "Grimoire Image", contentUri)
            clipboardManager.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
