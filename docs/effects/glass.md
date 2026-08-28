# Glass

A refraction-driven Glass effect that combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

![type:video](../media/glass.mp4)

## Download

Glass is published to Maven Central starting with Haze `2.0.0-alpha04`. Use the same version for
the core and Glass artifacts:

```kotlin
dependencies {
    implementation("dev.chrisbanes.haze:haze:<version>")
    implementation("dev.chrisbanes.haze:haze-glass:<version>")
}
```

For unreleased changes, follow the [snapshot build instructions](../using-snapshot-version.md) and
use the same snapshot version for both artifacts.

### Material 3

Add the optional Material 3 integration when a Glass surface should use the current theme surface
color:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze-glass-material3:<version>")
}
```

`GlassStyle.Material3()` uses `MaterialTheme.colorScheme.surface` as its default container color.
Pass `containerColor` to use a different color. Supply a replacement Style through recomposition
when the theme changes. It deliberately leaves the tint unset unless you pass one, so it never
derives a tint from `LocalContentColor`.

Apply Material 3 to a built-in or custom Style with `material3()`:

```kotlin
val regular = GlassStyle.regular.material3()
val clear = GlassStyle.clear.material3(tint = Color.White.copy(alpha = 0.16f))
```

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Sources(hazeState),
  style = GlassStyle.Material3(tint = Color.White.copy(alpha = 0.16f)) {
    optics(refractionStrength = 0.8f)
  },
)
```

The factory writes its container color and optional tint before its block, allowing the block to
override either value. It also works as a subtree default:

```kotlin
CompositionLocalProvider(LocalGlassStyle provides GlassStyle.Material3()) {
  // Glass modifiers in this subtree inherit the Material 3 surface background.
}
```

### Built-in styles

`GlassStyle.regular` is the default built-in Glass style. Its blur and depth adapt to the
surface's shortest dimension and include a restrained edge fold, where the local sampling direction
reverses so incoming content can appear inverted near the glass boundary. This makes it a good fit
for reusable components.

`GlassStyle.clear` is the alternative built-in style for surfaces that should keep more of the
background visible. Its blur and depth increase smoothly with the surface's shortest side, while
its authored refraction and distinct edge and lighting response remain recognizable on renderers
that simplify advanced optical effects. These are Haze styles informed by the platform distinction;
they do not promise pixel parity with another system.

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Sources(hazeState),
  style = GlassStyle.clear.material3(tint = Color.White.copy(alpha = 0.12f)),
)
```

## Parameters

- **backgroundColor**: Color composited behind captured content before refraction and blur
  (defaults to transparent). Use an opaque color when transparent captured content must fully
  obscure the original sharp source.
- **tint**: Glass tint (defaults to transparent).
- **optics**: Optical material configuration. `GlassStyle.regular` supplies the default
  size-aware optics. Call `optics(...)` for inline fixed configuration, or keep a `GlassOptics`
  value when it needs to be reused or selected programmatically.
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

Glass validates configuration when a Style or `GlassOptics` value is created instead of
silently correcting it later. Validate or clamp values from user input and remote data before
building the Style. The generated API reference documents the accepted range for each property.

## GlassStyle

`GlassStyle` is immutable and safe to share. Build a base Style, use `then` for variations, and
provide a replacement Style through recomposition when the appearance changes. Values omitted by
the replacement fall back to `LocalGlassStyle` and then the individual `GlassDefaults` values.

A Style captures its inputs when it is constructed. Changing captured state does not update an
existing Style; construct and provide a replacement instead.

```kotlin
val baseStyle = GlassStyle {
  backgroundColor(Color.White)
  tint(Color.White.copy(alpha = 0.16f))
  optics(refractionStrength = 0.8f)
  shape(RoundedCornerShape(20.dp))
}
val emphasizedStyle = baseStyle.then { specularIntensity(0.7f) }

CompositionLocalProvider(LocalGlassStyle provides baseStyle) {
  // Use baseStyle as the default for Glass in this subtree.
}
```

### Light alignment

Use Compose `Alignment` values so lighting adapts to each surface. Logical alignments such as
`Alignment.CenterStart` and `Alignment.CenterEnd` also follow LTR or RTL layout direction.

```kotlin
val sharedLighting = GlassStyle {
  lightPosition(Alignment.CenterStart)
}
```

Use `BiasAlignment` when the light needs a continuous proportional position rather than a named
alignment.

```kotlin
val movingLighting = GlassStyle {
  lightPosition(BiasAlignment(horizontalBias = 0.4f, verticalBias = -0.6f))
}
```

## Style defaults

`GlassStyle.regular` is the default built-in material response. Individual omitted values fall back
to `GlassDefaults`. Use `LocalGlassStyle` to set a default for a subtree, and pass an explicit Style
when one element needs to differ.

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeGlass(input = HazeInput.Sources(hazeState))
)
```

