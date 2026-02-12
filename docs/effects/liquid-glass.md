# Liquid Glass

A refraction-driven glass effect inspired by iOS “liquid” glass. It combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

## Parameters
- **tint**: Glass tint (defaults to white 12% alpha).
- **refractionStrength**: Distortion strength `0..1` (default 0.7).
- **refractionHeight**: Fraction of the shortest side that participates in refraction (default 0.25). Lower values pull the effect toward the edges; higher values push it deeper into the interior.
- **depth / blurRadius**: Blend refracted content with a blurred input to add depth (defaults 0.4 / 4.dp).
- **specularIntensity**: Highlight strength `0..1` (default 0.4).
- **ambientResponse**: Fresnel/edge lift `0..1` (default 0.5).
- **edgeSoftness**: Soft fade at the edges (default 12.dp). Set to 0.dp for hard edges.
- **shape (RoundedCornerShape)**: Rounded-rect boundary for refraction and masking (default all radii 0).
- **lightPosition**: Optional light source; defaults to the layer center.
- **chromaticAberrationStrength**: Simple dispersion strength `0..1` (default 0). TODO: expand to channel/spread controls.
- **alpha**: Overall opacity multiplier.

## Fallbacks
- Runtime shader path: rounded SDF refraction, depth mix, tint/specular/Fresnel, chromatic aberration, and edge softness.
- Fallback path (when runtime shaders unavailable): tinted fill + radial highlight + edge falloff; respects rounded shapes and alpha.

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
        chromaticAberrationStrength = 0.2f // TODO: expand controls later
      }
    }
)
```

## Tips
- Lower `refractionHeight` for a pronounced edge “lens”; raise it for a fuller dome.
- Keep `chromaticAberrationStrength` modest; start at 0.1–0.25 to avoid rainbow artifacts.
- Combine `edgeSoftness` with rounded shapes for smooth clipping; set `edgeSoftness = 0.dp` to rely purely on the shape.
