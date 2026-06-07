# V2 API Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean up the v2 API by removing partial v1 migration shims, fixing blur style immutability and empty-list semantics, grouping `LiquidGlassStyle`, updating docs, and regenerating API snapshots.

**Architecture:** Keep the hard v2 break explicit: blur APIs only live under `dev.chrisbanes.haze.blur`, liquid glass remains experimental but uses grouped immutable style values, and existing `VisualEffect` lifecycle behavior stays unchanged. Implement behavior-preserving refactors behind focused unit tests, then update docs and API files.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Runtime/UI, assertk, kotlin.test, Gradle, Metalava API snapshots.

---

## File Structure

- Modify `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`: delete the file if all declarations are removed.
- Modify `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyle.kt`: remove deprecated aliases/factories, add defensive list snapshots, and support `null` as unspecified color effects.
- Modify `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`: store direct color effects as nullable authoring state and resolve empty lists as explicit empty.
- Create `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyleTest.kt`: test defensive copies and explicit-empty semantics.
- Modify `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyle.kt`: replace the flat style with grouped immutable value types.
- Modify `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassDefaults.kt`: build defaults through grouped style values.
- Modify `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassVisualEffect.kt`: resolve grouped style values with the same precedence as the old flat style.
- Create `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt`: test grouped defaults and precedence.
- Modify sample/screenshot/docs source files that construct `LiquidGlassStyle` directly.
- Modify `docs/migrating-2.0.md`: document the hard break and grouped liquid glass migration.
- Modify API snapshots in `haze-blur/api/api.txt`, `haze-liquidglass/api/api.txt`, and any versioned snapshots required by the project.

---

### Task 1: Blur Style Tests

**Files:**
- Create: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyleTest.kt`
- Read: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyle.kt`
- Read: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`

- [ ] **Step 1: Write failing tests for list snapshot and empty-list semantics**

Create `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyleTest.kt` with:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlin.test.Test

class HazeBlurStyleTest {

  @Test
  fun hazeBlurStyle_colorEffectsSnapshotsMutableInput() {
    val first = HazeColorEffect.tint(Color.Red)
    val second = HazeColorEffect.tint(Color.Blue)
    val input = mutableListOf(first)

    val style = HazeBlurStyle(colorEffects = input)

    input += second

    assertThat(style.colorEffects).containsExactly(first)
  }

  @Test
  fun hazeBlurStyle_emptyColorEffectsAreExplicitlySpecified() {
    val inherited = HazeBlurStyle(colorEffect = HazeColorEffect.tint(Color.Red))
    val style = HazeBlurStyle(colorEffects = emptyList())
    val effect = BlurVisualEffect().apply {
      compositionLocalStyle = inherited
      this.style = style
    }

    assertThat(effect.colorEffects).isEmpty()
  }

  @Test
  fun blurVisualEffect_emptyColorEffectsClearsInheritedEffects() {
    val inherited = HazeBlurStyle(colorEffect = HazeColorEffect.tint(Color.Red))
    val effect = BlurVisualEffect().apply {
      compositionLocalStyle = inherited
      colorEffects = emptyList()
    }

    assertThat(effect.colorEffects).isEmpty()
  }
}
```

- [ ] **Step 2: Run the focused blur tests and verify failure**

Run:

```bash
./gradlew :haze-blur:test --tests dev.chrisbanes.haze.blur.HazeBlurStyleTest
```

Expected: FAIL. The snapshot test may pass if the current data class already stores an independent list instance for `listOfNotNull`, but the explicit-empty tests should fail because empty lists currently fall through to inherited style values.

- [ ] **Step 3: Commit the failing tests**

Run:

```bash
git add haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyleTest.kt
git commit -m "test: cover blur style color effect semantics"
```

---

### Task 2: Blur Style Implementation

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyle.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`
- Test: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyleTest.kt`

- [ ] **Step 1: Change `HazeBlurStyle` to distinguish unspecified from empty**

In `HazeBlurStyle.kt`, replace the `data class HazeBlurStyle` declaration with a regular immutable class that keeps the public constructor ergonomic but snapshots the list. Use this structure:

```kotlin
@Immutable
public class HazeBlurStyle public constructor(
  public val backgroundColor: Color = Color.Unspecified,
  colorEffects: List<HazeColorEffect>? = null,
  public val blurRadius: Dp = Dp.Unspecified,
  public val noiseFactor: Float = -1f,
  public val fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified,
) {
  public constructor(
    backgroundColor: Color = Color.Unspecified,
    colorEffect: HazeColorEffect? = null,
    blurRadius: Dp = Dp.Unspecified,
    noiseFactor: Float = -1f,
    fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified,
  ) : this(
    backgroundColor = backgroundColor,
    colorEffects = colorEffect?.let(::listOf),
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    fallbackColorEffect = fallbackColorEffect,
  )

  internal val specifiedColorEffects: List<HazeColorEffect>? = colorEffects?.toList()

  public val colorEffects: List<HazeColorEffect>
    get() = specifiedColorEffects.orEmpty()

  public operator fun component1(): Color = backgroundColor
  public operator fun component2(): List<HazeColorEffect> = colorEffects
  public operator fun component3(): Dp = blurRadius
  public operator fun component4(): Float = noiseFactor
  public operator fun component5(): HazeColorEffect = fallbackColorEffect

  public fun copy(
    backgroundColor: Color = this.backgroundColor,
    colorEffects: List<HazeColorEffect>? = this.specifiedColorEffects,
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
    fallbackColorEffect: HazeColorEffect = this.fallbackColorEffect,
  ): HazeBlurStyle = HazeBlurStyle(
    backgroundColor = backgroundColor,
    colorEffects = colorEffects,
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    fallbackColorEffect = fallbackColorEffect,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is HazeBlurStyle) return false
    return backgroundColor == other.backgroundColor &&
      specifiedColorEffects == other.specifiedColorEffects &&
      blurRadius == other.blurRadius &&
      noiseFactor == other.noiseFactor &&
      fallbackColorEffect == other.fallbackColorEffect
  }

  override fun hashCode(): Int {
    var result = backgroundColor.hashCode()
    result = 31 * result + specifiedColorEffects.hashCode()
    result = 31 * result + blurRadius.hashCode()
    result = 31 * result + noiseFactor.hashCode()
    result = 31 * result + fallbackColorEffect.hashCode()
    return result
  }

  override fun toString(): String {
    return "HazeBlurStyle(" +
      "backgroundColor=$backgroundColor, " +
      "colorEffects=$specifiedColorEffects, " +
      "blurRadius=$blurRadius, " +
      "noiseFactor=$noiseFactor, " +
      "fallbackColorEffect=$fallbackColorEffect" +
      ")"
  }

  public companion object {
    public val Unspecified: HazeBlurStyle = HazeBlurStyle(colorEffects = null)
  }
}
```

- [ ] **Step 2: Update `BlurVisualEffect.colorEffects` to store nullable authoring state**

In `BlurVisualEffect.kt`, replace:

```kotlin
public var colorEffects: List<HazeColorEffect> = emptyList()
```

with:

```kotlin
private var directColorEffects: List<HazeColorEffect>? = null

public var colorEffects: List<HazeColorEffect>
  get() {
    return directColorEffects
      ?: style.specifiedColorEffects
      ?: compositionLocalStyle.specifiedColorEffects
      ?: emptyList()
  }
  set(value) {
    val snapshot = value.toList()
    if (snapshot != directColorEffects) {
      HazeLogger.d(TAG) { "colorEffects changed. Current: $directColorEffects. New: $snapshot" }
      directColorEffects = snapshot
      dirtyTracker += BlurDirtyFields.ColorEffects
    }
  }
```

Update the copy constructor assignment to keep resolved-copy behavior:

```kotlin
colorEffects = other.colorEffects
```

Keep it as-is if the source already uses that line.

- [ ] **Step 3: Update style-change dirty tracking for fallback tint**

In `BlurVisualEffect.onStyleChanged`, replace:

```kotlin
if (old?.colorEffects != new?.colorEffects) dirtyTracker += BlurDirtyFields.ColorEffects
if (old?.fallbackColorEffect != new?.fallbackColorEffect) dirtyTracker += BlurDirtyFields.ColorEffects
```

with:

```kotlin
if (old?.specifiedColorEffects != new?.specifiedColorEffects) dirtyTracker += BlurDirtyFields.ColorEffects
if (old?.fallbackColorEffect != new?.fallbackColorEffect) dirtyTracker += BlurDirtyFields.FallbackColorEffect
```

- [ ] **Step 4: Run the focused blur tests**

Run:

```bash
./gradlew :haze-blur:test --tests dev.chrisbanes.haze.blur.HazeBlurStyleTest
```

Expected: PASS.

- [ ] **Step 5: Run existing blur common tests**

Run:

```bash
./gradlew :haze-blur:test
```

Expected: PASS.

- [ ] **Step 6: Commit blur implementation**

Run:

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyle.kt haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt
git commit -m "fix: clarify blur style color effects"
```

---

### Task 3: Liquid Glass Grouped Style Tests

**Files:**
- Create: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt`
- Read: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassDefaults.kt`
- Read: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassVisualEffect.kt`

- [ ] **Step 1: Write failing tests for grouped defaults and precedence**

