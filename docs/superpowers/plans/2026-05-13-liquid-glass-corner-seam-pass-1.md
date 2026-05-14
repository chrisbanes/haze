# Liquid Glass Corner Seam Pass 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the remaining Liquid Glass corner seam with a minimal smoothing change limited to the inside-corner branch of the analytical refraction direction field.

**Architecture:** Keep the current exact-shape distance mask, sampled-height displacement magnitude, and sampled specular normal unchanged. Only modify `gradSdRoundedRect(...)` so the inside-corner branch no longer snaps between horizontal and vertical directions at the corner bisector.

**Tech Stack:** Kotlin Multiplatform, AGSL/SKSL shader strings, Gradle, `kotlin.test`, AssertK.

---

### Task 1: Pin The Inside-Corner Smoothing In The Shader Regression

**Files:**
- Modify: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`

- [ ] **Step 1: Write the failing test**

Update the existing shader-string regression so it asserts the hard inside-corner `step(...)` branch is gone and replaced by a smoothing path that uses a narrow blend band. Keep the existing assertions for the current direction-only refraction path.

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test

class LiquidGlassShadersTest {

  @Test
  fun shader_uses_qmdeve_style_direction_field_only_for_refraction() {
    val shader = LiquidGlassShaders.LIQUID_GLASS_SKSL

    assertThat(shader).contains("vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius)")
    assertThat(shader).contains("vec2 axisSafeSign(vec2 value)")
    assertThat(shader).contains("vec2 safeNormalize(vec2 value, vec2 fallback)")
    assertThat(shader).contains("float cornerBlend =")
    assertThat(shader).contains("vec2 insideDir = mix(vec2(1.0, 0.0), vec2(0.0, 1.0), cornerBlend);")
    assertThat(shader).contains("return coordSign * safeNormalize(insideDir, vec2(1.0, 0.0));")
    assertThat(shader).contains("vec2 centerFallbackDir = vec2(1.0, 0.0);")
    assertThat(shader).contains("float gradRadius =")
    assertThat(shader).contains("depth * safeNormalize(centeredCoord, centerFallbackDir)")
    assertThat(shader).contains("vec2 refractionDir = safeNormalize(")
    assertThat(shader).contains("vec2 displacement = refractionDir * displacementMagnitude;")
    assertThat(shader).contains("float h = surfaceHeight(coord);")
    assertThat(shader).contains("float displacementMagnitude =")
    assertThat(shader).contains("vec2 grad = surfaceGradient(coord);")
    assertThat(shader).contains("vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));")

    assertThat(shader).doesNotContain("float gradX = step(cornerCoord.y, cornerCoord.x);")
    assertThat(shader).doesNotContain("return coordSign * vec2(gradX, 1.0 - gradX);")
    assertThat(shader).doesNotContain("vec2 displacement = -grad * refractionStrength;")
    assertThat(shader).doesNotContain("depth * normalize(centeredCoord)")
    assertThat(shader).doesNotContain("vec2 refractionDir = normalize(")
    assertThat(shader).doesNotContain("return sign(coord) *")
    assertThat(shader).doesNotContain("depth * safeNormalize(centeredCoord, vec2(0.0))")
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze-liquidglass:jvmTest --tests "dev.chrisbanes.haze.liquidglass.LiquidGlassShadersTest"`

Expected: FAIL with an AssertK containment failure because the current shader still contains the hard `step(...)`-based inside-corner branch and does not yet contain the smoothing expressions.

- [ ] **Step 3: Commit the failing test**

```bash
git add haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt
git commit -m "test: pin liquid glass corner seam pass 1"
```

### Task 2: Smooth The Inside-Corner Direction Transition

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShaders.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`

- [ ] **Step 1: Implement the minimal shader change**

Replace only the inside-corner branch of `gradSdRoundedRect(...)` with a narrow smoothing band based on the difference between `cornerCoord.x` and `cornerCoord.y`. Keep the outside branch, `safeNormalize(...)`, `axisSafeSign(...)`, and all code in `main(...)` unchanged.

```kotlin
vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
  vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
  vec2 coordSign = axisSafeSign(coord);
  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
    return coordSign * safeNormalize(max(cornerCoord, 0.0), vec2(0.0));
  } else {
    float cornerBlend = smoothstep(-1.0, 1.0, (cornerCoord.x - cornerCoord.y) * 0.5 + 0.5);
    vec2 insideDir = mix(vec2(0.0, 1.0), vec2(1.0, 0.0), cornerBlend);
    return coordSign * safeNormalize(insideDir, vec2(1.0, 0.0));
  }
}
```

If the `smoothstep(...)` input needs slight adjustment for AGSL/SKSL readability, keep the same behavior but use explicit temporaries, for example:

```kotlin
float cornerDelta = cornerCoord.x - cornerCoord.y;
float cornerBlend = smoothstep(-2.0, 2.0, cornerDelta);
vec2 insideDir = mix(vec2(0.0, 1.0), vec2(1.0, 0.0), cornerBlend);
return coordSign * safeNormalize(insideDir, vec2(1.0, 0.0));
```

The important constraint is that the blend band stays narrow and local to the bisector.

- [ ] **Step 2: Run the focused test to verify it passes**

Run: `./gradlew :haze-liquidglass:jvmTest --tests "dev.chrisbanes.haze.liquidglass.LiquidGlassShadersTest"`

Expected: PASS with the updated shader-string assertions.

- [ ] **Step 3: Run compile verification**

Run: `./gradlew :haze-liquidglass:compileDebugKotlinAndroid :haze-liquidglass:compileKotlinJvm :haze-liquidglass:compileKotlinIosArm64`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit the minimal fix**

```bash
git add haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShaders.kt haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt
git commit -m "Refine liquid glass corner refraction direction"
```

### Task 3: Reassess Whether Pass 2 Is Needed

**Files:**
- Review: `haze-screenshot-tests/screenshots/android/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Review: `haze-screenshot-tests/screenshots/android/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Review: `haze-screenshot-tests/screenshots/desktop/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Review: `haze-screenshot-tests/screenshots/desktop/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png`

- [ ] **Step 1: Record fresh screenshot baselines**

Run: `./gradlew :haze-screenshot-tests:recordRoborazziDebug :haze-screenshot-tests:recordRoborazziJvm`

Expected: BUILD SUCCESSFUL and the four seam-focused rounded-card baselines update in place.

- [ ] **Step 2: Review the same four seam-focused outputs**

Judge whether the remaining seam is reduced again while the card still reads as a rectangle instead of a pill.

Use this checklist:

```text
- Is the corner bisector seam weaker than in the previous pass?
- Do the straight edges still look unchanged?
- Do the rounded corners still avoid the over-rounded “pill” look?
- Is the remaining seam small enough to stop before pass 2?
```

- [ ] **Step 3: Decide on next action**

If the seam is acceptable, stop after pass 1. If the seam is still too visible, start a separate pass-2 plan for the broader corner-region blend.

```text
Stop here if:
- seam is further reduced
- silhouette still looks rectangular
- no new visual regression stands out

Continue to pass 2 if:
- seam is still distracting at normal viewing size
- corner smoothing still looks too piecewise
```
