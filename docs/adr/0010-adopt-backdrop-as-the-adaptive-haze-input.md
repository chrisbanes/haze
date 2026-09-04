---
status: accepted
supersedes: 0009-use-opt-in-android-window-backdrops
---

# Adopt Backdrop as the adaptive Haze input

`HazeInput.Sources` remains the exact source-capture input. `HazeInput.Backdrop` is the normal
input for a built-in Blur or Glass surface whose intent is to consume the pixels already drawn
behind it in the current window:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
)
```

The native path samples the combined earlier pixels in the same window surface. It cannot select
individual Haze sources, include later drawing, or cross a dialog, popup, or other window boundary.
When the native path is unavailable, Haze uses `Backdrop`'s source fallback. The fallback's
`fallbackSelection` and `fallbackRetention` options apply only to that source-capture path; they
do not filter native window pixels.

## Eligibility and fallback

`HazeFeatureFlags.isPlatformBackdropEnabled` is an experimental process-wide switch. It is
`false` by default. Set it before attaching the Haze effect nodes that should observe the switch:

```kotlin
HazeFeatureFlags.isPlatformBackdropEnabled = true
```

The switch makes the platform renderer eligible; it does not force native rendering. Built-in
effect support, the Android full 37.2 SDK gate, the current window and canvas, native setup, and
native drawing must all succeed. Unsupported platforms, incompatible effects, software canvases,
and setup or draw failures use source fallback. A known unavailable or failed native path remains
sticky until that modifier node detaches. Existing attached nodes keep the value observed at
attachment; a later attachment reads the current value.

The current implementation is experimental and has not established physical Android 37.2
acceptance or a performance result. Those remain release gates. The rollout is deliberately
staged: after acceptance a later release may enable the default, retain `false` temporarily as a
regression escape hatch, and then remove the flag.

## Diagnostics and consequences

Enable `HazeLogger.enabled` while diagnosing selection and fallback messages. Native draw work is
also marked by the `HazeBackdrop.draw` trace section. These are diagnostics, not a public backend
selection or status API.

On a healthy native attachment, Haze does not record dormant source layers. A native-to-source
transition may take one frame, and source fallback remains responsible for portable rendering and
retained-output behavior. [ADR-0009](0009-use-opt-in-android-window-backdrops.md) records the
earlier wrapper-based decision and remains unchanged as historical context; this ADR supersedes
its public input shape.