Create `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt` with:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class LiquidGlassStyleTest {

  @Test
  fun defaultsStyle_resolvesToLiquidGlassDefaults() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassDefaults.style
    }

    assertThat(effect.tint).isEqualTo(LiquidGlassDefaults.tint)
    assertThat(effect.shape).isEqualTo(LiquidGlassDefaults.shape)
    assertThat(effect.refractionStrength).isEqualTo(LiquidGlassDefaults.refractionStrength)
    assertThat(effect.refractionHeight).isEqualTo(LiquidGlassDefaults.refractionHeight)
    assertThat(effect.refractionScale).isEqualTo(LiquidGlassDefaults.refractionScale)
    assertThat(effect.depth).isEqualTo(LiquidGlassDefaults.depth)
    assertThat(effect.blurRadius).isEqualTo(LiquidGlassDefaults.blurRadius)
    assertThat(effect.specularIntensity).isEqualTo(LiquidGlassDefaults.specularIntensity)
    assertThat(effect.specularExponent).isEqualTo(LiquidGlassDefaults.specularExponent)
    assertThat(effect.fresnelExponent).isEqualTo(LiquidGlassDefaults.fresnelExponent)
    assertThat(effect.ambientResponse).isEqualTo(LiquidGlassDefaults.ambientResponse)
    assertThat(effect.alpha).isEqualTo(LiquidGlassDefaults.alpha)
    assertThat(effect.contrast).isEqualTo(LiquidGlassDefaults.contrast)
    assertThat(effect.whitePoint).isEqualTo(LiquidGlassDefaults.whitePoint)
    assertThat(effect.chromaMultiplier).isEqualTo(LiquidGlassDefaults.chromaMultiplier)
    assertThat(effect.edgeSoftness).isEqualTo(LiquidGlassDefaults.edgeSoftness)
    assertThat(effect.contentNormalBlend).isEqualTo(LiquidGlassDefaults.contentNormalBlend)
    assertThat(effect.surfaceProfile).isEqualTo(LiquidGlassDefaults.surfaceProfile)
    assertThat(effect.chromaticAberrationStrength).isEqualTo(LiquidGlassDefaults.chromaticAberrationStrength)
    assertThat(effect.chromaticAberrationMode).isEqualTo(LiquidGlassDefaults.chromaticAberrationMode)
  }

  @Test
  fun groupedStyle_partiallySpecifiedValuesInheritFromCompositionLocal() {
    val localStyle = LiquidGlassStyle(
      tint = Color.Blue,
      shape = RoundedCornerShape(12.dp),
      optics = LiquidGlassOptics(
        refractionStrength = 0.2f,
        refractionScale = 8f,
        depth = 0.3f,
      ),
      lighting = LiquidGlassLighting(
        specularIntensity = 0.25f,
        lightPosition = Offset(4f, 8f),
      ),
      color = LiquidGlassColor(alpha = 0.7f, contrast = 0.4f),
      rendering = LiquidGlassRendering(
        edgeSoftness = 6.dp,
        surfaceProfile = SurfaceProfile.Concave,
      ),
    )
    val directStyle = LiquidGlassStyle(
      optics = LiquidGlassOptics(refractionStrength = 0.9f),
      lighting = LiquidGlassLighting(ambientResponse = 0.8f),
      color = LiquidGlassColor(whitePoint = 0.1f),
      rendering = LiquidGlassRendering(chromaticAberrationStrength = 0.5f),
    )
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = localStyle
      style = directStyle
    }

    assertThat(effect.refractionStrength).isEqualTo(0.9f)
    assertThat(effect.refractionScale).isEqualTo(8f)
    assertThat(effect.depth).isEqualTo(0.3f)
    assertThat(effect.ambientResponse).isEqualTo(0.8f)
    assertThat(effect.specularIntensity).isEqualTo(0.25f)
    assertThat(effect.lightPosition).isEqualTo(Offset(4f, 8f))
    assertThat(effect.alpha).isEqualTo(0.7f)
    assertThat(effect.contrast).isEqualTo(0.4f)
    assertThat(effect.whitePoint).isEqualTo(0.1f)
    assertThat(effect.edgeSoftness).isEqualTo(6.dp)
    assertThat(effect.surfaceProfile).isEqualTo(SurfaceProfile.Concave)
    assertThat(effect.chromaticAberrationStrength).isEqualTo(0.5f)
  }

  @Test
  fun directPropertiesOverrideGroupedStyle() {
    val effect = LiquidGlassVisualEffect().apply {
      style = LiquidGlassStyle(
        tint = Color.Blue,
        optics = LiquidGlassOptics(refractionStrength = 0.2f),
        lighting = LiquidGlassLighting(ambientResponse = 0.3f),
        color = LiquidGlassColor(alpha = 0.4f),
        rendering = LiquidGlassRendering(edgeSoftness = 6.dp),
      )
      tint = Color.Red
      refractionStrength = 0.8f
      ambientResponse = 0.9f
      alpha = 0.5f
      edgeSoftness = 10.dp
    }

    assertThat(effect.tint).isEqualTo(Color.Red)
    assertThat(effect.refractionStrength).isEqualTo(0.8f)
    assertThat(effect.ambientResponse).isEqualTo(0.9f)
    assertThat(effect.alpha).isEqualTo(0.5f)
    assertThat(effect.edgeSoftness).isEqualTo(10.dp)
  }
}
```

- [ ] **Step 2: Run focused liquid glass tests and verify failure**

Run:

```bash
./gradlew :haze-liquidglass:test --tests dev.chrisbanes.haze.liquidglass.LiquidGlassStyleTest
```

Expected: FAIL because the grouped style types do not exist yet.

- [ ] **Step 3: Commit failing liquid glass tests**

Run:

```bash
git add haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt
git commit -m "test: cover liquid glass grouped style semantics"
```

---

### Task 4: Liquid Glass Grouped Style Types and Defaults

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyle.kt`
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassDefaults.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt`

- [ ] **Step 1: Replace flat `LiquidGlassStyle` with grouped value types**

In `LiquidGlassStyle.kt`, keep `LocalLiquidGlassStyle`, then replace the flat style class with:

```kotlin
@ExperimentalHazeApi
@Immutable
public data class LiquidGlassStyle(
  val tint: Color = Color.Unspecified,
  val shape: RoundedCornerShape? = null,
  val optics: LiquidGlassOptics = LiquidGlassOptics.Unspecified,
  val lighting: LiquidGlassLighting = LiquidGlassLighting.Unspecified,
  val color: LiquidGlassColor = LiquidGlassColor.Unspecified,
  val rendering: LiquidGlassRendering = LiquidGlassRendering.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassStyle = LiquidGlassStyle()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassOptics(
  val refractionStrength: Float = Float.NaN,
  val refractionHeight: Float = Float.NaN,
  val refractionScale: Float = Float.NaN,
  val depth: Float = Float.NaN,
  val blurRadius: Dp = Dp.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassOptics = LiquidGlassOptics()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassLighting(
  val specularIntensity: Float = Float.NaN,
  val specularExponent: Float = Float.NaN,
  val fresnelExponent: Float = Float.NaN,
  val ambientResponse: Float = Float.NaN,
  val lightPosition: Offset = Offset.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassLighting = LiquidGlassLighting()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassColor(
  val alpha: Float = Float.NaN,
  val contrast: Float = Float.NaN,
  val whitePoint: Float = Float.NaN,
  val chromaMultiplier: Float = Float.NaN,
) {
  public companion object {
    public val Unspecified: LiquidGlassColor = LiquidGlassColor()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassRendering(
  val edgeSoftness: Dp = Dp.Unspecified,
  val contentNormalBlend: Float = Float.NaN,
  val surfaceProfile: SurfaceProfile? = null,
  val chromaticAberrationStrength: Float = Float.NaN,
  val chromaticAberrationMode: ChromaticAberrationMode? = null,
) {
  public companion object {
    public val Unspecified: LiquidGlassRendering = LiquidGlassRendering()
  }
}
```

- [ ] **Step 2: Rebuild `LiquidGlassDefaults.style` with groups**

In `LiquidGlassDefaults.kt`, replace the flat `style` initializer with:

```kotlin
public val style: LiquidGlassStyle = LiquidGlassStyle(
  tint = tint,
  shape = shape,
  optics = LiquidGlassOptics(
    refractionStrength = refractionStrength,
    refractionHeight = refractionHeight,
    refractionScale = refractionScale,
    depth = depth,
    blurRadius = blurRadius,
  ),
  lighting = LiquidGlassLighting(
    specularIntensity = specularIntensity,
    specularExponent = specularExponent,
    fresnelExponent = fresnelExponent,
    ambientResponse = ambientResponse,
  ),
  color = LiquidGlassColor(
    alpha = alpha,
    contrast = contrast,
    whitePoint = whitePoint,
    chromaMultiplier = chromaMultiplier,
  ),
  rendering = LiquidGlassRendering(
    edgeSoftness = edgeSoftness,
    contentNormalBlend = contentNormalBlend,
    surfaceProfile = surfaceProfile,
    chromaticAberrationStrength = chromaticAberrationStrength,
    chromaticAberrationMode = chromaticAberrationMode,
  ),
)
```

- [ ] **Step 3: Run focused liquid glass tests and verify remaining failures**

Run:

```bash
./gradlew :haze-liquidglass:test --tests dev.chrisbanes.haze.liquidglass.LiquidGlassStyleTest
```

Expected: FAIL in `LiquidGlassVisualEffect` compilation because it still reads flat `style.refractionStrength`, `style.specularIntensity`, and related properties.

- [ ] **Step 4: Leave grouped style type changes uncommitted for the next task**

Do not commit these implementation changes yet. The module is expected to keep
failing until Task 5 updates `LiquidGlassVisualEffect` to read grouped style
values. Confirm the intended uncommitted files:

```bash
git status --short haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyle.kt haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassDefaults.kt
```

Expected: both files are modified.

---

### Task 5: Liquid Glass VisualEffect Resolution

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassVisualEffect.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt`

- [ ] **Step 1: Add grouped style helper accessors**

In `LiquidGlassVisualEffect.kt`, add private helper properties near `style`/`compositionLocalStyle`:

```kotlin
private val styleOptics: LiquidGlassOptics get() = style.optics
private val localOptics: LiquidGlassOptics get() = compositionLocalStyle.optics
private val styleLighting: LiquidGlassLighting get() = style.lighting
private val localLighting: LiquidGlassLighting get() = compositionLocalStyle.lighting
private val styleColor: LiquidGlassColor get() = style.color
private val localColor: LiquidGlassColor get() = compositionLocalStyle.color
private val styleRendering: LiquidGlassRendering get() = style.rendering
private val localRendering: LiquidGlassRendering get() = compositionLocalStyle.rendering
```

- [ ] **Step 2: Update optics-backed property getters**

Replace these style/local lookups:

```kotlin
style.refractionStrength
compositionLocalStyle.refractionStrength
style.depth
compositionLocalStyle.depth
style.blurRadius
compositionLocalStyle.blurRadius
style.refractionHeight
compositionLocalStyle.refractionHeight
style.refractionScale
compositionLocalStyle.refractionScale
```

with:

```kotlin
styleOptics.refractionStrength
localOptics.refractionStrength
styleOptics.depth
localOptics.depth
styleOptics.blurRadius
localOptics.blurRadius
styleOptics.refractionHeight
localOptics.refractionHeight
styleOptics.refractionScale
localOptics.refractionScale
```

- [ ] **Step 3: Update lighting-backed property getters**

Replace these style/local lookups:

```kotlin
style.specularIntensity
compositionLocalStyle.specularIntensity
style.ambientResponse
compositionLocalStyle.ambientResponse
style.lightPosition
compositionLocalStyle.lightPosition
style.specularExponent
compositionLocalStyle.specularExponent
style.fresnelExponent
compositionLocalStyle.fresnelExponent
```

with:

```kotlin
styleLighting.specularIntensity
localLighting.specularIntensity
styleLighting.ambientResponse
localLighting.ambientResponse
styleLighting.lightPosition
localLighting.lightPosition
styleLighting.specularExponent
localLighting.specularExponent
styleLighting.fresnelExponent
localLighting.fresnelExponent
```

- [ ] **Step 4: Update color-backed property getters**

Replace these style/local lookups:

```kotlin
style.alpha
compositionLocalStyle.alpha
style.contrast
compositionLocalStyle.contrast
style.whitePoint
compositionLocalStyle.whitePoint
style.chromaMultiplier
compositionLocalStyle.chromaMultiplier
```

with:

```kotlin
styleColor.alpha
localColor.alpha
styleColor.contrast
localColor.contrast
styleColor.whitePoint
localColor.whitePoint
styleColor.chromaMultiplier
localColor.chromaMultiplier
```

- [ ] **Step 5: Update rendering-backed and top-level property getters**

Replace:

```kotlin
style.chromaticAberrationStrength
compositionLocalStyle.chromaticAberrationStrength
style.surfaceProfile
compositionLocalStyle.surfaceProfile
style.chromaticAberrationMode
compositionLocalStyle.chromaticAberrationMode
style.edgeSoftness
compositionLocalStyle.edgeSoftness
style.contentNormalBlend
compositionLocalStyle.contentNormalBlend
```

with:

```kotlin
styleRendering.chromaticAberrationStrength
localRendering.chromaticAberrationStrength
styleRendering.surfaceProfile
localRendering.surfaceProfile
styleRendering.chromaticAberrationMode
localRendering.chromaticAberrationMode
styleRendering.edgeSoftness
localRendering.edgeSoftness
styleRendering.contentNormalBlend
localRendering.contentNormalBlend
```

Keep `tint` and `shape` as top-level style lookups:

```kotlin
style.tint
compositionLocalStyle.tint
style.shape
compositionLocalStyle.shape
```

- [ ] **Step 6: Replace `onStyleChanged` comparisons with grouped comparisons**

Replace the full body of `onStyleChanged(old: LiquidGlassStyle, new: LiquidGlassStyle)` with comparisons against group fields:

```kotlin
if (old.optics.refractionStrength != new.optics.refractionStrength) {
  dirtyTracker += LiquidGlassDirtyFields.RefractionStrength
}
if (old.optics.depth != new.optics.depth) {
  dirtyTracker += LiquidGlassDirtyFields.Depth
}
if (old.optics.blurRadius != new.optics.blurRadius) {
  dirtyTracker += LiquidGlassDirtyFields.BlurRadius
}
if (old.optics.refractionHeight != new.optics.refractionHeight) {
  dirtyTracker += LiquidGlassDirtyFields.RefractionHeight
}
if (old.optics.refractionScale != new.optics.refractionScale) {
  dirtyTracker += LiquidGlassDirtyFields.RefractionScale
}
if (old.lighting.specularIntensity != new.lighting.specularIntensity) {
  dirtyTracker += LiquidGlassDirtyFields.SpecularIntensity
}
if (old.lighting.ambientResponse != new.lighting.ambientResponse) {
  dirtyTracker += LiquidGlassDirtyFields.AmbientResponse
}
if (old.lighting.lightPosition != new.lighting.lightPosition) {
  dirtyTracker += LiquidGlassDirtyFields.LightPosition
}
if (old.lighting.specularExponent != new.lighting.specularExponent) {
  dirtyTracker += LiquidGlassDirtyFields.SpecularExponent
}
if (old.lighting.fresnelExponent != new.lighting.fresnelExponent) {
  dirtyTracker += LiquidGlassDirtyFields.FresnelExponent
}
if (old.tint != new.tint) {
  dirtyTracker += LiquidGlassDirtyFields.Tint
}
if (old.shape != new.shape) {
  dirtyTracker += LiquidGlassDirtyFields.Shape
}
if (old.color.alpha != new.color.alpha) {
  dirtyTracker += LiquidGlassDirtyFields.Alpha
}
if (old.color.contrast != new.color.contrast) {
  dirtyTracker += LiquidGlassDirtyFields.Contrast
}
if (old.color.whitePoint != new.color.whitePoint) {
  dirtyTracker += LiquidGlassDirtyFields.WhitePoint
}
if (old.color.chromaMultiplier != new.color.chromaMultiplier) {
  dirtyTracker += LiquidGlassDirtyFields.ChromaMultiplier
}
if (old.rendering.edgeSoftness != new.rendering.edgeSoftness) {
  dirtyTracker += LiquidGlassDirtyFields.EdgeSoftness
}
if (old.rendering.contentNormalBlend != new.rendering.contentNormalBlend) {
  dirtyTracker += LiquidGlassDirtyFields.ContentNormalBlend
}
if (old.rendering.surfaceProfile != new.rendering.surfaceProfile) {
  dirtyTracker += LiquidGlassDirtyFields.SurfaceProfile
}
if (old.rendering.chromaticAberrationStrength != new.rendering.chromaticAberrationStrength) {
  dirtyTracker += LiquidGlassDirtyFields.ChromaticAberration
}
if (old.rendering.chromaticAberrationMode != new.rendering.chromaticAberrationMode) {
  dirtyTracker += LiquidGlassDirtyFields.ChromaticAberrationMode
}
```

- [ ] **Step 7: Run focused liquid glass tests**

Run:

```bash
./gradlew :haze-liquidglass:test --tests dev.chrisbanes.haze.liquidglass.LiquidGlassStyleTest
```

Expected: PASS.

- [ ] **Step 8: Run all liquid glass tests**

Run:

```bash
./gradlew :haze-liquidglass:test
```

Expected: PASS.

- [ ] **Step 9: Commit grouped liquid glass implementation**

Run:

```bash
git add haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyle.kt haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassDefaults.kt haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassVisualEffect.kt
git commit -m "refactor: group liquid glass style values"
```

---

### Task 6: Remove Migration Aliases and Update Call Sites

**Files:**
- Delete: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyle.kt`
- Modify: source files found by the `rg` commands in this task

- [ ] **Step 1: Find deprecated migration declarations and old call sites**

Run:

```bash
rg "typealias HazeStyle|typealias HazeTint|typealias HazeProgressive|LocalHazeStyle|fun HazeTint|hazeEffect\\(.*style|dev\\.chrisbanes\\.haze\\.materials|LiquidGlassStyle\\(" haze haze-blur haze-liquidglass haze-materials sample docs -n
```

Expected: output includes `MigrationAliases.kt`, deprecated aliases in `HazeBlurStyle.kt`, docs, and any flat `LiquidGlassStyle(...)` call sites.

- [ ] **Step 2: Delete root-package migration aliases**

Delete `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`.

- [ ] **Step 3: Remove deprecated blur-package aliases/factories**

In `HazeBlurStyle.kt`, remove these declarations:

```kotlin
public typealias HazeStyle = HazeBlurStyle
public val LocalHazeStyle: ProvidableCompositionLocal<HazeBlurStyle> get() = LocalHazeBlurStyle
public typealias HazeTint = HazeColorEffect
public fun HazeTint(color: Color, blendMode: BlendMode = HazeColorEffect.DefaultBlendMode): HazeColorEffect
public fun HazeTint(brush: Brush, blendMode: BlendMode = HazeColorEffect.DefaultBlendMode): HazeColorEffect
```

Also remove imports that are only used by the deleted factories, such as `Brush` if no remaining declaration uses it.

- [ ] **Step 4: Update old package imports**

For any source file still importing `dev.chrisbanes.haze.materials.*`, change imports to `dev.chrisbanes.haze.blur.materials.*`. For example:

```kotlin
import dev.chrisbanes.haze.blur.materials.HazeMaterials
```

- [ ] **Step 5: Update flat `LiquidGlassStyle(...)` call sites**

For each flat style call site, move values into groups. Use this mapping:

```kotlin
LiquidGlassStyle(
  tint = tint,
  shape = shape,
  optics = LiquidGlassOptics(
    refractionStrength = refractionStrength,
    refractionHeight = refractionHeight,
    refractionScale = refractionScale,
    depth = depth,
    blurRadius = blurRadius,
  ),
  lighting = LiquidGlassLighting(
    specularIntensity = specularIntensity,
    specularExponent = specularExponent,
    fresnelExponent = fresnelExponent,
    ambientResponse = ambientResponse,
    lightPosition = lightPosition,
  ),
  color = LiquidGlassColor(
    alpha = alpha,
    contrast = contrast,
    whitePoint = whitePoint,
    chromaMultiplier = chromaMultiplier,
  ),
  rendering = LiquidGlassRendering(
    edgeSoftness = edgeSoftness,
    contentNormalBlend = contentNormalBlend,
    surfaceProfile = surfaceProfile,
    chromaticAberrationStrength = chromaticAberrationStrength,
    chromaticAberrationMode = chromaticAberrationMode,
  ),
)
```

Omit group properties whose values are unspecified/default in that call site.

- [ ] **Step 6: Confirm old aliases are gone from source**

Run:

```bash
rg "typealias HazeStyle|typealias HazeTint|typealias HazeProgressive|LocalHazeStyle|fun HazeTint|dev\\.chrisbanes\\.haze\\.materials" haze haze-blur haze-liquidglass haze-materials sample -n
```

Expected: no output.

- [ ] **Step 7: Run module compilation/tests**

Run:

```bash
./gradlew :haze-blur:test :haze-liquidglass:test :haze-materials:compileKotlinMetadata
```

Expected: PASS.

- [ ] **Step 8: Commit alias removal and call-site updates**

Run:

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur haze-liquidglass sample haze-materials
git commit -m "refactor: remove v1 blur migration aliases"
```

---

### Task 7: Documentation Updates

**Files:**
- Modify: `docs/migrating-2.0.md`
- Optionally modify: `docs/effects/liquid-glass.md`
- Optionally modify: `docs/blur/usage.md`

- [ ] **Step 1: Update the migration guide hard-break language**

In `docs/migrating-2.0.md`, change the overview bullets so they include:

```markdown
- **Hard source break:** v1 blur convenience names and root-package aliases are removed in v2.
- **New module dependency:** Blur functionality now requires the `haze-blur` module.
- **API nesting:** All blur properties now require a `blurEffect {}` wrapper.
- **Package changes:** Blur APIs moved to `dev.chrisbanes.haze.blur`; blur materials moved to `dev.chrisbanes.haze.blur.materials`.
- **Liquid glass style grouping:** `LiquidGlassStyle` parameters are grouped into `optics`, `lighting`, `color`, and `rendering`.
```

Remove the “Core modifiers signatures remain the same” claim or rewrite it as:

```markdown
- `hazeSource` remains in the core module.
- `hazeEffect` remains in the core module, but blur-specific style parameters moved into `blurEffect {}`.
```

- [ ] **Step 2: Update v1-to-v2 style examples**

Ensure the style migration example reads:

```kotlin
// v1
Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())

// v2
Modifier.hazeEffect(state = hazeState) {
  blurEffect {
    style = HazeMaterials.ultraThin()
  }
}
```

Ensure imports include:

```kotlin
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
```

- [ ] **Step 3: Add liquid glass grouped style migration example**

Add a section containing:

```markdown
### Liquid Glass Style Grouping

Flat `LiquidGlassStyle` construction has been grouped by concept.
```

and this code:

```kotlin
// Before
LiquidGlassStyle(
  tint = Color.White.copy(alpha = 0.12f),
  refractionStrength = 0.7f,
  specularIntensity = 0.4f,
  depth = 0.4f,
  edgeSoftness = 12.dp,
)

// After
LiquidGlassStyle(
  tint = Color.White.copy(alpha = 0.12f),
  optics = LiquidGlassOptics(
    refractionStrength = 0.7f,
    depth = 0.4f,
  ),
  lighting = LiquidGlassLighting(
    specularIntensity = 0.4f,
  ),
  rendering = LiquidGlassRendering(
    edgeSoftness = 12.dp,
  ),
)
```

- [ ] **Step 4: Run docs text checks by searching for old names**

Run:

```bash
rg "dev\\.chrisbanes\\.haze\\.materials|LocalHazeStyle|HazeTint\\(|HazeStyle\\b|core modifiers.*same|2\\.0\\.0-alpha01" docs README.md -n
```

Expected: no stale root-package material imports or claims. `HazeBlurStyle` and historical v1 examples are acceptable when explicitly labeled.

- [ ] **Step 5: Commit docs**

Run:

```bash
git add docs README.md
git commit -m "docs: update v2 API migration guide"
```

---

### Task 8: API Snapshots and Full Verification

**Files:**
- Modify: `haze-blur/api/api.txt`
- Modify: `haze-liquidglass/api/api.txt`
- Modify: `haze/api/api.txt` if Metalava reports changes.
- Modify: `haze-materials/api/api.txt` if docs/source changes affect signatures.

- [ ] **Step 1: Run API checks to see expected failures**

Run:

```bash
./gradlew :haze-blur:metalavaCheckCompatibility :haze-liquidglass:metalavaCheckCompatibility
```

Expected: FAIL because aliases are removed and grouped liquid glass types changed signatures.

- [ ] **Step 2: Regenerate API snapshots**

Run:

```bash
./gradlew :haze-blur:metalavaGenerateSignature :haze-liquidglass:metalavaGenerateSignature
```

Expected: PASS and update `api/api.txt` files.

If Gradle reports different Metalava task names, list module tasks:

```bash
./gradlew :haze-blur:tasks --all
./gradlew :haze-liquidglass:tasks --all
```

Then run the corresponding signature generation tasks from the output.

- [ ] **Step 3: Verify removed aliases are absent from API snapshots**

Run:

```bash
rg "MigrationAliases|typealias HazeStyle|typealias HazeTint|LocalHazeStyle|HazeTint\\(|hazeEffect\\(.*HazeBlurStyle" haze-blur/api/api.txt haze-liquidglass/api/api.txt haze/api/api.txt haze-materials/api/api.txt
```

Expected: no output.

- [ ] **Step 4: Verify grouped liquid glass API appears**

Run:

```bash
rg "LiquidGlassOptics|LiquidGlassLighting|LiquidGlassColor|LiquidGlassRendering|LiquidGlassStyle\\(" haze-liquidglass/api/api.txt
```

Expected: output includes the four group classes and `LiquidGlassStyle` constructor with grouped parameters.

- [ ] **Step 5: Run targeted module checks**

Run:

```bash
./gradlew :haze-blur:test :haze-liquidglass:test :haze-materials:check
```

Expected: PASS.

- [ ] **Step 6: Run broad verification**

Run:

```bash
./gradlew check
```

Expected: PASS. If this is too slow or fails due unrelated platform/tooling issues, capture the failing task and run the narrow affected tasks from Step 5 plus the module API checks.

- [ ] **Step 7: Commit API snapshots and verification fixes**

Run:

```bash
git add haze-blur/api haze-liquidglass/api haze/api haze-materials/api
git commit -m "chore: update API snapshots for v2 cleanup"
```

If source changes were needed during verification, include those files in this commit only if they directly fix the API cleanup work.

---

## Final Review Checklist

- [ ] `rg "MigrationAliases|LocalHazeStyle|fun HazeTint|typealias HazeStyle|typealias HazeTint" haze haze-blur haze-liquidglass haze-materials sample -n` produces no output.
- [ ] `rg "dev\\.chrisbanes\\.haze\\.materials" docs README.md haze haze-materials sample -n` produces no output except intentionally historical text.
- [ ] `./gradlew :haze-blur:test :haze-liquidglass:test :haze-materials:check` passes.
- [ ] `./gradlew check` passes or the final notes document any unrelated blocker with the exact failing task.
- [ ] API snapshots show grouped liquid glass style types and no v1 blur migration aliases.
