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
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.webkit.WebViewAssetLoader
import digital.demilich.grimoire.MainActivity
import digital.demilich.grimoire.R
import digital.demilich.grimoire.bridge.GrimoireBridge
import digital.demilich.grimoire.data.GrimoireTool
import digital.demilich.grimoire.data.PanelPosition
import digital.demilich.grimoire.data.RecentTools
import digital.demilich.grimoire.ui.theme.GrimoireTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "OverlayService"

// Shared between the Compose sizing below and the geometry math in
// isBubbleOverDismissZone()/computeFanLayout() - kept as named dp constants
// (rather than each place hardcoding its own numbers) so that math can't
// silently drift out of sync with what's actually drawn on screen.
private const val BUBBLE_SIZE_DP = 56
private const val DISMISS_ZONE_SIZE_DP = 72
private const val DISMISS_ZONE_HIGHLIGHTED_SIZE_DP = 96
private const val DISMISS_ZONE_BOTTOM_PADDING_DP = 32

// Fixed panel footprint - shared between the Compose Surface size in
// PanelContent and the clamp math in showPanel()/movePanel() so the two
// can't silently drift apart the way BUBBLE_SIZE_DP guards against above.
private const val PANEL_WIDTH_DP = 340
private const val PANEL_HEIGHT_DP = 560

// Explicit rather than left to wrap-content: the title Row's drag handle
// (see PanelContent) uses fillMaxHeight() to make the whole bar draggable,
// not just the title text's own line height. Without a fixed height on the
// Row itself, Column hands every child the full remaining panel height as
// its max-height constraint (nothing else claims a share of it via weight),
// so that fillMaxHeight() would resolve against the *entire panel*, not just
// the bar - expanding the Row to eat the whole window and squeezing the
// WebView/list below it down to nothing.
private const val PANEL_TITLE_BAR_HEIGHT_DP = 56

// The fan's icon count adapts to available screen space rather than being
// fixed (see computeFanLayout) - these bound that adaptation. Radius grows
// with available space up to FAN_MAX_RADIUS_DP (e.g. a bubble near the
// middle of a large tablet screen), and is otherwise capped by whatever
// room is actually there so the arc can never clip off-screen; only the
// absolute floor risks that, as a last-resort fallback for a bubble
// wedged into an extreme corner of a tiny screen.
private const val FAN_ICON_SIZE_DP = 44
private const val FAN_ICON_ARC_SPACING_DP = 68f
private const val FAN_ABSOLUTE_MIN_RADIUS_DP = 40f
private const val FAN_MAX_RADIUS_DP = 160f
private const val FAN_EDGE_MARGIN_DP = 12f

// Each icon's title label, shown just outside it (below when the icon sits
// below/level with the bubble, above when it sits above - see the
// per-icon labelBelow flag in computeFanLayout). Sized and reserved for
// explicitly, on top of the icon's own footprint, so the window's bounding
// box can be grown to fit it - otherwise it'd hit the exact same clipping
// bug the plain icons did.
private const val FAN_LABEL_WIDTH_DP = 60f
private const val FAN_LABEL_HEIGHT_DP = 28f
private const val FAN_LABEL_GAP_DP = 2f

// A label is wider than the icon it's centered under, so it overhangs past
// the icon's own left/right edges by this much - the window needs that much
// extra horizontal room wherever an icon can sit at the arc's X-extreme.
private const val FAN_LABEL_OVERHANG_DP = (FAN_LABEL_WIDTH_DP - FAN_ICON_SIZE_DP) / 2f

// How close to one edge (as a fraction of that axis's screen size) counts as
// "near" it for the purposes of picking the fan's shape - see the mode
// breakdown in computeFanLayout(). 0.3 means the outer 30% of the screen on
// each side counts as "near that edge"; the middle 40% doesn't.
private const val EDGE_ZONE_FRACTION = 0.3f

