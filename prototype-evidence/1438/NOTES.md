# #1438 Glass merge prototype: device evidence (T5)

Throwaway prototype evidence. Tested source: `d39504eda3759ee3d91f011a28812b264cb62bf7`
(T1 to T4). No source files were changed for this evidence.

## What was captured

| Platform | Path | Content |
| --- | --- | --- |
| Android | `android/merged_spacing16.mp4`, `android/merged_spacing48.mp4` | Release sample "Glass — Merge Prototype", merged mode. Each video drags pill B from about 100 dp right of A into full overlap (approach, bridge forms, full overlap), drags it back out (bridge thins and breaks, two surfaces), then taps Overlap and Separate once (spring onto A, spring 144 dp below A). |
| Android | `android/independent.mp4` | The same gesture script in Independent mode. |
| Android | `android/stills/` | Matching stills at the same pill positions: `merged_s16_*`, `merged_s48_*`, `independent_*`, plus `cmp_*` strips (top to bottom: merged 16 dp, merged 48 dp, independent). |
| Desktop (Skiko runtime shader) | `desktop/sequence/` | 160 frames, half resolution: approach (`a-approach`) and drag apart (`b-apart`) for merged and independent at spacing 16 and 48 dp, and the Overlap then Separate toggle sampled every 100 ms on the test clock (`*_toggle_*`). |
| Desktop | `desktop/keyframes/` | Full-resolution crops of the key neck and overlap frames. |
| Both | `zoom/` | Nearest-neighbour zooms of the neck and a corner. |
| Android | `benchmark/` | Summary JSON and XML per measured run, dry-run XML, run log, and the scenario layer sizes. |

Gap labels: the Android still names are targets. The measured gaps from the UI dump were 39.6, 19.4, 6.9,
-0.8, -70.9 and -139.0 dp (`gap40` ... `gapm140`). Desktop frame names carry the measured gap in dp
(negative means overlap, `m` = minus). The touch slop made the Desktop gaps differ from the targets by
about 7 dp, so the names give the measured values.

Android conditions for recordings: Pixel 8a (`akita_beta`, API 37, codename REL, build
`CP41.260831.007.A3`). Screen locked to portrait for the session and restored to auto-rotate afterwards.
Display at 60 Hz (the default). Recorded with `adb shell screenrecord`. The videos were re-encoded to
720 px wide H.264. The 48 dp spacing was set with a slider tap. The label read 48 dp, but the
underlying float may be up to about 1 dp higher.

Desktop: a throwaway `jvmTest` (never committed) composed `GlassMergePrototypeSample` with
`runSkikoComposeUiTest` at 1080×1800 px and density 2.625. It dragged pill B with touch input, set
spacing with `SetProgress`, and wrote `captureToImage()` PNGs. The Desktop sample renders in its light
appearance, and Android renders dark, so compare each platform with itself.

## The merged runtime path ran on Android (first AGSL compile)

The merged fused, rim and detail shaders compiled as AGSL and rendered on the Pixel 8a (API 37). The
evidence is:

- Every merged frame shows refraction that follows the smooth-minimum field. It includes the bridge,
  which exists only in the merged shader. The fallback delegate ignores the merge and would draw the
  rectangular container.
- Each merged benchmark run reports `hazeGlassRuntimeDrawCount` = 1 for all 8 iterations. Each
  independent run reports 2.
- The sample's logcat had no shader or runtime-effect errors.
- No crash, blank output or garbled output appeared in any frame.

## Gradient approach used

Numeric central differences of the merged field (`shapeGradient`, step = `sampleStep`) were evaluated
with polynomial smooth-minimum and k = 2 × spacing. The analytic smooth-minimum-weighted fallback was
**not** needed: neither platform showed faceting or noise. See the next section.

## Optics findings

The comparison is merged against independent at the same positions.

**Bridge threshold: holds.** The bridge appears exactly when the gap is less than the spacing. At
16 dp spacing the 19.4 dp gap stays two separate shapes inside the one merged container. It looks the
same as independent mode (`android/stills/cmp_gap20.png`, row 1). At 6.9 dp a neck has formed
(`cmp_gap8.png`, row 1). At 48 dp spacing, gaps of 39.6 dp and below are bridged (`cmp_gap40.png`,
row 2). On Desktop, `merged_s16_07_a-approach_gap19` has no bridge and `merged_s16_08_a-approach_gap15`
shows a pinched neck. Breaking on drag apart is symmetric: `merged_s16_18_b-apart_gap9` is bridged and
`merged_s16_19_b-apart_gap17` is split.

