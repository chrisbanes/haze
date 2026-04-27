# Haze Performance Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate four hot-path performance issues in the Haze rendering pipeline: per-frame GraphicsLayer allocation, unnecessary recomputation in `updateEffect()`, spurious snapshot observation edges, and cascading pre-draw invalidations.

**Architecture:** All four fixes are internal to the core rendering nodes and blur delegate with zero API surface changes. Fix 1 adds a persistent layer field to `RenderEffectBlurVisualEffectDelegate`. Fix 2 extends the existing `Bitmask` dirty tracking in `HazeEffectNode` to gate expensive sections. Fix 3 wraps a single read in `Snapshot.withoutReadObservation`. Fix 4 adds a boolean debounce gate for pre-draw listeners.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, `DrawModifierNode`, `GraphicsLayer`

---

### Task 1: Fix 2 (Part A) — Add `canDrawArea` dirty tracking

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`

- [ ] **Step 1: Add `DirtyFields.Areas` to `canDrawArea` setter**

The current setter at line 163-169 does not add any dirty flag. This means `canDrawArea` changes are not tracked, and our upcoming gating would miss them.

Change the setter from:

```kotlin
override var canDrawArea: ((HazeArea) -> Boolean)? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "canDrawArea changed. Current $field. New: $value" }
        field = value
      }
    }
```

To:

```kotlin
override var canDrawArea: ((HazeArea) -> Boolean)? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "canDrawArea changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Areas
        field = value
      }
    }
```

- [ ] **Step 2: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt
git commit -m "Add dirty tracking to canDrawArea setter for fix 2 preparation"
```

---

### Task 2: Fix 2 (Part B) — Gate `updateEffect()` sections behind dirty flags

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`

- [ ] **Step 1: Create composite dirty field masks**

After the existing `DirtyFields` object (around line 571), add two new constants:

```kotlin
/** Dirty fields that warrant recomputing layer bounds. */
internal val LayerBoundsDirtyFields: Int =
  ScreenPosition or
    Size or
    Areas or
    ExpandLayer or
    ClipToAreas

/** Dirty fields that warrant recomputing area offsets. */
internal val AreaOffsetsDirtyFields: Int =
  ScreenPosition or Areas
```

- [ ] **Step 2: Gate area filtering + `findNearestAncestor`**

In `updateEffect()` at lines 371-403, wrap the area listener removal, filtering, sorting, and `_areas` assignment in a dirty check. Replace:

```kotlin
    _areas.forEach { area ->
      // Remove our pre draw listener from the current areas
      area.preDrawListeners -= areaPreDrawListener
    }

    _areas = if (backgroundBlurring) {
      val ancestorSourceNode =
        (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)
          ?.takeIf { it.state == this.state }

      state?.areas.orEmpty()
        .also {
          HazeLogger.d(TAG) { "Background Areas observing: $it" }
        }
        .asSequence()
        .filter { area ->
          val filter = canDrawArea
          when {
            filter != null -> filter(area)
            ancestorSourceNode != null -> area.zIndex < ancestorSourceNode.zIndex
            else -> true
          }.also { included ->
            HazeLogger.d(TAG) { "Background Area: $area. Included=$included" }
          }
        }
        .toMutableList()
        .apply { sortBy(HazeArea::zIndex) }
    } else {
      contentDrawArea.size = size
      contentDrawArea.position = position
      contentDrawArea.windowId = windowId
      listOf(contentDrawArea)
    }
```

With:

```kotlin
    if (DirtyFields.Areas in dirtyTracker) {
      _areas.forEach { area ->
        // Remove our pre draw listener from the current areas
        area.preDrawListeners -= areaPreDrawListener
      }

      _areas = if (backgroundBlurring) {
        val ancestorSourceNode =
          (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)
            ?.takeIf { it.state == this.state }

        state?.areas.orEmpty()
          .also {
            HazeLogger.d(TAG) { "Background Areas observing: $it" }
          }
          .asSequence()
          .filter { area ->
            val filter = canDrawArea
            when {
              filter != null -> filter(area)
              ancestorSourceNode != null -> area.zIndex < ancestorSourceNode.zIndex
              else -> true
            }.also { included ->
              HazeLogger.d(TAG) { "Background Area: $area. Included=$included" }
            }
          }
          .toMutableList()
          .apply { sortBy(HazeArea::zIndex) }
      } else {
        contentDrawArea.size = size
        contentDrawArea.position = position
        contentDrawArea.windowId = windowId
        listOf(contentDrawArea)
      }
    }
```

- [ ] **Step 3: Gate `updateAreaOffsets()`**

At line 488, wrap the call with a dirty check. Change:

```kotlin
    updateAreaOffsets()
```

To:

```kotlin
    if (dirtyTracker.any(AreaOffsetsDirtyFields)) {
      updateAreaOffsets()
    }
