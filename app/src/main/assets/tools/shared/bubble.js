// Shared boilerplate for every bubble tool page. Loaded first, synchronously,
// before any tool-specific script - so the theme is set and window.onerror is
// registered before anything else has a chance to run or throw.

(function () {
  // The overlay's WebView (see OverlayService.kt's ToolWebView) passes the
  // real system theme two ways: this query param (read before first paint,
  // so there's no flash of the wrong theme) and, if the system theme flips
  // while the panel is already open, a live evaluateJavascript call that
  // sets the same attribute. Either way it overrides prefers-color-scheme in
  // theme.css, which stays as the fallback for previewing a page outside the
  // app.
  var theme = new URLSearchParams(location.search).get('theme');
  if (theme === 'dark' || theme === 'light') {
    document.documentElement.setAttribute('data-theme', theme);
  }
})();

// WebView doesn't surface JS errors anywhere you'd see them without hooking
// up remote debugging - this turns one into visible text on screen instead
// of a silent blank page.
window.onerror = function (message, source, lineno, colno) {
  document.documentElement.innerHTML =
    '<pre style="white-space:pre-wrap;margin:0;padding:16px;' +
    'font-family:monospace;font-size:13px;color:#b3261e;background:#fff;">' +
    'Script error:\n' + message + '\nat ' + source + ':' + lineno + ':' + colno +
    '</pre>';
  return true;
};

// Carried over from the notification-bubble version of this app, where
// BubbleBridge.reportResult updated the floating bubble's badge icon and a
// shade notification's history - neither exists in this overlay-based
// version (no per-tool notifications, no badge). Nothing registers
// window.BubbleBridge here, so this is currently an inert no-op; left in
// place (and guarded, so it's harmless either way) rather than ripped out of
// every tool page, in case a native badge bridge comes back later.
function reportToNative(displayText, badgeText) {
  if (window.BubbleBridge && window.BubbleBridge.reportResult) {
    window.BubbleBridge.reportResult(displayText, badgeText == null ? null : String(badgeText));
  }
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}

// Appends one result card to a #log element in the shared "Tumble" card
// style: a big total on the left (skipped for errors), a label, and a small
// monospace detail pip below it. gemVarName is one of the --gem-* custom
// properties from theme.css (defaults to the neutral violet).
function appendCard(log, { label, gemVarName, detailText, total, isError }) {
  const card = document.createElement('div');
  // Dice totals and short oracle words stay inline with their label/detail
  // (the tight, compact case) and get the full-size treatment; a longer
  // oracle result steps down in size and stacks the card vertically instead
  // of squeezing the label/detail pip beside it - see .card.stacked and
  // .card-total-md/-sm in theme.css.
  const totalLen = total == null ? 0 : String(total).length;
  const totalSizeClass = totalLen > 60 ? ' card-total-sm' : totalLen > 28 ? ' card-total-md' : '';
  const stackedClass = totalLen > 28 ? ' stacked' : '';
  card.className = 'card enter' + stackedClass + (isError ? ' error' : '');
  card.style.setProperty('--gem', 'var(' + (gemVarName || '--gem-d12') + ')');
  card.innerHTML =
    (isError ? '' : `<span class="card-total${totalSizeClass}">${total}</span>`) +
    `<span class="card-meta">` +
    (label ? `<span class="card-label">${escapeHtml(label)}</span>` : '') +
    (detailText != null
      ? `<span class="card-detail"><span class="pip">${escapeHtml(detailText)}</span></span>`
      : '') +
    `</span>`;
  log.appendChild(card);
  log.scrollTop = log.scrollHeight;
  return card;
}

// Restarts a CSS animation on tap: a spin on the element's own icon, plus a
// glow pulse on the element itself if it's a tile (has that class).
function tapFeedback(triggerEl) {
  const icon = triggerEl.querySelector('svg');
  if (icon) {
    icon.classList.remove('spin'); void icon.offsetWidth; icon.classList.add('spin');
  }
  if (triggerEl.classList.contains('tile')) {
    triggerEl.classList.remove('glow'); void triggerEl.offsetWidth; triggerEl.classList.add('glow');
  }
}
