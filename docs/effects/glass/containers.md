# Glass containers

A Glass container draws its Glass members' surfaces together, so that nearby members merge into
one shape. Use it for controls that should read as one object, such as a toolbar or a segmented
control, and for surfaces that appear next to an existing one and should grow out of it.

=== "Video"

    ![type:video](../../media/glass-container.mp4){ style="width:100%;height:auto" playsinline aria-label="Glass drawing palette expanding, separating and merging" }

=== "Interactive demo"

    <iframe data-haze-sample-src="../../../sample/wasm/index.html?embed=true&amp;sample=glass-container-showcase&amp;effect=glass&amp;theme=light" title="Interactive Glass drawing palette" style="width:100%;height:560px;border:0"></iframe>

    Open Brush to reveal the controls, then drag the colour control away and release it to rejoin.
    Use the speed button to switch between normal and half speed.
    Switching back to Video resets the demo.
    [Open the sample on its own](../../sample/wasm/index.html?embed=true&sample=glass-container-showcase&effect=glass&theme=light).

The brush palette expands into connected stroke and colour controls. The colour control separates
and rejoins before the palette collapses.

This page assumes you are familiar with [Glass](index.md) styles, inputs and interaction.

## Container and members

```kotlin
// Styles compare by identity, so remember the style.
val style = remember { GlassStyle.regular.then { shape(CircleShape) } }

Row(
  modifier = Modifier.hazeGlassContainer(spacing = 16.dp),
  horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
  repeat(3) {
    Box(
      Modifier
        .size(48.dp)
        .hazeGlass(input = HazeInput.Sources(hazeState), style = style),
    )
  }
}
```