**Edges: hold.** The outline is smooth, with no steps, along the straight sides, the caps and the
bulge of the neck. The bulge grows continuously with closeness, on both platforms and in both videos.

**Corners: hold.** When the members are separate, the merged cap corner matches the independent corner
pixel for pixel to the eye, including the rim and the refracted stripe
(`zoom/android_corner_s16_gap40_merged_vs_independent.png`).

**Rim at the neck: holds, and is thin.** The rim follows the merged outline continuously through the
concave neck. It neither breaks nor doubles at the join. It is as thin there as on the straight
edges. The rim uses the same central-difference SDF sampling and shows no faceting.

**Refraction across the neck: smooth, but compressed into a band.** Around the neck midline, the edge
refraction displaces content in opposite directions on either side. The normalised gradient turns
from +x to −x through the saddle. Backdrop content from both sides is squeezed into a narrow vertical
band, roughly 5 to 15 px wide at 16 dp spacing. It is visible as a mirrored crease in the stripes and
the "G" (`zoom/android_neck_s16_gap7_and_gap0.png`,
`zoom/android_neck_s16_gap7_vs_overlap70_pixels.png`, `zoom/desktop_neck_s16_gap15_and_gap7.png`).
At pixel level the band is anti-aliased and continuous, without stepping, speckle or facets, and both
platforms agree. This is the optical consequence of edge-normal refraction across a saddle. Changing
how the gradient is computed would not change it. Softening it is a design choice, for example fading
displacement where the two members' distances are close. It is not a gradient repair. At half and
full overlap the crease disappears (`cmp_gapm70.png`, `cmp_gapm140.png`).

**Diffusion grows with spacing: a prototype limitation.** At 48 dp spacing the merged surface is much
more diffused than independent pills, and the backdrop is almost unreadable (`cmp_gap8.png`, row 2,
and `merged_spacing48.mp4`). At 16 dp it is close to independent. The cause is that `GlassStyle.regular`
optics (depth, blur radius and `refractionHeight`) resolve from the container's shortest side. The
container is the union inflated by the spacing, not a member. In a row, the shortest side is
64 + 2 × spacing dp: 96 dp at 16 dp spacing and 160 dp at 48 dp spacing, against 64 dp for each
independent pill. Regular depth therefore moves from 0.65 to about 0.75 at 16 dp spacing and about
0.95 at 48 dp. Blur moves from 20 dp to about 21 dp and about 23.4 dp. A product version would
resolve optics from the member size, or from a merge-independent size.

**Full overlap grows the shape: expected maths, not a defect.** Where both members' distances are
equal, the polynomial smooth minimum lowers the field by k/4 = spacing/2. At full overlap the merged
surface is pill A grown by spacing/2 on every side: 8 dp at 16 dp spacing and 24 dp at 48 dp spacing
(`cmp_gapm140.png`, `desktop/keyframes/merged_s48_14_a-approach_gapm133.png`, and the Overlap toggle
frames). The independent pills stay at their own bounds. The container is inflated by the full
spacing, so the grown shape is not clipped.

**Dome: no difference.** The merged field forces the dome off. Under `GlassStyle.regular` at
140×64 dp, the independent members also have **no** dome contribution. Regular uses
`RefractionProfile.Edge(20.dp)`, so refraction takes the edge branch and never reads the field
weight. The lighting field weight is `elongationWeight × overlapWeight`. Here `refractionHeight` is
0.15 × 64 = 9.6 dp against an inradius of 32 dp, a ratio of 0.3, which is below the 0.75 start, so
`overlapWeight` = 0. Forcing the dome off is therefore invisible at the sample size.

**Toggle cycle: holds.** The springing Overlap and Separate cycle on both platforms shows a continuous
merge into the grown blob, a bounce, a neck stretching vertically, and a clean split. There is no
popping when the bridge breaks (`desktop/sequence/merged_s{16,48}_toggle_*`, and the end of each
Android video).

Interaction stages were not exercised (they are not wired for merging). The fused chroma corner weight
still uses the container. Regular has chromatic aberration at strength 0, so it is not visible here.

## Known visual differences from iOS `GlassEffectContainer`

These were not compared against an iOS device in this task. They come from the documented and
expected behaviour of `GlassEffectContainer(spacing:)`, and an iOS capture should confirm them.

