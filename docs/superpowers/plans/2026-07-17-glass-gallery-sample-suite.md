# Glass Gallery Sample Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing Glass credit-card and debug samples with a deterministic, responsive Product, Playground, and Lab suite designed for polished demo recording.

**Architecture:** Build a small shared Gallery foundation for poster data, diagnostic backdrops, Glass surfaces, recording chrome, Lab presets, and a pure normalized Playground timeline. Each route then owns only local UI state and passes immutable values plus callbacks to a plain content composable; frame-rate values cross boundaries as provider lambdas and are read in layout, draw, or `snapshotFlow` rather than the screen composition.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform Foundation and Material 3, Navigation Compose, Haze Glass, Compose UI tests, Roborazzi, Robolectric, Skiko Compose UI tests, Gradle.

## Global Constraints

- Replace `Glass` and `Glass (Debug)` with exactly `Glass — Product`, `Glass — Playground`, and `Glass — Lab`.
- Do not change Glass rendering, public APIs, defaults, delegates, or fallback behavior.
- Keep all artwork deterministic and local: no Coil, network, current time, random values, or platform-only assets.
- Product uses `GlassOptics.Adaptive` exclusively; literal optics belong only in Playground and Lab.
- Playground uses fixed material shapes, a seamless 12-second loop, pause/reset, and direct dragging; never morph or deform a Glass boundary.
- Portrait and landscape layouts preserve bounded Glass proportions instead of stretching surfaces to fill the window.
- Keep state local to the sample module; do not add ViewModels, repositories, Flows, dependency injection, or persistence.
- Read frame-rate values in layout/draw or `snapshotFlow`; do not pass changing scalar offsets through the whole composition tree.
- Keep the existing `CreditCardScene` and Blur credit-card sample.
- Record and verify both Android API 35 and Desktop baselines with fixed artwork and fixed Playground progress.
- Prefix every repository shell command with `rtk`.

---

## File Structure

### Shared sample production files

- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassGalleryModels.kt`: immutable poster data, backdrop identifiers, Lab preset identifiers/styles, and Lab state transitions.
- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassGalleryVisuals.kt`: deterministic backdrop drawing, reusable `GlassSurface`, and shared `DemoChrome`.
- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassProductSample.kt`: Product route state owner and plain Product UI.
- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundTimeline.kt`: pure normalized choreography and surface style selection.
- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt`: autoplay/drag controller, Playground route, and plain Playground UI.
- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassLabSample.kt`: Lab route, specimen, preset/backdrop selectors, and grouped advanced controls.
- Modify `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt`: register the three replacement destinations.
- Delete `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassSample.kt` and `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassDebugSample.kt` after their replacements compile.

### Sample tests

- Create `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassGalleryModelsTest.kt`.
- Create `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassGalleryVisualsTest.kt`.
- Create `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassProductSampleTest.kt`.
- Create `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundTimelineTest.kt`.
- Create `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSampleTest.kt`.
- Create `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassLabSampleTest.kt`.
- Modify `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/CreditCardSamplesTest.kt`.
- Modify `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/SamplesTest.kt`.

### Screenshot coverage

- Modify `haze-screenshot-tests/build.gradle.kts`: add a `commonTest` dependency on `projects.sample.shared`.
- Modify `internal/screenshot-test/src/jvmMain/kotlin/dev/chrisbanes/haze/test/ScreenshotTest.jvm.kt`: add a Desktop-only viewport-size overload.
- Create `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt`: shared fixed-state capture helpers.
- Create `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt`.
- Create `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt`.
- Create `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt`.
- Record new WebP baselines under `haze-screenshot-tests/screenshots/android/` and `haze-screenshot-tests/screenshots/desktop/`.

---

### Task 1: Add Gallery Models and Lab Presets

**Files:**
- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassGalleryModels.kt`
- Create: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassGalleryModelsTest.kt`

**Interfaces:**
- Consumes: `GlassDefaults.style`, `GlassOptics`, `GlassStyle`, `GlassLighting`, `GlassColor`, `GlassRendering`, `ChromaticAberrationMode`, and `SurfaceProfile` from `:haze-glass`.
- Produces: `GalleryArtworks`, `GlassGalleryBackdropId`, `GlassLabPresetId`, `SelectableGlassLabPresets`, `glassLabPresetStyle(id)`, and immutable `GlassLabState` transitions used by every later sample task.

- [ ] **Step 1: Write failing model and preset tests**

Create `GlassGalleryModelsTest.kt` with the concrete contracts:

```kotlin
package dev.chrisbanes.haze.sample

import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GlassGalleryModelsTest {
  @Test
  fun galleryArtworks_haveStableUniqueIds() {
    assertEquals(4, GalleryArtworks.size)
    assertEquals(4, GalleryArtworks.map { it.id }.toSet().size)
    assertTrue(GalleryArtworks.all { it.title.isNotBlank() && it.description.isNotBlank() })
  }

  @Test
  fun adaptivePreset_usesBuiltInAdaptiveOptics() {
    assertEquals(GlassOptics.Adaptive, glassLabPresetStyle(GlassLabPresetId.Adaptive).optics)
  }

  @Test
  fun literalPresets_haveDistinctCompleteOptics() {
    val clear = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Clear).optics)
    val frosted = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Frosted).optics)
    val deep = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Deep).optics)
    val prism = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Prism).optics)

    assertTrue(clear.blurRadius < frosted.blurRadius)
    assertTrue(clear.depth < deep.depth)
    assertNotEquals(deep, prism)
    assertEquals(
      ChromaticAberrationMode.Full,
      glassLabPresetStyle(GlassLabPresetId.Prism).rendering.chromaticAberrationMode,
    )
    assertEquals(
      0.22f,
      glassLabPresetStyle(GlassLabPresetId.Prism).rendering.chromaticAberrationStrength,
    )
  }

  @Test
  fun editingAdaptiveStyle_changesSelectionToCustomAndLiteralOptics() {
    val edited = GlassLabState().editStyle { style ->
      style.copy(optics = GlassOptics.Absolute(refractionStrength = 0.6f))
    }

    assertEquals(GlassLabPresetId.Custom, edited.preset)
    assertIs<GlassOptics.Absolute>(edited.style.optics)
  }

  @Test
  fun reset_restoresCompleteInitialLabState() {
    val changed = GlassLabState(
      preset = GlassLabPresetId.Prism,
      backdrop = GlassGalleryBackdropId.Grid,
      advancedExpanded = true,
      style = glassLabPresetStyle(GlassLabPresetId.Prism),
    )

    assertEquals(GlassLabState(), changed.reset())
  }
}
```

- [ ] **Step 2: Run the tests and verify the new contracts fail to compile**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassGalleryModelsTest
```

Expected: FAIL because `GalleryArtworks`, the public identifiers, preset mapping, and `GlassLabState` do not exist.

- [ ] **Step 3: Implement immutable poster data and stable identifiers**

Create `GlassGalleryModels.kt` with these declarations and exact poster values:

```kotlin
@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile

@Immutable
internal data class GalleryArtwork(
  val id: String,
  val title: String,
  val subtitle: String,
  val description: String,
  val colors: List<Color>,
  val accent: Color,
  val foreground: Color,
)

internal val GalleryArtworks = listOf(
  GalleryArtwork(
    id = "chromatic-bloom",
    title = "Chromatic Bloom",
    subtitle = "Studies in refracted colour",
    description = "A magenta and cyan poster with concentric circles and fine grid lines",
    colors = listOf(Color(0xFF4B1FFF), Color(0xFFFF3D9A), Color(0xFFFFB44A)),
    accent = Color(0xFF72F5FF),
    foreground = Color.White,
  ),
  GalleryArtwork(
    id = "signal-garden",
    title = "Signal Garden",
    subtitle = "Organic systems, synthetic light",
    description = "An emerald and ultraviolet poster with vertical signal bars",
    colors = listOf(Color(0xFF041E1A), Color(0xFF00B979), Color(0xFF9A63FF)),
    accent = Color(0xFFE8FF5A),
    foreground = Color.White,
  ),
  GalleryArtwork(
    id = "blue-hour",
    title = "Blue Hour",
    subtitle = "Quiet geometry after sunset",
    description = "A deep blue poster with coral geometry and narrow horizontal rules",
    colors = listOf(Color(0xFF04133A), Color(0xFF0E67D1), Color(0xFF1FD6C5)),
    accent = Color(0xFFFF6B6B),
    foreground = Color.White,
  ),
  GalleryArtwork(
    id = "solar-type",
    title = "Solar Type",
    subtitle = "Letterforms in orbital motion",
    description = "A warm orange poster with black typography and electric blue details",
    colors = listOf(Color(0xFFFF4D00), Color(0xFFFFC400), Color(0xFFFFF1A8)),
    accent = Color(0xFF0057FF),
    foreground = Color(0xFF15100B),
  ),
)

