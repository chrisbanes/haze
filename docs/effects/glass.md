# Glass

!!! danger "Not yet published"
    The Glass module is currently under active development and is **not published to Maven Central**. It exists in the repository for experimentation and internal development only. Do not attempt to add it as a Gradle dependency.

A refraction-driven Glass effect that combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

!!! warning "Experimental"
    This module is experimental and may change or be removed in future releases. APIs are gated behind `@ExperimentalHazeApi`.

### Built-in material

The default material uses Haze's geometry-aware Adaptive optics. Small surfaces retain a clearer,
more refractive edge treatment, while larger surfaces converge toward a calmer base response.
Aspect ratio and roundness make small bounded adjustments without changing the material boundary.
Adaptive content behavior, interaction-driven deformation, and morphing are unsupported.

## Parameters

- **tint**: Glass tint (defaults to transparent).
- **optics**: Optical material configuration. `GlassOptics.Adaptive` (the default) is the
  built-in Haze material; it responds to the material's size, aspect ratio, and
  rounded geometry. Use `GlassOptics.Absolute(...)` when you need a complete literal optical
  configuration. Its `refractionStrength`, `refractionHeight`, `refractionScale`, `depth`,
  `blurRadius`, and optional `progressive` values are used without geometry-dependent adjustment.
  `blurRadius` is density-independent `Dp`; `refractionHeight` is a fraction of the shortest side;
  and `refractionScale` is a raw full-resolution effect-pixel displacement, without density
  conversion. `progressive` accepts
  `HazeProgressive.verticalGradient`, `horizontalGradient`, `HazeProgressive.RadialGradient`,
  or `forShader` to vary blur radius across the glass surface.
- **specularIntensity**: Highlight strength `0..1` (default 0.4).
- **ambientResponse**: Fresnel/edge lift `0..1` (default 0.46).
- **edgeSoftness**: Soft fade at the edges (default 2.dp). Set to 0.dp for hard edges.
- **shape** (`RoundedCornerShape`): Rounded-rect boundary for refraction and masking (default 16.dp corners).
- **surfaceProfile**: Cross-section profile for the refraction bezel. Options: `Circle` (default), `Squircle`, `Lip`, `Concave`.
- **lightPosition**: Optional light source; defaults to the layer center.
- **chromaticAberrationStrength**: Dispersion strength `0..1` (default 0). Higher values produce prismatic color splitting at edges.
- **chromaticAberrationMode**: Quality mode for chromatic aberration. `Simple` (default, fast) or `Full` (spectral, more expensive).
- **alpha**: Overall opacity multiplier `0..1` (default 1).

## GlassStyle

Use a `GlassStyle` container to provide a shared `optics` configuration and grouped visual
styling. The style supports a four-tier precedence chain for each property:

1. Direct property value on the effect (highest priority)
2. Value set via the `style` parameter
3. Value from the `LocalGlassStyle` composition local
4. Default from `GlassDefaults`

```kotlin
val myStyle = GlassStyle(
  tint = Color.White.copy(alpha = 0.16f),
  optics = GlassOptics.Absolute(refractionStrength = 0.8f),
  shape = RoundedCornerShape(20.dp),
)

CompositionLocalProvider(LocalGlassStyle provides myStyle) {
  // All Glass effects in this scope will use myStyle as their baseline
}
```

## Default style

`GlassDefaults.style` uses the built-in Haze `GlassOptics.Adaptive` material and is
applied automatically when no style or direct property overrides are provided.

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeEffect(state = hazeState) {
      glassEffect()
    }
)
```

### Choosing optics

Use `GlassOptics.Adaptive` for the built-in Haze material, which adapts its optical response to the
material's size, aspect ratio, and roundness. Use `GlassOptics.Absolute` when your design needs a
complete literal configuration instead:

```kotlin
glassEffect {
  optics = GlassOptics.Adaptive
}
```

```kotlin
glassEffect {
  optics = GlassOptics.Absolute(
    blurRadius = 20.dp,
    refractionStrength = 0.8f,
    refractionHeight = 0.3f,
    refractionScale = 18f,
    depth = 0.5f,
  )
}
```

Absolute values are not geometry-adjusted. `blurRadius` is converted from `Dp`, while
`refractionScale` remains a density-unscaled full-resolution pixel displacement and is scaled only
by `HazeInputScale`. Backend kernel construction remains a rendering detail. `shape` and `tint`
stay independent of the selected optics. The `shape`
supplied to Glass is the authoritative material boundary. An outer `Modifier.clip()` is not visible
to Glass and does not define its optical boundary; add one with the same shape only when child
content also needs clipping.

### Retained Output

Glass can retain and redraw its last captured output when all source areas disappear. This
keeps source transitions smooth, but can briefly preserve stale pixels from removed source content.
For privacy-sensitive surfaces, disable retained output on the shared effect scope:

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeEffect(state = hazeState) {
      retainOutputWhenSourceUnavailable = false
      glassEffect()
    }
)
```

