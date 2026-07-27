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

1. The existing semantic horizontal and vertical blur kernels.
2. Sharp-to-blurred depth mixing.
3. A RuntimeShader for refraction, Simple or Full chromatic aberration, tint, tone, Fresnel
   response, and shape masking.
4. A refraction-detail shader that samples the original sharp source. The optical shader reserves
   the detail branch's geometric coverage, then the sharp detail is combined with premultiplied
   `Plus`.

The blur shaders are chained directly into the output effect instead of rasterizing intermediate
graphics layers. Large blur plans include bounded low-pass prefilters that reproduce the retained
half-resolution response without retaining a half-resolution surface. Progressive blur uses the
same caller mask and semantic two-pass kernels as the retained renderer. Full chromatic aberration
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
- Non-progressive and progressive blur retain the semantic two-pass kernel response. Large
  non-progressive plans reproduce downsample low-pass energy inside the composed graph.
- Progressive blur and Full chromatic aberration increase graph or shader sampling cost. Their one-
  and nine-effect scenarios must be profiled on physical devices as the implementation evolves.
- New Android RuntimeShader features must be implemented through this renderer. Silent partial
  support or sibling-dependent selection is not acceptable.
- Topology tests verify sibling-independent selection, stable interaction topology, retained
  source and output layers, intermediate optical-layer absence, and resource release. Pixel and
  physical-device checks cover visual and performance behavior.
- Interaction-only updates retain the shader provider, native effect topology, source capture, and
  graphics-layer allocation, but re-record fused output pixels. This evidence-driven exception
  supersedes the original issue preference for retaining the previous fused base pixels.
- CPU trace markers identify renderer preparation, source capture, fused output recording, rim,
  group alpha, and composition. Blur, depth, detail, and interaction execute inside one composed
  native effect DAG and therefore cannot emit truthful per-stage CPU slices; scenario-specific
  traces, render-plan assertions, layer traversal, and Vulkan submissions provide their
  observability.
- Performance claims require release-like physical-device benchmarks and representative Perfetto
  traces; host rendering alone cannot validate driver cost.

## References

- [ADR-0002: Use a shared retained stage graph for Glass](0002-use-a-shared-retained-stage-graph-for-glass.md)
- [Glass performance](../glass/performance.md)
- Android benchmark runbook: `internal/benchmark/README.md`
