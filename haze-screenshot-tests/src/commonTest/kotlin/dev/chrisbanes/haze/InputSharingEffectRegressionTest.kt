// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.test.Test

/**
 * Pixel proof that real Blur and Glass renderers draw the shared, bounded source layer.
 *
 * Every scene hides its source under an opaque black cover, so a positive colour can only come
 * from the effect. A control cell has the cover but no effect: it proves the cover hides the
 * source, so a positive result cannot be the original source showing through.
 */
class InputSharingEffectRegressionTest : ScreenshotTest() {

  @Test
  fun blur_sharedInput_showsDeclaredOutsetsAlignedWithUnsharedReference() =
    verifySharedOutput(SharingEffect.Blur)

  @Test
  fun glass_sharedInput_showsDeclaredOutsetsAlignedWithUnsharedReference() =
    verifySharedOutput(SharingEffect.Glass)

  @Test
  fun blur_sharingToggledOffAndOn_refreshesToFreshState() = verifySharingToggle(SharingEffect.Blur)

  @Test
  fun glass_sharingToggledOffAndOn_refreshesToFreshState() = verifySharingToggle(SharingEffect.Glass)

  @Test
  fun blur_sharingToggledWithZeroOutsets_refreshesToFreshState() = verifyZeroOutsetToggle(SharingEffect.Blur)

  @Test
  fun glass_sharingToggledWithZeroOutsets_refreshesToFreshState() = verifyZeroOutsetToggle(SharingEffect.Glass)

  @Test
  fun blur_outsetsChanged_refreshToFreshState() = verifyOutsetChanges(SharingEffect.Blur)

  @Test
  fun glass_outsetsChanged_refreshToFreshState() = verifyOutsetChanges(SharingEffect.Glass)

  @Test
  fun blur_sourceResized_redrawsAtNewBounds() = verifyResize(SharingEffect.Blur)

  @Test
  fun glass_sourceResized_redrawsAtNewBounds() = verifyResize(SharingEffect.Glass)

  @Test
  fun blur_sourceRemovedAndReadded_keepsLastFrameThenRefreshes() = verifyRemoveAndReadd(SharingEffect.Blur)

  @Test
  fun glass_sourceRemovedAndReadded_keepsLastFrameThenRefreshes() = verifyRemoveAndReadd(SharingEffect.Glass)

  @Test
  fun blur_sourceRemoved_clearsWhenRetentionClears() = verifyClearWhenUnavailable(SharingEffect.Blur)

  @Test
  fun glass_sourceRemoved_clearsWhenRetentionClears() = verifyClearWhenUnavailable(SharingEffect.Glass)

  @Test
  fun blur_nestedSources_sampleEachStateIndependently() = verifyNestedSources(SharingEffect.Blur)

  @Test
  fun glass_nestedSources_sampleEachStateIndependently() = verifyNestedSources(SharingEffect.Glass)

  @Test
  fun blur_backdropFallback_matchesSources() = verifyBackdropFallback(SharingEffect.Blur)

  @Test
  fun glass_backdropFallback_matchesSources() = verifyBackdropFallback(SharingEffect.Glass)

  @Test
  fun glassContainer_sharedInput_showsOutsetsAndRefreshesThroughSurfaceInput() = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture()
    setContent { ContainerScene(fixture) }
    val samples = SharingEffect.Glass.samplesSource(this)

    val initial = assertContainer(fixture, samples)

    // The container snapshots its members' input; the version bump must reach it unchanged.
    fixture.sharing = HazeInputSharing.Never
    waitForIdle()
    // Unshared, the overflow beyond every declared outset is sampled again.
    val unshared = captureRootPixels()
    for ((x, y, color) in ContainerUnsharedProbes) {
      assertColor(unshared[SOURCE_X + x, SOURCE_Y + y], if (samples) color else Color.Black, "container unshared ($x, $y)")
    }

    fixture.sharing = HazeInputSharing.Always
    assertContainer(fixture, samples).assertSameCell(initial, SHARED_CELL)