### Choosing optics

Use `GlassStyle.regular` for the built-in Haze material, which adapts blur and depth to the
material's shortest dimension. Use `GlassOptics` when authoring a custom Style with direct optical
control:

```kotlin
val regular = GlassStyle.regular
```

```kotlin
GlassStyle {
  optics(
    blurRadius = 20.dp,
    refractionStrength = 0.8f,
    refractionHeightFraction = 0.3f,
    refractionDisplacement = 18.dp,
    refractionFoldStrength = 0.65f,
    depth = 0.5f,
  )
}
```

The scalar Style overload creates fixed values. For a responsive custom configuration, provide
independent shortest-dimension points for blur and depth; values clamp at the endpoints and use a
smoothstep interpolation between points:

```kotlin
val responsiveOptics = GlassOptics(
  blurRadius = GlassOptics.SizeValue.Interpolated(
    listOf(
      GlassOptics.SizePoint(176.dp, 8.dp),
      GlassOptics.SizePoint(300.dp, 12.dp),
    ),
  ),
  depth = GlassOptics.SizeValue.Fixed(0.4f),
)
val style = GlassStyle { optics(responsiveOptics) }
```

Keep a complete value when it is reused, stored, copied, or selected programmatically:

```kotlin
val reusableOptics = GlassOptics(
  blurRadius = GlassOptics.SizeValue.Fixed(20.dp),
)
val style = GlassStyle { optics(reusableOptics) }
```

`refractionFoldStrength` controls the inverted edge-refraction fold from `0f` to `1f`. The default
for `GlassOptics` is `0f`, which preserves the original monotonic refraction map. The fold is
available with every `SurfaceProfile` and remains within the configured refraction displacement;
it does not expand the capture area.

`GlassOptics` controls the appearance; `HazePerformanceMode.Fixed` controls the normalized
rendering trade-off.
The `shape` supplied to Glass defines its material boundary. Add an outer `Modifier.clip()` with
the same shape only when child content also needs clipping.

### Retained output

Glass can retain and redraw its last captured output when all source areas disappear. This
keeps source transitions smooth, but can briefly preserve stale pixels from removed source content.
Keep the default for smooth transitions, and keep source ownership explicit with
`HazeInput.Sources`:

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

Use one `GlassStyle` on every platform. Haze chooses the available implementation automatically, so
applications do not need capability checks or a second fallback Style.

Fallback rendering keeps the material recognizable but may simplify advanced optics:

| Authored behavior | Preferred rendering | Fallback |
| --- | --- | --- |
| Tint, alpha, and rounded shape | Preserved | Preserved |
| Ambient edge response and edge softness | Preserved | Approximated as a soft rim |
| Specular intensity and resolved light `Alignment` | Preserved | Approximated as an aligned radial highlight |
| Interaction lighting and transforms | Preserved | Preserved |
| Fixed or responsive refraction, blur, and progressive optics | Preserved | Omitted |
| Chromatic aberration, surface profile, and advanced color adjustments | Preserved | Omitted |
| Interaction refraction and white-point deltas | Preserved | Omitted |

Do not make essential meaning depend on refraction or chromatic aberration alone, because those
details may be omitted by a fallback.

## Performance

Start with `HazePerformanceMode.Default` (the adaptive performance mode) and tune only after
measuring a representative screen. The [Glass performance guide](../glass/performance.md) explains
which Glass-specific workloads and interactions to test.

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

Direct mouse, stylus, and touch positions track immediately.
`interactionPositionAnimationSpec` controls movement to focus and `InteractionSource`-derived
positions.

Keep the visual response in `GlassStyle`, and pass each element's interaction source and behavior
options to `hazeGlass`. The same Style can be reused without sharing interaction state.

When states overlap, pressed takes priority over hovered, which takes priority over focused. Use
`animate(toSpec, fromSpec)` when arrival and departure need different motion.

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

`interactionTransformTarget` selects whether a response transforms only the material or also its
content. `interactionTransformPivot` selects `Pointer` or `Center`. Omit a state block from a
replacement Style to remove it.
`GlassReducedMotionPolicy.System` follows the available system duration scale, `Reduced` snaps
lighting and optics while suppressing transforms, and `Full` forces motion.

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

- `GlassStyle.regular` is the right starting point for material-like glass that should respond
  naturally to its shortest dimension. Use direct `optics(...)` for inline fixed authoring and
  `GlassOptics` when the complete value needs to be reused or selected programmatically.
- Keep `chromaticAberrationStrength` modest; start at 0.1-0.25 to avoid rainbow artifacts.
- Combine `edgeSoftness` with rounded shapes for smooth clipping; set `edgeSoftness = 0.dp` to rely purely on the shape.
- Use `SurfaceProfile.Concave` for an inward-curving bezel or `SurfaceProfile.Lip` for a raised rim effect.
