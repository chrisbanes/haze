# V2 API Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean up the v2 API by removing partial v1 migration shims, fixing blur style immutability and empty-list semantics, grouping `GlassStyle`, updating docs, and regenerating API snapshots.

**Architecture:** Keep the hard v2 break explicit: blur APIs only live under `dev.chrisbanes.haze.blur`, Glass remains experimental but uses grouped immutable style values, and existing `VisualEffect` lifecycle behavior stays unchanged. Implement behavior-preserving refactors behind focused unit tests, then update docs and API files.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Runtime/UI, assertk, kotlin.test, Gradle, Metalava API snapshots.

---

## File Structure

- Modify `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`: delete the file if all declarations are removed.
- Modify `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyle.kt`: remove deprecated aliases/factories, add defensive list snapshots, and support `null` as unspecified color effects.
- Modify `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`: store direct color effects as nullable authoring state and resolve empty lists as explicit empty.
- Create `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/HazeBlurStyleTest.kt`: test defensive copies and explicit-empty semantics.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassStyle.kt`: replace the flat style with grouped immutable value types.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`: build defaults through grouped style values.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`: resolve grouped style values with the same precedence as the old flat style.
- Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt`: test grouped defaults and precedence.
- Modify sample/screenshot/docs source files that construct `GlassStyle` directly.
- Modify `docs/migrating-2.0.md`: document the hard break and grouped Glass migration.
- Modify API snapshots in `haze-blur/api/api.txt`, `haze-glass/api/api.txt`, and any versioned snapshots required by the project.

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

### Task 3: Glass Grouped Style Tests

