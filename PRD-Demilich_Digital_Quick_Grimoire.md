# PRODUCT REQUIREMENTS DOCUMENT (PRD)

## Project Overview
* **Project Name:** Demilich Digital Quick Grimoire
* **Core Function:** An offline-first, floating overlay utility providing full-feature quick-access tools (dice rollers, complex custom random tables) over third-party note-taking apps like Obsidian.
* **Target OS Configuration:** 
  * `compileSdk = 36`
  * `targetSdk = 36` (Strict Google Play Compliance for New Apps)
  * `minSdk = 29` (Ensures full framework support for modern `TYPE_APPLICATION_OVERLAY`)
* **License:** MIT License.
* **Architecture Style:** Monolithic Single-Flavor. Full feature parity across both the Play Store binary and the alternative branded sideload path.
* **UI/UX Strategy:** UI layer decoupled from framework. Isolated tools run as static Single Page Applications (SPAs) inside an offline secure Android WebView container.

---

## 1. Architectural Constraints (Strict Non-Negotiables)
* **Zero Network Access:** The `android.permission.INTERNET` permission **must never** be declared anywhere in the source tree or `AndroidManifest.xml`.
* **Storage & Persistence Isolation:** No external databases are required for static configurations. User state, custom roll macros, and settings must be stored 100% locally on the device using **Jetpack DataStore** as stringified JSON payloads.
* **Direct Branding Swaps:** The project utilizes a clean, single-flavor `build.gradle`. Rebranding for sideloading will be handled via manual package/string overrides or an external build script, rather than native Gradle product flavors.

---

## 2. Google Play Review Compliance Framework
To ensure an effortless review pass on the Play Store despite utilizing the highly audited `SYSTEM_ALERT_WINDOW` permission, the implementation must enforce these guardrails:

### Requirement 2.1: Transparent Privacy Onboarding
Because the app uses overlay permissions, Google requires clear user communication during setup.
* **Trigger:** If `Settings.canDrawOverlays(context)` returns `false`, present an explicit explanatory dialog.
* **Onboarding Copy:** *"Demilich Digital Quick Grimoire requires the 'Display over other apps' permission to float a dice roller over your note-taking space. Because this app completely lacks Internet permissions, it is technically impossible for this overlay to intercept, log, or transmit your keystrokes or data."*
* **The Redirect Action:** A prominent button launching the system intent:
  ```kotlin
  val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
  context.startActivity(intent)
  ```

### Requirement 2.2: Uncluttered Lifecycle & Dismissal (Anti-Malware Policy)
Persistent system overlays without an obvious exit path get flagged during review.
* **Dismiss Target:** The floating bubble layout must feature an intuitive drag-to-delete drop zone (e.g., an "X" circle anchoring at the bottom center of the screen when dragging).
* **Service Sync:** Tearing down the bubble must trigger a lifecycle cleanup that explicitly shuts down the backing Foreground Service, clearing the active system notification.

---

## 3. Core Feature Requirements (Universal Feature Set)
Both distribution channels receive the full suite of features:
* **Expandable Layout:** A subtle floating action bubble that hovers unobtrusively over Obsidian and smoothly expands into a full web-rendered panel tray when tapped.
* **Isolated Webview Architecture:** Tools (Dice Matrix, Random Tables) are implemented as a local JavaScript SPA, running in a headless secure environment.
* **Clipboard Pipeline:** Ability to push bounded text results and generated images directly into the system clipboard buffer to ensure flawless cross-application workflows with note-taking suites.

---

## 4. Technical Implementation Specifications

### Component A: The Foreground Service (`OverlayService.kt`)
To guarantee the system doesn't kill the floating tool when note-taking apps scale up memory consumption, the overlay must run inside a Foreground Service.
* **Notification Settings:** 
  * Channel ID: `overlay_service_channel`
  * Importance: `NotificationManager.IMPORTANCE_LOW` (runs silently without an intrusive pop-up ringtone on launch).
  * Direct Actions: Must embed a prominent action button titled **"Dismiss Overlay"** to let users terminate the application service on the fly.

### Component B: Window Manager Injection Layout
The window layer parameters must keep background text fields interactive:
```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = 150
    y = 150
}
```
* **Critical Flag Context:** `FLAG_NOT_FOCUSABLE` ensures that the software keyboard continues to interact naturally with the note-taking application underneath while your bubble sits on top.

### Component C: Zero-Internet Jetpack Webkit Configuration
To avoid CORS blocks and path errors without declaring internet permissions, implement `WebViewAssetLoader`:
```kotlin
// In build.gradle: implementation "androidx.webkit:webkit:1.12.0"

class LocalAssetWebViewClient(private val assetLoader: WebViewAssetLoader) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return assetLoader.shouldInterceptRequest(request.url)
    }
}

// WebView Construction inside Foreground Service:
val assetLoader = WebViewAssetLoader.Builder()
    .setDomain("appassets.androidplatform.net")
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

val webView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
    }
    webViewClient = LocalAssetWebViewClient(assetLoader)
    loadUrl("https://appassets.androidplatform.net/assets/grimoire-ui/index.html")
}
```

### Component D: Persistence and Clipboard Bridge (`GrimoireBridge.kt`)
Expose native capabilities to the Webview window framework layer via a `@JavascriptInterface`:
```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

private val Context.dataStore by preferencesDataStore(name = "grimoire_settings")

class GrimoireBridge(private val context: Context) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val macrosKey = stringPreferencesKey("user_macros")

    @JavascriptInterface
    fun saveMacrosState(jsonString: String) {
        runBlocking { context.dataStore.edit { prefs -> prefs[macrosKey] = jsonString } }
    }

    @JavascriptInterface
    fun getMacrosState(): String {
        return runBlocking { context.dataStore.data.first()[macrosKey] ?: "[]" }
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
            if (imageBytes.size > 3 * 1024 * 1024) return // 3MB Safety limit

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
```

### Component E: Manifest File Sharing Permissions
```xml
<!-- AndroidManifest.xml -->
<application ...>
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>

<!-- res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_images" path="shared_images/" />
</paths>
```

---

## 5. Web Environment API Bridge Integrations (JS/TS)
The embedded SPA can interface directly with the platform using simple global hooks:
```javascript
// Persistent Macro States
window.AndroidBridge.saveMacrosState(JSON.stringify([{ name: "Fireball", formula: "8d6" }]));
const savedMacros = JSON.parse(window.AndroidBridge.getMacrosState());

// Clipboard Pipelines
window.AndroidBridge.copyTextToClipboard("Rolled 42 on the Encounter Table!");
window.AndroidBridge.copyImageToClipboard("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...");
```
