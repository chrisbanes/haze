---
status: accepted
---

# Do not guarantee frozen pixels for retained Glass output

`HazeSourceRetention.KeepLastFrame` keeps drawing retained output when selected sources become
unavailable. On Android, retained Glass output is a replayed layer, not an immutable image. It can
redraw from live source content after source selection is lost or a source detaches while the
material is hidden, and it can show descendant changes made after capture
([#1405](https://github.com/chrisbanes/haze/issues/1405)).

Haze will document this limit instead of guaranteeing frozen last-displayed pixels.
`KeepLastFrame` promises smooth source transitions, not a pixel-exact snapshot.
`ClearWhenUnavailable` remains the policy for surfaces where stale or live source pixels must not
appear.

The shared `HazeSourceRetention` contract is narrowed for every effect and platform, because Haze
makes no frozen-pixel guarantee anywhere. The observed leak is limited to Android Glass; Blur and
JVM/Skiko retention were not investigated for it.

## Considered options

- **Always capture immutable input images.** Rejected for now. The prototype in
  [#1383](https://github.com/chrisbanes/haze/pull/1383) fixed the source-loss cases, but raised
  nine-effect CPU frame P90 on a Pixel 8a from about 6.0–6.2 ms to 8.3–8.6 ms, with CPU placement
  uncontrolled. It also clipped Display-P3 sources to sRGB.
- **A cheaper always-on immutable capture.** Deferred. No candidate exists yet that meets the
  performance bar below. `HardwareBufferRenderer` with RGBA_FP16 preserved Display-P3 on API 37,
  and is a starting point for future work.
- **Capture only when a source is about to disappear.** Rejected. The pixels can already be gone,
  for example when a source detaches while the material is hidden.

## Consequences

- No runtime cost. The `KeepLastFrame` KDoc and the Glass, core-concepts and Blur usage docs state
  the limit.
- Two stacked or nested material failures from #1405 have an unknown root cause. This decision does
  not address them, and they remain uninvestigated.
- A future capture path must clear the
  [#1373](https://github.com/chrisbanes/haze/issues/1373) bar of no ongoing overhead beyond
  measurement noise, preserve the Glass colour-handling contract, and supersede this decision.
