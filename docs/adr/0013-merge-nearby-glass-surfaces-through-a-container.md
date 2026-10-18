---
status: accepted
---

# Merge nearby Glass surfaces through a container

On iOS, Liquid Glass elements inside a `GlassEffectContainer` blend into one shape when they come
within the container's spacing. Elements that are inserted or removed morph out of, or back into,
their neighbours. Haze couldn't do either, because each `hazeGlass` node draws its own layer from
the signed distance field of a single rounded rectangle
([#1409](https://github.com/chrisbanes/haze/issues/1409)). A prototype
([#1438](https://github.com/chrisbanes/haze/issues/1438)) showed that the existing optics hold on a
smooth minimum of member shapes. It needed three fixes:

- fade refraction across thin bridges;
- fade smoothing as members overlap, so a full overlap ends at their union;
- stop sizing optics from the inflated layer.

Haze will add `Modifier.hazeGlassContainer(spacing, transitionSpec, reducedMotionPolicy)`.
`spacing` is required and has no default. Ordinary `hazeGlass` modifiers below it become
**Glass members** of their nearest container, and the container draws every member's glass behind
its own children. A member's own style still sets its shape, input and appearance.

## Merging

- Members whose gap is less than half the spacing join into a **Glass cluster**. The container draws
  each cluster as one surface, shaped by a polynomial smooth minimum with `k = spacing`. Members
  outside every cluster draw as separate surfaces.
- A cluster merges at most four members, because shapes reach the shader as fixed-size uniform
  arrays. Beyond four, the extra members draw on their own without a bridge, in a deterministic
  order. Where an extra member overlaps the cluster, blur and tint apply twice in the overlap,
  which is the visible cost of the cap.
- A cluster draws at one z-position, so only members registered consecutively share a surface. A
  member registered between two compatible neighbours splits them, even when it is far away, so
  merging never reorders members.
- Members can merge only when their input, performance mode and material values are equal:
  optics, surface profile, specular and edge response, chromatic aberration, tone, light position
  and alpha. Values that belong to each member are excluded from that comparison: `shape`,
  `tint`, `backgroundColor` and interaction responses. Each member keeps its own shape, colours
  and interaction response, and the colours blend across the bridge. As a result, Regular and
  Clear surfaces never merge.
- The container resolves one optical size: the largest shortest side among all its members. Every
  member evaluates its own optics at that size, whether or not it is in a cluster. When the size
  changes because a member was added, removed or resized, it changes immediately and is not
  animated. A member that is leaving does not count.
- Each member's interaction transform and interaction lighting apply to that member's part of the
  field. Pressing one member can shrink it within the merged shape (interaction scales are at most
  1), and only that member brightens.

## Transitions

Members have no identifiers, because a Compose modifier node already keeps one identity for as long
as it is attached. Each member chooses a transition:

- **`MatchedGeometry`** is the default. A member that attaches within the spacing of another
  member grows out of it. A member that detaches retracts into it, while the container keeps
  drawing its shape until the animation ends.
- **`Materialize`** fades the glass in or out where it is. `MatchedGeometry` also falls back to it
  beyond the spacing.
- **`None`** applies no transition.

A morph starts or ends on the neighbour's drawn outline: its rect after its interaction transform,
with its corners scaled as they are drawn. A pressed neighbour therefore stays continuous with the
member growing out of it or retracting into it. Spacing and neighbour checks still ignore
interaction transforms. A member chooses a growing neighbour by its current morph, and keeps that
link while either the morph or the neighbour's layout is within the spacing.

A member with `HazeInput.Content` input leaves immediately, without a ghost, because the content
its glass samples leaves with it
([#1485](https://github.com/chrisbanes/haze/issues/1485)).

Haze doesn't animate member content, so callers fade content themselves.

Under reduced motion, the transition keeps its morph but uses a critically damped animation
instead of the spring, so it doesn't overshoot. `reducedMotionPolicy` uses the existing
`GlassReducedMotionPolicy` values. When `GlassReducedMotionPolicy.System` sees a zero platform
motion scale (animations off), member transitions snap instead
([#1560](https://github.com/chrisbanes/haze/issues/1560)).

## Considered options

- **Members registering through an explicit state object.** Rejected. It matches the `HazeState`
  pattern, but adds a public type and mismatch cases. Finding the nearest ancestor needs neither.
- **A container that declares the input and style.** Rejected. Shape is a style property and
  members need their own shapes. Ignoring some style values inside a container would also give
  Glass two places to declare a material.
- **Merging only identical styles.** Rejected. iOS merges elements with different tints. Comparing
  style instances would also break merging for an inline `GlassStyle {}` block, which creates a
  new instance whenever its call site recomposes.
- **Sizing optics from the smallest member, or per cluster.** Rejected. iOS sizes optics per
  container, so a small control next to a large panel takes on the panel's diffusion even when the
  two don't merge.
- **One layer covering every member.** Rejected. A sparse container would pay for a container-sized
  layer while nothing merges.
- **Snapping transitions under reduced motion.** Rejected for `GlassReducedMotionPolicy.Reduced`.
  iOS keeps the morph and removes its elasticity. Turning animations off is different:
  `GlassReducedMotionPolicy.System` snaps at a zero platform motion scale
  ([#1560](https://github.com/chrisbanes/haze/issues/1560)).
- **`glassEffectID`-style identifiers and `glassEffectUnion`-style forced unions.** Deferred.
  Identifiers add nothing over node identity for neighbour morphs. A forced union needs grouping
  rules that the first version doesn't need.

## Consequences

- **Where Glass samples from changes inside a container.** It sits behind all of the container's
  children, so with backdrop input it samples only content drawn before the container. Joining or leaving a cluster
  never moves a member in z-order, and `hazeGlass` outside a container is unchanged.
- **Optics depend on the container's members.** A small member next to a large one shows the large
  member's diffusion even when the two don't merge. Adding or removing the large member changes the
  small one's optics immediately.
- **A cluster is one Glass surface for the renderer.** This narrows ADR-0003's ownership rule from
  one node to one drawn surface. On Android API 33 and newer, each cluster owns one source capture
  and one fused output, built as ADR-0003 describes. Skiko and environments without RuntimeShader
  keep their existing adapters. An isolated member is a cluster of one. The container decides which
  members share a surface, but renderer selection still doesn't depend on member or sibling count.
- **The renderer gains shader inputs and new uniform plumbing.** The new shader inputs are the
  merged field, per-member colours, interaction transforms and lights, which needs an array setter
  for runtime-shader uniforms. In reduced-quality modes, merged silhouettes follow single surfaces
  and render at the reduced input scale. Full-resolution silhouettes are
  [out of scope](https://github.com/chrisbanes/haze/blob/main/.out-of-scope/full-resolution-glass-silhouettes-in-reduced-modes.md)
  ([#1339](https://github.com/chrisbanes/haze/issues/1339)).
- **The dome term is disabled for clusters.** It is defined only for a single rounded rectangle.
- **The fallback renderer draws no bridge.** It renders each cluster as one surface clipped to the
  hard union of the member shapes, so overlapping members of a cluster never double their blur or
  tint. Members beyond the cap still draw separately.
- **A system animation-scale change applies from the next transition.** A transition that is
  already running continues as it started.
- **A retract into a neighbour whose style alpha the caller animates ends immediately.** Alpha is
  part of the merge key, so the neighbour is no longer compatible once its alpha changes.
- **Merging costs extra GPU work.** On a Pixel 8a, two merged pills finished frames about 1.2–1.5 ms
  later at P50 and P90 than the same pills drawn independently, with a layer about three times the
  area. CPU frame time was within noise. These were single sessions, and CPU frame time does not
  measure GPU shader cost.
- **Lazy layouts pass their scroll state.** Compose gives a modifier no signal that tells a
  scroll-driven attach from a state-driven one. Callers pass the lazy layout's `ScrollableState` to
  `hazeGlassContainer`, and members attach and detach without a transition while it scrolls. The
  default stays `MatchedGeometry` ([#1485](https://github.com/chrisbanes/haze/issues/1485)).
  Inferring scrolls from member positions was measured and rejected: a uniform state-driven shift
  looks like a scroll, and a scroll past the viewport leaves no evidence
  ([#1524](https://github.com/chrisbanes/haze/issues/1524)).