public enum class GlassGalleryBackdropId {
  Gallery,
  Grid,
  Typography,
  Bands,
  Uniform,
}

public enum class GlassLabPresetId {
  Adaptive,
  Clear,
  Frosted,
  Deep,
  Prism,
  Custom,
}

internal val SelectableGlassLabPresets = GlassLabPresetId.entries - GlassLabPresetId.Custom
```

- [ ] **Step 4: Implement the exact Lab preset table and state transitions**

Append the following preset mapping and state type to `GlassGalleryModels.kt`:

```kotlin
private val GlassLabPresetStyles = mapOf(
  GlassLabPresetId.Adaptive to GlassDefaults.style,
  GlassLabPresetId.Clear to GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.06f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.85f,
      refractionHeight = 0.22f,
      refractionScale = 18f,
      depth = 0.1f,
      blurRadius = 2.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.55f,
      ambientResponse = 0.42f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = 0.08f,
      whitePoint = 0.02f,
      chromaMultiplier = 1.05f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 2.dp,
      contentNormalBlend = 0.15f,
      surfaceProfile = SurfaceProfile.Circle,
      chromaticAberrationStrength = 0f,
      chromaticAberrationMode = ChromaticAberrationMode.Simple,
    ),
  ),
  GlassLabPresetId.Frosted to GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.18f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.45f,
      refractionHeight = 0.18f,
      refractionScale = 10f,
      depth = 0.9f,
      blurRadius = 24.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.35f,
      ambientResponse = 0.55f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = -0.08f,
      whitePoint = 0.08f,
      chromaMultiplier = 0.72f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 8.dp,
      contentNormalBlend = 0.08f,
      surfaceProfile = SurfaceProfile.Circle,
      chromaticAberrationStrength = 0f,
      chromaticAberrationMode = ChromaticAberrationMode.Simple,
    ),
  ),
  GlassLabPresetId.Deep to GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.1f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.9f,
      refractionHeight = 0.32f,
      refractionScale = 20f,
      depth = 0.78f,
      blurRadius = 16.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.75f,
      ambientResponse = 0.62f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = 0.05f,
      whitePoint = 0.02f,
      chromaMultiplier = 1f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 10.dp,
      contentNormalBlend = 0.2f,
      surfaceProfile = SurfaceProfile.Squircle,
      chromaticAberrationStrength = 0.05f,
      chromaticAberrationMode = ChromaticAberrationMode.Simple,
    ),
  ),
  GlassLabPresetId.Prism to GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.08f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.82f,
      refractionHeight = 0.28f,
      refractionScale = 18f,
      depth = 0.35f,
      blurRadius = 8.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.72f,
      ambientResponse = 0.58f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = 0.1f,
      whitePoint = 0.02f,
      chromaMultiplier = 1.15f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 8.dp,
      contentNormalBlend = 0.18f,
      surfaceProfile = SurfaceProfile.Squircle,
      chromaticAberrationStrength = 0.22f,
      chromaticAberrationMode = ChromaticAberrationMode.Full,
    ),
  ),
)

public fun glassLabPresetStyle(id: GlassLabPresetId): GlassStyle = when (id) {
  GlassLabPresetId.Custom -> error("Custom style must come from GlassLabState")
  else -> requireNotNull(GlassLabPresetStyles[id])
}

@Immutable
internal data class GlassLabState(
  val preset: GlassLabPresetId = GlassLabPresetId.Adaptive,
  val backdrop: GlassGalleryBackdropId = GlassGalleryBackdropId.Gallery,
  val advancedExpanded: Boolean = false,
  val style: GlassStyle = glassLabPresetStyle(GlassLabPresetId.Adaptive),
) {
  fun selectPreset(id: GlassLabPresetId): GlassLabState {
    require(id != GlassLabPresetId.Custom)
    return copy(preset = id, style = glassLabPresetStyle(id))
  }

  fun editStyle(transform: (GlassStyle) -> GlassStyle): GlassLabState =
    copy(preset = GlassLabPresetId.Custom, style = transform(style))

  fun reset(): GlassLabState = GlassLabState()
}
```

- [ ] **Step 5: Run the focused tests**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassGalleryModelsTest
```

Expected: PASS with five tests.

- [ ] **Step 6: Commit the model and preset foundation**

```bash
rtk git add docs/superpowers/plans/2026-07-17-glass-gallery-sample-suite.md sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassGalleryModels.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassGalleryModelsTest.kt
rtk git commit -m "Add Glass Gallery models and presets"
```

### Task 2: Build Deterministic Backdrops, Glass Surfaces, and Demo Chrome

**Files:**
- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassGalleryVisuals.kt`
- Create: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassGalleryVisualsTest.kt`

**Interfaces:**
- Consumes: `GalleryArtwork`, `GalleryArtworks`, and `GlassGalleryBackdropId` from Task 1.
- Produces: `GalleryBackdrop(...)`, `GlassSurface(...)`, and `DemoChrome(...)` used by all three screens.

- [ ] **Step 1: Write the failing semantics test for optional chrome actions**

Create `GlassGalleryVisualsTest.kt`:

```kotlin
package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.rememberHazeState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassGalleryVisualsTest {
  @Test
  fun demoChrome_onlyShowsRequestedActions() = runComposeUiTest {
    setContent {
      val hazeState = rememberHazeState()
      Box(Modifier.fillMaxSize()) {
        GalleryBackdrop(
          hazeState = hazeState,
          artworkIndex = 0,
          backdrop = GlassGalleryBackdropId.Gallery,
          modifier = Modifier.fillMaxSize(),
        )
        DemoChrome(
          hazeState = hazeState,
          onBack = {},
          onEnterRecordingMode = {},
          isPlaying = true,
          onPlayPause = {},
          onReset = null,
        )
      }
    }

    onNodeWithContentDescription("Back").assertIsDisplayed()
    onNodeWithContentDescription("Enter recording mode").assertIsDisplayed()
    onNodeWithContentDescription("Pause animation").assertIsDisplayed()
    onNodeWithContentDescription("Reset demo").assertDoesNotExist()
  }
}
```

