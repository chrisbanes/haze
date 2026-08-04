# Glass

A refraction-driven Glass effect that combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

!!! warning "Experimental"
    This module is experimental and may change or be removed in future releases. APIs are gated behind `@ExperimentalHazeApi`.

## Download

Glass is published with Haze's [snapshot builds][glass-snap]. Add the Sonatype Central snapshot
repository and use the same snapshot version for both artifacts:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots")
}

dependencies {
    implementation("dev.chrisbanes.haze:haze:XXX-SNAPSHOT")
    implementation("dev.chrisbanes.haze:haze-glass:XXX-SNAPSHOT")
}
```

[glass-snap]: https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/dev/chrisbanes/haze/haze-glass/

### Built-in material

The default material uses Haze's geometry-aware Adaptive optics. Small surfaces retain a clearer,
more refractive edge treatment, while larger surfaces converge toward a calmer base response.
Aspect ratio and roundness make small bounded adjustments without changing the material boundary.
Adaptive content behavior, interaction-driven deformation, and morphing are unsupported.

## Parameters

- **tint**: Glass tint (defaults to transparent).
- **optics**: Optical material configuration. `GlassOptics.Adaptive` (the default) is the
  built-in Haze material; it responds to the material's size, aspect ratio, and
  rounded geometry. Call `optics(...)` directly for an inline fixed optical configuration, or use
  `GlassOptics.Fixed(...)` when you need to store, reuse, copy, or select the complete value. Its
  `refractionStrength`, `refractionHeightFraction`,
  `refractionDisplacement`, `depth`, `blurRadius`, and optional `progressive` values are used
  without geometry-dependent adjustment. `refractionDisplacement` and `blurRadius` are
  density-independent `Dp`, while `refractionHeightFraction` is a unitless fraction of the
  material's shortest side. `progressive` accepts
  `HazeProgressive.verticalGradient`, `horizontalGradient`, `HazeProgressive.RadialGradient`,
  or `forShader` to vary blur radius across the glass surface.
- **specularIntensity**: Highlight strength `0..1` (default 0.4).
- **ambientResponse**: Fresnel/edge lift `0..1` (default 0.46).
- **edgeSoftness**: Soft fade at the edges (default 2.dp). Set to 0.dp for hard edges.
- **shape** (`RoundedCornerShape`): Rounded-rect boundary for refraction and masking (default 16.dp corners).
- **surfaceProfile**: Cross-section profile for the refraction bezel. Options: `Circle` (default), `Squircle`, `Lip`, `Concave`.
- **lightPosition**: `Alignment` of the light within the material's measured bounds (default
  `Alignment.Center`). Logical start and end follow the node's layout direction.
- **chromaticAberrationStrength**: Dispersion strength `0..1` (default 0). Higher values produce prismatic color splitting at edges.
- **chromaticAberrationMode**: Quality mode for chromatic aberration. `Simple` (default, fast) or `Full` (spectral, more expensive).
- **alpha**: Overall opacity multiplier `0..1` (default 1).

## GlassStyle

`GlassStyle` is an opaque, immutable sequence of appearance writes. Its builder executes once when
the Style is constructed, canonicalizing and recording each value. Compose Styles with the
intrinsic `then` members; later writes win. A Style can be shared by any number of `hazeGlass`
nodes: each node replays defaults, `LocalGlassStyle`, and its explicit Style into its own snapshot
without rerunning caller code.

Values captured while constructing a Style are frozen into those writes. Mutating captured state
does not update attached nodes; construct and supply a replacement Style through recomposition to
change their appearance. Replacement also removes properties and interaction blocks omitted by the
new Style.

```kotlin
val baseStyle = GlassStyle {
  tint(Color.White.copy(alpha = 0.16f))
  optics(refractionStrength = 0.8f)
  shape(RoundedCornerShape(20.dp))
}
val emphasizedStyle = baseStyle.then { specularIntensity(0.7f) }

