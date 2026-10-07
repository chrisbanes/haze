# ADR-0003: Use one Android fused Glass renderer

## Status

Accepted

## Date

2026-07-25

## Context

ADR-0002 selected one retained stage graph for Android and Skiko. That graph preserves exact
semantic Gaussian blur and makes stage invalidation explicit, but each Glass surface can replay
several nested graphics layers on every source update.

Physical-device profiling of the `steadyFull9` scene on a Pixel 6 showed that this topology was
limited by Android RenderThread and Vulkan-driver work rather than Compose preparation or fragment
area. Removing Glass reduced P90 CPU frame duration to 4.82 ms, while reducing the capture scale
did not materially improve the full effect. Perfetto attributed the cost to repeated layer
traversal, `flush layers`, `flush commands`, and `Vulkan finish frame`.

Two alternatives reduced only part of that cost:

- Snapshotting the source into a raster cache still invalidated every frame and introduced
  repeated image allocation and snapshot work.
- Rebuilding blur, depth, and optical composition as a native Android `RenderEffect` graph reduced
  layer traversal, but full-resolution blur and blend submissions made the Vulkan path slower.

The renderer needs a bounded, feature-complete path whose selection is independent of how many
Glass siblings happen to be attached.

## Decision

On API 33 and newer, every supported Android Glass effect captures its source in one layer and
feeds that capture into one retained output layer backed by a composed `RenderEffect` graph.
“Fused” describes that single output renderer, not one monolithic shader or a single retained
layer. The graph performs:

1. Semantic horizontal and vertical blur kernels for small or progressive blur, and a native
   Gaussian node for wide uniform blur (the correctness revision described below).
2. Sharp-to-blurred depth mixing.
3. A RuntimeShader for refraction, Simple or Full chromatic aberration, tint, tone, Fresnel
   response, and shape masking.
4. A refraction-detail shader that samples the original sharp source. The optical shader reserves
   the detail branch's geometric coverage, then the sharp detail is combined with premultiplied
   `Plus`.

The blur stage is chained directly into the output effect instead of rasterizing intermediate
graphics layers. Progressive blur uses the same caller mask and semantic two-pass kernels as the
retained renderer. Full chromatic aberration
uses the same seven-position spectral reconstruction. The optical and detail branches are
driver-managed nodes in one native effect DAG; they do not allocate additional retained
intermediate graphics layers.

Rim, interaction lighting, and group-alpha composition remain separate when required. Configured
interaction optics are compiled into the optical and sharp-detail shaders. Interaction lighting
uses a localized foreground patch so that it remains visible above the effect's content. Live
press, hover, and focus values update retained shader providers without changing the retained-layer
topology. Interaction-only optical changes re-record the fused output rather than retaining its
previous pixels.

Sibling count, sibling compatibility, and live interaction values are not renderer-selection
inputs. Android effects do not register with the shared retained-blur registry. A surface owns its
source capture, fused output, and only the optional rim or group-alpha stages required by its
stable configuration.

Skiko and Android environments without RuntimeShader support continue to use their existing
adapters. The public Glass API is unchanged.

### Preserve the last displayed source-backed input