- [ ] **Step 2: Run the test and verify the visual foundation is missing**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassGalleryVisualsTest
```

Expected: FAIL because `GalleryBackdrop` and `DemoChrome` do not exist.

- [ ] **Step 3: Implement the deterministic backdrop renderer**

Create `GlassGalleryVisuals.kt`. Keep every pattern derived only from immutable inputs. Use this public contract and drawing structure:

```kotlin
@Composable
internal fun GalleryBackdrop(
  hazeState: HazeState,
  artworkIndex: Int,
  backdrop: GlassGalleryBackdropId,
  modifier: Modifier = Modifier,
  offsetProvider: () -> Float = { 0f },
) {
  val artwork = GalleryArtworks[artworkIndex.mod(GalleryArtworks.size)]
  Box(
    modifier = modifier
      .clipToBounds()
      .hazeSource(hazeState),
  ) {
    Canvas(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer { translationX = size.width * offsetProvider() },
    ) {
      when (backdrop) {
        GlassGalleryBackdropId.Gallery -> {
          drawRect(Brush.linearGradient(artwork.colors))
          repeat(12) { index ->
            val x = size.width * index / 11f
            drawLine(
              color = artwork.foreground.copy(alpha = 0.16f),
              start = Offset(x, 0f),
              end = Offset(size.width - x, size.height),
              strokeWidth = 1.dp.toPx(),
            )
          }
          drawCircle(
            color = artwork.accent.copy(alpha = 0.72f),
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.72f, size.height * 0.28f),
          )
        }
        GlassGalleryBackdropId.Grid -> {
          drawRect(Color(0xFF10131A))
          val spacing = 24.dp.toPx()
          var x = 0f
          while (x <= size.width) {
            drawLine(Color.White.copy(alpha = 0.24f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            x += spacing
          }
          var y = 0f
          while (y <= size.height) {
            drawLine(Color.White.copy(alpha = 0.24f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            y += spacing
          }
        }
        GlassGalleryBackdropId.Typography -> drawRect(Color(0xFF0057FF))
        GlassGalleryBackdropId.Bands -> {
          val bandHeight = size.height / artwork.colors.size
          artwork.colors.forEachIndexed { index, color ->
            drawRect(color, topLeft = Offset(0f, bandHeight * index), size = Size(size.width, bandHeight))
          }
        }
        GlassGalleryBackdropId.Uniform -> drawRect(artwork.colors.first())
      }
    }

    if (backdrop == GlassGalleryBackdropId.Gallery || backdrop == GlassGalleryBackdropId.Typography) {
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(32.dp),
      ) {
        Text(
          text = artwork.title.uppercase(),
          color = artwork.foreground,
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Black,
        )
        Text(
          text = artwork.subtitle,
          color = artwork.foreground.copy(alpha = 0.76f),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}
```

Use normal imports for the referenced Compose, Haze, geometry, graphics, and unit types. Do not introduce image loading or random decoration.

- [ ] **Step 4: Implement the reusable Glass boundary and chrome**

Add these exact interfaces to `GlassGalleryVisuals.kt`:

```kotlin
@Composable
internal fun GlassSurface(
  hazeState: HazeState,
  style: GlassStyle,
  shape: RoundedCornerShape,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier
      .clip(shape)
      .hazeEffect(state = hazeState) {
        glassEffect {
          this.style = style
          this.shape = shape
        }
      },
    content = content,
  )
}

@Composable
internal fun DemoChrome(
  hazeState: HazeState,
  onBack: () -> Unit,
  onEnterRecordingMode: () -> Unit,
  modifier: Modifier = Modifier,
  isPlaying: Boolean? = null,
  onPlayPause: (() -> Unit)? = null,
  onReset: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(24.dp)
  GlassSurface(
    hazeState = hazeState,
    style = GlassDefaults.style.copy(tint = Color.Black.copy(alpha = 0.08f)),
    shape = shape,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      if (isPlaying != null && onPlayPause != null) {
        IconButton(onClick = onPlayPause) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause animation" else "Play animation",
          )
        }
      }
      if (onReset != null) {
        IconButton(onClick = onReset) {
          Icon(Icons.Default.Replay, contentDescription = "Reset demo")
        }
      }
      IconButton(onClick = onEnterRecordingMode) {
        Icon(Icons.Default.VisibilityOff, contentDescription = "Enter recording mode")
      }
    }
  }
}
```

- [ ] **Step 5: Run visual foundation tests and formatting**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassGalleryVisualsTest
rtk ./gradlew spotlessApply
```

Expected: the focused test passes and Spotless formats the new Kotlin files.

- [ ] **Step 6: Commit the shared visual foundation**

```bash
rtk git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassGalleryVisuals.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassGalleryVisualsTest.kt
rtk git commit -m "Add Glass Gallery visual foundation"
```

### Task 3: Implement Glass — Product

**Files:**
- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassProductSample.kt`
- Create: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassProductSampleTest.kt`

**Interfaces:**
- Consumes: `GalleryArtworks`, `GalleryBackdrop`, `GlassSurface`, and `DemoChrome` from Tasks 1–2.
- Produces: `GlassProductSample(navController)` and public test/screenshot entry point `GlassProductSampleContent(...)`.

- [ ] **Step 1: Write failing Product behavior tests**

Create `GlassProductSampleTest.kt`:

```kotlin
package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GlassProductSampleTest {
  @Test
  fun productGlassStyle_alwaysUsesAdaptiveOptics() {
    assertEquals(GlassOptics.Adaptive, productGlassStyle(isDark = false).optics)
    assertEquals(GlassOptics.Adaptive, productGlassStyle(isDark = true).optics)
  }

  @Test
  fun nextAndFavoriteActions_updatePlainUiState() = runComposeUiTest {
    var selectedIndex by mutableIntStateOf(0)
    var favorite by mutableStateOf(false)
    setContent {
      GlassProductSampleContent(
        selectedArtworkIndex = selectedIndex,
        favorite = favorite,
        recordingMode = false,
        onArtworkSelected = { selectedIndex = it },
        onFavoriteChanged = { favorite = it },
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Next artwork").performClick()
    onNodeWithText("Signal Garden").assertIsDisplayed()
    onNodeWithContentDescription("Favorite artwork").performClick()
    onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    onNodeWithContentDescription("Artwork information").performClick()
    onNodeWithText("An emerald and ultraviolet poster with vertical signal bars").assertIsDisplayed()
  }

  @Test
  fun horizontalSwipe_selectsTheNextArtwork() = runComposeUiTest {
    var selectedIndex by mutableIntStateOf(0)
    setContent {
      GlassProductSampleContent(
        selectedArtworkIndex = selectedIndex,
        favorite = false,
        recordingMode = false,
        onArtworkSelected = { selectedIndex = it },
        onFavoriteChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithTag("glass_product_pager").performTouchInput { swipeLeft() }
    waitForIdle()
    onNodeWithText("Signal Garden").assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run the Product tests and verify the entry points are missing**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassProductSampleTest
```

Expected: FAIL because `productGlassStyle` and `GlassProductSampleContent` do not exist.

- [ ] **Step 3: Implement the Product state owner and Adaptive style**

Create `GlassProductSample.kt` with the following entry point and style helper:

```kotlin
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

@Composable
public fun GlassProductSample(navController: NavHostController) {
  var selectedArtworkIndex by rememberSaveable { mutableIntStateOf(0) }
  val favorites = remember { mutableStateMapOf<String, Boolean>() }
  var recordingMode by rememberSaveable { mutableStateOf(false) }
  val selectedId = GalleryArtworks[selectedArtworkIndex].id

  GlassProductSampleContent(
    selectedArtworkIndex = selectedArtworkIndex,
    favorite = favorites[selectedId] == true,
    recordingMode = recordingMode,
    onArtworkSelected = { selectedArtworkIndex = it.mod(GalleryArtworks.size) },
    onFavoriteChanged = { favorites[selectedId] = it },
    onRecordingModeChanged = { recordingMode = it },
    onBack = navController::navigateUp,
  )
}

internal fun productGlassStyle(isDark: Boolean): GlassStyle = GlassDefaults.style.copy(
  tint = if (isDark) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.1f),
  optics = GlassOptics.Adaptive,
)
```

- [ ] **Step 4: Implement the plain responsive Product UI**

Add this public primitive-state interface, then implement its body with the listed structure:

```kotlin
@Composable
public fun GlassProductSampleContent(
  selectedArtworkIndex: Int,
  favorite: Boolean,
  recordingMode: Boolean,
  onArtworkSelected: (Int) -> Unit,
  onFavoriteChanged: (Boolean) -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val pagerState = rememberPagerState(
    initialPage = selectedArtworkIndex,
    pageCount = { GalleryArtworks.size },
  )
  val isDark = isSystemInDarkTheme()
  var informationExpanded by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(selectedArtworkIndex) {
    if (pagerState.currentPage != selectedArtworkIndex) {
      pagerState.animateScrollToPage(selectedArtworkIndex)
    }
  }
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
      .distinctUntilChanged()
      .collect(onArtworkSelected)
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    HorizontalPager(
      state = pagerState,
      modifier = Modifier
        .fillMaxSize()
        .testTag("glass_product_pager"),
    ) { page ->
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GalleryBackdrop(
          hazeState = hazeState,
          artworkIndex = page,
          backdrop = GlassGalleryBackdropId.Gallery,
          modifier = Modifier.fillMaxWidth().height(maxHeight),
        )
        GalleryProductDetails(
          artwork = GalleryArtworks[page],
          modifier = Modifier.fillMaxWidth().background(Color(0xFF101218)).padding(32.dp),
        )
      }
    }

    ProductTopBar(
      hazeState = hazeState,
      selectedArtworkIndex = selectedArtworkIndex,
      recordingMode = recordingMode,
      onBack = onBack,
      onRecordingModeChanged = onRecordingModeChanged,
      modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
    )
    ProductMetadataCard(
      hazeState = hazeState,
      artwork = GalleryArtworks[selectedArtworkIndex],
      style = productGlassStyle(isDark),
      informationExpanded = informationExpanded,
      modifier = Modifier
        .align(if (landscape) Alignment.CenterStart else Alignment.Center)
        .padding(24.dp)
        .widthIn(max = 360.dp),
    )
    ProductActionDock(
      hazeState = hazeState,
      favorite = favorite,
      landscape = landscape,
      informationExpanded = informationExpanded,
      onPrevious = { onArtworkSelected((selectedArtworkIndex - 1).mod(GalleryArtworks.size)) },
      onNext = { onArtworkSelected((selectedArtworkIndex + 1).mod(GalleryArtworks.size)) },
      onFavoriteChanged = onFavoriteChanged,
      onInformationChanged = { informationExpanded = it },
      modifier = Modifier
        .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
        .padding(24.dp),
    )
  }
}
```

Define `GalleryProductDetails`, `ProductTopBar`, `ProductMetadataCard`, and `ProductActionDock` as private composables in the same file. Each must accept and apply `modifier` to its root. Use `GlassSurface` for all three overlays, `AnimatedContent` keyed by `artwork.id` inside the metadata card, and `AnimatedVisibility(informationExpanded)` for `artwork.description`. Use these exact action semantics:

```kotlin
IconButton(onClick = onPrevious) {
  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous artwork")
}
IconButton(onClick = { onFavoriteChanged(!favorite) }) {
  Icon(
    imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
    contentDescription = if (favorite) "Remove from favorites" else "Favorite artwork",
  )
}
IconButton(onClick = { onInformationChanged(!informationExpanded) }) {
  Icon(
    imageVector = Icons.Outlined.Info,
    contentDescription = "Artwork information",
  )
}
IconButton(onClick = onNext) {
  Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next artwork")
}
```

When `recordingMode` is true, hide only explanatory chrome with `AnimatedVisibility`; retain the Product top bar, metadata, and dock because they are part of the believable app scene. A tap on the poster backdrop calls `onRecordingModeChanged(false)`.

- [ ] **Step 5: Run Product tests and inspect both layout branches**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassProductSampleTest
rtk ./gradlew :sample:shared:testAndroidHostTest --tests dev.chrisbanes.haze.sample.GlassProductSampleTest
```

Expected: PASS on JVM and Android host tests; the pager swipe changes the visible title to `Signal Garden`.

- [ ] **Step 6: Commit the Product sample**

```bash
rtk git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassProductSample.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassProductSampleTest.kt
rtk git commit -m "Add Glass Product sample"
```

### Task 4: Define the Pure Playground Timeline

**Files:**
- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundTimeline.kt`
- Create: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundTimelineTest.kt`

**Interfaces:**
- Consumes: Adaptive, Deep, and Prism styles from Tasks 1–2.
- Produces: `GlassPlaygroundSurfaceId`, immutable `GlassPlaygroundFrame`, `glassPlaygroundFrame(progress)`, `glassPlaygroundStyle(id)`, and `glassPlaygroundShape(id)` for Task 5 and screenshot tests.

- [ ] **Step 1: Write failing loop and geometry tests**

Create `GlassPlaygroundTimelineTest.kt`:

```kotlin
package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassPlaygroundTimelineTest {
  @Test
  fun timeline_isSeamlessAtLoopBoundary() {
    assertEquals(glassPlaygroundFrame(0f), glassPlaygroundFrame(1f))
  }

  @Test
  fun keyFrames_keepEverySurfaceInsideNormalizedBounds() {
    listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { progress ->
      val frame = glassPlaygroundFrame(progress)
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        val position = frame.position(id)
        assertTrue(position.x in 0.1f..0.9f, "$id x out of bounds at $progress: $position")
        assertTrue(position.y in 0.1f..0.9f, "$id y out of bounds at $progress: $position")
      }
    }
  }

  @Test
  fun backdropAndLight_moveAcrossTheLoop() {
    val opening = glassPlaygroundFrame(0f)
    val quarter = glassPlaygroundFrame(0.25f)
    assertTrue(opening.backdropOffset != quarter.backdropOffset)
    assertTrue(opening.lightPosition != quarter.lightPosition)
  }

  @Test
  fun smallSurfacesUseAdaptiveAndFeatureSurfacesUseLiteralOptics() {
    assertEquals(GlassOptics.Adaptive, glassPlaygroundStyle(GlassPlaygroundSurfaceId.Lens).optics)
    assertEquals(GlassOptics.Adaptive, glassPlaygroundStyle(GlassPlaygroundSurfaceId.Pill).optics)
    assertTrue(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Card).optics is GlassOptics.Absolute)
    assertTrue(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Prism).optics is GlassOptics.Absolute)
  }

  @Test
  fun positionRejectsNoKnownSurface() {
    val frame = GlassPlaygroundFrame(
      backdropOffset = 0f,
      lightPosition = Offset.Zero,
      lensPosition = Offset(0.2f, 0.2f),
      pillPosition = Offset(0.4f, 0.4f),
      cardPosition = Offset(0.6f, 0.6f),
      prismPosition = Offset(0.8f, 0.8f),
    )
    assertEquals(Offset(0.8f, 0.8f), frame.position(GlassPlaygroundSurfaceId.Prism))
  }
}
```

- [ ] **Step 2: Run the timeline test and verify it fails to compile**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassPlaygroundTimelineTest
```