// Hand-rolled instead of detectDragGestures/detectTapGestures: some regions
// (the bubble itself, and the bubble-shaped hit region layered into the fan
// window below so a click-and-drag started there works the same as on the
// real bubble) need BOTH a tap and a drag recognized on the exact same
// pointer, which Compose's separate gesture detectors don't compose cleanly
// - so this tracks one pointer's whole down-to-up lifecycle itself and
// decides tap-vs-drag from whether it ever moved past touch slop.
//
// Deliberately built on pointerInteropFilter's raw android.view.MotionEvent
// rather than Compose's pointerInput/PointerInputChange, and onDragStart/
// onDrag report rawX/rawY (absolute screen coordinates) rather than a
// Compose-local position or delta. This wasn't the first design tried here -
// three earlier attempts drove bubbleParams off Compose's local per-event
// position and each failed differently (constant offset, runaway, laggy
// flicker). The common thread: Compose reports pointer position relative to
// this composable's window, and that window is exactly what moveBubble
// moves via WindowManager.updateViewLayout mid-gesture - so the "local"
// coordinate space Compose measures against is itself shifting under the
// gesture. MotionEvent.rawX/rawY sidestep that entirely: they're absolute
// screen coordinates by API contract, unaffected by anything happening to
// this window.
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.dragOrTap(
    onDragStart: (rawX: Float, rawY: Float) -> Unit,
    onDrag: (rawX: Float, rawY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    var downRawX by remember { mutableStateOf(0f) }
    var downRawY by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(event.rawX - downRawX, event.rawY - downRawY) > touchSlop) {
                    dragging = true
                    onDragStart(event.rawX, event.rawY)
                }
                if (dragging) onDrag(event.rawX, event.rawY)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd() else onTap()
                dragging = false
                true
            }
            else -> false
        }
    }
}

// Keeps a dragged window's top-left corner from ever landing where the
// window would hang off an edge of the screen - value is only let range over
// [0, screenSizePx - windowSizePx], and coerceAtLeast(0) on that upper bound
// covers the (screen smaller than window) edge case, where windowSizePx
// itself is left flush at 0 rather than the range flipping negative.
private fun clampToScreen(value: Int, windowSizePx: Int, screenSizePx: Int): Int =
    value.coerceIn(0, (screenSizePx - windowSizePx).coerceAtLeast(0))