`spacing` is required, finite and not negative. A spacing of zero disables bridges. Optionally
pass `transitionSpec` and `reducedMotionPolicy`, which are described under
[Transitions](#transitions).

Every `hazeGlass` modifier below the container becomes a member of its nearest container. A member
keeps its own style, shape, colors and interaction response. A `hazeGlass` modifier outside a
container is unchanged.

## Merge rule

Members join a cluster when the gap between them is less than the spacing. The container draws
each cluster as one surface: facing edges gradually stretch towards each other, and a bridge forms
when the gap drops below half the spacing. A member that joins no cluster draws as a separate
surface. Between full and half spacing, each member's refraction and lighting smoothly change
from its standalone profile to the merged profile, so elongated surfaces keep a continuous response
as they join or separate.

Members merge only when all of these hold:

- **Same input and rendering options.** Equal `input` (for `HazeInput.Sources`, the same
  `hazeState`, `selection` and `retention`), performance mode and `expandLayerBounds`.
- **Same material.** Equal optics, surface profile, specular and edge settings, chromatic
  aberration, tone, light position and `alpha`. Regular and Clear surfaces therefore never merge.
  Shape, tint, background color and interaction responses may differ; colors blend across the
  bridge.
- **No rotation or scale.** A member rotated or scaled by its layout, for example with
  `graphicsLayer`, draws separately. Translation and a layer alpha, such as an `AnimatedVisibility`
  fade, don't prevent merging.
- **Adjacent in order.** Members are ordered by when they attach: composition order at first, with
  members added later placed after the existing ones. Only members next to each other in that order
  share a cluster. A non-matching member between two compatible ones splits them, even when it is
  far away, because merging never changes drawing order.
- **At most four per cluster.** Extra members draw separately, without a bridge. Where an extra
  member overlaps the cluster, blur and tint apply twice in the overlap.

Lower-quality performance modes also render the merged shape at lower resolution.

## Optical size

A container uses one optical size: the largest shortest side among its members. Every member's
optics use that size, whether or not it is in a cluster, so a small member next to a large one
takes on the large member's diffusion, even when the two don't merge.

The size updates immediately, without animation, when a member is added, removed or resized.
Members that are animating out, or whose bounds have collapsed to zero area, don't count.

## Where Glass samples from

A container draws all of its members' Glass behind all of its children. With
`HazeInput.Backdrop`, the glass samples only content drawn before the container. With
`HazeInput.Sources`, the glass samples the captured sources as usual. Joining or leaving a cluster
never changes a member's z-order.

## Interaction

Interaction responses work as described in [Glass interaction](index.md#interaction), applied to
the member's own part of the merged shape: pressing a member scales and lights only that part. A
uniform pressed `scale` does not split the cluster.

With `HazeInput.Sources`, a scaled member can still change its sampled backdrop when it joins or
leaves a cluster. Standalone Glass scales the completed backdrop image, while a merged surface
keeps backdrop sampling anchored and scales the member's shape. The optical-profile blend does
not remove this existing sampling difference; it is tracked in [#1605](https://github.com/chrisbanes/haze/issues/1605).

## Transitions

A member that attaches or detaches after the container's first draw animates its glass. Choose
the transition with `hazeGlass(memberTransition = ...)`:

- `GlassMemberTransition.MatchedGeometry` is the default. A member that attaches within the
  spacing of another member grows out of it, and a member that detaches retracts into it. Beyond
  the spacing, it falls back to `Materialize`.
- `GlassMemberTransition.Materialize` fades the glass in or out where it is. A fading member draws
  on its own until it is fully opaque.
- `GlassMemberTransition.None` applies no transition.

`transitionSpec` times the animation and defaults to `spring()`. Members with
`GlassMemberTransition.None` do not animate. `reducedMotionPolicy` decides how the transition
responds to the platform motion-duration scale:

- `GlassReducedMotionPolicy.System`, the default, follows the scale. Above zero, transitions run
  under that scale. At zero, because animations are off, members snap: they appear and disappear
  with no transition, as with `GlassMemberTransition.None`.
- `GlassReducedMotionPolicy.Reduced` keeps the morph but uses a critically damped spring, so it
  does not overshoot, at full duration whatever the scale is. The spring keeps `transitionSpec`'s
  stiffness when it is a spring and otherwise uses medium stiffness.
- `GlassReducedMotionPolicy.Full` uses `transitionSpec` as written, whatever the scale is.

Changes to `transitionSpec` and `reducedMotionPolicy` apply from the next transition.

Haze animates only the glass. Animate member content yourself, for example with
`AnimatedVisibility` and fades. Don't animate a member's layout scale: a scaled member stops
merging and loses its matched-geometry transition.

The glass disappears immediately, without a transition, when:

- the member uses `HazeInput.Content`;
- the member is rotated or scaled;
- no remaining member uses the same input and sources, including when the last member leaves.

A member whose nearest container changes while it stays in the layout, for example when a nested
container is added or removed around it, appears in the new container immediately. Moving
a member with `movableContentOf` detaches and reattaches it, so it detaches from the old container
and attaches to the new one, each with the transition described above. A running transition ends
without animating when the member it grows from or retracts into leaves, or moves beyond the
spacing.

## Lazy layouts

Pass the lazy layout's state to `hazeGlassContainer` as `scrollState`. While that state is
scrolling, including the frame in which it stops, members appear and disappear without a
transition, whatever their `memberTransition`. Items inserted or removed while the layout is idle
still animate, but a scroll that starts while an item's glass is still animating out ends that
animation immediately.

```kotlin
// Styles compare by identity, so remember the style.
val style = remember { GlassStyle.regular.then { shape(CircleShape) } }
val listState = rememberLazyListState()

LazyRow(
  state = listState,
  modifier = Modifier.hazeGlassContainer(spacing = 16.dp, scrollState = listState),
  horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
  items(chips, key = { it.id }) { chip ->
    Chip(
      chip = chip,
      modifier = Modifier.hazeGlass(
        input = HazeInput.Sources(hazeState),
        style = style,
      ),
    )
  }
}
```

Drags, flings, `animateScrollBy` and `scrollToItem` are scrolls. Position changes made outside a
scroll, such as `requestScrollToItem` or `dispatchRawDelta`, are not, so their items animate.

Without `scrollState`, items that scroll into view animate like any other new member. With the
default `MatchedGeometry`, an item's glass grows out of its neighbor and lags its content, and an
item that scrolls out leaves a trailing ghost until the animation ends. Items present at the
container's first draw never animate.

## Fallback

Without `RuntimeShader` support, or when a cluster is too large to render in full, each cluster
draws as one surface clipped to the hard union of the member shapes, with no bridge. Members keep
their own tint and background color; where members overlap, the earlier member's colors apply.
Each member resolves `GlassAccessibilitySettings` and appearance in its own context. With
`reduceTransparency`, it draws an opaque white/light or black/dark base (or its opaque
`reducedTransparencyFallbackColor` override), then its authored background colour and tint. Earlier
members still own overlapping regions, and overall alpha still fades the material. Disabling the
preference restores that member's ordinary fallback appearance.

Per-member press lighting is still drawn. See [Fallbacks](index.md#fallbacks) for what else
fallback rendering omits.

## Samples

The `Glass — Container` sample shows a merging toolbar, two draggable members with a spacing
control, a member added and removed with `AnimatedVisibility`, and a cluster of members with
different tints. Its source is in
[`GlassContainerSample.kt`](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassContainerSample.kt).

The `Glass — Container Showcase` sample places a drawing palette over two brushstrokes. Open
Brush to reveal stroke-width and colour controls, then drag the colour control away to separate it
from the palette. Releasing it rejoins the controls, and closing Brush retracts them. The sample
plays these transitions as a loop until you interact with it. Its source is in
[`GlassContainerShowcaseSample.kt`](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassContainerShowcaseSample.kt).
