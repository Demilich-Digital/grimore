# Play review and the overlay rewrite

**2026-09-16**

## The first attempt

Before this repo existed, an earlier prototype of this idea (a separate
project, not carried into this repo's history) built the same core pitch -
floating quick-access tools like a dice roller, sitting over whatever
note-taking app you're actually using - on top of Android's Notification
"Bubbles" API. That API is the one behind Messenger-style chat heads: you
post a notification backed by a `MessagingStyle` and a long-lived
conversation shortcut, and if the OS and the user both allow it, that
notification can float as a small circular bubble that expands into your own
content when tapped.

It worked. Each tool got its own bubble, its own "conversation," and its own
tiny floating icon. But it was a workaround wearing a costume: none of these
tools are a conversation, there's no other party, no messages being
exchanged - the whole `Person`/shortcut/`MessagingStyle` scaffolding existed
purely to unlock a floating window, not because the app had anything
resembling a chat. That's exactly the shape of thing Play review tends to
catch: an API being used for something other than what it's for, dressed up
just enough to technically qualify.

## Why that specific shape is risky

A few compounding problems, beyond "this might read as misuse of a
messaging API":

- **No guaranteed floating behavior.** Bubbles are opt-in and gated per
  conversation by default - a brand new "conversation" (i.e. a brand new
  tool) doesn't bubble automatically unless the user has already set
  "All conversations can bubble," or manually taps the bubble affordance on
  its first notification. That's an awkward, easy-to-fail first impression
  for something pitched as "tap once, it floats."
- **Notification permission as a hard dependency.** Bubbles require
  notifications to exist at all, which means the whole feature was gated
  behind a notification-permission prompt for something that isn't, and
  never will be, sending you notifications in the ordinary sense.
- **No natural "this is a floating overlay, here's how you turn it off"
  story.** Because the floating window is technically a notification, its
  lifecycle is tangled up with notification-shade semantics (dismiss the
  notification vs. dismiss the bubble vs. the conversation shortcut still
  existing) instead of a single obvious on/off switch.

None of that is a single disqualifying bug - it's a pile of small
mismatches between what the API is designed to represent and what the app
actually is, and Play review has historically not been generous about that
gap for non-messaging apps.

## What the new PRD changes

The PRD this repo bootstraps from (`PRD-Demilich_Digital_Quick_Grimoire.md`)
throws out the Bubbles API entirely in favor of the pattern Android actually
has for "a small thing floats over other apps": a real
`TYPE_APPLICATION_OVERLAY` window via `WindowManager`, requested through the
`SYSTEM_ALERT_WINDOW` permission, kept alive by a foreground service. This is
the same mechanism Messenger's actual chat heads use under the hood, and
the same one a long tail of accepted Play Store apps use for legitimate
floating utilities (translation overlays, floating calculators, accessibility
tools). It's a heavily scrutinized permission, but a well-understood one -
which is a very different position than "technically-a-conversation."

The PRD is specific about *why* it expects this to clear review, and encodes
each reason as a concrete requirement rather than a vague intention:

- **No `INTERNET` permission, anywhere, ever.** This isn't just a privacy
  nicety - it's the thing that makes "this overlay cannot see or transmit
  your keystrokes" a verifiable claim instead of a promise, and it's stated
  directly in the required onboarding copy shown when asking for the overlay
  permission.
- **An explicit, required onboarding screen** for the `SYSTEM_ALERT_WINDOW`
  request, rather than just firing the system permission dialog cold. Google
  expects apps requesting sensitive permissions to explain themselves first;
  the PRD specifies the exact explanation.
- **An obvious, unambiguous way to get rid of the overlay** - a drag-to-delete
  drop zone on the floating bubble, plus a "Dismiss Overlay" action on the
  foreground service's own notification. Persistent overlays with no clear
  exit path are a recognizable pattern from overlay-based malware/adware, and
  a known thing reviewers watch for; having two redundant, obvious ways out
  is a direct answer to that.
- **The foreground service's lifecycle is the overlay's lifecycle** - tearing
  the bubble down stops the service and clears its notification, so there's
  no way to end up with an orphaned overlay running invisibly with nothing
  showing it's still active.

That last point turned out to need a bit more than the PRD's own code
sketch: Android 14 (API 34) requires foreground services to declare a
`foregroundServiceType`, and "floating a utility overlay" doesn't fit any of
the predefined types - it has to use `specialUse` with a plain-language
subtype description, which is itself a newer, stricter piece of Play's
compliance surface than the PRD's snippets accounted for.

## What stayed the same

Everything above is entirely about the native shell - the foreground
service, the permission flow, the window management, the exit path. The
actual tools (the dice roller, the oracle tables, the calculator) are static
HTML/CSS/JS pages that don't know or care whether they're being shown inside
a notification bubble's `WebView` or an overlay panel's `WebView`. That
split - keep the tool UI as dumb, portable web pages, change only the native
container around them - is what made this a rewrite of the shell rather than
a rewrite of the product.
