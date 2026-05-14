# Liquid Glass Corner Seam Pass 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Further reduce the remaining Liquid Glass corner seam by replacing the pass-1 inside-corner-only smoothing with a broader rounded-corner blend that still preserves a rectangular read.

**Architecture:** Keep the exact rounded-rectangle distance mask, sampled-height displacement magnitude, and sampled specular normal unchanged. Only change `gradSdRoundedRect(...)` so the inside rounded-corner band blends from edge-aligned direction toward arc-aligned direction late enough to protect straight edges and avoid the earlier pill-shape regression.

**Tech Stack:** Kotlin Multiplatform, AGSL/SKSL shader strings, Gradle, `kotlin.test`, AssertK, Roborazzi.

**Execution Note:** Do not create git commits while executing this plan unless the user explicitly requests one.

---

### Task 1: Pin The Broader Corner-Band Blend In The Shader Regression

**Files:**
- Modify: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`

- [ ] **Step 1: Write the failing test**

Update the existing shader-string regression so it requires the broader pass-2 corner-band blend markers and rejects the narrower pass-1-only inside-corner markers.

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

    // Direction-only refraction path: analytical rounded-rect gradient with center bias,
    // instead of deriving displacement direction from `surfaceGradient()`.
    assertThat(shader).contains("vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius)")
    assertThat(shader).contains("vec2 axisSafeSign(vec2 value)")
    assertThat(shader).contains("vec2 safeNormalize(vec2 value, vec2 fallback)")
    assertThat(shader).contains("vec2 centerFallbackDir = vec2(1.0, 0.0);")
    assertThat(shader).contains("float gradRadius =")
    assertThat(shader).contains("depth * safeNormalize(centeredCoord, centerFallbackDir)")
    assertThat(shader).contains("vec2 refractionDir = safeNormalize(")
    assertThat(shader).contains("vec2 displacement = refractionDir * displacementMagnitude;")

    // Exact-shape magnitude path remains based on the sampled surface height.
    assertThat(shader).contains("float h = surfaceHeight(coord);")
    assertThat(shader).contains("float displacementMagnitude =")

    // Specular normal stays on the existing sampled-height path for this experiment.
    assertThat(shader).contains("vec2 grad = surfaceGradient(coord);")
    assertThat(shader).contains("vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));")

    // Pass 2 should broaden the rounded-corner blend from edge-aligned to arc-aligned direction.
    assertThat(shader).contains("float edgeBlend =")
    assertThat(shader).contains("vec2 edgeDir = safeNormalize(")
    assertThat(shader).contains("float cornerProgress =")
    assertThat(shader).contains("float arcBlend = smoothstep(0.45, 0.9, cornerProgress);")
    assertThat(shader).contains("vec2 arcDir = safeNormalize(-cornerCoord, vec2(0.70710678, 0.70710678));")
    assertThat(shader).contains("vec2 insideDir = mix(edgeDir, arcDir, arcBlend);")
    assertThat(shader).contains("return coordSign * safeNormalize(insideDir, edgeDir);")

    assertThat(shader).doesNotContain("float cornerBlend = smoothstep(-2.0, 2.0, cornerDelta);")
    assertThat(shader).doesNotContain("vec2 insideDir = mix(vec2(0.0, 1.0), vec2(1.0, 0.0), cornerBlend);")
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze-liquidglass:jvmTest --tests "dev.chrisbanes.haze.liquidglass.LiquidGlassShadersTest"`

Expected: FAIL with an AssertK containment failure because the current shader still contains the pass-1-only inside-corner smoothing and does not yet contain the broader edge-to-arc blend markers.

### Task 2: Implement The Broader Corner-Band Direction Blend

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShaders.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`

- [ ] **Step 1: Replace the pass-1 inside-corner smoothing with the pass-2 corner-band blend**

Update only the inside branch of `gradSdRoundedRect(...)`. Keep the outside branch, `safeNormalize(...)`, `axisSafeSign(...)`, and all code in `main(...)` unchanged.

```kotlin
vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
  vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
  vec2 coordSign = axisSafeSign(coord);
  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
    return coordSign * safeNormalize(max(cornerCoord, 0.0), vec2(0.0));
  } else {
    float edgeBlend = smoothstep(-2.0, 2.0, cornerCoord.x - cornerCoord.y);
    vec2 edgeDir = safeNormalize(
      mix(vec2(0.0, 1.0), vec2(1.0, 0.0), edgeBlend),
      vec2(1.0, 0.0)
    );
    float cornerProgress = clamp(
      (max(cornerCoord.x, cornerCoord.y) + radius) / max(radius, 0.0001),
      0.0,
      1.0
    );
    float arcBlend = smoothstep(0.45, 0.9, cornerProgress);
    vec2 arcDir = safeNormalize(-cornerCoord, vec2(0.70710678, 0.70710678));
    vec2 insideDir = mix(edgeDir, arcDir, arcBlend);
    return coordSign * safeNormalize(insideDir, edgeDir);
  }
}
```

This keeps the dominant-axis edge direction near the straight edges, then transitions later inside the rounded-corner band toward the arc-aligned direction so the corner seam weakens without pushing the whole corner toward a pill-like look.

- [ ] **Step 2: Run the focused shader test to verify it passes**

Run: `./gradlew :haze-liquidglass:jvmTest --tests "dev.chrisbanes.haze.liquidglass.LiquidGlassShadersTest"`

Expected: PASS with the updated pass-2 shader-string assertions.

- [ ] **Step 3: Run compile verification**

Run: `./gradlew :haze-liquidglass:compileDebugKotlinAndroid :haze-liquidglass:compileKotlinJvm :haze-liquidglass:compileKotlinIosArm64`

Expected: BUILD SUCCESSFUL.

### Task 3: Re-Record The Seam-Focused Screenshots And Judge Pass 2

**Files:**
- Review: `haze-screenshot-tests/screenshots/android/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Review: `haze-screenshot-tests/screenshots/android/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Review: `haze-screenshot-tests/screenshots/desktop/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Review: `haze-screenshot-tests/screenshots/desktop/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png`

- [ ] **Step 1: Record fresh screenshot baselines**

Run: `./gradlew :haze-screenshot-tests:recordRoborazziDebug :haze-screenshot-tests:recordRoborazziJvm`

Expected: BUILD SUCCESSFUL and the four seam-focused rounded-card baselines update in place.

- [ ] **Step 2: Review the same four seam-focused outputs**

Judge the new outputs with this checklist:

```text
- Is the corner seam weaker than after pass 1?
- Do the straight edges still look unchanged?
- Do the rounded corners still read rectangular rather than pill-like?
- Is the residual seam small enough to stop after pass 2?
```

- [ ] **Step 3: Decide the follow-up action**

Use this decision rule:

```text
Stop after pass 2 if:
- the seam is weaker than pass 1
- the silhouette still reads rectangular
- no new visual regression stands out

Open a separate follow-up only if:
- the seam is still distracting at normal viewing size
- the broader corner blend softened the corners too much
- the screenshots reveal a new artifact that needs a different approach
```