    fixture.edges = Edges(0, 0, 0, 0)
    assertContainer(fixture, samples)

    // Non-zero to non-zero, without passing through zero, so the new outsets must reach the container.
    fixture.edges = DeclaredEdges
    assertContainer(fixture, samples).assertSameCell(initial, SHARED_CELL)
    fixture.edges = Edges(left = 40, top = 8, right = 12, bottom = 24)
    assertContainer(fixture, samples)

    fixture.edges = DeclaredEdges
    assertContainer(fixture, samples).assertSameCell(initial, SHARED_CELL)
  }

  private fun verifySharedOutput(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture()
    setContent { SharingScene(effect, fixture, control = true) }
    waitForIdle()

    val pixels = captureRootPixels()
    val samples = effect.samplesSource(this)
    pixels.assertProbes(SHARED_CELL, fixture.edges, samples)
    pixels.assertMatchesReference(fixture.widthPx, samples)
    // The cover alone hides the source, so nothing above can be the source showing through.
    pixels.assertProbes(CONTROL_CELL, fixture.edges, samples = false)
  }

  private fun verifySharingToggle(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture()
    setContent { SharingScene(effect, fixture) }
    val initial = assertShared(effect, fixture)

    // Same content, outsets and style: only the sharing mode changes.
    fixture.sharing = HazeInputSharing.Never
    waitForIdle()
    val unshared = captureRootPixels()
    val samples = effect.samplesSource(this)
    unshared.assertMatchesReference(fixture.widthPx, samples)
    unshared.assertProbes(SHARED_CELL, UnsharedEdges, samples)

    fixture.sharing = HazeInputSharing.Always
    assertShared(effect, fixture).assertSameCell(initial, SHARED_CELL)
  }

  /** The sharing mode alone changes: no outsets, so the input bounds never move. */
  private fun verifyZeroOutsetToggle(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture().apply { edges = Edges(0, 0, 0, 0) }
    setContent { SharingScene(effect, fixture) }
    val initial = assertShared(effect, fixture)

    fixture.sharing = HazeInputSharing.Never
    waitForIdle()
    val samples = effect.samplesSource(this)
    val unshared = captureRootPixels()
    unshared.assertMatchesReference(fixture.widthPx, samples)
    // Clipped while shared, the overflow must reappear once the source stops sharing.
    unshared.assertProbes(SHARED_CELL, UnsharedEdges, samples)

    fixture.sharing = HazeInputSharing.Always
    assertShared(effect, fixture).assertSameCell(initial, SHARED_CELL)
  }

  private fun verifyOutsetChanges(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture()
    setContent { SharingScene(effect, fixture) }
    val initial = assertShared(effect, fixture)

    fixture.edges = Edges(0, 0, 0, 0)
    assertShared(effect, fixture)

    fixture.edges = Edges(left = 40, top = 8, right = 12, bottom = 24)
    assertShared(effect, fixture)

    fixture.edges = DeclaredEdges
    assertShared(effect, fixture).assertSameCell(initial, SHARED_CELL)
  }

  private fun verifyResize(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture()
    setContent { SharingScene(effect, fixture) }
    val samples = effect.samplesSource(this)
    val initial = assertShared(effect, fixture)
    // x = 95 is the right-hand strip at 80px wide, but interior once the source is 120px wide.
    initial.assertPixel(SHARED_CELL, 95, 20, Color.Magenta, samples)

    fixture.widthPx = 120
    assertShared(effect, fixture).assertPixel(SHARED_CELL, 95, 20, Color.Green, samples)

    fixture.widthPx = 80
    val restored = assertShared(effect, fixture)
    restored.assertPixel(SHARED_CELL, 95, 20, Color.Magenta, samples)
    restored.assertSameCell(initial, SHARED_CELL)
  }

  private fun verifyRemoveAndReadd(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture()
    setContent { SharingScene(effect, fixture) }
    val initial = assertShared(effect, fixture)

    // The default retention keeps the last output while no selected source is attached.
    fixture.sourcePresent = false
    waitForIdle()
    captureRootPixels().assertSameCell(initial, SHARED_CELL)

    // A source returning with different outsets must replace the retained output.
    fixture.edges = Edges(0, 0, 0, 0)
    fixture.sourcePresent = true
    // The Android host draws only for a capture: draw once for the new source, then for the effect.
    waitForIdle()
    captureRootPixels()
    assertShared(effect, fixture)

    fixture.edges = DeclaredEdges
    // As above: the re-added source needs another host draw before the effect samples it.
    waitForIdle()
    captureRootPixels()
    assertShared(effect, fixture).assertSameCell(initial, SHARED_CELL)
  }

  private fun verifyClearWhenUnavailable(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val fixture = SharingFixture().apply { retention = HazeSourceRetention.ClearWhenUnavailable }
    setContent { SharingScene(effect, fixture) }
    val initial = assertShared(effect, fixture)

    fixture.sourcePresent = false
    waitForIdle()
    // Nothing is retained, so the whole cell is the cover.
    captureRootPixels().assertProbes(SHARED_CELL, fixture.edges, samples = false)

    fixture.sourcePresent = true
    assertShared(effect, fixture).assertSameCell(initial, SHARED_CELL)
  }

  private fun verifyNestedSources(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    setContent { NestedScene(effect) }
    waitForIdle()
    val pixels = captureRootPixels()
    val samples = effect.samplesSource(this)
    for ((x, y, color) in NestedProbes) {
      assertColor(pixels[x, y], if (samples) color else Color.Black, "nested ($x, $y)")
    }
  }

  private fun verifyBackdropFallback(effect: SharingEffect) = runScreenshotTest(size = SceneSize) {
    val previous = HazeFeatureFlags.isPlatformBackdropEnabled
    // The portable fallback must be exercised, not the native path.
    HazeFeatureFlags.isPlatformBackdropEnabled = false
    try {
      val fixture = SharingFixture()
      setContent { SharingScene(effect, fixture, backdrop = true) }
      waitForIdle()
      val pixels = captureRootPixels()
      val samples = effect.samplesSource(this)
      pixels.assertProbes(BACKDROP_CELL, fixture.edges, samples)
      pixels.assertCellsMatch(SHARED_CELL, BACKDROP_CELL)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previous
    }
  }

  private fun ScreenshotUiTest.assertShared(effect: SharingEffect, fixture: SharingFixture): PixelMap {
    waitForIdle()
    val pixels = captureRootPixels()
    val samples = effect.samplesSource(this)
    pixels.assertProbes(SHARED_CELL, fixture.edges, samples, fixture.widthPx)
    pixels.assertMatchesReference(fixture.widthPx, samples)
    return pixels
  }

  private fun ScreenshotUiTest.assertContainer(fixture: SharingFixture, samples: Boolean): PixelMap {
    waitForIdle()
    val pixels = captureRootPixels()
    pixels.assertProbes(SHARED_CELL, fixture.edges, samples, interior = ContainerInterior, topX = 20)
    pixels.assertMatchesReference(fixture.widthPx, samples)
    return pixels
  }
}