Expected: FAIL because the Playground timeline types and functions do not exist.

- [ ] **Step 3: Implement one allocation-light normalized frame**

Create `GlassPlaygroundTimeline.kt`:

```kotlin
@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

public enum class GlassPlaygroundSurfaceId {
  Lens,
  Pill,
  Card,
  Prism,
}

@Immutable
internal data class GlassPlaygroundFrame(
  val backdropOffset: Float,
  val lightPosition: Offset,
  val lensPosition: Offset,
  val pillPosition: Offset,
  val cardPosition: Offset,
  val prismPosition: Offset,
) {
  fun position(id: GlassPlaygroundSurfaceId): Offset = when (id) {
    GlassPlaygroundSurfaceId.Lens -> lensPosition
    GlassPlaygroundSurfaceId.Pill -> pillPosition
    GlassPlaygroundSurfaceId.Card -> cardPosition
    GlassPlaygroundSurfaceId.Prism -> prismPosition
  }
}

internal fun glassPlaygroundFrame(progress: Float): GlassPlaygroundFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val wrappedProgress = if (progress == 1f) 0f else progress
  val angle = (wrappedProgress * 2f * PI).toFloat()
  fun wave(phase: Float): Float = sin((angle + phase).toDouble()).toFloat()
  fun orbitX(center: Float, radius: Float, phase: Float): Float =
    center + radius * cos((angle + phase).toDouble()).toFloat()

  return GlassPlaygroundFrame(
    backdropOffset = 0.08f * wave(0f),
    lightPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.35f, phase = 0f),
      y = 0.5f + 0.25f * wave(0f),
    ),
    lensPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.28f, phase = 0f),
      y = 0.24f + 0.08f * wave(0f),
    ),
    pillPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.25f, phase = PI.toFloat()),
      y = 0.5f + 0.12f * wave(PI.toFloat()),
    ),
    cardPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.2f, phase = (PI / 2).toFloat()),
      y = 0.72f + 0.06f * wave((PI / 2).toFloat()),
    ),
    prismPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.3f, phase = (3 * PI / 2).toFloat()),
      y = 0.42f + 0.1f * wave((3 * PI / 2).toFloat()),
    ),
  )
}

internal fun glassPlaygroundStyle(id: GlassPlaygroundSurfaceId): GlassStyle = when (id) {
  GlassPlaygroundSurfaceId.Lens,
  GlassPlaygroundSurfaceId.Pill,
  -> GlassDefaults.style.copy(optics = GlassOptics.Adaptive)
  GlassPlaygroundSurfaceId.Card -> glassLabPresetStyle(GlassLabPresetId.Deep)
  GlassPlaygroundSurfaceId.Prism -> glassLabPresetStyle(GlassLabPresetId.Prism)
}

internal fun glassPlaygroundShape(id: GlassPlaygroundSurfaceId): RoundedCornerShape = when (id) {
  GlassPlaygroundSurfaceId.Lens -> RoundedCornerShape(percent = 50)
  GlassPlaygroundSurfaceId.Pill -> RoundedCornerShape(percent = 50)
  GlassPlaygroundSurfaceId.Card -> RoundedCornerShape(32.dp)
  GlassPlaygroundSurfaceId.Prism -> RoundedCornerShape(24.dp)
}
```

