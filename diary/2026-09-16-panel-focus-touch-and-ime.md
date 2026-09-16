# Getting the panel's keyboard and non-modality both right

**2026-09-16**

The draggable, non-modal panel (see the `feature/draggable-panel` work
earlier the same day) set out to do two things at once: let a tool's text
input take the keyboard when tapped, and never steal input focus from
whatever app is running underneath while it sits open. Those two goals are
in genuine tension on a raw `WindowManager` overlay - there's no Activity, no
`Window.Callback`, none of the scaffolding that normally makes this stuff
"just work" - and getting both at once took three separate, unrelated bugs,
two of which had been quietly canceling each other out. Worth writing down
in detail, because the next time this area changes, it's very easy to fix
one of these and reintroduce another.

## The setup

The panel window starts `FLAG_NOT_FOCUSABLE` so it never steals keyboard
focus from the app underneath just by existing. A touch landing in the panel
is supposed to clear that flag on demand - "this tap might be aimed at one
of my own inputs, so make the window eligible for focus" - and losing window
focus (the user tapped elsewhere) is supposed to set it right back. That's
the intended design, and the comment describing it in `OverlayService.kt`
predates all three bugs below - it was correct in *intent* the whole time.

## Bug 1: the wake-up listener could never fire for the thing it existed for

The clear-the-flag logic was a `View.OnTouchListener` set directly on the
panel's root `ComposeView`. That's a reasonable first instinct and it
compiles clean - and it is dead code for exactly the taps that matter.

A `ViewGroup` only invokes its *own* `OnTouchListener` when none of its
children end up consuming the touch (see `ViewGroup.dispatchTouchEvent`'s
`dispatchTransformedTouchEvent(ev, ..., child, ...)` vs. the `child == null`
"treat myself as the target" branch - only the latter checks the listener).
A `WebView` consumes `ACTION_DOWN` unconditionally to track its own gesture
state, and so does every Compose `clickable`/text field via its own pointer
input handling. So a tap on the search box, or on a tool's own input field -
the *entire reason this mechanism exists* - never reached the listener. Taps
on genuinely empty panel background did trigger it, which is exactly
misleading enough to make manual testing look like it was working if you
weren't specifically poking a text field.

**Fix:** move the check into a `dispatchTouchEvent()` override on a plain
`FrameLayout` wrapping the `ComposeView`. That override runs before the
child-consumption decision is even made, so it sees every touch down
regardless of what ends up underneath it.

## Bug 2: Compose looks for the lifecycle owner on the wrong view

Fixing bug 1 meant the panel's actual window root was no longer the
`ComposeView` itself - it was the new `FrameLayout` wrapper, with the
`ComposeView` nested one level in. The lifecycle/view-model-store/saved-state
owners (the "Compose in a raw overlay" shim in `ComposeOverlayLifecycle.kt`)
were still being attached to the `ComposeView`, which seemed harmless - it's
still *somewhere* in the same view tree.

It crashed immediately on next window open:

```
java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from
OverlayService$showPanel$rootView$1{...}
  at ...WindowRecomposer_androidKt.createLifecycleAwareWindowRecomposer
  at ...AbstractComposeView.resolveParentCompositionContext
  at ...AbstractComposeView.onAttachedToWindow
```

Compose's window-level recomposer is looked up from `view.rootView` - the
literal top of the window's view hierarchy, i.e. whatever was actually
passed to `WindowManager.addView()` - not from the `ComposeView` that's
asking for it. `findViewTreeLifecycleOwner()` searches *upward* from a
starting view, so tagging the owner on a child is fine for anything that
starts its search there (the `ComposeView` itself, for instance), but it's
useless for a lookup that specifically starts at the window root and doesn't
know or care that a nested `ComposeView` has its own tag three levels down.

**Fix:** tag the owner on the `FrameLayout` wrapper instead of the
`ComposeView`. A child's own upward search still finds an owner tagged on
its parent, so this covers both lookup paths with one call.

## Bug 3: fixing the toggle exposed a flag nobody had asked for

With 1 and 2 fixed, the keyboard worked - and the panel started swallowing
touches meant for the app underneath it, the exact non-modal behavior the
whole feature was built around.

`FLAG_NOT_FOCUSABLE`'s platform doc has a line easy to miss if you're not
reading it end to end: *"This flag will also enable FLAG_NOT_TOUCH_MODAL
whether or not that is explicitly set."* The panel's `WindowManager.LayoutParams`
never requested `FLAG_NOT_TOUCH_MODAL` on its own - it didn't need to, as
long as `FLAG_NOT_FOCUSABLE` was permanently set, because it was getting
`FLAG_NOT_TOUCH_MODAL` for free as a side effect. The moment a tap correctly
clears `FLAG_NOT_FOCUSABLE` (bug 1's fix), that free grant goes with it, and
an ordinary-looking overlay window quietly becomes touch-modal: it starts
eating touches outside its own bounds instead of letting them fall through
to whatever's behind it.

**Fix:** request `FLAG_NOT_TOUCH_MODAL` explicitly, unconditionally, in the
same `WindowManager.LayoutParams` construction - and never touch that bit in
the on-demand toggle, which only flips `FLAG_NOT_FOCUSABLE`.

## The part worth remembering

Bug 1 and bug 3 mask each other. While bug 1 was in place, the flag-clearing
code was effectively unreachable for any tap that mattered, so the window
stayed `FLAG_NOT_FOCUSABLE` forever - which meant it *also* kept the free
`FLAG_NOT_TOUCH_MODAL` grant forever, so bug 3 had no way to manifest. Fixing
bug 1 in isolation - which is the obviously-correct, narrowly-scoped fix for
"the keyboard doesn't show up" - was exactly what uncovered bug 3. Neither
bug was hiding in the other's code; they're two independent flags with a
documented (for 3) and undocumented-in-practice (for 1) coupling, and only
showed their real behavior once both were exercised together.

If this code moves again - different window flags, a different panel
shape, a rewrite of the focus-toggle mechanism - the thing to hold onto
isn't "don't break the keyboard" or "don't break background interaction" as
two separate checklist items. It's that a raw `WindowManager` overlay hosting
Compose + text input + "stay out of the way of the app underneath" needs all
three of these simultaneously true, and Android will happily let any one of
them silently regress while the others still look fine in casual testing:

1. Whatever triggers the focus-unlock has to run somewhere a consuming child
   can't shadow it (`dispatchTouchEvent`, not `OnTouchListener`, on whatever
   is genuinely the window's own root).
2. The `ViewTreeLifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`
   trio has to be tagged on that same literal window root, not on a
   `ComposeView` nested inside it.
3. `FLAG_NOT_TOUCH_MODAL` has to be requested explicitly and permanently if
   `FLAG_NOT_FOCUSABLE` is ever toggled at runtime - never rely on the free
   grant, because it only lasts as long as the window stays non-focusable.

All three live together in `OverlayService.showPanel()`; the commits are
`b0d77b3`, `d92191a`, `befa616`, and `29a69ce` on `fix/webview-keyboard-focus`.
