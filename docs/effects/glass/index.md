# Glass

A refraction-driven Glass effect that combines refraction, depth blur, tint, Fresnel/ambient lift, and specular highlights with optional rounded shapes and dispersion.

![type:video](../../media/glass.mp4)

## Download

Glass is published to Maven Central starting with Haze `2.0.0-alpha04`. Use the same version for
the core and Glass artifacts:

```kotlin
dependencies {
    implementation("dev.chrisbanes.haze:haze:<version>")
    implementation("dev.chrisbanes.haze:haze-glass:<version>")
}
```

For unreleased changes, follow the [snapshot build instructions](../../using-snapshot-version.md) and
use the same snapshot version for both artifacts.

### Material 3

Add the optional Material 3 integration when a Glass surface should use the current theme surface
color:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze-glass-material3:<version>")
}
```

`material3()` uses `MaterialTheme.colorScheme.surface` as its background color. Pass
`containerColor` to use a different color. It leaves the tint unset unless you pass one. It is
composable, so the Style follows theme changes when the call site recomposes.

Material 3 supplies only colors. Regular and Clear still pick their light or dark response from the
system appearance, not from `MaterialTheme`, and you don't need Material 3 for that.

Apply Material 3 to a built-in or custom Style with `material3()`. The Material 3 colors are
written first, so the receiver Style can override either:

```kotlin
val regular = GlassStyle.regular.material3()
val clear = GlassStyle.clear.material3(tint = Color.White.copy(alpha = 0.16f))

val custom = remember {
  GlassStyle { optics(GlassOptics.regular.copy(refractionStrength = 0.8f)) }
}
Modifier.hazeGlass(
  input = HazeInput.Backdrop(hazeState),
  style = custom.material3(tint = Color.White.copy(alpha = 0.16f)),
)
```

Call it on `GlassStyle` for the Material 3 colors alone, for example as a subtree default:

```kotlin
CompositionLocalProvider(LocalGlassStyle provides GlassStyle.material3()) {
  // Glass modifiers in this subtree inherit the Material 3 surface background.
}
```

### Built-in styles

`GlassStyle.regular` is the default style. Its blur and depth grow with the surface's shortest
side: compact controls keep some refracted detail and a curved edge, while larger surfaces fully
diffuse the backdrop. In dark appearance it uses a darker tone, edge, and lighting response.

`GlassStyle.clear` keeps more of the background visible. It uses the same shallow blur at every
size, with refraction concentrated in a band along the edge that forms loops around the corners.
Fallback renderers keep Clear's background color, tint, and edges, but drop the refraction, blur,
and tone adjustments that give it its look.

Prefer Regular when background content could interfere with labels or controls, especially for
surfaces containing substantial text. Use Clear for controls over photos and video where the
underlying content should remain visible. This follows Apple's
[Materials guidance](https://developer.apple.com/design/human-interface-guidelines/materials).
For Clear over bright content, add a dark dimming layer behind the controls when needed for
contrast. Haze does not add dimming automatically.

Both styles are informed by iOS 27 Liquid Glass, but Haze does not promise pixel parity with iOS.
They follow the system appearance supplied by the Compose host, which may reflect an app or window
override; an unknown appearance uses the light response. They do not follow the iOS appearance
slider or adapt to the content beneath them, so check foreground contrast with your own content.

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Backdrop(hazeState),
  style = GlassStyle.clear.material3(tint = Color.White.copy(alpha = 0.12f)),
)
```

## Parameters

- **backgroundColor**: Color composited behind captured content before refraction and blur
  (defaults to transparent). Use an opaque color when transparent captured content must fully
  obscure the original sharp source.