- [ ] **Step 4: Run timeline tests and commit**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassPlaygroundTimelineTest
```

Expected: PASS with five tests and exact equality between progress `0f` and `1f`.

Commit:

```bash
rtk git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundTimeline.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundTimelineTest.kt
rtk git commit -m "Add deterministic Glass Playground timeline"
```

### Task 5: Implement Glass — Playground Autoplay and Dragging

**Files:**
- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt`
- Create: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSampleTest.kt`

**Interfaces:**
- Consumes: the pure timeline and surface definitions from Task 4 plus shared visuals from Task 2.
- Produces: `GlassPlaygroundSample(navController)`, public fixed-state `GlassPlaygroundSampleContent(...)`, and a remembered controller that owns autoplay, drag override, reset, and recording state.

- [ ] **Step 1: Write failing plain-UI tests for controls and drag callbacks**

Create `GlassPlaygroundSampleTest.kt`:

```kotlin
package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GlassPlaygroundSampleTest {
  @Test
  fun controlsForwardPlayResetAndRecordingEvents() = runComposeUiTest {
    var playPauseCount = 0
    var resetCount = 0
    var recordingMode = false
    setContent {
      GlassPlaygroundSampleContent(
        progressProvider = { 0.25f },
        dragOffsetProvider = { Offset.Zero },
        isPlaying = true,
        recordingMode = recordingMode,
        onPlayPause = { playPauseCount++ },
        onReset = { resetCount++ },
        onRecordingModeChanged = { recordingMode = it },
        onBack = {},
        onDragStart = {},
        onDrag = { _, _ -> },
        onDragEnd = {},
      )
    }

    onNodeWithContentDescription("Pause animation").performClick()
    onNodeWithContentDescription("Reset demo").performClick()
    onNodeWithContentDescription("Enter recording mode").performClick()

    assertEquals(1, playPauseCount)
    assertEquals(1, resetCount)
    assertTrue(recordingMode)
  }

  @Test
  fun draggingLensForwardsOwnershipDeltaAndRelease() = runComposeUiTest {
    var started: GlassPlaygroundSurfaceId? = null
    var totalDelta = Offset.Zero
    var ended: GlassPlaygroundSurfaceId? = null
    setContent {
      GlassPlaygroundSampleContent(
        progressProvider = { 0f },
        dragOffsetProvider = { Offset.Zero },
        isPlaying = false,
        recordingMode = true,
        onPlayPause = {},
        onReset = {},
        onRecordingModeChanged = {},
        onBack = {},
        onDragStart = { started = it },
        onDrag = { _, delta -> totalDelta += delta },
        onDragEnd = { ended = it },
      )
    }

    onNodeWithTag("glass_playground_lens")
      .assertIsDisplayed()
      .performTouchInput { swipe(center, center + Offset(80f, 40f), durationMillis = 300) }

    assertEquals(GlassPlaygroundSurfaceId.Lens, started)
    assertEquals(GlassPlaygroundSurfaceId.Lens, ended)
    assertTrue(totalDelta.getDistance() > 0f)
  }
}
```

- [ ] **Step 2: Run the Playground UI tests and verify the content contract is missing**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassPlaygroundSampleTest
```

Expected: FAIL because `GlassPlaygroundSampleContent` does not exist.

- [ ] **Step 3: Implement the remembered Playground controller**

Create `GlassPlaygroundSample.kt` and add this controller. It deliberately keeps animation state out of a ViewModel and exposes provider reads for frame-rate values:

```kotlin
private const val PlaygroundLoopDurationMillis = 12_000

@Stable
internal class GlassPlaygroundState {
  private val progressAnimation = Animatable(0f)
  private val dragOffsets = mutableStateMapOf<GlassPlaygroundSurfaceId, Offset>()

  var isPlaying by mutableStateOf(true)
    private set
  var recordingMode by mutableStateOf(false)
    private set
  var activeSurface by mutableStateOf<GlassPlaygroundSurfaceId?>(null)
    private set

  fun progress(): Float = progressAnimation.value
  fun dragOffset(id: GlassPlaygroundSurfaceId): Offset = dragOffsets[id] ?: Offset.Zero
  fun togglePlayback() { isPlaying = !isPlaying }
  fun setRecordingMode(value: Boolean) { recordingMode = value }

  fun beginDrag(id: GlassPlaygroundSurfaceId) {
    activeSurface = id
    dragOffsets.putIfAbsent(id, Offset.Zero)
  }

  fun dragBy(id: GlassPlaygroundSurfaceId, delta: Offset) {
    if (activeSurface == id) dragOffsets[id] = dragOffset(id) + delta
  }

  suspend fun endDrag(id: GlassPlaygroundSurfaceId) {
    val start = dragOffset(id)
    Animatable(start, Offset.VectorConverter).animateTo(
      targetValue = Offset.Zero,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
    ) {
      dragOffsets[id] = value
    }
    dragOffsets.remove(id)
    if (activeSurface == id) activeSurface = null
  }

  suspend fun runAutoplayLoop() {
    while (isPlaying && activeSurface == null) {
      val remaining = 1f - progressAnimation.value
      progressAnimation.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = (PlaygroundLoopDurationMillis * remaining).roundToInt(),
          easing = LinearEasing,
        ),
      )
      progressAnimation.snapTo(0f)
    }
  }

  suspend fun reset() {
    activeSurface = null
    dragOffsets.clear()
    progressAnimation.snapTo(0f)
    isPlaying = true
    recordingMode = false
  }

  suspend fun disableAutoplay() {
    progressAnimation.snapTo(0f)
    isPlaying = false
  }
}

@Composable
internal fun rememberGlassPlaygroundState(): GlassPlaygroundState =
  remember { GlassPlaygroundState() }
```

- [ ] **Step 4: Wire the route owner, autoplay lifecycle, and disabled-motion hero state**

Add the route entry point. Key autoplay to the values that genuinely change its lifecycle, and prevent a zero-duration tight loop when system motion is disabled:

```kotlin
@Composable
public fun GlassPlaygroundSample(navController: NavHostController) {
  val state = rememberGlassPlaygroundState()
  val scope = rememberCoroutineScope()

  LaunchedEffect(state.isPlaying, state.activeSurface) {
    val animationsEnabled =
      (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!animationsEnabled) {
      state.disableAutoplay()
    } else if (state.isPlaying && state.activeSurface == null) {
      state.runAutoplayLoop()
    }
  }

  GlassPlaygroundSampleContent(
    progressProvider = state::progress,
    dragOffsetProvider = state::dragOffset,
    isPlaying = state.isPlaying,
    recordingMode = state.recordingMode,
    onPlayPause = state::togglePlayback,
    onReset = { scope.launch { state.reset() } },
    onRecordingModeChanged = state::setRecordingMode,
    onBack = navController::navigateUp,
    onDragStart = state::beginDrag,
    onDrag = state::dragBy,
    onDragEnd = { id -> scope.launch { state.endDrag(id) } },
  )
}
```

Import the public `androidx.compose.ui.MotionDurationScale` coroutine-context element. Do not add a platform `expect`/`actual` wrapper.

- [ ] **Step 5: Implement the plain Playground scene with deferred motion reads**

Add this public fixed-state entry point:

```kotlin
@Composable
public fun GlassPlaygroundSampleContent(
  progressProvider: () -> Float,
  dragOffsetProvider: (GlassPlaygroundSurfaceId) -> Offset,
  isPlaying: Boolean,
  recordingMode: Boolean,
  onPlayPause: () -> Unit,
  onReset: () -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    GalleryBackdrop(
      hazeState = hazeState,
      artworkIndex = 0,
      backdrop = GlassGalleryBackdropId.Gallery,
      offsetProvider = { glassPlaygroundFrame(progressProvider()).backdropOffset },
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(recordingMode) {
          detectTapGestures { if (recordingMode) onRecordingModeChanged(false) }
        },
    )

    GlassPlaygroundSurfaceId.entries.forEach { id ->
      PlaygroundSurface(
        id = id,
        hazeState = hazeState,
        progressProvider = progressProvider,
        dragOffsetProvider = { dragOffsetProvider(id) },
        sceneSizeProvider = { IntSize(constraints.maxWidth, constraints.maxHeight) },
        onDragStart = { onDragStart(id) },
        onDrag = { onDrag(id, it) },
        onDragEnd = { onDragEnd(id) },
      )
    }

    AnimatedVisibility(
      visible = !recordingMode,
      modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
    ) {
      DemoChrome(
        hazeState = hazeState,
        onBack = onBack,
        onEnterRecordingMode = { onRecordingModeChanged(true) },
        isPlaying = isPlaying,
        onPlayPause = onPlayPause,
        onReset = onReset,
      )
    }
  }
}
```