- **Growth at overlap.** This prototype grows overlapping members by spacing/2 on every side. iOS is
  expected to keep fully overlapping shapes at their union.
- **Spacing-dependent diffusion.** Here, optics resolve from the inflated container. iOS keeps a
  member's material appearance independent of the container spacing.
- **Neck refraction crease.** The prototype compresses content at the saddle. iOS's neck reads as one
  continuous lens.
- **Dome.** It is off on the merged field. At these sizes regular has no dome either, so this has no
  visible effect.
- **Interaction.** Pressed and interaction optics do not follow the merged shape. On iOS, interactive
  glass in a container responds as one surface.
- **Chroma corner weight.** It uses the container, not the merged field. This is invisible at
  regular's zero chroma.

## Frame cost (Android)

Pixel 8a (`akita_beta`), API 37, codename REL, build `CP41.260831.007.A3`. Release benchmark build,
`CompilationMode.Full`, warm start. The display was fixed at 60 Hz (`peak_refresh_rate` and
`min_refresh_rate` = 60, read back as 60, render rate 60.0) with fixed-performance mode on, in portrait
on AC power at 90% battery. The thermal status was 0 before and after every run. The battery
temperature was 25.8 °C at the start and 27.9 °C at the end.

The runbook's Android 17 beta check: four Google app processes respawned after `force-stop`. Settings
was brought to the foreground and `com.google.android.googlequicksearchbox` was disabled with
`pm disable-user` (zero processes afterwards). It was re-enabled after the runs (readback `enabled=1`).
Fixed-performance mode was turned off, and both refresh-rate settings were deleted back to their
original unset (`null`) state. This was done from an EXIT trap, and the readback shows `null`/`null`.

Each method was dry-run once and passed: XML testcases `mergePrototypeMerged` and
`mergePrototypeIndependent`. Each was then measured individually, with no combined selector, in the
order merged, independent, independent, merged. Each measured run has 8 iterations and 8 traces
(verified by `archive_result.py`).

Layer sizes (tag `glass_profiling_merge_areas`): `merged=987x378 independent=368x168+368x168` px. That
is 373,086 px² for the merged layer against 123,648 px² for two independent layers (3.02×). Effect
nodes: 1 merged against 2 independent (`hazeGlassRuntimeDrawCount`).

| Run (order) | Method | Iter. | CPU P50 / P90 / P99 (ms) | Overrun P50 / P90 / P99 (ms) | GPU mem max (median, KB) | Glass draws |
| --- | --- | --- | --- | --- | --- | --- |
| fwd1 | mergePrototypeMerged | 8 | 2.56 / 4.93 / 7.47 | −9.02 / −6.92 / −4.08 | 103,868 | 1 |
| fwd2 | mergePrototypeIndependent | 8 | 2.78 / 4.56 / 8.38 | −10.15 / −8.13 / −4.45 | 95,864 | 2 |
| rev1 | mergePrototypeIndependent | 8 | 2.85 / 4.83 / 7.88 | −10.07 / −7.88 / −5.11 | 95,864 | 2 |
| rev2 | mergePrototypeMerged | 8 | 2.67 / 5.02 / 8.01 | −9.15 / −7.03 / −4.08 | 103,868 | 1 |

Reading the table:

- CPU frame duration is within run-to-run noise between the modes in both orders (P90 4.93 and 5.02
  merged against 4.56 and 4.83 independent).
- Merged frame overrun is consistently about 1 ms later at P50 and P90 in both orders. It is still far
  inside the 16.7 ms deadline: no run has a positive P99 overrun.
- The merged layer holds about 8 MB more GPU memory, consistent with a 3× larger layer.
- `frameDurationCpuMs` is UI-thread plus RenderThread CPU time. It does not measure GPU shader cost.
- These are four single sessions of 8 iterations. CPU placement in the traces was not analysed.

Archived raw results (outside the repo, with all traces):
`/private/tmp/claude-501/-Users-chris-dev-haze--claude-worktrees-mattpocock-skills-triage-1409-73ad72/7c7d5fc2-7e85-4714-9145-ce662af4a782/scratchpad/t5/bench/{fwd1-mergePrototypeMerged,fwd2-mergePrototypeIndependent,rev1-mergePrototypeIndependent,rev2-mergePrototypeMerged}`.
Committed copies of each run's summary JSON and XML are in `benchmark/<run>/`.
