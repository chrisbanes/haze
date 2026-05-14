# Liquid Glass QmDeve Direction Experiment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Liquid Glass refraction direction with a QmDeve-style direction-only field experiment that may reduce corner seam artifacts without globally rounding rectangular silhouettes.

**Architecture:** Keep Haze's exact shape distance field for edge masking, height profile, displacement magnitude, and the existing sampled-height specular normal. Only the refraction direction changes: reintroduce an analytical rounded-rect SDF direction helper, inflate its radius mildly, and blend in a center-bias term driven by the existing `depth` uniform.

**Tech Stack:** Kotlin Multiplatform, AGSL/SKSL runtime shader strings, Gradle, Roborazzi screenshot recording, `kotlin.test`, AssertK.

---

### Task 1: Pin The Direction-Only Experiment In The Shader Regression

**Files:**
- Modify: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`

- [ ] **Step 1: Write the failing test**

Update `LiquidGlassShadersTest.kt` so the single test asserts the shader now contains the QmDeve-style direction-only pieces while preserving the exact-shape magnitude path.

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import assertk.assertThat
import assertk.assertions.contains
import kotlin.test.Test

class LiquidGlassShadersTest {

  @Test
  fun shader_uses_qmdeve_style_direction_field_only_for_refraction() {
    val shader = LiquidGlassShaders.LIQUID_GLASS_SKSL

    assertThat(shader).contains("vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius)")
    assertThat(shader).contains("float smoothRadius = max(radius * 1.5, 30.0);")
    assertThat(shader).contains("float gradRadius = min(smoothRadius, min(halfSize.x, halfSize.y));")
    assertThat(shader).contains(
      "vec2 refractionDir = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depth * normalize(centeredCoord));"
    )
    assertThat(shader).contains("vec2 displacement = refractionDir * displacementMagnitude;")
    assertThat(shader).contains("vec2 grad = surfaceGradient(coord);")
    assertThat(shader).contains("vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));")
    assertThat(shader).contains("float h = surfaceHeight(coord);")
    assertThat(shader).contains("float heightNorm = clamp(h / refractionZone, 0.0, 1.0);")
    assertThat(shader).contains("float displacementMagnitude = -heightNorm * refractionStrength * 12.0;")
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze-liquidglass:jvmTest --tests "dev.chrisbanes.haze.liquidglass.LiquidGlassShadersTest"`

Expected: FAIL with an AssertK containment failure because `gradSdRoundedRect`, `smoothRadius`, `gradRadius`, and `refractionDir` do not exist in the current shader.

- [ ] **Step 3: Commit the failing test**

```bash
git add haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt
git commit -m "test: pin liquid glass refraction direction experiment"
```

### Task 2: Switch Refraction Direction To QmDeve-Style Analytical Field

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShaders.kt`
- Test: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt`

- [ ] **Step 1: Implement the minimal shader change**

Update `LiquidGlassShaders.kt` so refraction direction comes from an analytical rounded-rect gradient with mild radius inflation and center bias, while displacement magnitude and specular normal remain on the current exact-shape sampled-height path.

The target shader shape is:

```kotlin
internal object LiquidGlassShaders {
  val LIQUID_GLASS_SKSL: String by lazy(mode = LazyThreadSafetyMode.NONE) {
    """
    uniform shader content;
    uniform shader blurredContent;
    uniform float2 layerSize;
    uniform float refractionStrength;
    uniform float specularIntensity;
    uniform float depth;
    uniform float ambientResponse;
    uniform float edgeSoftness;
    uniform float refractionHeight;
    uniform float chromaticAberrationStrength;
    uniform float2 lightPosition;
    uniform vec4 cornerRadii;
    uniform vec4 tintColor;
    uniform float surfaceProfile;
    uniform float chromaticAberrationMode;

    vec2 clampCoord(vec2 coord) {
      return clamp(coord, vec2(0.5, 0.5), layerSize - vec2(0.5, 0.5));
    }

    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    float squircleMap(float x) {
      return pow(1.0 - pow(1.0 - x, 4.0), 0.25);
    }

    float smootherstep(float x) {
      float t = clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    float radiusAt(vec2 coord, vec4 radii) {
      if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
      } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
      }
    }

    float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      float outside = length(max(cornerCoord, 0.0)) - radius;
      float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
      return outside + inside;
    }

    vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
      } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * vec2(gradX, 1.0 - gradX);
      }
    }

    float evaluateProfile(float t) {
      float x = 1.0 - clamp(t, 0.0, 1.0);
      if (surfaceProfile == 1) {
        return squircleMap(x);
      } else if (surfaceProfile == 2) {
        return -circleMap(x);
      } else if (surfaceProfile == 3) {
        float convex = circleMap(x);
        float concave = -circleMap(x);
        float blend = smootherstep(clamp(t / 0.7, 0.0, 1.0));
        return mix(convex, concave, blend);
      }
      return circleMap(x);
    }

    float surfaceHeightAt(vec2 coord, float customRadius) {
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float sd = sdRoundedRect(centeredCoord, halfSize, customRadius);
      float distToEdge = max(-sd, 0.0);
      float refractionZone = max(refractionHeight, 0.0001);
      if (distToEdge >= refractionZone) return 0.0;
      float t = clamp(distToEdge / refractionZone, 0.0, 1.0);
      return evaluateProfile(t) * refractionZone;
    }

    float surfaceHeight(vec2 coord) {
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      return surfaceHeightAt(coord, radius);
    }

    vec2 surfaceGradient(vec2 coord) {
      float sampleStep = 2.0;
      float left = surfaceHeight(clampCoord(coord - vec2(sampleStep, 0.0)));
      float right = surfaceHeight(clampCoord(coord + vec2(sampleStep, 0.0)));
      float up = surfaceHeight(clampCoord(coord - vec2(0.0, sampleStep)));
      float down = surfaceHeight(clampCoord(coord + vec2(0.0, sampleStep)));
      return vec2(right - left, down - up) * 0.25;
    }

    float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

    vec3 computeContentNormal(vec2 coord) {
      float l = luma(content.eval(clampCoord(coord + vec2(1.0, 0.0))).rgb);
      float r = luma(content.eval(clampCoord(coord - vec2(1.0, 0.0))).rgb);
      float t = luma(content.eval(clampCoord(coord + vec2(0.0, 1.0))).rgb);
      float b = luma(content.eval(clampCoord(coord - vec2(0.0, 1.0))).rgb);
      vec2 grad = vec2(r - l, b - t);
      return normalize(vec3(grad, 1.0));
    }

    float edgeMask(float distToEdge) {
      if (edgeSoftness <= 0.0) return 1.0;
      float e = distToEdge / max(edgeSoftness, 0.0001);
      return clamp(e, 0.0, 1.0);
    }

    vec4 sampleChromaSimple(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationStrength <= 0.0001) return content.eval(clampCoord(coord));
      vec2 forward = clampCoord(coord + chromaOffset);
      vec2 backward = clampCoord(coord - chromaOffset);
      vec4 base = content.eval(clampCoord(coord));
      return vec4(content.eval(forward).r, base.g, content.eval(backward).b, base.a);
    }

    vec4 sampleChromaFull(vec2 coord, vec2 chromaOffset) {
      if (length(chromaOffset) < 0.0001) return content.eval(clampCoord(coord));
      vec4 color = vec4(0.0);
      vec4 red = content.eval(clampCoord(coord + chromaOffset));
      color.r += red.r / 3.5;
      color.a += red.a / 7.0;
      vec4 orange = content.eval(clampCoord(coord + chromaOffset * (2.0 / 3.0)));
      color.r += orange.r / 3.5;
      color.g += orange.g / 7.0;
      color.a += orange.a / 7.0;
      vec4 yellow = content.eval(clampCoord(coord + chromaOffset * (1.0 / 3.0)));
      color.r += yellow.r / 3.5;
      color.g += yellow.g / 3.5;
      color.a += yellow.a / 7.0;
      vec4 green = content.eval(clampCoord(coord));
      color.g += green.g / 3.5;
      color.a += green.a / 7.0;
      vec4 cyan = content.eval(clampCoord(coord - chromaOffset * (1.0 / 3.0)));
      color.g += cyan.g / 3.5;
      color.b += cyan.b / 3.0;
      color.a += cyan.a / 7.0;
      vec4 blue = content.eval(clampCoord(coord - chromaOffset * (2.0 / 3.0)));
      color.b += blue.b / 3.0;
      color.a += blue.a / 7.0;
      vec4 purple = content.eval(clampCoord(coord - chromaOffset));
      color.r += purple.r / 7.0;
      color.b += purple.b / 3.0;
      color.a += purple.a / 7.0;
      return color;
    }

    vec4 sampleChroma(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationMode == 1) {
        return sampleChromaFull(coord, chromaOffset);
      }
      return sampleChromaSimple(coord, chromaOffset);
    }

    vec4 main(vec2 coord) {
      vec4 base = content.eval(coord);
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);

      float sd = sdRoundedRect(centeredCoord, halfSize, radius);
      float distToEdge = max(-sd, 0.0);

      float h = surfaceHeight(coord);
      float refractionZone = max(refractionHeight, 0.0001);
      float heightNorm = clamp(h / refractionZone, 0.0, 1.0);
      float displacementMagnitude = -heightNorm * refractionStrength * 12.0;

      float smoothRadius = max(radius * 1.5, 30.0);
      float gradRadius = min(smoothRadius, min(halfSize.x, halfSize.y));
      vec2 refractionDir = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depth * normalize(centeredCoord)
      );
      vec2 displacement = refractionDir * displacementMagnitude;
      vec2 refractCoord = clampCoord(coord + displacement);

      vec2 chromaOffset = displacement * chromaticAberrationStrength * 0.5;
      vec4 refracted = sampleChroma(refractCoord, chromaOffset);
      vec4 blurred = blurredContent.eval(refractCoord);

      vec2 grad = surfaceGradient(coord);
      vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));
      vec3 contentNormal = computeContentNormal(coord);
      vec3 normal = normalize(mix(shapeNormal, contentNormal, 0.15));

      vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));
      vec3 tinted = mix(mixedColor, tintColor.rgb, tintColor.a);
      vec2 lightDir2D = normalize(lightPosition - coord);
      vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
      float spec = pow(max(dot(normal, lightDir), 0.0), 24.0) * specularIntensity;
      float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), 3.0);
      float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));
      vec3 finalColor = mix(tinted, refracted.rgb, refractionStrength) * ambient + spec;
      float edge = edgeMask(distToEdge);
      return vec4(finalColor, base.a) * edge;
    }
    """
  }
}
```