/**
 * Owns the whole floating overlay: a small draggable bubble (always present
 * while the overlay is "on"); on tap, a fan-out arc of recent tools plus a
 * search shortcut; and, once something's picked, an expanded panel showing
 * either the full searchable tool list or one tool's WebView. Runs as a
 * foreground service so the system doesn't reclaim it while the note-taking
 * app underneath scales up memory use.
 *
 * Four independent WindowManager windows, added/removed as needed rather
 * than one window whose flags/size are mutated in place - simpler lifecycle,
 * and lets e.g. the bubble stay FLAG_NOT_FOCUSABLE (so the app underneath
 * keeps its keyboard) independently of the panel, which starts
 * FLAG_NOT_FOCUSABLE too (so it can be left open - dragged out of the way
 * rather than closed - while the user keeps typing in the app underneath) and
 * only toggles focusable transiently when the panel's own text inputs need
 * it - see the flag-toggling comment in showPanel().
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
    private var fanView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var dismissZoneView: ComposeView? = null

    private val bubbleLifecycleOwner = ComposeOverlayLifecycleOwner()
    private val fanLifecycleOwner = ComposeOverlayLifecycleOwner()
    private val panelLifecycleOwner = ComposeOverlayLifecycleOwner()
    private val dismissLifecycleOwner = ComposeOverlayLifecycleOwner()

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private var isOverDismissZone by mutableStateOf(false)
    private var selectedTool by mutableStateOf<GrimoireTool?>(null)
    private var recentTools by mutableStateOf<List<GrimoireTool>>(emptyList())

    // Loaded once in onCreate (see RecentTools's recentTools above for the
    // same pattern) and consulted only when the panel window is first created
    // in showPanel() - once that window exists, panelParams is the live
    // source of truth and this is never read again until the next process.
    private var savedPanelPosition: Pair<Int, Int>? = null

    // Fixed anchors captured once at drag start (see startBubbleDrag) - moveBubble
    // re-derives the target position fresh from these every event rather than
    // accumulating, so a single bad event can't compound into drift.
    private var dragAnchorRawX = 0f
    private var dragAnchorRawY = 0f
    private var dragAnchorBubbleX = 0
    private var dragAnchorBubbleY = 0

    // Same pattern as the bubble's drag anchors above, kept separate since a
    // bubble drag and a panel drag are never in flight at the same time but
    // do belong to two different windows with independent positions.
    private var panelDragAnchorRawX = 0f
    private var panelDragAnchorRawY = 0f
    private var panelDragAnchorX = 0
    private var panelDragAnchorY = 0

    // Off the UI thread for DataStore reads/writes triggered from Compose
    // callbacks (tapping a fan icon or list row) - a blocking call there
    // would jank the tap response. Cancelled in onDestroy().
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubbleView()
        serviceScope.launch { recentTools = RecentTools.get(this@OverlayService) }
        serviceScope.launch { savedPanelPosition = PanelPosition.get(this@OverlayService) }
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
        serviceScope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    // Opens a tool in the panel and records it as most-recently-used - the
    // single path every "pick a tool" interaction (fan icon, list row) goes
    // through, so recency tracking can't be forgotten in one of them.
    private fun selectTool(tool: GrimoireTool) {
        selectedTool = tool
        serviceScope.launch {
            RecentTools.recordUsed(this@OverlayService, tool)
            recentTools = RecentTools.get(this@OverlayService)
        }
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
                    onTap = { toggleFan() },
                    onDragStart = { rawX, rawY ->
                        startBubbleDrag(rawX, rawY)
                        showDismissZone()
                    },
                    onDrag = { rawX, rawY -> moveBubble(rawX, rawY) },
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

    // Called once, right when dragOrTap's touch-slop threshold is first
    // crossed - captures where the finger is (raw screen coords) and where
    // the bubble's window currently sits as the two fixed anchors moveBubble
    // re-derives the target position from on every subsequent event.
    private fun startBubbleDrag(rawX: Float, rawY: Float) {
        dragAnchorRawX = rawX
        dragAnchorRawY = rawY
        dragAnchorBubbleX = bubbleParams.x
        dragAnchorBubbleY = bubbleParams.y
    }

    // rawX/rawY are the touch's current absolute screen position (see
    // dragOrTap's kdoc for why raw coordinates, not Compose's local pointer
    // position). Re-derived fresh from the drag-start anchors every call
    // instead of accumulating a running total, so a single skipped or
    // out-of-order event can't leave the bubble permanently offset from the
    // finger.
    private fun moveBubble(rawX: Float, rawY: Float) {
        bubbleParams.x = dragAnchorBubbleX + (rawX - dragAnchorRawX).roundToInt()
        bubbleParams.y = dragAnchorBubbleY + (rawY - dragAnchorRawY).roundToInt()
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

    // --- Fan window (recent tools + search, shown on bubble tap) ---

    private fun toggleFan() {
        if (fanView != null) hideFan() else showFan()
    }

    private fun showFan() {
        if (fanView != null) return
        val layout = computeFanLayout()
        val view = ComposeView(this)
        fanLifecycleOwner.attachTo(view)
        view.setContent {
            GrimoireTheme {
                FanContent(
                    icons = layout.icons,
                    bubbleOffsetXDp = layout.bubbleOffsetXDp,
                    bubbleOffsetYDp = layout.bubbleOffsetYDp,
                    onSelectTool = { tool ->
                        hideFan()
                        selectTool(tool)
                        showPanel()
                    },
                    onSelectSearch = {
                        hideFan()
                        selectedTool = null
                        showPanel()
                    },
                    onDismiss = { hideFan() },
                    // A click-and-drag started on the bubble's own position
                    // (which the fan window physically covers - see
                    // FanIconSlot's kdoc) should behave exactly like starting
                    // that drag on the real bubble: close the fan immediately
                    // and hand off to the same drag handling the bubble's own
                    // gesture uses.
                    //
                    // Crucially, "close" here can't mean windowManager.
                    // removeView() yet - this callback fires mid-gesture,
                    // while that same fan window is still the one actively
                    // carrying the touch stream. Tearing it down here orphans
                    // the rest of the drag: Android has nowhere left to
                    // deliver the remaining move/up events, so the bubble
                    // moves a few pixels and then just stops until the user
                    // lifts and grabs it again. FanContent instead hides
                    // itself visually (see its draggingBubble flag) while
                    // keeping the window - and the gesture - alive; the
                    // window is only actually removed in onBubbleDragEnd,
                    // once the touch has genuinely finished.
                    onBubbleDragStart = { rawX, rawY ->
                        startBubbleDrag(rawX, rawY)
                        showDismissZone()
                    },
                    onBubbleDrag = { rawX, rawY -> moveBubble(rawX, rawY) },
                    onBubbleDragEnd = {
                        endDrag()
                        hideFan()
                    },
                )
            }
        }
        // Tap-outside-to-dismiss, same ACTION_OUTSIDE/FLAG_WATCH_OUTSIDE_TOUCH
        // pattern as the panel below. This alone isn't enough to make "tap the
        // bubble again" close the fan, though: the fan window's bounding box is
        // sized to include the bubble's own corner (so the arc can be anchored
        // to it), which means it sits in front of the bubble window there and
        // the bubble's own tap handler never sees that touch. FanContent's
        // full-size background tap-catcher is what actually covers that case.
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideFan()
                true
            } else {
                false
            }
        }
        val params = WindowManager.LayoutParams(
            layout.windowWidthPx,
            layout.windowHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = layout.windowXPx
            y = layout.windowYPx
        }
        windowManager.addView(view, params)
        fanLifecycleOwner.onWindowAdded()
        fanView = view
    }

    private fun hideFan() {
        fanView?.let {
            windowManager.removeView(it)
            fanLifecycleOwner.onWindowRemoved()
        }
        fanView = null
    }

    private data class FanIconSlot(
        val tool: GrimoireTool?,
        val centerXDp: Float,
        val centerYDp: Float,
        // Whether this icon's title label sits below it or above it - varies
        // per icon rather than per fan, since a semicircle can have icons on
        // both sides of the bubble's own vertical center within one arc.
        val labelBelow: Boolean,
    )
    private data class FanLayout(
        val windowXPx: Int,
        val windowYPx: Int,
        val windowWidthPx: Int,
        val windowHeightPx: Int,
        val icons: List<FanIconSlot>,
        // Where the bubble itself sits in the fan window's local coordinate
        // space - FanContent uses this to lay an invisible bubble-shaped hit
        // region over the real bubble (which the fan window physically
        // covers), so a click-and-drag started there closes the fan and
        // moves the bubble instead of being silently swallowed.
        val bubbleOffsetXDp: Float,
        val bubbleOffsetYDp: Float,
    )

    /**
     * Lays out the fan's icons anchored to the bubble's current position,
     * always opening toward the middle of the screen (so it can never point
     * off an edge), in one of three shapes depending on where the bubble
     * actually is:
     * - **Corner** (near both a horizontal and a vertical edge): the
     *   original quarter-circle, swept between the two away-from-edge
     *   directions - there's limited room on both axes, so a diagonal
     *   wedge is the only shape with room for more than one or two icons.
     * - **Middle of one edge** (near only a horizontal *or* vertical edge,
     *   not both): a full semicircle, opening away from the near edge and
     *   swinging the *other* axis a full 180 degrees rather than being
     *   arbitrarily biased toward whichever half of that axis the bubble's
     *   center happens to fall on.
     * - **Roughly centered** (near neither edge): falls back to the
     *   corner's quarter-circle shape as a simple default - not something
     *   users are likely to rest the bubble at deliberately.
     *
     * Within whichever shape applies, the icon count still adapts to actual
     * room: more room fits more recent tools at a comfortable tap-target
     * spacing, less room fits fewer, down to just the always-present search
     * icon in the extreme case. One search slot is reserved first; recent
     * tools (most-recent first) fill whatever's left.
     */
    private fun computeFanLayout(): FanLayout {
        val density = resources.displayMetrics.density
        val screenWidthDp = resources.displayMetrics.widthPixels / density
        val screenHeightDp = resources.displayMetrics.heightPixels / density

        val bubbleCenterXDp = bubbleParams.x / density + BUBBLE_SIZE_DP / 2f
        val bubbleCenterYDp = bubbleParams.y / density + BUBBLE_SIZE_DP / 2f

        val nearLeftDp = bubbleCenterXDp
        val nearRightDp = screenWidthDp - bubbleCenterXDp
        val nearTopDp = bubbleCenterYDp
        val nearBottomDp = screenHeightDp - bubbleCenterYDp

        // +1 = the near edge on this axis is the "low" one (left/top), so the
        // arc opens toward +X/+Y; -1 = the near edge is the "high" one
        // (right/bottom), arc opens toward -X/-Y. Meaningless for whichever
        // axis turns out to be the "free" one below, but always computed
        // since it's cheap and every branch needs at most one of them.
        val signX = if (nearLeftDp < nearRightDp) 1f else -1f
        val signY = if (nearTopDp < nearBottomDp) 1f else -1f

        val xNearEdge = min(nearLeftDp, nearRightDp) < screenWidthDp * EDGE_ZONE_FRACTION
        val yNearEdge = min(nearTopDp, nearBottomDp) < screenHeightDp * EDGE_ZONE_FRACTION
        // "Free" = this axis is the one that gets to swing both directions in
        // a semicircle, which only happens when it's NOT near an edge while
        // the OTHER axis IS - mutually exclusive by construction, so at most
        // one of these is ever true.
        val xFree = !xNearEdge && yNearEdge
        val yFree = !yNearEdge && xNearEdge
        val useQuarterArc = !xFree && !yFree

        // How much radius each axis can accommodate: the tighter of both
        // sides if that axis is swinging both ways (free), or just the room
        // in the one direction it actually opens toward otherwise. The
        // smaller of the two axis bounds is what the fan is actually
        // constrained by, same as it was pre-semicircle.
        val radiusBoundXDp = if (xFree) min(nearLeftDp, nearRightDp) else (if (signX > 0) nearRightDp else nearLeftDp)
        val radiusBoundYDp = if (yFree) min(nearTopDp, nearBottomDp) else (if (signY > 0) nearBottomDp else nearTopDp)
        val availableDp = min(radiusBoundXDp, radiusBoundYDp) - FAN_EDGE_MARGIN_DP

        val radiusDp = availableDp.coerceIn(FAN_ABSOLUTE_MIN_RADIUS_DP, FAN_MAX_RADIUS_DP)

        val sweepRadians = if (useQuarterArc) (PI / 2).toFloat() else PI.toFloat()
        val arcLengthDp = radiusDp * sweepRadians
        val maxIconsFit = (arcLengthDp / FAN_ICON_ARC_SPACING_DP).toInt() + 1
        val totalIcons = min(maxIconsFit, recentTools.size + 1).coerceAtLeast(1)
        val recentCount = totalIcons - 1

        // A one-directional (constrained) axis only ever needs room from the
        // bubble's own near edge out to the far icon's outer edge; a free
        // (both-directional) axis needs room on both sides of the bubble's
        // center out to each far icon edge, so its window span is exactly
        // double that, centered on the bubble instead of flush against it.
        // Labels add two more kinds of overhang beyond the plain icon circle:
        // FAN_LABEL_OVERHANG_DP horizontally (a label is wider than its icon,
        // centered under/over it, on every icon regardless of axis mode), and
        // FAN_LABEL_HEIGHT_DP vertically, on whichever vertical side(s) a
        // label can actually land on for this arc's shape - both sides when Y
        // is free (a semicircle can have icons, and so labels, above and
        // below the bubble within the same arc), only the signY side
        // otherwise (every icon's label points the same way).
        val windowWidthDp: Float
        val bubbleOffsetXDp: Float
        if (xFree) {
            windowWidthDp = 2f * radiusDp + FAN_ICON_SIZE_DP + 2f * FAN_LABEL_OVERHANG_DP
            bubbleOffsetXDp = windowWidthDp / 2f
        } else {
            windowWidthDp = BUBBLE_SIZE_DP / 2f + radiusDp + FAN_ICON_SIZE_DP / 2f + FAN_LABEL_OVERHANG_DP
            bubbleOffsetXDp = if (signX > 0) BUBBLE_SIZE_DP / 2f else windowWidthDp - BUBBLE_SIZE_DP / 2f
        }
        val windowHeightDp: Float
        val bubbleOffsetYDp: Float
        if (yFree) {
            windowHeightDp = 2f * radiusDp + FAN_ICON_SIZE_DP + 2f * (FAN_LABEL_GAP_DP + FAN_LABEL_HEIGHT_DP)
            bubbleOffsetYDp = windowHeightDp / 2f
        } else {
            windowHeightDp = BUBBLE_SIZE_DP / 2f + radiusDp + FAN_ICON_SIZE_DP / 2f + FAN_LABEL_GAP_DP + FAN_LABEL_HEIGHT_DP
            bubbleOffsetYDp = if (signY > 0) BUBBLE_SIZE_DP / 2f else windowHeightDp - BUBBLE_SIZE_DP / 2f
        }

        val sweepStart = if (useQuarterArc) 0f else -(PI / 2).toFloat()
        val sweepEnd = (PI / 2).toFloat()

        val icons = (0 until totalIcons).map { i ->
            val t = if (totalIcons == 1) {
                (sweepStart + sweepEnd) / 2f
            } else {
                sweepStart + (i / (totalIcons - 1).toFloat()) * (sweepEnd - sweepStart)
            }
            // dirX/dirY are always a unit vector regardless of shape - only
            // which one is the fixed-sign (constrained) component and which
            // one is the free-swinging cos/sin changes.
            val dirX: Float
            val dirY: Float
            when {
                useQuarterArc -> {
                    dirX = signX * cos(t)
                    dirY = signY * sin(t)
                }
                yFree -> {
                    dirX = signX * cos(t)
                    dirY = sin(t)
                }
                else -> {
                    dirX = sin(t)
                    dirY = signY * cos(t)
                }
            }
            val centerX = bubbleOffsetXDp + radiusDp * dirX
            val centerY = bubbleOffsetYDp + radiusDp * dirY
            val tool = if (i < recentCount) recentTools[i] else null // null slot = search
            FanIconSlot(tool, centerX, centerY, labelBelow = dirY >= 0f)
        }

        val windowLeftDp = bubbleCenterXDp - bubbleOffsetXDp
        val windowTopDp = bubbleCenterYDp - bubbleOffsetYDp

        return FanLayout(
            windowXPx = (windowLeftDp * density).roundToInt(),
            windowYPx = (windowTopDp * density).roundToInt(),
            windowWidthPx = (windowWidthDp * density).roundToInt(),
            windowHeightPx = (windowHeightDp * density).roundToInt(),
            icons = icons,
            bubbleOffsetXDp = bubbleOffsetXDp,
            bubbleOffsetYDp = bubbleOffsetYDp,
        )
    }

    // --- Panel window (expanded tray, opened directly on a tool or the list) ---

    private fun showPanel() {
        if (panelView != null) return
        val density = resources.displayMetrics.density
        val panelWidthPx = (PANEL_WIDTH_DP * density).roundToInt()
        val panelHeightPx = (PANEL_HEIGHT_DP * density).roundToInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val screenHeightPx = resources.displayMetrics.heightPixels

        // Re-clamp a saved position on every open, not just at save time - the
        // screen this was saved on may have been a different size/orientation
        // (rotation, a different device restoring backed-up prefs), and an
        // un-clamped restore could otherwise place the panel back offscreen.
        val (initialX, initialY) = savedPanelPosition?.let { (x, y) ->
            clampToScreen(x, panelWidthPx, screenWidthPx) to clampToScreen(y, panelHeightPx, screenHeightPx)
        } ?: ((screenWidthPx - panelWidthPx) / 2 to (screenHeightPx - panelHeightPx) / 2)

        val view = ComposeView(this)
        panelLifecycleOwner.attachTo(view)
        view.setContent {
            GrimoireTheme {
                PanelContent(
                    tool = selectedTool,
                    onSelectTool = { tool -> selectTool(tool) },
                    onBackToList = { selectedTool = null },
                    onCollapse = { hidePanel() },
                    onTitleDragStart = { rawX, rawY -> startPanelDrag(rawX, rawY) },
                    onTitleDrag = { rawX, rawY -> movePanel(rawX, rawY) },
                    onTitleDragEnd = { endPanelDrag() },
                )
            }
        }
        // The panel starts FLAG_NOT_FOCUSABLE (below) so it never steals
        // keyboard focus from whatever's running underneath - the whole point
        // of leaving it open is to keep typing there while it sits out of the
        // way. That costs the panel's own text inputs (the tool search box, a
        // WebView field) their ability to take focus, since a non-focusable
        // window can't hold it - so it's toggled on demand instead of fixed
        // for the panel's whole lifetime:
        // - Any touch landing in the panel (there's nowhere else this
        //   listener could fire from - it's on the panel's own root view) may
        //   be aimed at one of those inputs, so it clears FLAG_NOT_FOCUSABLE
        //   in case. The flag flip races the very touch that triggered it -
        //   WindowManager grants window focus based on the focusable flag at
        //   dispatch time, and updateViewLayout() here only lands just after
        //   - so the first tap into an unfocused panel only wakes it up; the
        //   input itself needs a second tap to actually take focus. Accepted
        //   trade-off: the common direction (going back to typing in the app
        //   underneath, below) needs no extra tap at all.
        // - Losing window focus (onWindowFocusChangeListener below) means
        //   something else - the app underneath, the bubble, anywhere else -
        //   just took focus instead, so nothing in the panel still needs it:
        //   flip FLAG_NOT_FOCUSABLE back on immediately, so that same tap
        //   finishes handing focus to whatever the user actually tapped, with
        //   no extra step on their side.
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
            ) {
                panelParams.flags = panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(view, panelParams)
            }
            false
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus && panelParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) {
                panelParams.flags = panelParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(view, panelParams)
            }
        }
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        windowManager.addView(view, panelParams)
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

    // Called once, right when dragOrTap's touch-slop threshold is first
    // crossed on the panel's title bar - same anchor-capture approach as
    // startBubbleDrag, just against panelParams instead of bubbleParams.
    private fun startPanelDrag(rawX: Float, rawY: Float) {
        panelDragAnchorRawX = rawX
        panelDragAnchorRawY = rawY
        panelDragAnchorX = panelParams.x
        panelDragAnchorY = panelParams.y
    }

    private fun movePanel(rawX: Float, rawY: Float) {
        val density = resources.displayMetrics.density
        val panelWidthPx = (PANEL_WIDTH_DP * density).roundToInt()
        val panelHeightPx = (PANEL_HEIGHT_DP * density).roundToInt()
        val targetX = panelDragAnchorX + (rawX - panelDragAnchorRawX).roundToInt()
        val targetY = panelDragAnchorY + (rawY - panelDragAnchorRawY).roundToInt()
        panelParams.x = clampToScreen(targetX, panelWidthPx, resources.displayMetrics.widthPixels)
        panelParams.y = clampToScreen(targetY, panelHeightPx, resources.displayMetrics.heightPixels)
        panelView?.let { windowManager.updateViewLayout(it, panelParams) }
    }

    // Persists wherever the drag left the panel, so the next showPanel() (this
    // session or a future one, via savedPanelPosition loaded in onCreate)
    // reopens it in the same place.
    private fun endPanelDrag() {
        val x = panelParams.x
        val y = panelParams.y
        savedPanelPosition = x to y
        serviceScope.launch { PanelPosition.save(this@OverlayService, x, y) }
    }

    // --- Full teardown ---

    private fun dismissAll() {
        removeAllViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeAllViews() {
        hidePanel()
        hideFan()
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
        onDragStart: (rawX: Float, rawY: Float) -> Unit,
        onDrag: (rawX: Float, rawY: Float) -> Unit,
        onDragEnd: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(BUBBLE_SIZE_DP.dp)
                .clip(CircleShape)
                .background(
                    if (isOverDismissZone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                .dragOrTap(onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd, onTap = onTap),
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
    private fun FanContent(
        icons: List<FanIconSlot>,
        bubbleOffsetXDp: Float,
        bubbleOffsetYDp: Float,
        onSelectTool: (GrimoireTool) -> Unit,
        onSelectSearch: () -> Unit,
        onDismiss: () -> Unit,
        onBubbleDragStart: (rawX: Float, rawY: Float) -> Unit,
        onBubbleDrag: (rawX: Float, rawY: Float) -> Unit,
        onBubbleDragEnd: () -> Unit,
    ) {
        // Once a drag through the bubble-shaped region below has started, the
        // rest of the fan hides itself - but only visually. The window it
        // lives in has to stay attached and this composable has to stay
        // around, or the pointerInput coroutine tracking that drag gets
        // cancelled mid-gesture (see the long comment on onBubbleDragStart
        // where this is wired up in showFan()).
        var draggingBubble by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxSize()) {
            if (!draggingBubble) {
                // Background tap-catcher, sized to the whole fan window: closes
                // the fan on any tap that doesn't land on one of the icons
                // below, or on the bubble-shaped region right after this one.
                // Compose dispatches a tap to the front-most consumer under the
                // finger, so those two consume it first; this only fires for
                // genuinely empty space.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
                )
            }
            // Invisible - the fan window's bounds are anchored to include the
            // bubble's own corner (see computeFanLayout), which means it sits
            // in front of the real bubble window there and would otherwise
            // silently swallow any touch that lands on the bubble's visible
            // position. This region exists purely so that touch still does
            // the right thing: a plain tap closes the fan (same as the
            // background catcher), and a click-and-drag closes the fan *and*
            // continues as a normal bubble drag, exactly as if the fan had
            // never been in the way.
            Box(
                modifier = Modifier
                    .offset(
                        x = (bubbleOffsetXDp - BUBBLE_SIZE_DP / 2f).dp,
                        y = (bubbleOffsetYDp - BUBBLE_SIZE_DP / 2f).dp,
                    )
                    .size(BUBBLE_SIZE_DP.dp)
                    .dragOrTap(
                        onDragStart = { rawX, rawY ->
                            draggingBubble = true
                            onBubbleDragStart(rawX, rawY)
                        },
                        onDrag = onBubbleDrag,
                        onDragEnd = onBubbleDragEnd,
                        onTap = onDismiss,
                    ),
            )
            if (!draggingBubble) {
                icons.forEach { slot ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (slot.centerXDp - FAN_ICON_SIZE_DP / 2f).dp,
                                y = (slot.centerYDp - FAN_ICON_SIZE_DP / 2f).dp,
                            )
                            .size(FAN_ICON_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .pointerInput(slot) {
                                detectTapGestures(
                                    onTap = {
                                        if (slot.tool != null) onSelectTool(slot.tool) else onSelectSearch()
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (slot.tool != null) {
                            Image(
                                painter = painterResource(slot.tool.iconRes),
                                contentDescription = slot.tool.displayName,
                                modifier = Modifier.size(26.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search all tools",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    val labelText = slot.tool?.displayName ?: "Search"
                    val labelYDp = if (slot.labelBelow) {
                        slot.centerYDp + FAN_ICON_SIZE_DP / 2f + FAN_LABEL_GAP_DP
                    } else {
                        slot.centerYDp - FAN_ICON_SIZE_DP / 2f - FAN_LABEL_GAP_DP - FAN_LABEL_HEIGHT_DP
                    }
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (slot.centerXDp - FAN_LABEL_WIDTH_DP / 2f).dp,
                                y = labelYDp.dp,
                            )
                            .width(FAN_LABEL_WIDTH_DP.dp)
                            .pointerInput(slot) {
                                detectTapGestures(
                                    onTap = {
                                        if (slot.tool != null) onSelectTool(slot.tool) else onSelectSearch()
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            labelText,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelContent(
        tool: GrimoireTool?,
        onSelectTool: (GrimoireTool) -> Unit,
        onBackToList: () -> Unit,
        onCollapse: () -> Unit,
        onTitleDragStart: (rawX: Float, rawY: Float) -> Unit,
        onTitleDrag: (rawX: Float, rawY: Float) -> Unit,
        onTitleDragEnd: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.size(PANEL_WIDTH_DP.dp, PANEL_HEIGHT_DP.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PANEL_TITLE_BAR_HEIGHT_DP.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tool != null) {
                        IconButton(onClick = onBackToList) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                        }
                    }
                    // Drag handle: the title bar's own click targets (back and
                    // collapse) sit outside this Box so dragOrTap's
                    // pointerInteropFilter never gets a chance to swallow their
                    // taps - only the blank/title space between them moves the
                    // panel.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .dragOrTap(
                                onDragStart = onTitleDragStart,
                                onDrag = onTitleDrag,
                                onDragEnd = onTitleDragEnd,
                                onTap = {},
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            tool?.displayName ?: "Quick Grimoire",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = if (tool != null) 4.dp else 12.dp),
                        )
                    }
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Default.Close, contentDescription = "Collapse")
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (tool == null) {
                        ToolList(onSelectTool = onSelectTool)
                    } else {
                        ToolWebView(tool = tool)
                    }
                }
            }
        }
    }

    // Search box + tag filter chips over a scrollable list - same
    // text/tag-matching logic bubble-dice's original StartScreen used, but
    // each row now also surfaces its tags and credit line directly (they
    // weren't shown per-row there, only used as filter data).
    @Composable
    private fun ToolList(onSelectTool: (GrimoireTool) -> Unit) {
        val allTools = remember { GrimoireTool.entries.filter { !it.unlisted } }
        val allTags = remember(allTools) { allTools.flatMap { it.tags }.distinct().sorted() }

        var query by remember { mutableStateOf("") }
        var selectedTags by remember { mutableStateOf(emptySet<String>()) }

        val visibleTools = allTools.filter { tool ->
            val matchesQuery = query.isBlank() ||
                tool.displayName.contains(query, ignoreCase = true) ||
                tool.description.contains(query, ignoreCase = true) ||
                tool.tags.any { it.contains(query, ignoreCase = true) }
            val matchesTags = selectedTags.isEmpty() || tool.tags.any { it in selectedTags }
            matchesQuery && matchesTags
        }

        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search tools") },
            )
            if (allTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(allTags, key = { it }) { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                            },
                            label = { Text(tag) },
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visibleTools, key = { it.id }) { tool ->
                    ToolListRow(tool = tool, onClick = { onSelectTool(tool) })
                }
                if (visibleTools.isEmpty()) {
                    item {
                        Text(
                            "No tools match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ToolListRow(tool: GrimoireTool, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(tool.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tool.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            tool.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        tool.credit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
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
                    // A WebView wrapped in Compose's AndroidView doesn't reliably
                    // claim Android view focus on tap the way it would sitting in
                    // a plain XML layout - Compose's own pointer-input dispatch
                    // sits in front of it and never calls the View-level
                    // touch-to-focus path WebView relies on. Without that view
                    // focus, a tapped <input> inside the page can select itself
                    // in the DOM but never actually gets an IME connection, so no
                    // keyboard ever shows - request it explicitly here instead.
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) v.requestFocus()
                        false
                    }
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