private val SceneSize = Size(960f, 280f)

private enum class SharingEffect {
  Blur,
  Glass,
  ;

  /** Whether this effect samples its source here. Elsewhere it leaves the black cover visible. */
  fun samplesSource(ui: ScreenshotUiTest): Boolean = when (this) {
    Blur -> ui.supportsRuntimeBlur
    Glass -> isRuntimeShaderRenderEffectSupported()
  }

  fun modifier(input: HazeInput): Modifier = when (this) {
    Blur -> Modifier.hazeBlur(
      input = input,
      performanceMode = HazePerformanceMode.Quality,
      style = HazeBlurStyle {
        blurRadius(2.dp)
        noiseFactor(0f)
        colorEffects(emptyList())
      },
    )
    Glass -> Modifier.hazeGlass(
      input = input,
      performanceMode = HazePerformanceMode.Quality,
      style = PassthroughGlassStyle,
    )
  }
}

/** Source colours without the built-in material's tone, refraction, edge or tint adjustments. */
private val PassthroughGlassStyle = GlassStyle.regular.then {
  optics(
    GlassOptics(
      refractionStrength = 0f,
      refractionDisplacement = 0.dp,
      refractionDetailIntensity = 0f,
      depth = OpticalSizeValue.Fixed(0f),
      blurRadius = OpticalSizeValue.Fixed(0.dp),
    ),
  )
  tint(Color.Transparent)
  backgroundColor(Color.Transparent)
  edgeSoftness(0.dp)
  edgeShadow(Color.Transparent)
  specularIntensity(0f)
  ambientResponse(0f)
  chromaticAberrationStrength(0f)
  contrast(0f)
  whitePoint(0f)
  chromaMultiplier(1f)
}