CompositionLocalProvider(LocalGlassStyle provides baseStyle) {
  // Each node gets a fresh snapshot; an explicit Style is applied last.
}
```

### Light alignment

Light position is authored semantically with Compose `Alignment` and resolved independently for
each node. A shared Style therefore places `Alignment.Center` at the center of every consuming
material, even when their measured sizes differ. Logical alignments such as `Alignment.TopStart`,
`Alignment.CenterStart`, and `Alignment.CenterEnd` automatically follow LTR or RTL layout direction;
use physical alignments only when that is the intended semantic.

```kotlin
val sharedLighting = GlassStyle {
  lightPosition(Alignment.CenterStart)
}
```

Use `BiasAlignment` for a continuously moving proportional light. Biases `-1f`, `0f`, and `1f`
represent the start/top edge, center, and end/bottom edge respectively, and values outside that
range place the virtual light beyond the material bounds.

```kotlin
val movingLighting = GlassStyle {
  lightPosition(BiasAlignment(horizontalBias = 0.4f, verticalBias = -0.6f))
}
```

## Default style

`GlassDefaults.style` uses the built-in Haze `GlassOptics.Adaptive` material and is replayed
before `LocalGlassStyle` and the modifier's explicit Style.

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeGlass(input = HazeInput.Sources(hazeState))
)
```

### Choosing optics

Use `GlassOptics.Adaptive` for the built-in Haze material, which adapts its optical response to the
material's size, aspect ratio, and roundness. For ordinary inline fixed Style authoring, call the
direct `optics(...)` function:

```kotlin
GlassStyle { optics(GlassOptics.Adaptive) }
```

```kotlin
GlassStyle {
  optics(
    blurRadius = 20.dp,
    refractionStrength = 0.8f,
    refractionHeightFraction = 0.3f,
    refractionDisplacement = 18.dp,
    depth = 0.5f,
  )
}
```

The direct function constructs `GlassOptics.Fixed`. Fixed values are not geometry-adjusted:
`refractionStrength`, `refractionHeightFraction`, and `depth` must be finite values in `0f..1f`;
`refractionDisplacement` and `blurRadius` must be specified, finite, non-negative `Dp`; and
`progressive` is optional. Large logical displacement and blur distances remain valid even when a
renderer caps effective sampling or kernel work. `refractionHeightFraction` remains relative to the
material's shortest side.

Keep a complete value when it is reused, stored, copied, or selected programmatically:

```kotlin
val reusableOptics = GlassOptics.Fixed(blurRadius = 20.dp)
val style = GlassStyle { optics(reusableOptics) }
```

Backend sampling and kernel construction remain rendering details. `GlassOptics.Fixed` is distinct
from `HazeSampling.Fixed(pixelFraction)`, which selects a sampling policy rather than optical
values. `shape` and `tint` stay independent of the selected optics. The `shape`
supplied to Glass is the authoritative material boundary. An outer `Modifier.clip()` is not visible
to Glass and does not define its optical boundary; add one with the same shape only when child
content also needs clipping.

### Retained Output

Glass can retain and redraw its last captured output when all source areas disappear. This
keeps source transitions smooth, but can briefly preserve stale pixels from removed source content.
The typed modifier preserves Glass's default retained-output policy. Keep source ownership explicit
with `HazeInput.Sources` so transitions are visible at the call site:

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeGlass(
      input = HazeInput.Sources(hazeState),
      style = GlassStyle,
    )
)
```

The default is `HazeSourceRetention.KeepLastFrame`. For privacy-sensitive source content, opt out
of retaining pixels when the source disappears:

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Sources(
    state = hazeState,
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
  style = GlassStyle,
)
```

You can select a literal optical configuration when the built-in material does not fit the design:

```kotlin
GlassStyle {
  tint(Color.White.copy(alpha = 0.20f))
  optics(
    progressive = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0.25f,
    ),
  )
}
```

## Fallbacks

- Runtime shader path: rounded SDF refraction, tint/specular/Fresnel, chromatic aberration, and
  edge softness. On Android API 33 and newer, one single-output renderer handles single and
  multiple effects, semantic and progressive blur, Full chromatic aberration, sharp-source
  refraction detail, and configured interaction optics. Interaction lighting uses a localized
  foreground patch so that it remains above content.
- Fallback path: an approximation using tinted fill, radial highlight, and a soft rim; it respects rounded shapes and alpha when runtime shader render effects are unavailable. Interaction lighting and transforms work on this path, but interactive optics, white-point adjustment, and refraction are no-ops.

## Performance