**Files:**
- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt`
- Read: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`
- Read: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`

- [ ] **Step 1: Write failing tests for grouped defaults and precedence**

Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt` with:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class GlassStyleTest {

  @Test
  fun defaultsStyle_resolvesToGlassDefaults() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassDefaults.style
    }

    assertThat(effect.tint).isEqualTo(GlassDefaults.tint)
    assertThat(effect.shape).isEqualTo(GlassDefaults.shape)
    assertThat(effect.refractionStrength).isEqualTo(GlassDefaults.refractionStrength)
    assertThat(effect.refractionHeight).isEqualTo(GlassDefaults.refractionHeight)
    assertThat(effect.refractionScale).isEqualTo(GlassDefaults.refractionScale)
    assertThat(effect.depth).isEqualTo(GlassDefaults.depth)
    assertThat(effect.blurRadius).isEqualTo(GlassDefaults.blurRadius)
    assertThat(effect.specularIntensity).isEqualTo(GlassDefaults.specularIntensity)
    assertThat(effect.specularExponent).isEqualTo(GlassDefaults.specularExponent)
    assertThat(effect.fresnelExponent).isEqualTo(GlassDefaults.fresnelExponent)
    assertThat(effect.ambientResponse).isEqualTo(GlassDefaults.ambientResponse)
    assertThat(effect.alpha).isEqualTo(GlassDefaults.alpha)
    assertThat(effect.contrast).isEqualTo(GlassDefaults.contrast)
    assertThat(effect.whitePoint).isEqualTo(GlassDefaults.whitePoint)
    assertThat(effect.chromaMultiplier).isEqualTo(GlassDefaults.chromaMultiplier)
    assertThat(effect.edgeSoftness).isEqualTo(GlassDefaults.edgeSoftness)
    assertThat(effect.contentNormalBlend).isEqualTo(GlassDefaults.contentNormalBlend)
    assertThat(effect.surfaceProfile).isEqualTo(GlassDefaults.surfaceProfile)
    assertThat(effect.chromaticAberrationStrength).isEqualTo(GlassDefaults.chromaticAberrationStrength)
    assertThat(effect.chromaticAberrationMode).isEqualTo(GlassDefaults.chromaticAberrationMode)
  }

  @Test
  fun groupedStyle_partiallySpecifiedValuesInheritFromCompositionLocal() {
    val localStyle = GlassStyle(
      tint = Color.Blue,
      shape = RoundedCornerShape(12.dp),
      optics = GlassOptics(
        refractionStrength = 0.2f,
        refractionScale = 8f,
        depth = 0.3f,
      ),
      lighting = GlassLighting(
        specularIntensity = 0.25f,
        lightPosition = Offset(4f, 8f),
      ),
      color = GlassColor(alpha = 0.7f, contrast = 0.4f),
      rendering = GlassRendering(
        edgeSoftness = 6.dp,
        surfaceProfile = SurfaceProfile.Concave,
      ),
    )
    val directStyle = GlassStyle(
      optics = GlassOptics(refractionStrength = 0.9f),
      lighting = GlassLighting(ambientResponse = 0.8f),
      color = GlassColor(whitePoint = 0.1f),
      rendering = GlassRendering(chromaticAberrationStrength = 0.5f),
    )
    val effect = GlassVisualEffect().apply {
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
    val effect = GlassVisualEffect().apply {
      style = GlassStyle(
        tint = Color.Blue,
        optics = GlassOptics(refractionStrength = 0.2f),
        lighting = GlassLighting(ambientResponse = 0.3f),
        color = GlassColor(alpha = 0.4f),
        rendering = GlassRendering(edgeSoftness = 6.dp),
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

- [ ] **Step 2: Run focused Glass tests and verify failure**

Run:

```bash
./gradlew :haze-glass:test --tests dev.chrisbanes.haze.glass.GlassStyleTest
```

Expected: FAIL because the grouped style types do not exist yet.

- [ ] **Step 3: Commit failing Glass tests**

Run:

```bash
git add haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt
git commit -m "test: cover Glass grouped style semantics"
```

---

### Task 4: Glass Grouped Style Types and Defaults

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassStyle.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt`

- [ ] **Step 1: Replace flat `GlassStyle` with grouped value types**

In `GlassStyle.kt`, keep `LocalGlassStyle`, then replace the flat style class with:

```kotlin
@ExperimentalHazeApi
@Immutable
public data class GlassStyle(
  val tint: Color = Color.Unspecified,
  val shape: RoundedCornerShape? = null,
  val optics: GlassOptics = GlassOptics.Unspecified,
  val lighting: GlassLighting = GlassLighting.Unspecified,
  val color: GlassColor = GlassColor.Unspecified,
  val rendering: GlassRendering = GlassRendering.Unspecified,
) {
  public companion object {
    public val Unspecified: GlassStyle = GlassStyle()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassOptics(
  val refractionStrength: Float = Float.NaN,
  val refractionHeight: Float = Float.NaN,
  val refractionScale: Float = Float.NaN,
  val depth: Float = Float.NaN,
  val blurRadius: Dp = Dp.Unspecified,
) {
  public companion object {
    public val Unspecified: GlassOptics = GlassOptics()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassLighting(
  val specularIntensity: Float = Float.NaN,
  val specularExponent: Float = Float.NaN,
  val fresnelExponent: Float = Float.NaN,
  val ambientResponse: Float = Float.NaN,
  val lightPosition: Offset = Offset.Unspecified,
) {
  public companion object {
    public val Unspecified: GlassLighting = GlassLighting()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassColor(
  val alpha: Float = Float.NaN,
  val contrast: Float = Float.NaN,
  val whitePoint: Float = Float.NaN,
  val chromaMultiplier: Float = Float.NaN,
) {
  public companion object {
    public val Unspecified: GlassColor = GlassColor()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassRendering(
  val edgeSoftness: Dp = Dp.Unspecified,
  val contentNormalBlend: Float = Float.NaN,
  val surfaceProfile: SurfaceProfile? = null,
  val chromaticAberrationStrength: Float = Float.NaN,
  val chromaticAberrationMode: ChromaticAberrationMode? = null,
) {
  public companion object {
    public val Unspecified: GlassRendering = GlassRendering()
  }
}
```

- [ ] **Step 2: Rebuild `GlassDefaults.style` with groups**

In `GlassDefaults.kt`, replace the flat `style` initializer with:

```kotlin
public val style: GlassStyle = GlassStyle(
  tint = tint,
  shape = shape,
  optics = GlassOptics(
    refractionStrength = refractionStrength,
    refractionHeight = refractionHeight,
    refractionScale = refractionScale,
    depth = depth,
    blurRadius = blurRadius,
  ),
  lighting = GlassLighting(
    specularIntensity = specularIntensity,
    specularExponent = specularExponent,
    fresnelExponent = fresnelExponent,
    ambientResponse = ambientResponse,
  ),
  color = GlassColor(
    alpha = alpha,
    contrast = contrast,
    whitePoint = whitePoint,
    chromaMultiplier = chromaMultiplier,
  ),
  rendering = GlassRendering(
    edgeSoftness = edgeSoftness,
    contentNormalBlend = contentNormalBlend,
    surfaceProfile = surfaceProfile,
    chromaticAberrationStrength = chromaticAberrationStrength,
    chromaticAberrationMode = chromaticAberrationMode,
  ),
)
```

- [ ] **Step 3: Run focused Glass tests and verify remaining failures**

Run:

```bash
./gradlew :haze-glass:test --tests dev.chrisbanes.haze.glass.GlassStyleTest
```

Expected: FAIL in `GlassVisualEffect` compilation because it still reads flat `style.refractionStrength`, `style.specularIntensity`, and related properties.

- [ ] **Step 4: Leave grouped style type changes uncommitted for the next task**

Do not commit these implementation changes yet. The module is expected to keep
failing until Task 5 updates `GlassVisualEffect` to read grouped style
values. Confirm the intended uncommitted files:

```bash
git status --short haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassStyle.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt
```

Expected: both files are modified.

---

### Task 5: Glass VisualEffect Resolution

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Test: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStyleTest.kt`

- [ ] **Step 1: Add grouped style helper accessors**

In `GlassVisualEffect.kt`, add private helper properties near `style`/`compositionLocalStyle`:

```kotlin
private val styleOptics: GlassOptics get() = style.optics
private val localOptics: GlassOptics get() = compositionLocalStyle.optics
private val styleLighting: GlassLighting get() = style.lighting
private val localLighting: GlassLighting get() = compositionLocalStyle.lighting
private val styleColor: GlassColor get() = style.color
private val localColor: GlassColor get() = compositionLocalStyle.color
private val styleRendering: GlassRendering get() = style.rendering
private val localRendering: GlassRendering get() = compositionLocalStyle.rendering
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

Replace the full body of `onStyleChanged(old: GlassStyle, new: GlassStyle)` with comparisons against group fields:

```kotlin
if (old.optics.refractionStrength != new.optics.refractionStrength) {
  dirtyTracker += GlassDirtyFields.RefractionStrength
}
if (old.optics.depth != new.optics.depth) {
  dirtyTracker += GlassDirtyFields.Depth
}
if (old.optics.blurRadius != new.optics.blurRadius) {
  dirtyTracker += GlassDirtyFields.BlurRadius
}
if (old.optics.refractionHeight != new.optics.refractionHeight) {
  dirtyTracker += GlassDirtyFields.RefractionHeight
}
if (old.optics.refractionScale != new.optics.refractionScale) {
  dirtyTracker += GlassDirtyFields.RefractionScale
}
if (old.lighting.specularIntensity != new.lighting.specularIntensity) {
  dirtyTracker += GlassDirtyFields.SpecularIntensity
}
if (old.lighting.ambientResponse != new.lighting.ambientResponse) {
  dirtyTracker += GlassDirtyFields.AmbientResponse
}
if (old.lighting.lightPosition != new.lighting.lightPosition) {
  dirtyTracker += GlassDirtyFields.LightPosition
}
if (old.lighting.specularExponent != new.lighting.specularExponent) {
  dirtyTracker += GlassDirtyFields.SpecularExponent
}
if (old.lighting.fresnelExponent != new.lighting.fresnelExponent) {
  dirtyTracker += GlassDirtyFields.FresnelExponent
}
if (old.tint != new.tint) {
  dirtyTracker += GlassDirtyFields.Tint
}
if (old.shape != new.shape) {
  dirtyTracker += GlassDirtyFields.Shape
}
if (old.color.alpha != new.color.alpha) {
  dirtyTracker += GlassDirtyFields.Alpha
}
if (old.color.contrast != new.color.contrast) {
  dirtyTracker += GlassDirtyFields.Contrast
}
if (old.color.whitePoint != new.color.whitePoint) {
  dirtyTracker += GlassDirtyFields.WhitePoint
}
if (old.color.chromaMultiplier != new.color.chromaMultiplier) {
  dirtyTracker += GlassDirtyFields.ChromaMultiplier
}
if (old.rendering.edgeSoftness != new.rendering.edgeSoftness) {
  dirtyTracker += GlassDirtyFields.EdgeSoftness
}
if (old.rendering.contentNormalBlend != new.rendering.contentNormalBlend) {
  dirtyTracker += GlassDirtyFields.ContentNormalBlend
}
if (old.rendering.surfaceProfile != new.rendering.surfaceProfile) {
  dirtyTracker += GlassDirtyFields.SurfaceProfile
}
if (old.rendering.chromaticAberrationStrength != new.rendering.chromaticAberrationStrength) {
  dirtyTracker += GlassDirtyFields.ChromaticAberration
}
if (old.rendering.chromaticAberrationMode != new.rendering.chromaticAberrationMode) {
  dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
}
```

- [ ] **Step 7: Run focused Glass tests**

Run:

```bash
./gradlew :haze-glass:test --tests dev.chrisbanes.haze.glass.GlassStyleTest
```

Expected: PASS.

- [ ] **Step 8: Run all Glass tests**

Run:

```bash
./gradlew :haze-glass:test
```

Expected: PASS.

- [ ] **Step 9: Commit grouped Glass implementation**

Run:

```bash
git add haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassStyle.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt
git commit -m "refactor: group Glass style values"
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
rg "typealias HazeStyle|typealias HazeTint|typealias HazeProgressive|LocalHazeStyle|fun HazeTint|hazeEffect\\(.*style|dev\\.chrisbanes\\.haze\\.materials|GlassStyle\\(" haze haze-blur haze-glass haze-materials sample docs -n
```

Expected: output includes `MigrationAliases.kt`, deprecated aliases in `HazeBlurStyle.kt`, docs, and any flat `GlassStyle(...)` call sites.

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

- [ ] **Step 5: Update flat `GlassStyle(...)` call sites**

For each flat style call site, move values into groups. Use this mapping:

```kotlin
GlassStyle(
  tint = tint,
  shape = shape,
  optics = GlassOptics(
    refractionStrength = refractionStrength,
    refractionHeight = refractionHeight,
    refractionScale = refractionScale,
    depth = depth,
    blurRadius = blurRadius,
  ),
  lighting = GlassLighting(
    specularIntensity = specularIntensity,
    specularExponent = specularExponent,
    fresnelExponent = fresnelExponent,
    ambientResponse = ambientResponse,
    lightPosition = lightPosition,
  ),
  color = GlassColor(
    alpha = alpha,
    contrast = contrast,
    whitePoint = whitePoint,
    chromaMultiplier = chromaMultiplier,
  ),
  rendering = GlassRendering(
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
rg "typealias HazeStyle|typealias HazeTint|typealias HazeProgressive|LocalHazeStyle|fun HazeTint|dev\\.chrisbanes\\.haze\\.materials" haze haze-blur haze-glass haze-materials sample -n
```

Expected: no output.

- [ ] **Step 7: Run module compilation/tests**

Run:

```bash
./gradlew :haze-blur:test :haze-glass:test :haze-materials:compileKotlinMetadata
```

Expected: PASS.

- [ ] **Step 8: Commit alias removal and call-site updates**

Run:

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur haze-glass sample haze-materials
git commit -m "refactor: remove v1 blur migration aliases"
```

---

### Task 7: Documentation Updates

**Files:**
- Modify: `docs/migrating-2.0.md`
- Optionally modify: `docs/effects/glass.md`
- Optionally modify: `docs/blur/usage.md`

- [ ] **Step 1: Update the migration guide hard-break language**

In `docs/migrating-2.0.md`, change the overview bullets so they include:

```markdown
- **Hard source break:** v1 blur convenience names and root-package aliases are removed in v2.
- **New module dependency:** Blur functionality now requires the `haze-blur` module.
- **API nesting:** All blur properties now require a `blurEffect {}` wrapper.
- **Package changes:** Blur APIs moved to `dev.chrisbanes.haze.blur`; blur materials moved to `dev.chrisbanes.haze.blur.materials`.
- **Glass style grouping:** `GlassStyle` parameters are grouped into `optics`, `lighting`, `color`, and `rendering`.
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

- [ ] **Step 3: Add Glass grouped style migration example**

Add a section containing:

```markdown
### Glass Style Grouping

Flat `GlassStyle` construction has been grouped by concept.
```

and this code:

```kotlin
// Before
GlassStyle(
  tint = Color.White.copy(alpha = 0.12f),
  refractionStrength = 0.7f,
  specularIntensity = 0.4f,
  depth = 0.4f,
  edgeSoftness = 12.dp,
)

// After
GlassStyle(
  tint = Color.White.copy(alpha = 0.12f),
  optics = GlassOptics(
    refractionStrength = 0.7f,
    depth = 0.4f,
  ),
  lighting = GlassLighting(
    specularIntensity = 0.4f,
  ),
  rendering = GlassRendering(
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
- Modify: `haze-glass/api/api.txt`
- Modify: `haze/api/api.txt` if Metalava reports changes.
- Modify: `haze-materials/api/api.txt` if docs/source changes affect signatures.

- [ ] **Step 1: Run API checks to see expected failures**

Run:

```bash
./gradlew :haze-blur:metalavaCheckCompatibility :haze-glass:metalavaCheckCompatibility
```

Expected: FAIL because aliases are removed and grouped Glass types changed signatures.

- [ ] **Step 2: Regenerate API snapshots**

Run:

```bash
./gradlew :haze-blur:metalavaGenerateSignature :haze-glass:metalavaGenerateSignature
```

Expected: PASS and update `api/api.txt` files.

If Gradle reports different Metalava task names, list module tasks:

```bash
./gradlew :haze-blur:tasks --all
./gradlew :haze-glass:tasks --all
```

Then run the corresponding signature generation tasks from the output.

- [ ] **Step 3: Verify removed aliases are absent from API snapshots**

Run:

```bash
rg "MigrationAliases|typealias HazeStyle|typealias HazeTint|LocalHazeStyle|HazeTint\\(|hazeEffect\\(.*HazeBlurStyle" haze-blur/api/api.txt haze-glass/api/api.txt haze/api/api.txt haze-materials/api/api.txt
```

Expected: no output.

- [ ] **Step 4: Verify grouped Glass API appears**

Run:

```bash
rg "GlassOptics|GlassLighting|GlassColor|GlassRendering|GlassStyle\\(" haze-glass/api/api.txt
```

Expected: output includes the four group classes and `GlassStyle` constructor with grouped parameters.

- [ ] **Step 5: Run targeted module checks**

Run:

```bash
./gradlew :haze-blur:test :haze-glass:test :haze-materials:check
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
git add haze-blur/api haze-glass/api haze/api haze-materials/api
git commit -m "chore: update API snapshots for v2 cleanup"
```

If source changes were needed during verification, include those files in this commit only if they directly fix the API cleanup work.

---

## Final Review Checklist

- [ ] `rg "MigrationAliases|LocalHazeStyle|fun HazeTint|typealias HazeStyle|typealias HazeTint" haze haze-blur haze-glass haze-materials sample -n` produces no output.
- [ ] `rg "dev\\.chrisbanes\\.haze\\.materials" docs README.md haze haze-materials sample -n` produces no output except intentionally historical text.
- [ ] `./gradlew :haze-blur:test :haze-glass:test :haze-materials:check` passes.
- [ ] `./gradlew check` passes or the final notes document any unrelated blocker with the exact failing task.
- [ ] API snapshots show grouped Glass style types and no v1 blur migration aliases.
