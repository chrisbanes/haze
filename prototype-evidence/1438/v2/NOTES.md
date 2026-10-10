# #1438 Glass merge prototype: v2 notes

These notes supersede the [T5 notes](../NOTES.md) where they disagree, in particular on the smooth-minimum
radius, the optics limitations, and the differences from iOS.

## Changes since the first report

| Commit | Change |
| --- | --- |
| `35a4cd6b` | The size-responsive optics (depth, blur, refraction height) now come from the smaller member instead of the container, which is the members' union inflated by the spacing. Smoothing fades to zero as the members overlap, so a full overlap ends at their union instead of growing by `k / 4`. Chromatic aberration is weighted by the nearer member's corners. |
| `5df007c8` | Where a bridge is thinner than the refraction width, merged refraction fades out instead of pulling content across the neck's midline from both edges. A probe one refraction width along the inward normal measures the thickness, and the refraction direction comes from a wider gradient. Members at least twice the refraction width thick are unchanged. |
| `137f4d6e` | The smooth-minimum radius is `k = spacing`, not `2 × spacing`. Members therefore bridge when the gap is less than **half** the spacing, which matches iOS (see below). |
| `8078329d` | The droplet experiment in the sample (`8c964be9`: hysteresis, join overshoot, wobble, motion stretch) was removed after the iOS reference showed none of it. |

Non-merged Glass shader sources stayed byte-identical through every change. Each fix has a `MergedGlassShaderTest` case, and each was confirmed to fail without its fix:

- the thin-bridge case;
- the half-spacing threshold;
- the union at overlap;
- the optics sized from a member.

## iOS reference (`../ios-harness/`)

The harness is a throwaway SwiftUI app that mirrors the Android sample: two 140×64 pt capsules in `GlassEffectContainer(spacing:)`, on the same backdrop.

- It runs on the iOS 27 simulator (iPhone 17 Pro), not iOS 26 or a device.
- Pill motion is scripted inside the app. Each frame sets the position without animation, which is what a finger drag does. The simulator's injected drags never reached the pills.
- A live gap label is drawn in each frame, so readings don't depend on the variable-frame-rate recording's timestamps.

Observed at 40 pt spacing (`recordings/ios-threshold2.mp4`, `recordings/threshold_open_s40.png`, `recordings/threshold_snap_s40.png`):

- **Opening slowly:** the neck thins smoothly to a point at a gap of about 20.0 pt and has separated at 20.7 pt. That is half the spacing.
- **Closing quickly (40 → 4 pt in 0.2 s):** the ends start to reach towards each other at about 22–25 pt and join at about 19–22 pt. There is no hysteresis between joining and breaking.
- **No overshoot, wobble or recoil** at the join or at the break. There is no stretch along the direction of motion either.
- **Overlap (the Move toggle):** B slides into A and the result stays at the union, with no growth or bounce.

This is the behaviour of a polynomial smooth minimum with `k = spacing`, which the prototype now uses. The pre-join "reaching" comes from the same field, so it needs nothing extra.

`android_vs_ios.mp4` runs the same gesture script on both: the Pixel 8a with `8078329d` on the left, the iOS simulator on the right. The clips are aligned by their first drag, so the timing is approximate. The Android sample renders its dark appearance and iOS renders light, so compare shapes and motion, not colour.

## Optics after the fixes (Desktop, Skiko)

- `desktop/blur_s48_before_after.png`: 48 dp spacing.
  - Top: before `35a4cd6b` (T5, 7 dp gap). The surface is fully diffused because the optics were sized from a 160 dp container.
  - Bottom: after, with a 14 dp gap. It refracts like a single 64 dp pill. The gaps differ because of touch slop in the capture harness.
- `desktop/overlap_s40_union.png`: a full overlap ends at the pill's own outline.
- `desktop/neck_s16_before_after.png` and `desktop/neck_s40_before_after.png`: the neck before and after `5df007c8`.
  - These were rendered with `k = 2 × spacing`, before `137f4d6e`.
  - In the thin bridge, the content that was folded into an X-shaped crease now passes straight through.
  - The thinnest necks still show a slight pinch from the bulbs either side.
- **Not changed, with reasons:**
  - The mirrored "eye" visible in a concave neck at partial overlap is the same edge-refraction behaviour that every pill shows at its rounded caps. It is not specific to merging.
  - A curvature-based limiter was tried. It suppressed refraction far beyond the neck and was dropped.

## Frame cost after `5df007c8` (Android)

The method is the same as the T5 table: Pixel 8a, API 37, 60 Hz, fixed-performance mode, portrait, thermal status 0 throughout. Each method was dry-run once, then measured individually in the order merged → independent → independent → merged, with 8 iterations and 8 traces per run. The runs are in `benchmark/`, and `benchmark/bench-run.log` has the device conditions and restoration readback.

These numbers were measured with `k = 2 × spacing`. `137f4d6e` only narrows the bridging range, so the per-pixel work in the bridged benchmark layout (16 dp gap, 40 dp spacing) is the same kind of work. The runs were not repeated.

| Run | Method | CPU P50 / P90 / P99 (ms) | Overrun P50 / P90 / P99 (ms) | GPU mem max (KB) | Glass draws |
| --- | --- | --- | --- | --- | --- |
| fwd1 | merged | 2.41 / 4.82 / 7.32 | −8.92 / −6.75 / −4.03 | 103,880 | 1 |
| fwd2 | independent | 2.83 / 4.70 / 8.12 | −10.12 / −8.07 / −4.86 | 95,856 | 2 |
| rev1 | independent | 2.83 / 4.66 / 8.15 | −10.16 / −8.10 / −4.93 | 95,628 | 2 |
| rev2 | merged | 2.70 / 4.82 / 7.32 | −8.58 / −6.65 / −4.08 | 104,084 | 1 |

- **Merged:** frames finish 0.1–0.6 ms later than in the first report at P50 and P90. That is the cost of the thin-bridge fade.
- **Independent:** unchanged.
- **Merged against independent:** about 1.2–1.5 ms later at P50 and P90, still far inside the 16.7 ms deadline. The merged layer is about 3× the pixel area.
- **Caveats:**
  - CPU frame time is UI-thread plus RenderThread time, not GPU shader cost.
  - These are single sessions of 8 iterations.
  - CPU placement was not analysed.

## Remaining known differences from iOS

- **Interaction:** interaction stages are not wired for merging. Combining a merge with an `interactionSource` throws on press.
- **Thinnest necks:** they still pinch slightly from the bulbs either side.
- **Optics with unequal members:** the optics are uniform per layer and sized from the smaller member.
- **Fallback:** the fallback (non-runtime-shader) delegate ignores the merge.
- **Not verified:**
  - iOS 26;
  - an iOS device;
  - the `glassEffectID` transition beyond the one recorded toggle cycle.