// All geometry is in pixels, because the scene fixes the density at 1.
private const val CELL_WIDTH = 240
private const val CELL_HEIGHT = 220
private const val SOURCE_X = 64
private const val SOURCE_Y = 64
private const val SOURCE_HEIGHT = 80

private const val SHARED_CELL = 0
private const val REFERENCE_CELL = CELL_WIDTH
private const val CONTROL_CELL = CELL_WIDTH * 2
private const val BACKDROP_CELL = CELL_WIDTH * 3

/** Whole pixels of overflow a source keeps beyond each edge while sharing is active. */
private data class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  fun toOutsets() = LayerOutsets(left = left.dp, top = top.dp, right = right.dp, bottom = bottom.dp)
}

/** Reaches all 56px the pattern overflows: an unshared source's overflow is all sampled. */
private val UnsharedEdges = Edges(56, 56, 56, 56)

// Asymmetric, so a swapped or shifted edge cannot pass.
private val DeclaredEdges = Edges(left = 16, top = 24, right = 32, bottom = 40)

private class SharingFixture {
  var sharing by mutableStateOf(HazeInputSharing.Always)
  var edges by mutableStateOf(DeclaredEdges)
  var widthPx by mutableStateOf(80)
  var sourcePresent by mutableStateOf(true)
  var retention: HazeSourceRetention by mutableStateOf(HazeSourceRetention.KeepLastFrame)
}

@Composable
private fun SharingScene(
  effect: SharingEffect,
  fixture: SharingFixture,
  control: Boolean = false,
  backdrop: Boolean = false,
) {
  CompositionLocalProvider(LocalDensity provides Density(1f)) {
    Box(Modifier.size((CELL_WIDTH * 4).dp, CELL_HEIGHT.dp)) {
      Cell(Modifier.offset(SHARED_CELL.dp), effect, remember { HazeState() }, fixture, reference = false)
      Cell(Modifier.offset(REFERENCE_CELL.dp), effect, remember { HazeState() }, fixture, reference = true)
      if (control) {
        Cell(Modifier.offset(CONTROL_CELL.dp), null, remember { HazeState() }, fixture, reference = false)
      }
      if (backdrop) {
        Cell(Modifier.offset(BACKDROP_CELL.dp), effect, remember { HazeState() }, fixture, reference = false, backdrop = true)
      }
    }
  }
}

/**
 * One cell holding a source, an opaque cover, and optionally an effect over the whole cell.
 * The [reference] cell never shares and clips its drawing to the declared bounds itself.
 */