Notes for the implementer:
- Do not change public Kotlin API or defaults in this task.
- Do not reuse the previously reverted proxy-radius height-field approach.
- Keep `surfaceGradient(coord)` and specular normal path intact so the experiment isolates refraction direction.
- Use `depth` directly as the center-bias scalar for this experiment; do not add a new uniform.

- [ ] **Step 2: Run the regression to verify it passes**

Run: `./gradlew :haze-liquidglass:jvmTest --tests "dev.chrisbanes.haze.liquidglass.LiquidGlassShadersTest"`

Expected: PASS with `1 test completed, 0 failed`.

- [ ] **Step 3: Run compile checks for affected targets**

Run: `./gradlew :haze-liquidglass:compileDebugKotlinAndroid :haze-liquidglass:compileKotlinJvm :haze-liquidglass:compileKotlinIosArm64`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit the shader experiment**

```bash
git add \
  haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShaders.kt \
  haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassShadersTest.kt
git commit -m "experiment: try qmdeve-style liquid glass refraction direction"
```

### Task 3: Regenerate And Review Liquid Glass Baselines

**Files:**
- Modify: `haze-screenshot-tests/screenshots/android/LiquidGlass*.png`
- Modify: `haze-screenshot-tests/screenshots/desktop/LiquidGlass*.png`
- Reference: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt`
- Reference: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassContentScreenshotTest.kt`