Implement private `PlaygroundSurface` with fixed sizes of `128.dp` Lens, `220.dp × 88.dp` Pill, `280.dp × 180.dp` Card, and `180.dp × 112.dp` Prism. Apply `Modifier.offset { ... }` and compute its `IntOffset` inside the block from `glassPlaygroundFrame(progressProvider()).position(id)`, the scene size, and `dragOffsetProvider()`. Add `Modifier.testTag("glass_playground_${id.name.lowercase()}")`, a semantics content description of `${id.name} draggable glass surface`, and `pointerInput(id)` with `detectDragGestures`. Keep one return `Job?` per surface and cancel it on a new drag before forwarding `onDragStart`. Forward both `onDragEnd` and `onDragCancel` through the same return-to-timeline callback.

Create one remembered `GlassVisualEffect` per surface. Keep the latest provider without restarting the long-lived collector, then update lighting from snapshot state:

```kotlin
val latestProgressProvider by rememberUpdatedState(progressProvider)
LaunchedEffect(effect, id) {
  snapshotFlow { glassPlaygroundFrame(latestProgressProvider()).lightPosition }
    .distinctUntilChanged()
    .collect { normalized ->
      effect.lightPosition = Offset(
        x = normalized.x * sceneSizeProvider().width,
        y = normalized.y * sceneSizeProvider().height,
      )
    }
}
```

Pass the remembered effect directly through `hazeEffect { visualEffect = effect }`; do not rebuild it on each frame. Lens and Pill contain no text, Card displays `DEPTH`, and Prism displays `PRISM` with high-contrast typography.

