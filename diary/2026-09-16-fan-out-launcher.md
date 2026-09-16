# The fan-out launcher

**2026-09-16**

## Why the grid had to go

The whole pitch of this app is speed - a tool should be one tap from
wherever the bubble happens to be, closer to how stylus-tablet quick-tool
launchers work (Samsung's S Pen Air Command, Onyx Boox's side-tools panel)
than a phone-style app drawer. But since the overlay rewrite (see the first
diary entry), tapping the bubble always opened the same centered modal panel
with a plain icon grid of every tool. Reaching a tool you use constantly
took exactly as long as reaching one you'd never opened - the interaction
model didn't actually deliver on the "fast" premise, it just wasn't a
notification bubble anymore.

The replacement: tapping the bubble fans out your recently-used tools in an
arc anchored right where the bubble is, plus a search shortcut for anything
not in your recents. Picking a recent tool jumps straight to it - no
intermediate list. The old grid didn't disappear; it grew into a proper
searchable, tag-filterable tool browser (search box + tag chips, each row
now showing icon, title, description, tags, and a source/credit line) and
moved behind the fan's search icon, reached only when you actually need to
browse rather than every single time.

## Adaptive, not fixed

The fan's icon count was deliberately never pinned to a number. The project
expects to accumulate a lot of tools, and screens vary wildly - a bubble
sitting near the middle of a large tablet has far more room to work with
than one pinned in the corner of a phone. So the arc's radius is derived
from actual available space in the direction it opens, and the icon count
falls out of that radius at a fixed minimum tap-target spacing, down to a
floor of "just the search icon" in the tightest corner. Recency itself is
tracked and persisted independently of how many of those recents the fan
can currently show (`RecentTools.kt`, DataStore-backed, capped at 8 stored)
- display capacity and storage capacity are two different concerns that
happened to get conflated in an early draft of the plan before landing on
this split.

## The shape evolved under real feedback

The first working version only ever swept a quarter-circle, biased toward
whichever screen quadrant the bubble's center fell into. That's the right
shape for an actual corner, but it produces an arbitrary, lopsided arc for
a bubble sitting in the middle of a screen edge - top-center, say - where
there's symmetric room on both sides and no real reason to favor one over
the other. The fix, after trying it: classify each axis independently as
"near an edge" or not (outer 30% of that axis's screen size counts as
near), and only fall back to the diagonal quarter-circle when *both* axes
are constrained (a real corner) or *neither* is (bubble roughly centered,
where the quarter shape is just a reasonable default). When exactly one
axis is constrained, the fan opens as a full semicircle, swinging the free
axis a full 180 degrees instead of guessing a side.

That single change rippled through every formula in `computeFanLayout()` -
the window's bounding box, the per-axis available-radius bound, and the
icon-angle sweep all needed a "which axis is fixed-direction and which is
swinging both ways" branch rather than a single fixed quarter-circle
formula. It's the kind of change that looks like "just widen the angle" from
the outside and turns out to touch every piece of the geometry once you're
actually in it.

## Bugs encountered during iteration

- **Icons clipped at the window edge** - the fan's WindowManager window was
  sized from icon *center* positions without correctly accounting for how
  far their edges extend past that; the outermost icon hung past the
  window's own boundary and got clipped by the window itself, not the
  screen.
- **Same mistake again, when titles were added** - labels overhang their
  icons and can land above or below depending on arc position (even both,
  within one semicircle), reopening the identical center-vs-edge sizing gap
  in two new dimensions.
- **Fan swallowing bubble drags** - the fan's window bounds are anchored to
  cover the bubble's own corner (needed so the arc can anchor to it), so a
  drag started exactly there landed on the fan and silently did nothing.
  Fixed with an invisible bubble-shaped hit region inside the fan, running
  the same tap-or-drag gesture as the real bubble - worth pulling that
  gesture logic out into a shared `Modifier.dragOrTap` once a second place
  needed it.
- **That fix then orphaning the gesture** - closing (removing) the fan
  window mid-drag killed the touch stream Android was using to deliver the
  rest of the move events, so the bubble moved a few pixels and stopped
  until re-grabbed. Fixed by hiding the fan's content visually first and
  only actually removing the window once the drag genuinely ends.
- **Drag lag/offset** - the bubble's position was updated by accumulating
  Compose's frame-to-frame local position deltas, which quietly undercounts
  real movement once the window itself starts moving (each frame's delta is
  measured against an already-shifted reference). Fixed by anchoring to a
  fixed local position captured at drag start and re-deriving the total
  offset from that anchor every frame, instead of accumulating.

## One incidental fix along the way

Wiring `RecentTools` up to the same local DataStore `GrimoireBridge`
already used (for macro persistence) surfaced that Jetpack's
`preferencesDataStore` delegate throws at runtime if it's declared twice
for the same file name in one process - an easy trap once a second class
needs the same store. Pulled the single `Context.appDataStore` property out
into its own file so there's exactly one declaration site everything else
imports.