- [ ] **Step 1: Record Android baselines**

Run: `./gradlew :haze-screenshot-tests:recordRoborazziDebug`

Expected: `BUILD SUCCESSFUL` and updated files under `haze-screenshot-tests/screenshots/android/`.

- [ ] **Step 2: Record JVM baselines**

Run: `./gradlew :haze-screenshot-tests:recordRoborazziJvm`

Expected: `BUILD SUCCESSFUL` and updated files under `haze-screenshot-tests/screenshots/desktop/`.

- [ ] **Step 3: Inspect the diff scope**

Run: `git diff --stat -- "haze-screenshot-tests/screenshots/android/LiquidGlass*" "haze-screenshot-tests/screenshots/desktop/LiquidGlass*"`

Expected: only Liquid Glass screenshot baselines show binary changes.

- [ ] **Step 4: Verify formatting for the changed module**

Run: `./gradlew :haze-liquidglass:spotlessCheck`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the regenerated baselines**

```bash
git add \
  haze-screenshot-tests/screenshots/android \
  haze-screenshot-tests/screenshots/desktop
git commit -m "test: regenerate liquid glass baselines for direction experiment"
```

### Task 4: Summarize Visual Outcome And Decide Next Iteration

**Files:**
- Reference: `haze-screenshot-tests/screenshots/android/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Reference: `haze-screenshot-tests/screenshots/android/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Reference: `haze-screenshot-tests/screenshots/desktop/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png`
- Reference: `haze-screenshot-tests/screenshots/desktop/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png`

- [ ] **Step 1: Re-check the seam-focused baselines**

Inspect these outputs first:

```text
haze-screenshot-tests/screenshots/android/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png
haze-screenshot-tests/screenshots/android/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png
haze-screenshot-tests/screenshots/desktop/LiquidGlassScreenshotTest.creditCard_shape_refractionHeight_rounded.png
haze-screenshot-tests/screenshots/desktop/LiquidGlassContentScreenshotTest.creditCard_shape_refractionHeight_rounded.png
```

Decision rule:
- If triangular seams are reduced and rectangles still look rectangular, keep the experiment for further tuning.
- If seams remain unchanged, the next experiment should reduce reliance on `step(...)` in `gradSdRoundedRect`.
- If rectangles begin to pill out again, reduce or gate the `max(radius * 1.5, 30.0)` floor before trying anything broader.

- [ ] **Step 2: Capture the outcome in the final notes**

Write the session handoff with:
- whether seams improved
- whether rectangle silhouettes stayed intact
- which screenshots best show the result

- [ ] **Step 3: Do not add more code in the same pass**

Stop after visual evaluation. If another shader tweak is needed, start a fresh red-green cycle instead of stacking more changes into this experiment.

## Self-Review

- Spec coverage: This plan covers the approved QmDeve-style direction-only experiment, keeps exact-shape masking and sampled-height specular normal, adds a failing regression first, verifies Android/JVM/iOS compilation, and regenerates Android/JVM baselines.
- Placeholder scan: No `TODO`, `TBD`, or generic “add tests” placeholders remain; each code-changing step includes explicit target code and commands.
- Type consistency: The plan uses the current shader names (`LiquidGlassShaders`, `surfaceGradient`, `surfaceHeight`, `depth`, `cornerRadii`) and only introduces `gradSdRoundedRect`, `smoothRadius`, `gradRadius`, `refractionDir`, and `displacementMagnitude` consistently inside the shader string.

Plan complete and saved to `docs/superpowers/plans/2026-05-13-liquid-glass-qmdeve-direction-experiment.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
