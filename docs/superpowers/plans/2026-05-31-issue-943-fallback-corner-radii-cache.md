# Issue 943: Liquid glass fallback path cache retains stale corner radii

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the `FallbackLiquidGlassDelegate` path cache so it invalidates when actual corner radii change, not just when the zero-radius boolean flips.

**Architecture:** The delegate currently caches `cachedShapePath` using only `size` and `cachedRadiiIsZero`. Switching between two rounded shapes at the same size (or changing layout direction with asymmetric corners) therefore reuses the stale path. Replace the boolean cache field with a full `CornerRadii` instance and compare the resolved radii directly.

**Tech Stack:** Kotlin Multiplatform, Compose UI, Roborazzi screenshot tests

---

## File Map

| File | Responsibility |
|------|----------------|
| `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/FallbackLiquidGlassDelegate.kt` | The delegate with the buggy cache logic. |
| `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt` | Screenshot tests that exercise shape changes and layout-direction swaps. |

---

### Task 1: Fix cache invalidation in FallbackLiquidGlassDelegate

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/FallbackLiquidGlassDelegate.kt`

- [ ] **Step 1: Replace boolean cache with full CornerRadii cache**

Change the cached fields from `cachedRadiiIsZero: Boolean` to `cachedRadii: CornerRadii`, and update the invalidation check to compare the resolved `CornerRadii` values.

```kotlin
internal class FallbackLiquidGlassDelegate(
  private val effect: LiquidGlassVisualEffect,
) : LiquidGlassVisualEffect.Delegate {

  private var cachedShapePath: Path? = null
  private var cachedSize: Size = Size.Zero
  private var cachedRadii: CornerRadii = CornerRadii(0f, 0f, 0f, 0f)

  override fun DrawScope.draw(context: VisualEffectContext) {
    val tint = effect.tint
    if (!tint.isSpecified) return

    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val edgeSoftnessPx = with(context.requireDensity()) { effect.edgeSoftness.toPx() }
    val highlightCenter = effect.lightPosition.takeUnless { it == Offset.Unspecified }
      ?: Offset(size.width / 2f, size.height / 3f)

    val radii = effect.shape.toCornerRadiiPx(layerSize = size, density = density, layoutDirection = layoutDirection)

    if (size != cachedSize || radii != cachedRadii) {
      cachedSize = size
      cachedRadii = radii
      cachedShapePath = if (!radii.isZero()) {
        radii.toRoundRect(size).let { Path().apply { addRoundRect(it) } }
      } else {
        null
      }
    }
    val shapePath = cachedShapePath
    // ... rest of draw body unchanged
```

- [ ] **Step 2: Run Spotless**

```bash
./gradlew spotlessApply
```

Expected: Formatting applied, no errors.

- [ ] **Step 3: Commit**

```bash
git add haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/FallbackLiquidGlassDelegate.kt
git commit -m "fix: include resolved CornerRadii in fallback path cache invalidation (#943)"
```

---

### Task 2: Add screenshot test for shape change without size change

**Files:**
- Modify: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt`

- [ ] **Step 1: Add test that switches between two non-zero rounded shapes at the same size**

Append the following test to `LiquidGlassScreenshotTest`:

```kotlin
@Test
fun creditCard_shape_change_sameSize() = runScreenshotTest {
  val visualEffect = LiquidGlassVisualEffect().apply {
    tint = DefaultTint
    shape = RoundedCornerShape(24.dp)
  }

  setContent {
    ScreenshotTheme {
      CreditCardSample(visualEffect = visualEffect, shape = RoundedCornerShape(24.dp))
    }
  }

  captureRoot("24dp")

  visualEffect.shape = RoundedCornerShape(8.dp)
  waitForIdle()
  captureRoot("8dp")
}
```

- [ ] **Step 2: Run the new test to verify it passes**

```bash
./gradlew :haze-screenshot-tests:test --tests "dev.chrisbanes.haze.LiquidGlassScreenshotTest.creditCard_shape_change_sameSize"
```

Expected: Test passes and generates reference screenshots.

- [ ] **Step 3: Commit**

```bash
git add haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt
git commit -m "test: add screenshot test for fallback shape cache with same-size radii change (#943)"
```

---

### Task 3: Add screenshot test for LTR/RTL asymmetric corners

**Files:**
- Modify: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt`

- [ ] **Step 1: Add test that toggles layout direction with asymmetric corners**

Append the following test to `LiquidGlassScreenshotTest`:

```kotlin
@Test
fun creditCard_shape_rtl_asymmetric() = runScreenshotTest {
  val visualEffect = LiquidGlassVisualEffect().apply {
    tint = DefaultTint
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 0.dp)
  }

  setContent {
    ScreenshotTheme {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        CreditCardSample(
          visualEffect = visualEffect,
          shape = RoundedCornerShape(topStart = 24.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 0.dp),
        )
      }
    }
  }

  captureRoot("ltr")

  setContent {
    ScreenshotTheme {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CreditCardSample(
          visualEffect = visualEffect,
          shape = RoundedCornerShape(topStart = 24.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 0.dp),
        )
      }
    }
  }

  waitForIdle()
  captureRoot("rtl")
}
```

Make sure the required imports are present at the top of the file:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
```

- [ ] **Step 2: Run the new test to verify it passes**

```bash
./gradlew :haze-screenshot-tests:test --tests "dev.chrisbanes.haze.LiquidGlassScreenshotTest.creditCard_shape_rtl_asymmetric"
```

Expected: Test passes and generates reference screenshots.

- [ ] **Step 3: Commit**

```bash
git add haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt
git commit -m "test: add screenshot test for fallback shape cache with LTR/RTL asymmetric corners (#943)"
```

---

### Task 4: Verify full suite

- [ ] **Step 1: Run the screenshot test class**

```bash
./gradlew :haze-screenshot-tests:test --tests "dev.chrisbanes.haze.LiquidGlassScreenshotTest"
```

Expected: All tests pass.

- [ ] **Step 2: Run the liquid-glass module unit tests**

```bash
./gradlew :haze-liquidglass:test
```

Expected: All tests pass.

- [ ] **Step 3: Apply Spotless one final time**

```bash
./gradlew spotlessApply
```

- [ ] **Step 4: Commit any Spotless changes**

```bash
git add -A && git commit -m "Apply Spotless" || echo "Nothing to commit"
```

---

## Spec Coverage Checklist

| Requirement | Task |
|-------------|------|
| Include resolved `CornerRadii` in cache invalidation | Task 1 |
| Account for layout direction through resolved radii | Task 1 (layout direction already flows into `toCornerRadiiPx`, now the resulting radii are compared) |
| Test switching between non-zero radii without size change | Task 2 |
| Test LTR ↔ RTL asymmetric corners without size change | Task 3 |

## Placeholder Scan

- No "TBD", "TODO", or "fill in later" entries.
- Every code block contains the full content needed.
- Every command includes the expected outcome.
- Exact file paths are used throughout.

---

**Plan complete.** Two execution options:

1. **Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using `executing-plans`, batch execution with checkpoints.

Which approach would you prefer?