@Composable
private fun Cell(
  modifier: Modifier,
  effect: SharingEffect?,
  state: HazeState,
  fixture: SharingFixture,
  reference: Boolean,
  backdrop: Boolean = false,
  effects: @Composable () -> Unit = {
    if (effect != null) {
      val sources = HazeInput.Sources(state, retention = fixture.retention)
      Box(Modifier.fillMaxSize().then(effect.modifier(if (backdrop) HazeInput.Backdrop(sources) else sources)))
    }
  },
) {
  Box(modifier.size(CELL_WIDTH.dp, CELL_HEIGHT.dp)) {
    if (fixture.sourcePresent) {
      val sourceModifier = if (reference) {
        Modifier.hazeSource(state)
      } else {
        Modifier.hazeSource(state, inputSharing = fixture.sharing, inputSharingOutsets = fixture.edges.toOutsets())
      }
      val clip = if (reference) fixture.edges else null
      Box(
        Modifier
          .offset(SOURCE_X.dp, SOURCE_Y.dp)
          .size(fixture.widthPx.dp, SOURCE_HEIGHT.dp)
          .then(sourceModifier)
          .drawBehind { drawPattern(clip) },
      )
    }
    Box(Modifier.fillMaxSize().background(Color.Black))
    effects()
  }
}

/**
 * Two Glass members in a container sampling the shared source, beside the unshared reference.
 * The members sit either side of the source's centre, so each strip falls inside one of them.
 */