- **tint**: Glass tint (defaults to transparent).
- **optics**: Optical configuration. See [Choosing optics](#choosing-optics).
- **refractionDetailIntensity**: Secondary edge-refraction detail strength `0..1`. `0` disables it.
  `GlassOptics` defaults to `0.76`; Regular uses `0`, and Clear uses `0.76`. Non-zero values add
  rendering work on full renderers.
- **specularIntensity**: Highlight strength `0..1` (default 0.48).
- **edgeShadow**: Shape-following dark rim beneath the specular highlight (defaults to black at 14%
  opacity). Its alpha controls strength; set `Color.Transparent` to disable it.
- **ambientResponse**: Fresnel/edge lift `0..1` (default 0.15).
- **edgeSoftness**: Soft fade at the edges (default 1.dp). Set to 0.dp for hard edges.
- **shape** (`RoundedCornerShape`): Boundary for refraction and masking (default 16.dp corners).
  Add an outer `Modifier.clip()` with the same shape only when child content also needs clipping.
- **surfaceProfile**: Cross-section for lighting and, with `RefractionProfile.Surface`, refraction. Options: `Circle` (default), `Squircle`, `Lip`, `Concave`.
- **lightPosition**: `Alignment` of the light within the surface (default `Alignment.Center`).
  Start and end follow layout direction.
- **chromaticAberrationStrength**: Dispersion strength `0..1` (default 0). Higher values produce prismatic color splitting at edges.
- **chromaticAberrationMode**: `Simple` (default, cheaper) or `Full` (spectral, more expensive).
- **alpha**: Overall opacity multiplier `0..1` (default 1). Values outside that range are clamped.

### Color handling

`chromaMultiplier` values above `1f` boost saturation without clipping colors that are already in
the sRGB gamut; out-of-gamut source colors are left unchanged. `tint` and `edgeShadow` keep their
Compose `ColorSpace`.

## GlassStyle

`GlassStyle` is safe to share. Build a base Style and use `then` for variations:

```kotlin
val baseStyle = GlassStyle {
  backgroundColor(Color.White)
  tint(Color.White.copy(alpha = 0.16f))
  optics(GlassOptics.regular.copy(refractionStrength = 0.8f))
  shape(RoundedCornerShape(20.dp))
}
val emphasizedStyle = baseStyle.then { specularIntensity(0.7f) }

CompositionLocalProvider(LocalGlassStyle provides baseStyle) {
  // Use baseStyle as the default for Glass in this subtree.
}
```

A Style's block runs whenever a Glass surface resolves its style, not when the Style is built. The
block may read snapshot state, such as an animated value; changes apply on the next frame without
recomposing the `hazeGlass` call site. Keep the block free of side effects. Styles compare by
identity, so `remember` or hoist a Style: a new Style in each composition replaces it on every
surface.

```kotlin
val radius = animateDpAsState(if (expanded) 20.dp else 28.dp)
val style = remember {
  GlassStyle.regular.then { shape(RoundedCornerShape(radius.value)) }
}
```

Invalid values in a block throw `IllegalArgumentException` when the block runs, not where the Style
is built; a `GlassOptics` value is validated when it is created. Values that animation can overshoot
are coerced instead: `alpha` and tint alpha are clamped into `0f..1f`, and negative corner radii are
treated as zero. Validate or clamp other values from user input and remote data. Percent corner
sizes, such as `CircleShape`, follow size changes without any state in the Style.

Each surface applies `LocalGlassStyle` and then its explicit Style. Values that neither sets come
from Regular and `GlassDefaults`.

### Accessibility preferences

Haze does not read platform accessibility settings. Map them to `GlassAccessibilitySettings` and
provide it where Glass is used:

```kotlin
CompositionLocalProvider(
  LocalGlassAccessibilitySettings provides GlassAccessibilitySettings(
    reduceTransparency = reduceTransparency,
    increaseContrast = increaseContrast,
    showBorders = showBorders,
  ),
) {
  // Glass here diffuses the background more or strengthens its separating edge when requested.
}
```

The defaults leave the Style unchanged, and the settings never modify your Style values.
`reduceTransparency` uses a stronger, fully diffused blur during full rendering. In fallback rendering,
it hides source detail with an opaque base: white in light appearance (also the default when appearance
is unknown), or black in dark appearance. Light or dark comes from the system appearance that the Compose host supplies:
`LocalConfiguration.uiMode` on Android, where anything other than night mode is light, and
`LocalSystemTheme` on other platforms. An app or window override of those values is followed.
`MaterialTheme` and other in-app theme state are not read. Set `reducedTransparencyFallbackColor`
to an opaque `Color` to override this base, for example when your app has its own light and dark
setting; null selects the automatic colour. Translucent and
unspecified overrides are rejected.

Fallback draws this base, then your background colour, then tint. Overall `alpha` still fades the
whole material, so source detail is hidden at alpha 1 and can show through during a fade. Turning
`reduceTransparency` off restores ordinary fallback rendering. The colour override only affects
reduced-transparency fallback; full rendering keeps its stronger blur.

`increaseContrast` raises tone contrast and enforces a visible
edge. `showBorders` enforces a visible edge without changing tone. The edge appears even when the
Style has zero edge softness.

### Light alignment

Use Compose `Alignment` values so lighting adapts to each surface. Logical alignments such as
`Alignment.CenterStart` and `Alignment.CenterEnd` also follow LTR or RTL layout direction.

```kotlin
val sharedLighting = GlassStyle {
  lightPosition(Alignment.CenterStart)
}
```

Use `BiasAlignment` for a continuous proportional position:

```kotlin
val movingLighting = GlassStyle {
  lightPosition(BiasAlignment(horizontalBias = 0.4f, verticalBias = -0.6f))
}
```

## Style defaults

`hazeGlass` uses `GlassStyle.regular` when you omit `style`. Use `LocalGlassStyle` to set a default
for a subtree, and pass an explicit Style when one element needs to differ.

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeGlass(input = HazeInput.Backdrop(hazeState)),
)
```

### Choosing optics

Use `GlassStyle.regular` for the built-in material:

```kotlin
val regular = GlassStyle.regular
```

For direct optical control, call `optics(...)` in a custom Style. The `GlassOptics` constructor
defaults are not Regular's. For example, the constructor defaults to a `15.dp` refraction
displacement and `RefractionProfile.Surface`, while Regular uses `48.dp` and `Edge(20.dp)`. To
change part of the built-in material, start from `GlassOptics.regular.copy(...)`:

```kotlin
GlassStyle {
  optics(
    GlassOptics.regular.copy(
      blurRadius = OpticalSizeValue.Fixed(20.dp),
      refractionStrength = 0.8f,
      refractionHeightFraction = 0.3f,
      refractionDisplacement = 18.dp,
      refractionFoldStrength = 0.65f,
      depth = OpticalSizeValue.Fixed(0.5f),
    ),
  )
}
```

Use `optics { copy(...) }` to adjust the optical configuration established by earlier style writes:

```kotlin
val softerClear = GlassStyle.clear.then {
  optics { copy(blurRadius = OpticalSizeValue.Fixed(4.dp)) }
}
```

The receiver starts from `GlassOptics.regular` and then includes optics from `LocalGlassStyle` and
earlier writes in the Style, including built-in Styles it extends.
The block runs on every style evaluation and can read snapshot state without recomposing the call
site. Keep it free of side effects. Later `optics(value)` writes or built-in styles replace its result.

For a configuration you want to store or choose outside a style, copy `GlassOptics.regular` or
`GlassOptics.clear`:

```kotlin
val softerClearOptics = GlassOptics.clear.copy(blurRadius = OpticalSizeValue.Fixed(4.dp))
val softerClear = GlassStyle.clear.then { optics(softerClearOptics) }
```

`optics(value)` replaces the whole optical model. To vary blur or depth with size, give
`GlassOptics` points keyed by the surface's shortest side. Values hold at the end points and blend smoothly between them. Each
responsive value needs at least two points with positive, finite, strictly increasing sizes:

```kotlin
val responsiveOptics = GlassOptics.regular.copy(
  blurRadius = OpticalSizeValue.Responsive(
    OpticalSizePoint(176.dp, 8.dp),
    OpticalSizePoint(300.dp, 12.dp),
  ),
  depth = OpticalSizeValue.Fixed(0.4f),
)
val style = GlassStyle { optics(responsiveOptics) }
```

Keep a `GlassOptics` value when it is reused, stored, copied, or selected programmatically:

```kotlin
val reusableOptics = GlassOptics.regular.copy(
  blurRadius = OpticalSizeValue.Fixed(20.dp),
)
val style = GlassStyle { optics(reusableOptics) }
```

Optics also accept a progressive intensity:

```kotlin
GlassStyle {
  tint(Color.White.copy(alpha = 0.20f))
  optics(
    GlassOptics.regular.copy(
      progressive = HazeProgressive.verticalGradient(
        startIntensity = 1f,
        endIntensity = 0.25f,
      ),
    ),
  )
}
```

`refractionProfile` chooses where refraction happens:

- `RefractionProfile.Surface` (the `GlassOptics` default) refracts across the surface, following
  `surfaceProfile` and `refractionHeightFraction`.
- `RefractionProfile.Edge(width)` refracts most at the boundary and fades to none over `width`.
  Refraction follows wider corners than the visible shape, and displacement is capped at half the
  shortest side, so compact capsules refract less than large cards. `refractionHeightFraction`
  then affects lighting only. A zero width disables refraction.

Regular uses `Edge(20.dp)`, strength `0.7`, displacement `48.dp`, and lighting height fraction
`0.15`. Clear uses `Edge(28.dp)`, strength `0.85`, displacement `56.dp`, and lighting height
fraction `0.35`.

`refractionFoldStrength` (`0f..1f`, default `0f`) folds refraction back on itself near the edge. It
works with every `SurfaceProfile` and stays within `refractionDisplacement`.

`GlassOptics` controls appearance; `HazePerformanceMode` controls the quality and cost trade-off.

### Retained output

When all of its sources disappear, Glass keeps drawing its last output by default
(`HazeSourceRetention.KeepLastFrame`). This keeps source transitions smooth, but can briefly show
pixels from removed content.

This is not a frozen copy of the last frame. On Android, retained Glass can redraw from a source's
current pixels when it becomes visible again, and content changed inside a source after capture can
appear. For privacy-sensitive content, use `ClearWhenUnavailable`:

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Sources(
    state = hazeState,
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
)
```

