# Glass

!!! danger "Not yet published"
    The Glass module is currently under active development and is **not published to Maven Central**. It exists in the repository for experimentation and internal development only. Do not attempt to add it as a Gradle dependency.

A refraction-driven Glass effect calibrated against Apple's iOS Liquid Glass Regular material. It combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

!!! warning "Experimental"
    This module is experimental and may change or be removed in future releases. APIs are gated behind `@ExperimentalHazeApi`.

### Fidelity target

The runtime-shader renderer targets static, untinted iOS 26 Regular glass. Android API 33+ and
Skiko share a deterministic semantic Gaussian blur model: uniform blur uses multiscale
prefiltering, while progressive blur remains full-resolution. Both platforms apply depth as a
premultiplied linear mix of the source and blurred source before refraction, followed by optical
shading, masking, and the foreground rim in the same order. Expanded capture bounds provide
sampling support without changing the material shape.

The material is calibrated as geometry-aware static Regular glass. Its optical iOS bands are
separate from alpha and geometry invariants, so visual calibration does not redefine the surface
boundary. Adaptive content behavior, interaction-driven deformation, morphing, Clear glass, and
native-fidelity fallback rendering are unsupported.

## Parameters

- **tint**: Glass tint (defaults to transparent).
- **refractionStrength**: Distortion strength `0..1` (default 0.7).
- **refractionHeight**: Fraction of the shortest side that participates in refraction (default 0.25). Lower values pull the effect toward the edges; higher values push it deeper into the interior.
- **depth / blurRadius**: Blur is applied before Glass refraction so refracted content can soften with depth. Higher `depth` increases the premultiplied linear contribution of the blurred source relative to the original source.
- **progressive**: Optional progressive blur mask. Use `HazeProgressive.verticalGradient`, `horizontalGradient`, `HazeProgressive.RadialGradient`, or `forShader` to vary blur radius across the glass surface.
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

All parameters can be set individually or grouped via a `GlassStyle` container. The style supports a four-tier precedence chain for each property:

1. Direct property value on the effect (highest priority)
2. Value set via the `style` parameter
3. Value from the `LocalGlassStyle` composition local
4. Default from `GlassDefaults`

```kotlin
val myStyle = GlassStyle(
  tint = Color.White.copy(alpha = 0.16f),
  optics = GlassOptics(refractionStrength = 0.8f),
  shape = RoundedCornerShape(20.dp),
)

CompositionLocalProvider(LocalGlassStyle provides myStyle) {
  // All Glass effects in this scope will use myStyle as their baseline
}
```

## Default style

`GlassDefaults.style` contains the calibrated static Regular glass values and is applied
automatically when no style or direct property overrides are provided.

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeEffect(state = hazeState) {
      glassEffect()
    }
)
```

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

You can override individual default values:

```kotlin
glassEffect {
  tint = Color.White.copy(alpha = 0.20f)
  progressive = HazeProgressive.verticalGradient(
    startIntensity = 1f,
    endIntensity = 0.25f,
  )
}
```

## Fallbacks

- Runtime shader path: deterministic semantic Gaussian blur, rounded SDF refraction, tint/specular/Fresnel, chromatic aberration, and edge softness.
- Fallback path: an approximation using tinted fill, radial highlight, and a soft rim; it respects rounded shapes and alpha when runtime shader render effects are unavailable, but does not target native fidelity.

## Usage

```kotlin
Box(
  Modifier
    .size(180.dp)
    .clip(RoundedCornerShape(20.dp))
    .hazeEffect(state = hazeState) {
      glassEffect {
        tint = Color.White.copy(alpha = 0.16f)
        refractionStrength = 0.8f
        refractionHeight = 0.32f
        depth = 0.5f
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

- Lower `refractionHeight` for a pronounced edge "lens"; raise it for a fuller dome.
- Keep `chromaticAberrationStrength` modest; start at 0.1-0.25 to avoid rainbow artifacts.
- Combine `edgeSoftness` with rounded shapes for smooth clipping; set `edgeSoftness = 0.dp` to rely purely on the shape.
- Use `SurfaceProfile.Concave` for an inward-curving bezel or `SurfaceProfile.Lip` for a raised rim effect.