- [ ] **Step 6: Run Playground tests and a targeted compilation**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassPlaygroundSampleTest
rtk ./gradlew :sample:shared:compileKotlinJvm
```

Expected: both tests pass, the common sample compiles, and no frame-rate progress value is read in the screen composable body.

- [ ] **Step 7: Commit the Playground sample**

```bash
rtk git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSampleTest.kt
rtk git commit -m "Add interactive Glass Playground sample"
```

### Task 6: Implement Glass — Lab

**Files:**
- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassLabSample.kt`
- Create: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassLabSampleTest.kt`

**Interfaces:**
- Consumes: `GlassLabState`, preset styles, backdrop identifiers, and shared visuals from Tasks 1–2.
- Produces: `GlassLabSample(navController)`, internal plain `GlassLabSampleContent(...)` for module UI tests, and public primitive `GlassLabScreenshotContent(...)` for screenshot tests.

- [ ] **Step 1: Write failing Lab selection and reset tests**

Create `GlassLabSampleTest.kt`:

```kotlin
package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GlassLabSampleTest {
  @Test
  fun presetBackdropAdvancedAndResetEventsUpdatePlainState() = runComposeUiTest {
    var state by mutableStateOf(GlassLabState())
    setContent {
      GlassLabSampleContent(
        state = state,
        recordingMode = false,
        onStateChanged = { state = it },
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithText("Prism").performClick()
    assertEquals(GlassLabPresetId.Prism, state.preset)

    onNodeWithText("Grid").performClick()
    assertEquals(GlassGalleryBackdropId.Grid, state.backdrop)

    onNodeWithText("Advanced").performClick()
    onNodeWithText("Optics").assertIsDisplayed()

    onNodeWithContentDescription("Reset demo").performClick()
    assertEquals(GlassLabState(), state)
  }

  @Test
  fun recordingModeKeepsSpecimenAndHidesExplanatoryCopy() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = true,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Glass specimen").assertIsDisplayed()
    onNodeWithText("Choose a material preset").assertDoesNotExist()
  }
}
```

Import `assertDoesNotExist` in the final test file.

- [ ] **Step 2: Run the Lab tests and verify the screen is missing**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassLabSampleTest
```

Expected: FAIL because `GlassLabSampleContent` does not exist.

- [ ] **Step 3: Implement the Lab route state owner**

Create `GlassLabSample.kt`:

```kotlin
@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

@Composable
public fun GlassLabSample(navController: NavHostController) {
  var state by remember { mutableStateOf(GlassLabState()) }
  var recordingMode by rememberSaveable { mutableStateOf(false) }

  GlassLabSampleContent(
    state = state,
    recordingMode = recordingMode,
    onStateChanged = { state = it },
    onRecordingModeChanged = { recordingMode = it },
    onBack = navController::navigateUp,
  )
}
```

- [ ] **Step 4: Implement the responsive specimen and selectors**

Add the plain UI. It is public for screenshot tests while its state remains immutable and module-owned:

```kotlin
@Composable
internal fun GlassLabSampleContent(
  state: GlassLabState,
  recordingMode: Boolean,
  onStateChanged: (GlassLabState) -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    if (landscape) {
      Row(Modifier.fillMaxSize()) {
        LabSpecimen(
          hazeState = hazeState,
          state = state,
          recordingMode = recordingMode,
          onRevealChrome = { onRecordingModeChanged(false) },
          modifier = Modifier.weight(0.6f).fillMaxHeight(),
        )
        LabControls(
          state = state,
          recordingMode = recordingMode,
          onStateChanged = onStateChanged,
          modifier = Modifier.weight(0.4f).fillMaxHeight(),
        )
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        LabSpecimen(
          hazeState = hazeState,
          state = state,
          recordingMode = recordingMode,
          onRevealChrome = { onRecordingModeChanged(false) },
          modifier = Modifier.weight(0.52f).fillMaxWidth(),
        )
        LabControls(
          state = state,
          recordingMode = recordingMode,
          onStateChanged = onStateChanged,
          modifier = Modifier.weight(0.48f).fillMaxWidth(),
        )
      }
    }

    AnimatedVisibility(
      visible = !recordingMode,
      modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
    ) {
      DemoChrome(
        hazeState = hazeState,
        onBack = onBack,
        onEnterRecordingMode = { onRecordingModeChanged(true) },
        onReset = { onStateChanged(state.reset()) },
      )
    }
  }
}
```

Keep `GlassLabSampleContent` `internal`; screenshot tests consume the following public primitive wrapper declared in the same file:

```kotlin
@Composable
public fun GlassLabScreenshotContent(
  preset: GlassLabPresetId,
  backdrop: GlassGalleryBackdropId,
  modifier: Modifier = Modifier,
) {
  GlassLabSampleContent(
    state = GlassLabState(
      preset = preset,
      backdrop = backdrop,
      style = glassLabPresetStyle(preset),
    ),
    recordingMode = true,
    onStateChanged = {},
    onRecordingModeChanged = {},
    onBack = {},
    modifier = modifier,
  )
}
```

- [ ] **Step 5: Implement the exact control groups**

Implement private `LabSpecimen` with `recordingMode: Boolean` and `onRevealChrome: () -> Unit` parameters, a selected `GalleryBackdrop` filling its root, and one centered `GlassSurface`, maximum `360.dp × 240.dp`, shape `RoundedCornerShape(32.dp)`, semantic content description `Glass specimen`, and the active preset name as its content. Apply `detectTapGestures` to unobstructed backdrop content and call `onRevealChrome` only when `recordingMode` is true.

Implement `LabControls` as a vertically scrolling `Column` with:

- `SingleChoiceSegmentedButtonRow` for `SelectableGlassLabPresets`, calling `state.selectPreset(id)`.
- A second selector for all `GlassGalleryBackdropId.entries`, copying `backdrop`.
- `TextButton` labeled `Advanced` that toggles `advancedExpanded`.
- Explanatory text `Choose a material preset` inside `AnimatedVisibility(visible = !recordingMode)`.

When Advanced is expanded, render these exact groups and controls:

```kotlin
LabSlider("Refraction", absolute.refractionStrength, 0f..1f) { value ->
  onStateChanged(state.editStyle { it.copy(optics = absolute.copy(refractionStrength = value)) })
}
LabSlider("Depth", absolute.depth, 0f..1f) { value ->
  onStateChanged(state.editStyle { it.copy(optics = absolute.copy(depth = value)) })
}
LabSlider("Blur", absolute.blurRadius.value, 0f..32f) { value ->
  onStateChanged(state.editStyle { it.copy(optics = absolute.copy(blurRadius = value.dp)) })
}
LabSlider("Specular", lighting.specularIntensity, 0f..1f) { value ->
  onStateChanged(state.editStyle { it.copy(lighting = lighting.copy(specularIntensity = value)) })
}
LabSlider("Ambient", lighting.ambientResponse, 0f..1f) { value ->
  onStateChanged(state.editStyle { it.copy(lighting = lighting.copy(ambientResponse = value)) })
}
LabSlider("Contrast", color.contrast, -1f..1f) { value ->
  onStateChanged(state.editStyle { it.copy(color = color.copy(contrast = value)) })
}
LabSlider("Chroma", color.chromaMultiplier, 0f..2f) { value ->
  onStateChanged(state.editStyle { it.copy(color = color.copy(chromaMultiplier = value)) })
}
LabSlider("Edge softness", rendering.edgeSoftness.value, 0f..24f) { value ->
  onStateChanged(state.editStyle { it.copy(rendering = rendering.copy(edgeSoftness = value.dp)) })
}
LabSlider("Chromatic", rendering.chromaticAberrationStrength, 0f..0.4f) { value ->
  onStateChanged(
    state.editStyle {
      it.copy(rendering = rendering.copy(chromaticAberrationStrength = value))
    },
  )
}
```

Derive `absolute` as `(state.style.optics as? GlassOptics.Absolute) ?: GlassOptics.Absolute()`. The Adaptive style therefore stays Adaptive until the user moves a control; the first edit changes it to Custom with a complete literal `GlassOptics.Absolute`. Read grouped properties from the complete preset style, falling back to `GlassDefaults.style` only for an unspecified field. Label the group headings exactly `Optics`, `Lighting`, `Colour`, and `Rendering`.

- [ ] **Step 6: Run Lab tests and commit**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.GlassLabSampleTest
rtk ./gradlew :sample:shared:testAndroidHostTest --tests dev.chrisbanes.haze.sample.GlassLabSampleTest
```

Expected: PASS on JVM and Android host tests; selecting Prism, Grid, Advanced, and Reset updates the exact state asserted by the test.

Commit:

```bash
rtk git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassLabSample.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassLabSampleTest.kt
rtk git commit -m "Add Glass Lab sample"
```

### Task 7: Replace the Old Sample Destinations

**Files:**
- Modify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/SamplesTest.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/CreditCardSamplesTest.kt`
- Delete: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassSample.kt`
- Delete: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassDebugSample.kt`

**Interfaces:**
- Consumes: the three route entry points from Tasks 3, 5, and 6.
- Produces: final public sample navigation with no route to the obsolete Glass credit-card or debug UI.

- [ ] **Step 1: Change the sample-list test to the replacement contract**

Replace the current Glass assertion in `SamplesTest.kt` with:

```kotlin
@Test
fun samplesList_replacesOldGlassEntriesWithShowcaseSuite() = runComposeUiTest {
  setContent {
    Samples(
      appTitle = "Haze Samples",
      samples = listOf(
        Sample.CreditCard,
        Sample.GlassProduct,
        Sample.GlassPlayground,
        Sample.GlassLab,
      ),
    )
  }

  onNodeWithTag("Credit Card").assertIsDisplayed()
  onNodeWithTag("Glass — Product").assertIsDisplayed()
  onNodeWithTag("Glass — Playground").assertIsDisplayed()
  onNodeWithTag("Glass — Lab").assertIsDisplayed()
  onNodeWithTag("Glass").assertDoesNotExist()
  onNodeWithTag("Glass (Debug)").assertDoesNotExist()
}
```

Import `assertDoesNotExist` and retain the existing Compose test setup imports.

- [ ] **Step 2: Run the list test and verify the new destination types are absent**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest --tests dev.chrisbanes.haze.sample.SamplesTest
```

Expected: compilation FAIL because the three `Sample.Glass*` objects are not registered yet.

- [ ] **Step 3: Replace the two destination objects and common-list entries**

In `CommonSamples`, replace:

```kotlin
Sample.Glass,
Sample.GlassDebug,
```

with:

```kotlin
Sample.GlassProduct,
Sample.GlassPlayground,
Sample.GlassLab,
```

Replace the old `Sample.Glass` and `Sample.GlassDebug` objects with:

```kotlin
@Serializable
data object GlassProduct : Sample {
  override val title: String = "Glass — Product"

  @Composable
  override fun Content(navController: NavHostController, blurEnabled: Boolean) {
    GlassProductSample(navController = navController)
  }
}

@Serializable
data object GlassPlayground : Sample {
  override val title: String = "Glass — Playground"

  @Composable
  override fun Content(navController: NavHostController, blurEnabled: Boolean) {
    GlassPlaygroundSample(navController = navController)
  }
}

@Serializable
data object GlassLab : Sample {
  override val title: String = "Glass — Lab"

  @Composable
  override fun Content(navController: NavHostController, blurEnabled: Boolean) {
    GlassLabSample(navController = navController)
  }
}
```

The global Blur checkbox intentionally does not alter Glass; retain the required `blurEnabled` interface parameter without forwarding it.

- [ ] **Step 4: Remove obsolete Glass-specific code and assertions**

Run:

```bash
rtk git rm sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassSample.kt
rtk git rm sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassDebugSample.kt
```

Remove `glassSample_keepsBenchmarkCardTag` from `CreditCardSamplesTest.kt`. Keep `creditCardSample_keepsBenchmarkCardTag` unchanged because it protects the Blur benchmark scene.

- [ ] **Step 5: Run all sample common tests**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest :sample:shared:testAndroidHostTest
```

Expected: PASS, including Product, Playground, Lab, model, timeline, chrome, navigation, and retained Blur credit-card tests.

- [ ] **Step 6: Commit the destination replacement**

```bash
rtk git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/SamplesTest.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/CreditCardSamplesTest.kt
rtk git commit -m "Replace Glass samples with showcase suite"
```

### Task 8: Add Android and Desktop Showcase Screenshots

**Files:**
- Modify: `haze-screenshot-tests/build.gradle.kts`
- Modify: `internal/screenshot-test/src/jvmMain/kotlin/dev/chrisbanes/haze/test/ScreenshotTest.jvm.kt`
- Create: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt`
- Create: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt`
- Create: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt`
- Create: `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt`
- Create: new `.webp` baselines under `haze-screenshot-tests/screenshots/android/` and `haze-screenshot-tests/screenshots/desktop/`

**Interfaces:**
- Consumes: public fixed-state Product and Playground content plus `GlassLabScreenshotContent` from Tasks 3, 5, and 6.
- Produces: fixed-state visual contracts for Product portrait/landscape, four Playground beats plus manual displacement, and Adaptive/Frosted/Prism Lab states on Android and Desktop.

- [ ] **Step 1: Add the sample module as a screenshot-test dependency**

In `haze-screenshot-tests/build.gradle.kts`, add to `commonTest.dependencies`:

```kotlin
implementation(projects.sample.shared)
```

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:compileTestKotlinJvm
```

Expected: PASS and the screenshot test module can import `dev.chrisbanes.haze.sample` public entry points.

- [ ] **Step 2: Add a Desktop-only viewport overload**

Refactor `ScreenshotTest.jvm.kt` so the existing actual function delegates to one private helper and add a JVM-visible size overload:

```kotlin
actual fun ScreenshotTest.runScreenshotTest(
  block: ScreenshotUiTest.() -> Unit,
) {
  runScreenshotTest(size = Size(1080f, 1920f), block = block)
}

fun ScreenshotTest.runScreenshotTest(
  size: Size,
  block: ScreenshotUiTest.() -> Unit,
) {
  runSkikoComposeUiTest(
    size = size,
    density = Density(2.75f),
  ) {
    provideRoborazziContext().apply {
      setRuleOverrideRoborazziOptions(HazeRoborazziDefaults.roborazziOptions)
      setRuleOverrideOutputDirectory("screenshots/desktop")
    }
    createScreenshotUiTest().block()
  }
}
```

Remove the old duplicated `runSkikoComposeUiTest` body from the actual function. Leave Android screenshot sizing controlled by Robolectric qualifiers.

- [ ] **Step 3: Add fixed-state shared capture helpers**

Create `GlassGalleryScreenshotAssertions.kt` with these helpers:

```kotlin
package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import dev.chrisbanes.haze.sample.GlassGalleryBackdropId
import dev.chrisbanes.haze.sample.GlassLabPresetId
import dev.chrisbanes.haze.sample.GlassLabScreenshotContent
import dev.chrisbanes.haze.sample.GlassPlaygroundSampleContent
import dev.chrisbanes.haze.sample.GlassPlaygroundSurfaceId
import dev.chrisbanes.haze.sample.GlassProductSampleContent
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureGlassProductHero() {
  setContent {
    SamplesTheme(useDarkColors = true) {
      GlassProductSampleContent(
        selectedArtworkIndex = 0,
        favorite = false,
        recordingMode = true,
        onArtworkSelected = {},
        onFavoriteChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }
  }
  waitForIdle()
  captureRoot()
}

internal fun ScreenshotUiTest.captureGlassPlaygroundBeats() {
  var progress by mutableFloatStateOf(0f)
  var displacedLens by mutableStateOf(Offset.Zero)
  setContent {
    SamplesTheme(useDarkColors = true) {
      GlassPlaygroundSampleContent(
        progressProvider = { progress },
        dragOffsetProvider = { id ->
          if (id == GlassPlaygroundSurfaceId.Lens) displacedLens else Offset.Zero
        },
        isPlaying = false,
        recordingMode = true,
        onPlayPause = {},
        onReset = {},
        onRecordingModeChanged = {},
        onBack = {},
        onDragStart = {},
        onDrag = { _, _ -> },
        onDragEnd = {},
      )
    }
  }

  waitForIdle()
  captureRoot("opening")
  progress = 0.2f
  waitForIdle()
  captureRoot("typography")
  progress = 0.5f
  waitForIdle()
  captureRoot("depth")
  progress = 0.8f
  waitForIdle()
  captureRoot("prism")
  displacedLens = Offset(120f, 72f)
  waitForIdle()
  captureRoot("dragged")
}

internal fun ScreenshotUiTest.captureGlassLabPresets() {
  var preset by mutableStateOf(GlassLabPresetId.Adaptive)
  var backdrop by mutableStateOf(GlassGalleryBackdropId.Gallery)
  setContent {
    SamplesTheme(useDarkColors = true) {
      GlassLabScreenshotContent(preset = preset, backdrop = backdrop)
    }
  }

  waitForIdle()
  captureRoot("adaptive")
  preset = GlassLabPresetId.Frosted
  backdrop = GlassGalleryBackdropId.Grid
  waitForIdle()
  captureRoot("frosted")
  preset = GlassLabPresetId.Prism
  backdrop = GlassGalleryBackdropId.Bands
  waitForIdle()
  captureRoot("prism")
}
```

- [ ] **Step 4: Add Android API 35 portrait and landscape classes**

Create `GlassGalleryPortraitAndroidScreenshotTest.kt`:

```kotlin
package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassGalleryPortraitAndroidScreenshotTest : ScreenshotTest() {
  @Test fun productHero() = runScreenshotTest { captureGlassProductHero() }
  @Test fun playgroundBeats() = runScreenshotTest { captureGlassPlaygroundBeats() }
  @Test fun labPresets() = runScreenshotTest { captureGlassLabPresets() }
}
```

Create `GlassGalleryLandscapeAndroidScreenshotTest.kt`:

```kotlin
package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w698dp-h393dp-320dpi")
class GlassGalleryLandscapeAndroidScreenshotTest : ScreenshotTest() {
  @Test fun productHero() = runScreenshotTest { captureGlassProductHero() }
}
```

- [ ] **Step 5: Add Desktop portrait and landscape coverage**

Create `GlassGalleryDesktopScreenshotTest.kt`:

```kotlin
package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassGalleryDesktopScreenshotTest : ScreenshotTest() {
  @Test fun productPortrait() = runScreenshotTest { captureGlassProductHero() }

  @Test
  fun productLandscape() = runScreenshotTest(size = Size(1920f, 1080f)) {
    captureGlassProductHero()
  }

  @Test fun playgroundBeats() = runScreenshotTest { captureGlassPlaygroundBeats() }
  @Test fun labPresets() = runScreenshotTest { captureGlassLabPresets() }
}
```

- [ ] **Step 6: Compile the new screenshot fixtures before recording**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:compileTestKotlinJvm :haze-screenshot-tests:compileAndroidHostTest
```

Expected: PASS. Fix source-set visibility, imports, or signature mismatches before creating baselines.

- [ ] **Step 7: Record the new WebP baselines**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:recordRoborazzi
```

Expected: new Android API 35 and Desktop WebP files are created for Product, Playground, and Lab; existing unrelated baselines remain byte-identical.

- [ ] **Step 8: Inspect every new baseline against the design acceptance criteria**

Inspect the new `GlassGallery*ScreenshotTest*.webp` files in both output directories. Confirm:

- Product has readable overlay content, visible refraction, no stretched surfaces, and correct portrait/landscape composition.
- Playground opening and loop beats remain balanced; each fixed shape crosses high-detail content; Prism is restrained rather than rainbow-heavy; the displaced lens is visibly offset.
- Lab Adaptive, Frosted, and Prism are visually distinct and controls never overlap the specimen.
- No screenshot contains clipped controls, missing sources, transient animation frames, network placeholders, or debug copy.

If a visual adjustment is required, change only Gallery artwork/layout constants or the Task 1 preset table, rerun the focused model/UI tests, then re-record all new Gallery baselines together.

- [ ] **Step 9: Verify and commit screenshot coverage**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:jvmTest --tests dev.chrisbanes.haze.GlassGalleryDesktopScreenshotTest
rtk ./gradlew :haze-screenshot-tests:testAndroidHostTest --tests 'dev.chrisbanes.haze.GlassGallery*AndroidScreenshotTest'
```

Expected: PASS against the recorded baselines.

Commit:

```bash
rtk git add haze-screenshot-tests/build.gradle.kts internal/screenshot-test/src/jvmMain/kotlin/dev/chrisbanes/haze/test/ScreenshotTest.jvm.kt haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt haze-screenshot-tests/screenshots/android haze-screenshot-tests/screenshots/desktop
rtk git commit -m "Add Glass Gallery screenshot coverage"
```

### Task 9: Run Cross-Platform Final Verification

**Files:**
- Verify: all files from Tasks 1–8
- Modify only if required by Spotless or a failing targeted assertion

**Interfaces:**
- Consumes: the complete suite and committed baselines.
- Produces: a clean, buildable branch whose final diff contains only the approved sample suite, test support, and design/plan documents.

- [ ] **Step 1: Apply formatting and inspect the final diff**

Run:

```bash
rtk ./gradlew spotlessApply
rtk git diff --check
rtk git status --short
```

Expected: `git diff --check` prints nothing. Status contains no untracked generated files outside the intended plan, source, test, and WebP files.

- [ ] **Step 2: Run all sample tests and assemble the Android sample**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest :sample:shared:testAndroidHostTest :sample:android:assembleDebug
```

Expected: PASS. The shared sample compiles for JVM/Android and the Android application packages the three destinations.

- [ ] **Step 3: Run the complete screenshot suite**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:test
```

Expected: PASS for Desktop and Android host screenshots with no unexpected Roborazzi diffs.

- [ ] **Step 4: Run repository style verification**

Run:

```bash
rtk ./gradlew spotlessCheck
```

Expected: PASS.

- [ ] **Step 5: Confirm the final history and commit any formatting-only delta**

Run:

```bash
rtk git status --short
rtk git log -8 --oneline
```

Expected: the history shows one focused commit per implementation task and status is clean. If `spotlessApply` changed tracked files after the last task commit, commit only those formatting changes:

```bash
rtk git add sample/shared haze-screenshot-tests internal/screenshot-test
rtk git commit -m "Apply Glass Gallery formatting"
```