```

- [ ] **Step 4: Gate layer bounds calculation**

At lines 429-464, wrap the entire layer bounds block in a dirty check. Change:

```kotlin
    if (backgroundBlurring && areas.isNotEmpty() && size.isSpecified && position.isSpecified) {
      // Now we clip the expanded layer bounds...
      ...
      _layerSize = ...
      _layerOffset = ...
    } else if (!backgroundBlurring && size.isSpecified && !visualEffect.shouldClip() && shouldExpandLayer()) {
      ...
      _layerSize = ...
      _layerOffset = ...
    } else {
      _layerSize = size
      _layerOffset = Offset.Zero
    }
```

To:

```kotlin
    if (dirtyTracker.any(LayerBoundsDirtyFields)) {
      if (backgroundBlurring && areas.isNotEmpty() && size.isSpecified && position.isSpecified) {
        ...
        _layerSize = ...
        _layerOffset = ...
      } else if (!backgroundBlurring && size.isSpecified && !visualEffect.shouldClip() && shouldExpandLayer()) {
        ...
        _layerSize = ...
        _layerOffset = ...
      } else {
        _layerSize = size
        _layerOffset = Offset.Zero
      }
    }
```

- [ ] **Step 5: Add import for `any`**

Ensure `Bitmask.any()` is imported. Check line 14 — `import dev.chrisbanes.haze.Bitmask` is at line 20 with other imports. The `any` extension on `Bitmask` is already available since `Bitmask` is imported. Verify nothing else is needed.

- [ ] **Step 6: Run build to verify compilation**

Run: `./gradlew :haze:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt
git commit -m "Gate expensive updateEffect sections behind dirty flag checks"
```

---

### Task 3: Fix 3 — Wrap `contentLayer` read in `Snapshot.withoutReadObservation`

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt`

- [ ] **Step 1: Wrap the snapshot read**

At lines 191-193, the `area.contentLayer` read inside `layer.record {}` creates unintended snapshot observation edges. Change:

```kotlin
            val areaLayer = area.contentLayer
              ?.takeUnless { it.isReleased }
              ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
```

To:

```kotlin
            val areaLayer = Snapshot.withoutReadObservation {
              area.contentLayer
                ?.takeUnless { it.isReleased }
                ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
            }
```

`Snapshot` is already imported at line 8.

- [ ] **Step 2: Run build to verify compilation**

Run: `./gradlew :haze-blur:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt
git commit -m "Remove spurious snapshot observation inside layer.record block"
```

---

### Task 4: Fix 4 — Debounce pre-draw listener invalidations

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`

- [ ] **Step 1: Add debounce flag**

After line 68 (`internal var dirtyTracker = Bitmask()`), add:

```kotlin
  private var needsPreDrawInvalidation = false
```

- [ ] **Step 2: Update pre-draw listener to use the gate**

Change the `areaPreDrawListener` lazy block at lines 218-220:

```kotlin
  private val areaPreDrawListener by lazy(LazyThreadSafetyMode.NONE) {
    OnPreDrawListener(::invalidateDraw)
  }
```

To:

```kotlin
  private val areaPreDrawListener by lazy(LazyThreadSafetyMode.NONE) {
    OnPreDrawListener {
      if (!needsPreDrawInvalidation) {
        needsPreDrawInvalidation = true
        invalidateDraw()
      }
    }
  }
```

- [ ] **Step 3: Reset the flag in `onPostDraw()`**

At lines 469-471, change:

```kotlin
  private fun onPostDraw() {
    dirtyTracker = Bitmask()
  }
```

To:

```kotlin
  private fun onPostDraw() {
    dirtyTracker = Bitmask()
    needsPreDrawInvalidation = false
  }
```

- [ ] **Step 4: Run build to verify compilation**

Run: `./gradlew :haze:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt
git commit -m "Debounce pre-draw invalidation to once per frame"
```

---

### Task 5: Fix 1 — Persistent GraphicsLayer in blur draw path

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurRenderEffectVisualEffect.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt`

- [ ] **Step 1: Add persistent layer fields to `RenderEffectBlurVisualEffectDelegate`**

In `BlurRenderEffectVisualEffect.kt`, after line 25 (`private var renderEffect: RenderEffect? = null`), add:

```kotlin
  private var scaledContentLayer: GraphicsLayer? = null
  private var lastScaledLayerSize: Size? = null
```

- [ ] **Step 2: Add `detach()` override to release the cached layer**

After the `draw` method (line 49), add:

```kotlin
  override fun detach() {
    scaledContentLayer?.let { layer ->
      layer.release()
    }
    scaledContentLayer = null
    lastScaledLayerSize = null
  }
```

- [ ] **Step 3: Modify `draw()` to pass persistent layer through**

Change the `draw` method (lines 27-48) from:

```kotlin
  override fun DrawScope.draw(context: VisualEffectContext) {
    createAndDrawScaledContentLayer(context = context) { layer ->
      val p = blurVisualEffect.progressive
      if (p != null) {
        drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
          context = context,
        )
      } else {
        // First make sure that the RenderEffect is updated (if necessary)
        updateRenderEffectIfDirty(context)

        layer.renderEffect = renderEffect
        layer.alpha = blurVisualEffect.alpha

        // Since we included a border around the content, we need to translate so that
        // we don't see it (but it still affects the RenderEffect)
        drawLayer(layer)
      }
    }
  }
```

To:

```kotlin
  override fun DrawScope.draw(context: VisualEffectContext) {
    // Calculate scaled layer size to detect size changes (needs re-allocation)
    val scaleFactor = blurVisualEffect.calculateInputScaleFactor(context.inputScale)
    val currentScaledSize = (context.layerSize * scaleFactor).roundToIntSize().let {
      Size(it.width.toFloat(), it.height.toFloat())
    }

    // Allocate the scaled content layer once, re-recording into it each frame
    if (scaledContentLayer == null || scaledContentLayer!!.isReleased || lastScaledLayerSize != currentScaledSize) {
      scaledContentLayer?.let { it.release() }
      scaledContentLayer = context.requireGraphicsContext().createGraphicsLayer()
      lastScaledLayerSize = currentScaledSize
    }

    val layer = scaledContentLayer!!

    // Always re-record — the source area layers change each frame during scrolling.
    // Returns null when the scaled size is 0 (e.g., window minimized).
    val resultLayer = createScaledContentLayer(
      context = context,
      scaleFactor = scaleFactor,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      backgroundColor = blurVisualEffect.backgroundColor,
      existingLayer = layer,
    ) ?: return

    val clip = blurVisualEffect.shouldClip()
    resultLayer.clip = clip

    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clip,
    ) {
      val p = blurVisualEffect.progressive
      if (p != null) {
        drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
          context = context,
        )
      } else {
        updateRenderEffectIfDirty(context)
        layer.renderEffect = renderEffect
        layer.alpha = blurVisualEffect.alpha
        drawLayer(layer)
      }
    }
  }
```

- [ ] **Step 4: Add `existingLayer` parameter to `createScaledContentLayer`**

In `BlurHelpers.kt`, change the `createScaledContentLayer` function signature (line 158) from:

```kotlin
internal fun DrawScope.createScaledContentLayer(
  context: VisualEffectContext,
  backgroundColor: Color,
  scaleFactor: Float,
  layerSize: Size,
  layerOffset: Offset,
): GraphicsLayer? {
```

To:

```kotlin
internal fun DrawScope.createScaledContentLayer(
  context: VisualEffectContext,
  backgroundColor: Color,
  scaleFactor: Float,
  layerSize: Size,
  layerOffset: Offset,
  existingLayer: GraphicsLayer? = null,
): GraphicsLayer? {
```

- [ ] **Step 5: Use existing layer when provided**

In `createScaledContentLayer`, change lines 174-175:

```kotlin
  val graphicsContext = context.requireGraphicsContext()
  val layer = graphicsContext.createGraphicsLayer()
```

To:

```kotlin
  val graphicsContext = context.requireGraphicsContext()
  val layer = existingLayer?.takeUnless { it.isReleased }
    ?: graphicsContext.createGraphicsLayer()
```

When `existingLayer` is provided and not released, it is reused. The `record()` call at line 177 replaces its previous drawing commands.

- [ ] **Step 6: Add necessary imports to `BlurRenderEffectVisualEffect.kt`**

Add `Size` and `roundToIntSize` imports since the size calculation needs them. Ensure these are present (check existing imports): `Size` is not currently imported, `roundToIntSize` is not currently imported.

Add at the top with other imports:

```kotlin
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.roundToIntSize
```

- [ ] **Step 7: Run build to verify compilation**

Run: `./gradlew :haze-blur:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurRenderEffectVisualEffect.kt haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt
git commit -m "Reuse GraphicsLayer across frames instead of allocating per-draw"
```

---

### Task 6: Full build verification & screenshot tests

**Files:** None (verification only)

- [ ] **Step 1: Run full multi-platform build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL across all platforms

- [ ] **Step 2: Run screenshot tests**

Run: `./gradlew :haze-screenshot-tests:test`
Expected: All screenshot tests pass (no visual regression)

- [ ] **Step 3: If snapshot tests fail due to visual changes, regenerate**

Only if tests fail with visual differences:

```bash
./gradlew :haze-screenshot-tests:recordRoborazzi
```

Then re-run tests to confirm they pass.

---

### Task 7: Final commit

- [ ] **Step 1: Commit any remaining changes**

```bash
git add -A
git commit -m "Performance: persistent GraphicsLayer, dirty-gated updateEffect, debounce pre-draw, fix snapshot edges"
```