You can select a literal optical configuration when the built-in material does not fit the design:

```kotlin
glassEffect {
  tint = Color.White.copy(alpha = 0.20f)
  optics = GlassOptics.Absolute(
    progressive = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0.25f,
    ),
  )
}
```

## Fallbacks

- Runtime shader path: deterministic semantic Gaussian blur, rounded SDF refraction, tint/specular/Fresnel, chromatic aberration, and edge softness.
- Fallback path: an approximation using tinted fill, radial highlight, and a soft rim; it respects rounded shapes and alpha when runtime shader render effects are unavailable. Interaction lighting and transforms work on this path, but interactive optics, white-point adjustment, and refraction are no-ops.

## Performance

On the modern Android path, compatible sibling Glass effects share retained blur and tiled optical
work. Effects that cannot safely share use their dedicated rendering path. See
[Glass performance](../glass/performance.md) for the physical-device benchmark setup, results, and
Perfetto interpretation.

## Interaction

Glass interaction is default-disabled and entirely opt-in. It adds a visual response only: it does
not add click handling, focusability, semantics, or keyboard/D-pad activation.

```kotlin
Modifier.hazeEffect(hazeState) {
  glassEffect {
    pressed()
  }
}
```

`hovered()`, `focused()`, and `pressed()` enable their respective default visual responses.
`interactable()` enables all three. To make focus and keyboard/D-pad activation useful, retain one
`MutableInteractionSource` and share it with both the glass effect and your behavior modifiers:

```kotlin
val interactionSource = remember { MutableInteractionSource() }

Modifier
  .clickable(interactionSource = interactionSource, indication = null) { onClick() }
  .focusable(interactionSource = interactionSource)
  .hazeEffect(hazeState) {
    glassEffect {
      this.interactionSource = interactionSource
      interactable()
    }
  }
```

Custom blocks replace that state's preset from identity. Resolve each property with fixed
precedence: focused, then hovered, then pressed. Use `animate(toSpec, fromSpec)` inside a custom
block to own the arrival and departure animation specs respectively; entering or replacement uses
`toSpec`, while departing uses `fromSpec`.

```kotlin
glassEffect {
  pressed {
    animate(
      toSpec = GlassDefaults.pressAnimationSpec,
      fromSpec = GlassDefaults.releaseAnimationSpec,
    ) {
      scale(0.98f)
    }
  }
}
```

`interactionTransformTarget` selects whether a response transforms only the material or the
material and content. `interactionTransformPivot` selects `Pointer` or `Center`. Use
`clearHovered()`, `clearFocused()`, `clearPressed()`, or `clearInteractions()` to remove configured
responses. `GlassReducedMotionPolicy.System` follows the available system duration scale,
`Reduced` snaps lighting and optics while suppressing transforms, and `Full` forces motion.

## Usage

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeEffect(state = hazeState) {
      glassEffect {
        tint = Color.White.copy(alpha = 0.16f)
        optics = GlassOptics.Absolute(
          refractionStrength = 0.8f,
          refractionHeight = 0.32f,
          depth = 0.5f,
        )
        specularIntensity = 0.7f
        ambientResponse = 0.7f
        edgeSoftness = 14.dp
        shape = RoundedCornerShape(20.dp)
        surfaceProfile = SurfaceProfile.Squircle
        chromaticAberrationStrength = 0.2f
      }
    }
)
```

## Tips

- `GlassOptics.Adaptive` is the right starting point for material-like glass that should respond
  naturally to its geometry. Use `GlassOptics.Absolute` only when you need to control the whole
  optical configuration.
- Keep `chromaticAberrationStrength` modest; start at 0.1-0.25 to avoid rainbow artifacts.
- Combine `edgeSoftness` with rounded shapes for smooth clipping; set `edgeSoftness = 0.dp` to rely purely on the shape.
- Use `SurfaceProfile.Concave` for an inward-curving bezel or `SurfaceProfile.Lip` for a raised rim effect.