### Android window-backdrop Glass

`HazeInput.Backdrop` samples the pixels already drawn earlier in the current window. On Android its
native path is experimental; enable it before the Glass surface attaches:

```kotlin
HazeFeatureFlags.isPlatformBackdropEnabled = true

Modifier.hazeGlass(
  input = HazeInput.Backdrop(hazeState),
  style = GlassStyle.regular,
)
```

Native Glass keeps the full look, including refraction, blur, chromatic aberration, rim lighting,
and interaction lighting. It cannot select a Haze source, include content drawn later, or cross a
dialog, popup, or window boundary.

The flag makes Glass eligible, not guaranteed. Where native rendering is unavailable or fails,
`HazeInput.Backdrop(hazeState)` renders from its source fallback, while `HazeInput.Backdrop()`,
which has no fallback, draws its content without Glass. A frame that native rendering cannot
handle after it has started also draws content without Glass for that frame. Changing the flag
affects later attachments only. See
[Android window backdrops](../../core-concepts.md#android-window-backdrops) for eligibility,
fallback, and diagnostics.

Use `HazeInput.Sources` directly when exact source selection, cross-window alignment, or retained
output must govern the actual input rather than only the fallback.

## Fallbacks

Use one `GlassStyle` on every platform. Haze chooses the available renderer automatically, so you
don't need capability checks or a second Style.

Fallback rendering keeps the material recognizable but simplifies advanced optics:

| Authored behavior | Preferred rendering | Fallback |
| --- | --- | --- |
| Tint, alpha, and rounded shape | Preserved | Preserved |
| Ambient edge response and edge softness | Preserved | Approximated as a soft rim |
| Specular intensity and resolved light `Alignment` | Preserved | Approximated as an aligned radial highlight |
| Interaction lighting and transforms | Preserved | Preserved |
| Fixed or responsive refraction, blur, and progressive optics | Preserved | Omitted |
| Chromatic aberration, surface profile, and advanced color adjustments | Preserved | Omitted |
| Interaction refraction and white-point deltas | Preserved | Omitted |
| [Glass container](containers.md) clusters | Members merge into one shape with smooth bridges | One surface per cluster, clipped to the hard union of the member shapes, with no bridge |

Don't make essential meaning depend on refraction or chromatic aberration alone, because fallback
rendering omits them.

## Performance

Start with `HazePerformanceMode.Default` (the fixed `Balanced` profile) and tune only after
measuring a representative screen. The [performance guide](../../performance.md#glass) explains
which Glass-specific workloads and interactions to test.

## Interaction

Glass interaction is opt-in. It adds a visual response only: no click handling, focusability,
semantics, or keyboard/D-pad activation.

```kotlin
Modifier.hazeGlass(
  input = HazeInput.Backdrop(hazeState),
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

Declare only the states and responses you need. For focus and keyboard/D-pad activation, share one
remembered `MutableInteractionSource` between the Glass modifier and your behavior modifiers:

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
    input = HazeInput.Backdrop(hazeState),
    style = interactionStyle,
    interactionSource = interactionSource,
    interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
    interactionTransformPivot = GlassTransformPivot.Pointer,
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
  )
```

The visual response lives in `GlassStyle`; the interaction source and behavior options are passed
per element to `hazeGlass`, so one Style can be reused without sharing interaction state.

Mouse, stylus, and touch positions are tracked immediately. `interactionPositionAnimationSpec`
animates the light towards focus and other `InteractionSource`-derived positions.

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

- `interactionTransformTarget` transforms the material only, or the material and its content.
- `interactionTransformPivot` scales around the `Pointer` or the `Center`.
- `interactionReducedMotionPolicy`: `System` follows the system animation scale and behaves as
  `Reduced` when that scale is zero, `Reduced` snaps lighting and optics and suppresses transforms,
  and `Full` always animates.

Omit a state block from a replacement Style to remove that response.

## Containers

Wrap nearby Glass surfaces in `Modifier.hazeGlassContainer` to merge them into one
shape, for controls that should read as one object, such as a toolbar or a segmented control. See
[Glass containers](containers.md) for the merge rule, transitions, lazy layouts and fallbacks.

## Usage

```kotlin
Box(
  Modifier
    .size(180.dp)
    .hazeGlass(
      input = HazeInput.Backdrop(hazeState),
      style = GlassStyle {
        tint(Color.White.copy(alpha = 0.16f))
        optics(
          GlassOptics.regular.copy(
            refractionStrength = 0.8f,
            refractionHeightFraction = 0.32f,
            depth = OpticalSizeValue.Fixed(0.5f),
          ),
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

- Keep `chromaticAberrationStrength` modest; start at 0.1-0.25 to avoid rainbow artifacts.
- Combine `edgeSoftness` with rounded shapes for smooth clipping; set `edgeSoftness = 0.dp` to rely purely on the shape.
- Use `SurfaceProfile.Concave` for an inward-curving bezel or `SurfaceProfile.Lip` for a raised rim effect.
