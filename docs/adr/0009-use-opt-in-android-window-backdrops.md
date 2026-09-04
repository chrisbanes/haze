---
status: superseded
superseded_by: 0010-adopt-backdrop-as-the-adaptive-haze-input
---

# Use opt-in Android window backdrops

Haze will expose `HazeInput.Backdrop(fallback: HazeInput.Sources)` as an experimental input. On a
supported hardware-accelerated Android window, built-in effects may filter pixels already drawn
behind the effect through a dedicated `RenderNode`. The fallback is mandatory and remains the
authoritative result on older Android releases, other platforms, unsupported renderers, and after
native setup or drawing fails.

Backdrop input is not another source-selection policy. It samples the combined earlier pixels in
the current window surface; it cannot select individual Haze sources, include later drawing, or
cross a dialog, popup, or window boundary. Callers that require those semantics continue to use
`HazeInput.Sources` directly.

Core owns attachment-scoped backend selection, geometry, clipping, transforms, draw ordering, and
fallback capture demand. A missing capability, unavailable canvas, unusable root effect, or native
failure selects source fallback until the modifier node detaches. A healthy native node neither
allocates nor records its fallback source layers. This keeps fallback reliable without charging
the native path for dormant capture work.

The Android platform renderer is gated on the full 37.2 SDK version and hardware acceleration,
not major API 37 alone. Its backend and built-in renderer capability remain internal; callers
cannot select a backend or supply a custom backdrop root effect through the supported public API.

## Consequences

Existing `HazeInput.Sources` and `HazeInput.Content` behavior is unchanged. Blur and Glass retain
ownership of their complete platform effect graphs and may opt into the internal capability only
when their styling can be represented without weakening their public contract. A native-to-source
transition may take one frame, but known failures are not retried every frame.

[ADR-0003](0003-use-one-android-fused-glass-renderer.md) remains authoritative for source-backed
Android Glass. Backdrop is an explicit input exception: it may reuse the fused effect graph, but it
must not replace or simplify the retained source-backed renderer.
