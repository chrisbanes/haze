# Liquid Glass

!!! danger "Not yet published"
    This module is currently under active development and is **not published to Maven Central**. It exists in the repository for experimentation and internal development only. Do not attempt to add it as a Gradle dependency.

A refraction-driven glass effect inspired by iOS "liquid" glass. It combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

!!! warning "Experimental"
    This module is experimental and may change or be removed in future releases. APIs are gated behind `@ExperimentalHazeApi`.

## Parameters

- **tint**: Glass tint (defaults to white 12% alpha).
- **refractionStrength**: Distortion strength `0..1` (default 0.7).
- **refractionHeight**: Fraction of the shortest side that participates in refraction (default 0.25). Lower values pull the effect toward the edges; higher values push it deeper into the interior.
- **depth / blurRadius**: Blur is applied before liquid glass refraction so refracted content can soften with depth. Higher `depth` increases the visual contribution of blurred content.
- **progressive**: Optional progressive blur mask. Use `HazeProgressive.verticalGradient`, `horizontalGradient`, `HazeProgressive.RadialGradient`, or `forShader` to vary blur radius across the glass surface.
- **specularIntensity**: Highlight strength `0..1` (default 0.4).
- **ambientResponse**: Fresnel/edge lift `0..1` (default 0.5).
- **edgeSoftness**: Soft fade at the edges (default 12.dp). Set to 0.dp for hard edges.
- **shape** (`RoundedCornerShape`): Rounded-rect boundary for refraction and masking (default 16.dp corners).
- **surfaceProfile**: Cross-section profile for the refraction bezel. Options: `Circle` (default), `Squircle`, `Lip`, `Concave`.
- **lightPosition**: Optional light source; defaults to the layer center.
- **chromaticAberrationStrength**: Dispersion strength `0..1` (default 0). Higher values produce prismatic color splitting at edges.
- **chromaticAberrationMode**: Quality mode for chromatic aberration. `Simple` (default, fast) or `Full` (spectral, more expensive).
- **alpha**: Overall opacity multiplier `0..1` (default 1).

## LiquidGlassStyle

All parameters can be set individually or grouped via a `LiquidGlassStyle` container. The style supports a three-tier precedence chain for each property:

1. Direct property value on the effect (highest priority)
2. Value set via the `style` parameter
3. Value from the `LocalLiquidGlassStyle` composition local
4. Default from `LiquidGlassDefaults`

```kotlin
val myStyle = LiquidGlassStyle(
  tint = Color.White.copy(alpha = 0.16f),
  optics = LiquidGlassOptics(refractionStrength = 0.8f),
  shape = RoundedCornerShape(20.dp),
)

CompositionLocalProvider(LocalLiquidGlassStyle provides myStyle) {
  // All liquid glass effects in this scope will use myStyle as their baseline
}
```

## Fallbacks

- Runtime shader path: rounded SDF refraction, native or progressive blur, tint/specular/Fresnel, chromatic aberration, and edge softness.
- Fallback path: tinted fill + radial highlight + soft rim; respects rounded shapes and alpha when runtime shader render effects are unavailable.

## Usage

```kotlin
Box(
  Modifier
    .size(180.dp)
    .clip(RoundedCornerShape(20.dp))
    .hazeEffect(state = hazeState) {
      liquidGlassEffect {
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