Issue [#1373](https://github.com/chrisbanes/haze/issues/1373) exposed a separate
correctness limit: retaining a recorded source layer does not freeze its mutable
descendants. After source selection loss, modifier detach or another consumer's
recapture, replay can lose or change the input that produced the displayed
material. Layer identity and retained metadata do not establish retained pixels.

The [approved exception](https://github.com/chrisbanes/haze/issues/1373#issuecomment-6013730424)
permits a completed immutable image of the cropped raw input before presenting
source-backed Android fused Glass. The existing fused effect graph consumes that
image. Native Backdrop, own-content input, Skiko and unsupported Android paths
retain their existing rendering paths.

Capture publication distinguishes pending, ready and displayed input. A completed
image becomes the retained last frame only after a successful Glass draw. Source
loss discards undrawn candidates and preserves the last displayed input under
`KeepLastFrame`; full clear and lifetime teardown discard it. Pending replacement
uses the displayed image's copied crop and scale, rather than interpreting its
pixels with new geometry.

The graph rebuilt for retained geometry must satisfy the existing aggregate layer
budget. If it exceeds that budget, existing fallback rendering is used temporarily
while the displayed immutable input remains owned for recovery. Full rendering
resumes when its actual graph fits; explicit clear, trim and detach release both.

Conservative snapshot invalidation observes descendant changes that do not
re-record the parent source. It can also trigger image capture and allocation
during interaction-only animation. This is an explicit exception to the earlier
capture-cadence policy; it preserves the fused output topology, not the earlier
source-capture and allocation behavior. Capture completion requests presentation
without advancing the input revision.

On API 34 and newer, immutable input capture renders the cropped layer into a
fresh `HardwareBuffer` through `HardwareBufferRenderer`. Publication requires a
successful render result and a valid, completed fence. The wrapped image owns
its buffer reference, and that buffer is never rendered into again. Cancellation
retains the recorded input until native cleanup finishes; failed captures are
abandoned without publishing their pixels. An unsupported or failed backend uses
the existing Compose capture path, which remains the API 33 implementation.
Buffer support checks and fresh allocation run off the UI thread. Cancellation
keeps input ownership until allocation returns and the buffer is closed; the
original caller must still be active before submission. RenderNode access and
renderer construction, submission and cleanup remain on the UI thread that owns
the recorded input. Renderer cleanup retires traversal but does not itself
establish GPU completion.

This changes the capture operation, not the fused effect graph or invalidation
policy. Physical qualification on a Pixel 8a running Android 17 supports this
backend at 60 Hz for the measured scenarios. Each release benchmark used eight
iterations with fixed-performance mode and thermal status zero. Steady nine-effect
frame-overrun P90 was -1.60 ms versus baseline -8.89 ms; reversing build order
produced -1.76 ms versus -8.86 ms. Source-update nine-effect overrun P90 was
-1.49 ms versus -4.92 ms. These are one-device measurements, not isolated GPU
shader timings or a guarantee for other workloads.

The correctness change still costs CPU time and memory. Nine-effect CPU frame
P90 was approximately 8.6-8.8 ms versus baseline 5.5-6.4 ms, leaving less deadline
slack. Median sampled anonymous memory maxima increased by approximately 10 MiB;
median sampled bitmap accounting increased by approximately 115-129 MiB. Those
categories overlap and must not be added. Bitmap accounting repeatedly decreased
within every measured window, and its peaks did not accumulate over eight
iterations. GPU counters contained only a pre-window sample, so they establish
neither peak GPU usage nor complete driver reclamation.

The physical lifetime fixture released all 36 tracked consumers across three
cycles and reached zero logical input owners at clear, trim and detach. Process
PSS settled naturally to approximately 128 MiB five seconds after activity close,
remaining approximately 54 MiB above the cold baseline. The render budget bounds
planned layer pixels, not total process memory or images awaiting platform
reclamation. These observations support a bounded measured correctness tradeoff;
they do not establish return to cold memory or complete native reclamation.
Build identities, run order, trace provenance and qualification limits are
recorded in [issue #1373](https://github.com/chrisbanes/haze/issues/1373).

The earlier measurements and rejection of a separate cached output renderer
remain applicable to the measured alternatives.

## Alternatives Considered

### Keep the shared retained graph for every platform

This preserves one topology and exact semantic blur everywhere. It was rejected as the only
Android path because physical traces showed that nested layer replay and Vulkan submission could
not meet the nine-effect frame budget.

### Rasterize the source before replaying the graph

This reduced some frame CPU time, but an updating source invalidated the raster every frame and
caused repeated image allocation and snapshot work. It did not remove the downstream multi-pass
graph.

### Apply the fused effect directly to the source capture

This removes the separate output layer and its recorded source draw. It was rejected after the
otherwise identical nine-effect Pixel 6 benchmark regressed: retaining the source and fused output
layers produced the lower frame-time distribution. The extra retained layer is therefore an
intentional Android rendering boundary, not an unfused optical stage.

### Use Android's native blur and blend effects

This collapsed layer traversal, but Android's built-in full-resolution blur and blend stages
increased `QueueSubmit`, `flush commands`, and `Vulkan finish frame` time enough to regress the
benchmark. The selected graph instead composes the existing RuntimeShader semantic kernels and a
premultiplied-safe depth blend.

The [Regular calibration in ADR-0007](0007-export-regular-and-clear-as-built-in-glass-styles.md#regular-refraction-and-wide-diffusion-calibration-2026-09-16)
introduces a narrow correctness exception: wide uniform blur uses a native Gaussian node inside
the same fused output graph. Host pixel tests exposed clipped sampling bounds in the custom blur
path, leaving a sharp frame around the material. Native filtering removes that frame. Small-radius
and progressive blur retain semantic kernels. The earlier performance result still applies to
the measured alternative; this wide-blur change needs separate physical-device measurement.

### Lower capture resolution

This reduces fragment work, but the profiled bottleneck was pass and submission count. A 0.5 input
scale did not materially improve the original graph and would reduce quality for every stage.

### Retain the fused base pixels and composite local interaction patches

This most directly preserves cached base pixels during interaction-only updates. Physical-device
measurements rejected it: a cached fused base sampled by a local interaction overlay recorded a
26.9 ms CPU P90, and keeping live optics in the base with a separate lighting patch recorded
12.2 ms. Both missed the frame deadline because the additional layer replay restored the
RenderThread and Vulkan cost that fusion removes. Compiling configured interaction capabilities
into the fused shader recorded a 10.9 ms CPU P90 and a -1.2 ms frame-overrun P90.

## Consequences

- Android retains a source capture and one fused Glass output renderer; common code still owns
  their lifetime, retained-output behavior, and fallbacks.
- Small uniform and progressive blur retain the semantic two-pass kernel response. Wide uniform
  blur uses a native Gaussian node in the composed graph rather than reproducing downsample
  low-pass energy in semantic kernels.
- Progressive blur and Full chromatic aberration increase graph or shader sampling cost. Their one-
  and nine-effect scenarios must be profiled on physical devices as the implementation evolves.
- New Android RuntimeShader features must be implemented through this renderer. Silent partial
  support or sibling-dependent selection is not acceptable.
- Topology tests verify sibling-independent selection, stable interaction topology, retained
  source and output layers, intermediate optical-layer absence, and resource release. Pixel and
  physical-device checks cover visual and performance behavior.
- Interaction-only updates retain the shader provider and native effect topology, but re-record
  fused output pixels. Paths without immutable input capture also retain the source capture and
  graphics-layer allocation. The approved source-backed input exception can capture and allocate
  during interaction animation. The repaired capture backend has negative frame-overrun P90
  on the measured Pixel 8a, with higher CPU and memory cost and less deadline slack. Its
  qualification is limited to that device and the measured scenarios. The fused output decision
  still supersedes the original preference for retaining previous fused base pixels.
- Effect alpha groups the base material stages. Rim and interaction lighting receive the same
  alpha in a separate foreground pass because child content is drawn between the base and
  foreground passes. This preserves the established `VisualEffect` ordering and partial-alpha
  behavior rather than moving foreground lighting behind opaque child content.
- CPU trace markers identify renderer preparation, source capture, fused output recording, rim,
  group alpha, and composition. Blur, depth, detail, and interaction execute inside one composed
  native effect DAG and therefore cannot emit truthful per-stage CPU slices; scenario-specific
  traces, render-plan assertions, layer traversal, and Vulkan submissions provide their
  observability.
- Performance claims require release-like physical-device benchmarks and representative Perfetto
  traces; host rendering alone cannot validate driver cost.

## References

- [ADR-0002: Use a shared retained stage graph for Glass](0002-use-a-shared-retained-stage-graph-for-glass.md)
- [Performance guide](../performance.md#glass)
- Android benchmark runbook: `internal/benchmark/README.md`
