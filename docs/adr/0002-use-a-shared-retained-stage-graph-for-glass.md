# ADR-0002: Use a shared retained stage graph for Glass

## Status

Superseded by [ADR-0003](0003-use-one-android-fused-glass-renderer.md)

## Date

2026-07-22

## Context

Glass needs original content, blurred content, depth mixing, optical refraction, lighting, shape
masking, and interaction output to compose into one material. The available runtime-shader APIs are
not symmetrical across platforms: Skiko can connect multiple child shaders directly, while
Android's runtime-shader render-effect path exposes a more constrained content input.

An early Android spike proposed drawing a blurred underlay and an original-content Glass overlay as
two platform-specific passes. That approach risked different depth semantics between Android and
Skiko, relied on mutating a retained layer's render effect between draws, and would have duplicated
renderer policy at the platform boundary.

The renderer also needs to support animated parameters without capturing, blurring, and composing
every stage again on every frame. Rebuilding the entire effect graph would be simpler, but would
make retained Glass unnecessarily expensive and would increase transient graphics memory.

## Decision

Runtime-shader Glass uses one shared retained stage graph on Android and Skiko:

1. Capture source content into a retained layer.
2. Produce an optional separable-blur layer.
3. Select or record the depth input:
   - depth `0` uses the source;
   - depth `1` uses the blurred layer;
   - intermediate depth records a retained mix of source and blurred content.
4. Apply the optical pass to the selected depth input.
5. Compose optional refraction-detail, rim, interaction-lighting, and group-alpha stages.

The common renderer owns graph topology, stage ordering, retained layers, invalidation, and resource
lifetime. Platform-specific factories create and update the runtime-shader render effects, but do
not define a different rendering graph.

The renderer records only stages invalidated by changed source content, parameters, topology, or
resource availability. It releases obsolete stages when topology changes and releases retained
resources on detach, relevant memory pressure, or incompatible size changes.

Fallback rendering remains a separate delegate for environments where the runtime path is
unsupported or outside the render budget. It should preserve public Glass semantics as closely as
its capabilities allow without changing the runtime graph.

## Alternatives Considered

### Use a monolithic dual-input shader everywhere

This would let the shader sample original and blurred content directly and could minimize explicit
layer stages. It was rejected because Android and Skiko do not expose equivalent multi-input
runtime-shader capabilities.

### Use an Android-specific two-pass underlay and overlay

The proposed Android spike would draw a blurred underlay followed by a Glass overlay sourced from
original content, while Skiko retained its dual-input shader. It was rejected as the long-term
architecture because it would encode different composition policy per platform and depend on
render-effect mutation between draws.

### Rebuild the complete graph on every draw

This would simplify invalidation and retained-state bookkeeping. It was rejected because source
capture, blur, and intermediate composition are expensive and often unchanged while only dynamic
uniforms or interaction state animate.

### Use a common retained stage graph

This was chosen because it provides the same stage ordering and depth semantics across platforms,
fits each platform's single-input render-effect path, and allows unchanged stages to be reused.

## Consequences

- Android and Skiko share depth semantics and renderer topology even though their shader wrappers
  are platform-specific.
- Intermediate graphics layers and stage-invalidation bookkeeping make the renderer more complex.
- Retained rendering lowers repeated work during animation but consumes graphics memory, so memory
  budgeting, trim handling, and deterministic resource release are part of the architecture.
- A stage must never be reused after its inputs, parameters, size, or required topology change.
- Runtime and fallback implementations require cross-platform invariant tests rather than assuming
  that identical shader source alone guarantees semantic parity.
- Screenshot coverage at depth `0`, intermediate depth, and depth `1` is the highest-level contract
  for depth progression and shape masking.

## References

- [Android Glass Depth Spike Design](https://github.com/chrisbanes/haze/blob/c51ea4aa02e626ac2c1112a1cec4e8015774ba0a/docs/superpowers/specs/2026-07-01-android-glass-depth-spike-design.md)
- [PR #1032: Overhaul and rename the Glass visual effect](https://github.com/chrisbanes/haze/pull/1032)
- [PR #1051: Fix Glass shader correctness](https://github.com/chrisbanes/haze/pull/1051)
- [PR #1053: Limit retained Glass rendering memory](https://github.com/chrisbanes/haze/pull/1053)
- [PR #1057: Make retained Glass rendering cheap during animation](https://github.com/chrisbanes/haze/pull/1057)
- [PR #1064: Include Glass group-alpha composition in the render budget](https://github.com/chrisbanes/haze/pull/1064)