On the modern Android path, every Glass effect composes a blurred optical branch and sharp-source
detail branch into one retained output per surface. Renderer selection does not depend on sibling
count. Progressive blur and Full chromatic aberration remain in the same native effect graph,
while live interaction values update locally weighted math without allocating retained detail
layers. See
[Glass performance](../glass/performance.md) for the physical-device benchmark setup, results, and
Perfetto interpretation.

## Interaction

Glass interaction is default-disabled and entirely opt-in. It adds a visual response only: it does
not add click handling, focusability, semantics, or keyboard/D-pad activation.

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Sources(hazeState),
  style = GlassStyle {
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
      scale(0.98f)
    }
  },
)
```

Declare only the states and response channels the material needs. To make focus and keyboard/D-pad
activation useful, retain one `MutableInteractionSource` and share it with both the glass effect
and your behavior modifiers:

```kotlin
val interactionStyle = GlassStyle {
  hovered { lightingIntensity(0.35f) }
  focused { lightingIntensity(0.35f) }
  pressed {
    lightingIntensity(1f)
    refractionMultiplier(1.08f)
    whitePointDelta(0.04f)
    scale(0.98f)
  }
  interactionLightRadiusFraction(0.7f)
  interactionPositionAnimationSpec(
    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
  )
}

val interactionSource = remember { MutableInteractionSource() }

Modifier
  .clickable(interactionSource = interactionSource, indication = null) { onClick() }
  .focusable(interactionSource = interactionSource)
  .hazeGlass(
    input = HazeInput.Sources(hazeState),
    style = interactionStyle,
    interactionSource = interactionSource,
    interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
    interactionTransformPivot = GlassTransformPivot.Pointer,
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
  )
```

Hover, focus, and press responses, the localized-light radius, and light-position animation are
presentation. They travel with `GlassStyle`, compose through `LocalGlassStyle` and `then`, and can
be reused across nodes. Each consuming node still owns its `InteractionSource`, transform target,
transform pivot, reduced-motion policy, geometry, animation state, controller, renderer, and
platform resources. Sharing `interactionStyle` therefore shares appearance without coupling
interaction signals or runtime state. Replace the Style to update presentation on the existing
renderer; replace modifier mechanics to reconfigure only that node.

Custom blocks replace that state's preset from identity. Resolve each property with fixed
precedence: focused, then hovered, then pressed. Use `animate(toSpec, fromSpec)` inside a custom
block to own the arrival and departure animation specs respectively; entering or replacement uses
`toSpec`, while departing uses `fromSpec`.

```kotlin
GlassStyle {
  pressed {
    animate(
      toSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
      fromSpec = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
      ),
    ) {
      scale(0.98f)
    }
  }
}
```

The node-owned `interactionTransformTarget` argument selects whether a response transforms only
the material or the material and content. `interactionTransformPivot` selects `Pointer` or
`Center`. Omit a state block from a replacement Style to remove it.
`GlassReducedMotionPolicy.System` follows the available system duration scale, `Reduced` snaps
lighting and optics while suppressing transforms, and `Full` forces motion. The
`GlassInteractionScope` receiver is sealed and implemented by Haze; it is a declaration DSL, not a
consumer implementation point.

## Usage

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeGlass(
      input = HazeInput.Sources(hazeState),
      style = GlassStyle {
        tint(Color.White.copy(alpha = 0.16f))
        optics(
          refractionStrength = 0.8f,
          refractionHeightFraction = 0.32f,
          depth = 0.5f,
        )
        specularIntensity(0.7f)
        ambientResponse(0.7f)
        edgeSoftness(14.dp)
        shape(RoundedCornerShape(20.dp))
        surfaceProfile(SurfaceProfile.Squircle)
        chromaticAberrationStrength(0.2f)
      },
    )
)
```

## Tips

- `GlassOptics.Adaptive` is the right starting point for material-like glass that should respond
  naturally to its geometry. Use direct `optics(...)` for inline fixed authoring and
  `GlassOptics.Fixed` when the complete value needs to be reused or selected programmatically.
- Keep `chromaticAberrationStrength` modest; start at 0.1-0.25 to avoid rainbow artifacts.
- Combine `edgeSoftness` with rounded shapes for smooth clipping; set `edgeSoftness = 0.dp` to rely purely on the shape.
- Use `SurfaceProfile.Concave` for an inward-curving bezel or `SurfaceProfile.Lip` for a raised rim effect.