@Composable
private fun ContainerScene(fixture: SharingFixture) {
  CompositionLocalProvider(LocalDensity provides Density(1f)) {
    Box(Modifier.size((CELL_WIDTH * 2).dp, CELL_HEIGHT.dp)) {
      for ((x, reference) in listOf(SHARED_CELL to false, REFERENCE_CELL to true)) {
        val state = remember { HazeState() }
        Cell(Modifier.offset(x.dp), null, state, fixture, reference) {
          Box(Modifier.fillMaxSize().hazeGlassContainer(8.dp)) {
            for (memberX in listOf(20, 120)) {
              Box(
                Modifier
                  .offset(memberX.dp)
                  .size(80.dp, CELL_HEIGHT.dp)
                  .hazeGlass(
                    input = HazeInput.Sources(state),
                    style = PassthroughGlassStyle,
                    performanceMode = HazePerformanceMode.Quality,
                  ),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * An outer source holding an inner source, each sampled by its own effect over a black cover.
 * Both share. The inner effect covers part of the inner source and the outer-only area beside it,
 * so it can only show the inner state; the outer effect covers everything, so it shows the outer
 * state with the inner source drawn inside it.
 */
@Composable
private fun NestedScene(effect: SharingEffect) {
  CompositionLocalProvider(LocalDensity provides Density(1f)) {
    val outer = remember { HazeState() }
    val inner = remember { HazeState() }
    Box(Modifier.size(280.dp, 240.dp)) {
      Box(
        Modifier
          .offset(20.dp, 20.dp)
          .size(240.dp, 200.dp)
          .hazeSource(outer, inputSharing = HazeInputSharing.Always)
          .drawBehind {
            drawRect(Color.Blue)
            drawRect(Color.Green, Offset(190f, 0f), Size(50f, 200f))
          },
      ) {
        Box(
          Modifier
            .offset(60.dp, 60.dp)
            .size(100.dp, 60.dp)
            .hazeSource(
              inner,
              inputSharing = HazeInputSharing.Always,
              inputSharingOutsets = LayerOutsets(left = 16.dp, top = 16.dp, right = 0.dp, bottom = 0.dp),
            )
            .drawBehind {
              drawRect(Color.Yellow, Offset(-40f, 0f), Size(40f, size.height))
              drawRect(Color.Yellow, Offset(0f, -40f), Size(size.width, 40f))
              drawRect(Color.Red)
            },
        )
      }
      Box(Modifier.fillMaxSize().background(Color.Black))
      Box(Modifier.fillMaxSize().then(effect.modifier(HazeInput.Sources(outer))))
      // Backs the inner effect, which draws nothing outside its source, so the outer one stays out.
      Box(Modifier.offset(40.dp, 40.dp).size(90.dp, 100.dp).background(Color.Black))
      Box(Modifier.offset(40.dp, 40.dp).size(90.dp, 100.dp).then(effect.modifier(HazeInput.Sources(inner))))
    }
  }
}

// Root pixels. Inner source: (80, 80) to (180, 140), with 16px outsets on its left and top.
private val NestedProbes = listOf(
  // Inner effect: the inner source and its outsets, but nothing of the outer source beside them.
  Triple(110, 110, Color.Red),
  Triple(72, 110, Color.Yellow),
  Triple(100, 72, Color.Yellow),
  Triple(50, 110, Color.Black),
  Triple(100, 50, Color.Black),
  // Outer effect: the outer source with the inner one inside, clipped to its outsets there too.
  Triple(155, 110, Color.Red),
  Triple(155, 72, Color.Yellow),
  Triple(155, 50, Color.Blue),
  Triple(200, 110, Color.Blue),
  Triple(235, 100, Color.Green),
  Triple(200, 200, Color.Blue),
  Triple(10, 10, Color.Black),
)

private val Orange = Color(0xFFFF8000)

/**
 * Quadrants inside the bounds, then a distinct strip on every side that reaches 56px beyond the
 * edge, past any declared outset. [clip] explicitly bounds the drawing to those outsets.
 */
private fun DrawScope.drawPattern(clip: Edges?) {
  val width = size.width
  val height = size.height
  val draw: DrawScope.() -> Unit = {
    drawRect(Color.Yellow, Offset(-56f, 0f), Size(56f, height))
    drawRect(Color.Cyan, Offset(0f, -56f), Size(width, 56f))
    drawRect(Color.Magenta, Offset(width, 0f), Size(56f, height))
    drawRect(Orange, Offset(0f, height), Size(width, 56f))
    drawRect(Color.Red, Offset(0f, 0f), Size(40f, 40f))
    drawRect(Color.Green, Offset(40f, 0f), Size(width - 40f, 40f))
    drawRect(Color.Blue, Offset(0f, 40f), Size(40f, height - 40f))
    drawRect(Color.White, Offset(40f, 40f), Size(width - 40f, height - 40f))
  }
  if (clip == null) {
    draw()
  } else {
    clipRect(-clip.left.toFloat(), -clip.top.toFloat(), width + clip.right, height + clip.bottom) { draw() }
  }
}

/** Either side of the quadrant boundaries, and against every layout edge. */
private fun sourceInterior(width: Int): List<Probe> = listOf(
  Probe(20, 20, Color.Red),
  Probe(35, 20, Color.Red),
  Probe(45, 20, Color.Green),
  Probe(60, 20, Color.Green),
  Probe(width - 5, 20, Color.Green),
  Probe(20, 35, Color.Red),
  Probe(20, 45, Color.Blue),
  Probe(20, 60, Color.Blue),
  Probe(20, SOURCE_HEIGHT - 5, Color.Blue),
  Probe(60, 60, Color.White),
)

/** Interior pixels inside a container member: members cover source x in [-40, 40) and [60, 140). */
private val ContainerInterior = listOf(
  Probe(20, 20, Color.Red),
  Probe(20, 60, Color.Blue),
  Probe(70, 20, Color.Green),
  Probe(70, 60, Color.White),
)

/** Overflow past the declared outsets, inside a container member, that only an unshared source shows. */
private val ContainerUnsharedProbes = listOf(
  Triple(-35, 40, Color.Yellow),
  Triple(20, -35, Color.Cyan),
  Triple(125, 20, Color.Magenta),
  Triple(20, 125, Orange),
)

/** A pixel in source-local coordinates and the colour the effect should draw there. */
private class Probe(val x: Int, val y: Int, val color: Color) {
  fun withColor(color: Color) = Probe(x, y, color)

  override fun toString() = "($x, $y)"
}

/**
 * Probes for a source [width] wide while sharing clips to [edges]. Each side is probed inside
 * the declared outset and 5px beyond it, where only the strip or the cover can show.
 */
private fun probes(
  edges: Edges,
  width: Int = 80,
  interior: List<Probe> = sourceInterior(width),
  topX: Int = width / 2,
): List<Probe> = buildList {
  addAll(interior)
  // Beyond each layout edge, a strip shows only up to the declared outset.
  fun side(edge: Int, color: Color, at: (Int) -> Probe) {
    add(at(5).withColor(if (edge > 0) color else Color.Black))
    if (edge >= 10) {
      add(at(edge - 5).withColor(color))
      add(at(edge / 2).withColor(color))
    }
    add(at(edge + 5).withColor(Color.Black))
  }
  side(edges.left, Color.Yellow) { Probe(-it, 40, Color.Black) }
  side(edges.top, Color.Cyan) { Probe(topX, -it, Color.Black) }
  side(edges.right, Color.Magenta) { Probe(width + it - 1, 20, Color.Black) }
  side(edges.bottom, Orange) { Probe(20, SOURCE_HEIGHT + it - 1, Color.Black) }
}

private fun PixelMap.assertProbes(
  cellX: Int,
  edges: Edges,
  samples: Boolean,
  width: Int = 80,
  interior: List<Probe> = sourceInterior(width),
  topX: Int = width / 2,
) {
  for (probe in probes(edges, width, interior, topX)) {
    val expected = if (samples) probe.color else Color.Black
    assertColor(this[cellX + SOURCE_X + probe.x, SOURCE_Y + probe.y], expected, probe)
  }
}

private fun assertColor(actual: Color, expected: Color, at: Any, tolerance: Float = PROBE_TOLERANCE) {
  if (abs(actual.red - expected.red) <= tolerance &&
    abs(actual.green - expected.green) <= tolerance &&
    abs(actual.blue - expected.blue) <= tolerance
  ) {
    return
  }
  assertThat(actual.red, "red at $at").isCloseTo(expected.red, tolerance)
  assertThat(actual.green, "green at $at").isCloseTo(expected.green, tolerance)
  assertThat(actual.blue, "blue at $at").isCloseTo(expected.blue, tolerance)
}

private fun PixelMap.assertPixel(cellX: Int, x: Int, y: Int, expected: Color, samples: Boolean) {
  assertColor(this[cellX + SOURCE_X + x, SOURCE_Y + y], if (samples) expected else Color.Black, "($x, $y)")
}

/** Every pixel of the cell at [cellX] equals the same pixel of the cell at [otherCellX]. */
private fun PixelMap.assertCellsMatch(cellX: Int, otherCellX: Int) {
  for (y in 0 until CELL_HEIGHT) {
    for (x in 0 until CELL_WIDTH) {
      assertColor(this[cellX + x, y], this[otherCellX + x, y], "($x, $y) of cell $cellX against $otherCellX", REFERENCE_TOLERANCE)
    }
  }
}

/** Every pixel of the cell at [cellX] equals that cell in the [fresh] capture. */
private fun PixelMap.assertSameCell(fresh: PixelMap, cellX: Int) {
  for (y in 0 until CELL_HEIGHT) {
    for (x in 0 until CELL_WIDTH) {
      assertColor(this[cellX + x, y], fresh[cellX + x, y], "($x, $y) against the fresh state", REFERENCE_TOLERANCE)
    }
  }
}

private const val PROBE_TOLERANCE = 0.06f

/**
 * Compares the layout bounds of the shared cell with the unshared reference, away from the outer
 * pixels where a blur legitimately reaches the strip the reference does not sample.
 */
private fun PixelMap.assertMatchesReference(width: Int, samples: Boolean) {
  // The reference itself must show the source, or matching it proves nothing.
  if (samples) assertColor(this[REFERENCE_CELL + SOURCE_X + 20, SOURCE_Y + 20], Color.Red, "reference interior")
  for (y in SOURCE_Y + 5 until SOURCE_Y + SOURCE_HEIGHT - 5) {
    for (x in SOURCE_X + 5 until SOURCE_X + width - 5) {
      assertColor(this[SHARED_CELL + x, y], this[REFERENCE_CELL + x, y], "($x, $y) against reference", REFERENCE_TOLERANCE)
    }
  }
}

private const val REFERENCE_TOLERANCE = 0.02f
