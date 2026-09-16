package digital.demilich.grimoire.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.webkit.WebViewAssetLoader
import digital.demilich.grimoire.MainActivity
import digital.demilich.grimoire.R
import digital.demilich.grimoire.bridge.GrimoireBridge
import digital.demilich.grimoire.data.GrimoireTool
import digital.demilich.grimoire.ui.theme.GrimoireTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "OverlayService"

// Shared between the Compose sizing below and the dismiss-zone hit test in
// isBubbleOverDismissZone() - kept as named dp constants (rather than each
// place hardcoding its own numbers) so the hit-test math can't silently drift
// out of sync with what's actually drawn on screen.
private const val BUBBLE_SIZE_DP = 56
private const val DISMISS_ZONE_SIZE_DP = 72
private const val DISMISS_ZONE_HIGHLIGHTED_SIZE_DP = 96
private const val DISMISS_ZONE_BOTTOM_PADDING_DP = 32

/**
 * Owns the whole floating overlay: a small draggable bubble (always present while
 * the overlay is "on") and, on tap, an expandable panel showing either the tool
 * grid or one tool's WebView. Runs as a foreground service so the system doesn't
 * reclaim it while the note-taking app underneath scales up memory use.
 *
 * Three independent WindowManager windows, added/removed as needed rather than
 * one window whose flags/size are mutated in place - simpler lifecycle, and lets
 * the bubble stay FLAG_NOT_FOCUSABLE (so the app underneath keeps its keyboard)
 * while the panel is focusable (so a tool's text inputs work).
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISMISS = "digital.demilich.grimoire.action.DISMISS"

        // MainActivity's own source of truth for "is the overlay currently up" -
        // there's no clean API to query a Service's running state from outside it,
        // so the service publishes it here instead. Only meaningful within this
        // process, which is fine: that's the only place either side of this runs.
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        // Only valid to call while isRunning is true - stopping via startService()
        // when nothing is running would spin up a fresh instance (onCreate adds a
        // bubble) just to immediately tear it down again.
        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_DISMISS))
        }
    }

    private lateinit var windowManager: WindowManager

    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var dismissZoneView: ComposeView? = null

    private val bubbleLifecycleOwner = ComposeOverlayLifecycleOwner()
    private val panelLifecycleOwner = ComposeOverlayLifecycleOwner()
    private val dismissLifecycleOwner = ComposeOverlayLifecycleOwner()

    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var isOverDismissZone by mutableStateOf(false)
    private var selectedTool by mutableStateOf<GrimoireTool?>(null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubbleView()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            dismissAll()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        removeAllViews()
        _isRunning.value = false
        super.onDestroy()
    }

    // --- Notification ---

    private fun buildNotification(): android.app.Notification {
        val dismissIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dice)
            .setContentTitle("Quick Grimoire is floating")
            .setContentText("Tap the bubble to open your tools.")
            .setContentIntent(contentIntent)
            .addAction(0, "Dismiss Overlay", dismissIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- Bubble window (collapsed, always present) ---

    private fun addBubbleView() {
        val view = ComposeView(this)
        bubbleLifecycleOwner.attachTo(view)
        view.setContent {
            GrimoireTheme {
                BubbleBubble(
                    isOverDismissZone = isOverDismissZone,
                    onTap = { togglePanel() },
                    onDragStart = { showDismissZone() },
                    onDrag = { dx, dy -> moveBubble(dx, dy) },
                    onDragEnd = { endDrag() },
                )
            }
        }
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 150
            y = 150
        }
        windowManager.addView(view, bubbleParams)
        bubbleLifecycleOwner.onWindowAdded()
        bubbleView = view
    }

    private fun moveBubble(dx: Float, dy: Float) {
        bubbleParams.x += dx.roundToInt()
        bubbleParams.y += dy.roundToInt()
        bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
        isOverDismissZone = isBubbleOverDismissZone()
    }

    // bubbleParams.x/y are raw pixels (WindowManager.LayoutParams doesn't know
    // about dp), so the Compose sizes above have to be converted through the
    // screen density before comparing against them - comparing the dp literals
    // directly against bubbleParams.y would only happen to work on an exact
    // 1x-density screen and silently misjudge the hit zone on everything else.
    private fun isBubbleOverDismissZone(): Boolean {
        val density = resources.displayMetrics.density
        val bubbleCenterY = bubbleParams.y + (BUBBLE_SIZE_DP / 2f) * density
        val dismissZoneTopY = resources.displayMetrics.heightPixels -
            (DISMISS_ZONE_HIGHLIGHTED_SIZE_DP + DISMISS_ZONE_BOTTOM_PADDING_DP) * density
        return bubbleCenterY > dismissZoneTopY
    }

    private fun endDrag() {
        hideDismissZone()
        if (isOverDismissZone) {
            dismissAll()
        }
        isOverDismissZone = false
    }

    // --- Dismiss drop-zone window (shown only while dragging) ---

    private fun showDismissZone() {
        if (dismissZoneView != null) return
        val view = ComposeView(this)
        dismissLifecycleOwner.attachTo(view)
        view.setContent {
            GrimoireTheme { DismissZone(highlighted = isOverDismissZone) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_TOUCHABLE: this window is purely a visual target, never a
            // touch target - the bubble decides whether it's "over" this zone
            // itself (isBubbleOverDismissZone), so without this flag the zone
            // would sit on top of the bubble and steal its drag/tap events.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 48
        }
        windowManager.addView(view, params)
        dismissLifecycleOwner.onWindowAdded()
        dismissZoneView = view
    }

    private fun hideDismissZone() {
        dismissZoneView?.let {
            windowManager.removeView(it)
            dismissLifecycleOwner.onWindowRemoved()
        }
        dismissZoneView = null
    }

    // --- Panel window (expanded tray, added/removed on toggle) ---

    private fun togglePanel() {
        if (panelView != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return
        val view = ComposeView(this)
        panelLifecycleOwner.attachTo(view)
        view.setContent {
            GrimoireTheme {
                PanelContent(
                    tool = selectedTool,
                    onSelectTool = { tool -> selectedTool = tool },
                    onBackToGrid = { selectedTool = null },
                    onCollapse = { hidePanel() },
                )
            }
        }
        // Tap-outside-to-collapse: ACTION_OUTSIDE is only ever delivered at all
        // if FLAG_WATCH_OUTSIDE_TOUCH is set on this window (an easy thing to
        // miss - without it, this listener would just silently never fire).
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else {
                false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(view, params)
        panelLifecycleOwner.onWindowAdded()
        panelView = view
    }

    private fun hidePanel() {
        panelView?.let {
            windowManager.removeView(it)
            panelLifecycleOwner.onWindowRemoved()
        }
        panelView = null
    }

    // --- Full teardown ---

    private fun dismissAll() {
        removeAllViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeAllViews() {
        hidePanel()
        hideDismissZone()
        bubbleView?.let {
            windowManager.removeView(it)
            bubbleLifecycleOwner.onWindowRemoved()
        }
        bubbleView = null
    }

    // --- Compose content ---

    @Composable
    private fun BubbleBubble(
        isOverDismissZone: Boolean,
        onTap: () -> Unit,
        onDragStart: () -> Unit,
        onDrag: (Float, Float) -> Unit,
        onDragEnd: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(BUBBLE_SIZE_DP.dp)
                .clip(CircleShape)
                .background(
                    if (isOverDismissZone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                // Hand-rolled instead of detectDragGestures/detectTapGestures: this
                // bubble needs BOTH a tap (open the panel) and a drag (move the
                // bubble, check the dismiss zone) on the exact same pointer, which
                // Compose's separate gesture detectors don't compose cleanly - so
                // this tracks one pointer's whole down-to-up lifecycle itself and
                // decides tap-vs-drag from whether it ever moved past touch slop.
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        awaitFirstDown()
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val delta = change.positionChange()
                            if (!dragging && delta.getDistance() > touchSlop) {
                                dragging = true
                                onDragStart()
                            }
                            if (dragging) {
                                change.consume()
                                onDrag(delta.x, delta.y)
                            }
                            if (!change.pressed) {
                                if (dragging) onDragEnd() else onTap()
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_dice),
                contentDescription = "Quick Grimoire",
                modifier = Modifier.size(32.dp),
            )
        }
    }

    @Composable
    private fun DismissZone(highlighted: Boolean) {
        Box(
            modifier = Modifier
                .padding(bottom = DISMISS_ZONE_BOTTOM_PADDING_DP.dp)
                .size(if (highlighted) DISMISS_ZONE_HIGHLIGHTED_SIZE_DP.dp else DISMISS_ZONE_SIZE_DP.dp)
                .clip(CircleShape)
                .background(
                    if (highlighted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Drop here to dismiss the overlay",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }

    @Composable
    private fun PanelContent(
        tool: GrimoireTool?,
        onSelectTool: (GrimoireTool) -> Unit,
        onBackToGrid: () -> Unit,
        onCollapse: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.size(320.dp, 480.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tool != null) {
                        IconButton(onClick = onBackToGrid) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                        }
                        Text(
                            tool.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    } else {
                        Text(
                            "Quick Grimoire",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (tool == null) {
                        ToolGrid(onSelectTool = onSelectTool)
                    } else {
                        ToolWebView(tool = tool)
                    }
                }
            }
        }
    }

    @Composable
    private fun ToolGrid(onSelectTool: (GrimoireTool) -> Unit) {
        val tools = remember { GrimoireTool.entries.filter { !it.unlisted } }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(8.dp),
        ) {
            items(tools, key = { it.id }) { tool ->
                ToolTile(tool = tool, onClick = { onSelectTool(tool) })
            }
        }
    }

    @Composable
    private fun ToolTile(tool: GrimoireTool, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .padding(6.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(tool.id) {
                    detectTapGestures(onTap = { onClick() })
                }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(tool.iconRes),
                contentDescription = tool.displayName,
                modifier = Modifier.size(36.dp),
            )
            Text(
                tool.displayName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    /**
     * Loads a tool's self-contained page from app/src/main/assets/ via
     * WebViewAssetLoader (serves it from a virtual https origin rather than
     * file://, avoiding the CORS and mixed-content restrictions plain file://
     * URLs run into - Google's recommended way to load bundled local content).
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ToolWebView(tool: GrimoireTool) {
        val isDark = isSystemInDarkTheme()
        val appContext = applicationContext
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            super.onReceivedError(view, request, error)
                            Log.e(TAG, "Load error for ${request.url}: ${error.description} (${error.errorCode})")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d(
                                TAG,
                                "console: ${consoleMessage.message()} " +
                                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                            )
                            return true
                        }
                    }
                    addJavascriptInterface(GrimoireBridge(appContext), "AndroidBridge")
                    val theme = if (isDark) "dark" else "light"
                    loadUrl("https://appassets.androidplatform.net/assets/${tool.assetPath}?theme=$theme")
                }
            },
            update = { webView ->
                val theme = if (isDark) "dark" else "light"
                webView.evaluateJavascript(
                    "document.documentElement.setAttribute('data-theme', '$theme');",
                    null,
                )
            },
        )
    }
}
