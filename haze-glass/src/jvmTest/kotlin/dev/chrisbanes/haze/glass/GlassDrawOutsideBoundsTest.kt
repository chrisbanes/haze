// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class GlassDrawOutsideBoundsTest {
  @Test
  fun glassScale_singleSurfaceGrowsOutsideLayoutRect_runtime() = assertPressGrowsOutsideLayoutRect(fallback = false)

  @Test
  fun glassScale_singleSurfaceGrowsOutsideLayoutRect_fallback() = assertPressGrowsOutsideLayoutRect(fallback = true)

  @Test
  fun glassScale_acceptsUpToMaximumAndRejectsAbove() {
    val response = GlassInteractionScopeImpl().apply { scale(1.2f) }.build()
    assertThat(response.scaleX?.value).isEqualTo(1.2f)
    assertFailure { GlassInteractionScopeImpl().scale(1.21f) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage("scaleX must be $INTERACTION_SCALE_DOMAIN")
  }

  @Test
  fun drawOutset_inflatesClipAndGroupComposite_runtime() = assertDrawOutsetInflatesComposite(fallback = false)

  @Test
  fun drawOutset_inflatesClipAndGroupComposite_fallback() = assertDrawOutsetInflatesComposite(fallback = true)

  private fun assertDrawOutsetInflatesComposite(fallback: Boolean) {
    // A green bar 4px wide just left of the node, inside a 6px outset but outside the node.
    val previousDraw = glassOutsetDrawForTest
    glassOutsetDrawForTest = { drawRect(Color.Green, topLeft = Offset(-4f, 30f), size = Size(4f, 20f)) }
    try {
      lateinit var baseline: PixelMap
      runComposeUiTest { baseline = surface(GlassStyle { alpha(0.5f) }, fallback).capture() }
      runComposeUiTest {
        val fixture = surface(
          GlassStyle {
            drawOutset(6.dp)
            alpha(0.5f)
          },
          fallback,
        )

        assertThat(fixture.effect.nodeClipOutsetPx()).isEqualTo(6f)
        if (fallback) {
          val delegate = fixture.effect.lastDrawDelegateForTest as FallbackGlassDelegate
          assertThat(delegate.groupAlphaSizeForTest).isEqualTo(IntSize(112, 92))
        } else {
          assertThat(checkNotNull(fixture.effect.preparedRender).groupCompositeSize).isEqualTo(IntSize(112, 92))
        }
        val capture = fixture.capture()
        // Without an outset the bar is clipped away, leaving the white stripe behind it.
        assertThat(baseline[98, 120]).isEqualTo(Color.White)
        // With one, it survives the clip and the composite at the group alpha: green over white.
        val outset = capture[98, 120]
        assertThat(outset.red).isCloseTo(0.5f, 0.05f)
        assertThat(outset.green).isCloseTo(1f, 0.05f)
        assertThat(outset.blue).isCloseTo(0.5f, 0.05f)
        // A misplaced composite would shift the material within the node, and nothing else may
        // leak into the outset ring around it.
        assertThat(regionDifference(capture, baseline, 100, 80, 200, 160)).isLessThan(1f / 255f)
        assertThat(regionDifference(capture, baseline, 94, 74, 206, 80)).isLessThan(1f / 255f)
        assertThat(regionDifference(capture, baseline, 94, 160, 206, 166)).isLessThan(1f / 255f)
        assertThat(regionDifference(capture, baseline, 94, 80, 100, 108)).isLessThan(1f / 255f)
        assertThat(regionDifference(capture, baseline, 94, 132, 100, 160)).isLessThan(1f / 255f)
        assertThat(regionDifference(capture, baseline, 200, 80, 206, 160)).isLessThan(1f / 255f)
      }
    } finally {
      glassOutsetDrawForTest = previousDraw
    }
  }

  @Test
  fun edgeOutline_drawsOutsideShape_runtime() {
    assertOutlineDrawsOutsideShape(fallback = false, expandLayerBounds = false)
    assertOutlineDrawsOutsideShape(fallback = false, expandLayerBounds = true)
  }

  @Test
  fun edgeOutline_drawsOutsideShape_fallback() {
    assertOutlineDrawsOutsideShape(fallback = true, expandLayerBounds = false)
    assertOutlineDrawsOutsideShape(fallback = true, expandLayerBounds = true)
  }

  @Test
  fun edgeOutline_disabled_matchesNoOutline_runtime() = assertDisabledOutlineMatchesNoOutline(fallback = false)

  @Test
  fun edgeOutline_disabled_matchesNoOutline_fallback() = assertDisabledOutlineMatchesNoOutline(fallback = true)

  @Test
  fun edgeOutline_outlineOnly_drawsWithoutRimLighting() {
    val unlit = GlassStyle {
      specularIntensity(0f)
      edgeShadow(Color.Transparent)
    }
    runComposeUiTest {
      val fixture = surface(unlit, fallback = false)
      fixture.capture()
      assertThat((fixture.effect.lastDrawDelegateForTest as RuntimeShaderGlassDelegate).rimRecordCount).isEqualTo(0)
    }
    runComposeUiTest {
      val capture = surface(unlit.then { edgeOutline(Color.Green, 4.dp) }, fallback = false).capture()
      assertGreen(capture, 98, 120)
    }
  }

  @Test
  fun edgeOutline_squareSurfaceOverMatchingSource_drawsOutsideShape_runtime() =
    assertSquareSurfaceOutlineDrawsOutsideShape(fallback = false)

  @Test
  fun edgeOutline_squareSurfaceOverMatchingSource_drawsOutsideShape_fallback() =
    assertSquareSurfaceOutlineDrawsOutsideShape(fallback = true)

  private fun assertSquareSurfaceOutlineDrawsOutsideShape(fallback: Boolean) {
    // A square, unsoftened surface does not clip to its node, so its layer prefers the source bounds.
    val square = GlassStyle {
      shape(RoundedCornerShape(0.dp))
      edgeSoftness(0.dp)
    }
    runComposeUiTest {
      val fixture = surface(square, fallback, sourceMatchesNode = true)
      assertThat(fixture.effect.shouldPreferClipToInputBounds()).isTrue()
    }
    for (expandLayerBounds in listOf(false, true)) {
      lateinit var baseline: PixelMap
      runComposeUiTest { baseline = surface(square, fallback, expandLayerBounds, sourceMatchesNode = true).capture() }
      runComposeUiTest {
        val fixture = surface(
          square.then { edgeOutline(Color.Green, 4.dp) },
          fallback,
          expandLayerBounds = expandLayerBounds,
          sourceMatchesNode = true,
        )
        assertThat(fixture.effect.shouldClipToNodeBounds()).isFalse()
        assertThat(fixture.effect.shouldPreferClipToInputBounds()).isFalse()
        val capture = fixture.capture()
        val variant = "fallback=$fallback expand=$expandLayerBounds"
        for ((x, y) in listOf(98 to 120, 201 to 120, 150 to 78, 150 to 161)) {
          assertGreen(capture, x, y, "$variant ($x, $y)")
        }
        // 7px outside, and 3px inside, the outline leaves the surface unchanged.
        for ((x, y) in listOf(92 to 120, 207 to 120, 150 to 72, 150 to 167, 103 to 120, 196 to 120, 150 to 83, 150 to 156)) {
          assertThat(pixelDifference(capture, baseline, x, y), "$variant ($x, $y)").isLessThan(1f / 255f)
        }
      }
    }
  }

  private fun assertOutlineDrawsOutsideShape(fallback: Boolean, expandLayerBounds: Boolean) {
    lateinit var baseline: PixelMap
    runComposeUiTest { baseline = surface(GlassStyle, fallback, expandLayerBounds).capture() }
    runComposeUiTest {
      val capture = surface(GlassStyle { edgeOutline(Color.Green, 4.dp) }, fallback, expandLayerBounds).capture()
      val variant = "fallback=$fallback expand=$expandLayerBounds"
      // The node is (100, 80)-(200, 160); each probe is 1.5px outside an edge midpoint.
      for ((x, y) in listOf(98 to 120, 201 to 120, 150 to 78, 150 to 161)) {
        assertGreen(capture, x, y, "$variant ($x, $y)")
      }
      // 7px outside, and 3px inside, the outline leaves the surface unchanged.
      for ((x, y) in listOf(92 to 120, 207 to 120, 150 to 72, 150 to 167, 103 to 120, 196 to 120, 150 to 83, 150 to 156)) {
        assertThat(pixelDifference(capture, baseline, x, y), "$variant ($x, $y)").isLessThan(1f / 255f)
      }
    }
  }

  private fun assertDisabledOutlineMatchesNoOutline(fallback: Boolean) {
    lateinit var baseline: PixelMap
    runComposeUiTest { baseline = surface(GlassStyle, fallback).capture() }
    for (style in listOf(GlassStyle { edgeOutline(Color.Green, 0.dp) }, GlassStyle { edgeOutline(Color.Transparent, 4.dp) })) {
      runComposeUiTest {
        val fixture = surface(style, fallback)
        assertThat(fixture.effect.nodeClipOutsetPx()).isEqualTo(0f)
        assertThat(regionDifference(fixture.capture(), baseline, 0, 0, 300, 240)).isEqualTo(0f)
      }
    }
  }

  @Test
  fun zeroExtent_clipAndCompositeMatchToday() {
    runComposeUiTest {
      val runtime = surface(GlassStyle { alpha(0.5f) }, fallback = false)
      assertThat(runtime.effect.nodeClipOutsetPx()).isEqualTo(0f)
      assertThat(checkNotNull(runtime.effect.preparedRender).groupCompositeSize).isEqualTo(IntSize(100, 80))
    }
    runComposeUiTest {
      val fallback = surface(GlassStyle { alpha(0.5f) }, fallback = true)
      assertThat(fallback.effect.nodeClipOutsetPx()).isEqualTo(0f)
      val delegate = fallback.effect.lastDrawDelegateForTest as FallbackGlassDelegate
      assertThat(delegate.groupAlphaSizeForTest).isEqualTo(IntSize(100, 80))
    }
  }

  @Test
  fun pressAnimation_singleSurfaceLayerBoundsStable() = runComposeUiTest {
    val linear = tween<Float>(300, easing = LinearEasing)
    val fixture = surface(GlassStyle { pressed { animate(linear, linear) { scale(1.2f) } } }, fallback = false)
    mainClock.autoAdvance = false
    runBlocking { fixture.source.emit(PressInteraction.Press(Offset(50f, 40f))) }
    mainClock.advanceTimeByFrame()
    val resolves = fixture.effect.layerPaddingResolveCountForTest
    val sampleSize = checkNotNull(fixture.effect.preparedRender).params.coordinates.sampleSize
    val scales = mutableSetOf<Float>()
    repeat(10) {
      mainClock.advanceTimeByFrame()
      fixture.capture()
      scales += fixture.effect.currentInteractionState.scaleX
      assertThat(fixture.effect.layerPaddingResolveCountForTest).isEqualTo(resolves)
      assertThat(checkNotNull(fixture.effect.preparedRender).params.coordinates.sampleSize).isEqualTo(sampleSize)
    }
    assertThat(scales.size, "press animated").isGreaterThan(1)
    mainClock.autoAdvance = true
    waitForIdle()
  }

  private fun assertPressGrowsOutsideLayoutRect(fallback: Boolean) = runComposeUiTest {
    val fixture = surface(GlassStyle { pressed { animate(snap(), snap()) { scale(1.2f) } } }, fallback)
    val idle = fixture.capture()
    runBlocking { fixture.source.emit(PressInteraction.Press(Offset(50f, 40f))) }
    waitForIdle()
    val pressed = fixture.capture()

    // Layout rect is (100, 80)-(200, 160); the 1.2x Center-pivot rect is (90, 72)-(210, 168).
    for ((x, y) in listOf(96 to 120, 203 to 120, 150 to 76, 150 to 163)) {
      assertThat(pixelDifference(pressed, idle, x, y), "grown probe ($x, $y)").isGreaterThan(0.01f)
    }
    for ((x, y) in listOf(85 to 120, 214 to 120, 150 to 67, 150 to 172)) {
      assertThat(pixelDifference(pressed, idle, x, y), "outside probe ($x, $y)").isLessThan(1f / 255f)
    }
  }

  @Test
  fun interpolatedContinuousCorners_clipLikeStaticCorners_runtime() {
    lateinit var static: PixelMap
    runComposeUiTest {
      val fixture = cornerClipSurface(ContinuousRoundedCornerShape(10f))
      static = fixture.capture()
      fixture.assertCapture(Rect(0f, 0f, 100f, 100f), clipsToNode = true)
    }
    runComposeUiTest {
      val fixture = cornerClipSurface(mixedPxCorners)
      val capture = fixture.capture()
      fixture.assertCapture(Rect(0f, 0f, 100f, 100f), clipsToNode = true)
      // Opaque material away from the source, the source at its centre, and the underlay outside a
      // rounded corner.
      assertColor(capture, 100, 70, Color.Red)
      assertColor(capture, 100, 100, Color.Green)
      assertColor(capture, 50, 50, Color.Blue)
      assertThat(regionDifference(capture, static, 50, 50, 150, 150)).isEqualTo(0f)
    }
  }

  @Test
  fun interpolatedContinuousCorners_resizeUpdatesClip_runtime() {
    val controls = mutableMapOf<Int, PixelMap>()
    runComposeUiTest { controls[100] = cornerClipSurface(ContinuousRoundedCornerShape(10f)).capture() }
    runComposeUiTest { controls[30] = cornerClipSurface(ContinuousRoundedCornerShape(0f), sizePx = 30).capture() }
    runComposeUiTest {
      // A genuinely square, unsoftened surface without an outset still crops to its source.
      val square = cornerClipSurface(ContinuousRoundedCornerShape(0f))
      square.capture()
      square.assertCapture(Rect(40f, 40f, 60f, 60f), clipsToNode = false)
    }
    runComposeUiTest {
      val fixture = cornerClipSurface(mixedPxCorners)
      for ((size, rounded) in listOf(100 to true, 30 to false, 100 to true)) {
        fixture.sizePx.value = size
        waitForIdle()
        val capture = fixture.capture()
        assertThat(fixture.effect.attachedContextForTest?.modifierSize).isEqualTo(Size(size.toFloat(), size.toFloat()))
        val expected = if (rounded) Rect(0f, 0f, size.toFloat(), size.toFloat()) else Rect(5f, 5f, 25f, 25f)
        fixture.assertCapture(expected, clipsToNode = rounded)
        assertThat(regionDifference(capture, controls.getValue(size), 50, 50, 50 + size, 50 + size), "size $size")
          .isEqualTo(0f)
      }
    }
  }

  @Test
  fun interpolatedContinuousCorners_densityUpdatesClipAtFixedPixelSize_runtime() = runComposeUiTest {
    // 10 dp → 10% at 1.5 resolves to 5 px at density 2 and to square corners at density 4.
    val shape = Interpolatable.lerp(ContinuousRoundedCornerShape(10.dp), ContinuousRoundedCornerShape(10), 1.5f)
      as ContinuousRoundedCornerShape
    val fixture = cornerClipSurface(shape, density = 2f)
    for ((density, rounded) in listOf(2f to true, 4f to false, 2f to true)) {
      fixture.density.value = density
      waitForIdle()
      fixture.capture()
      assertThat(fixture.effect.attachedContextForTest?.requireDensity()?.density).isEqualTo(density)
      assertThat(fixture.effect.attachedContextForTest?.modifierSize).isEqualTo(Size(100f, 100f))
      fixture.assertCapture(if (rounded) Rect(0f, 0f, 100f, 100f) else Rect(40f, 40f, 60f, 60f), clipsToNode = rounded)
    }
  }

  internal class CornerClipFixture(
    val test: ComposeUiTest,
    val effect: GlassRuntimeEffect,
    val sizePx: MutableState<Int>,
    val density: MutableState<Float>,
  ) {
    fun capture() = test.onNodeWithTag("root").captureToImage().toPixelMap()

    /** Asserts the clip decisions, and the captured rect in material pixels, of the last draw. */
    fun assertCapture(expected: Rect, clipsToNode: Boolean) {
      assertThat(effect.shouldClipToNodeBounds()).isEqualTo(clipsToNode)
      assertThat(effect.shouldPreferClipToInputBounds()).isEqualTo(!clipsToNode)
      assertThat((effect.lastDrawDelegateForTest as RuntimeShaderGlassDelegate).sourceRecordCount).isGreaterThan(0)
      val coordinates = checkNotNull(effect.preparedRender).params.coordinates
      val size = sizePx.value.toFloat()
      assertThat(coordinates.materialSize).isEqualTo(Size(size, size))
      assertThat(coordinates.captureScale).isEqualTo(ScaleFactor(1f, 1f))
      val bounds = checkNotNull(coordinates.captureBounds).translate(-coordinates.materialOrigin)
      assertThat(bounds).isEqualTo(expected)
    }
  }

  internal class SurfaceFixture(
    val test: ComposeUiTest,
    val effect: GlassRuntimeEffect,
    val source: MutableInteractionSource,
  ) {
    fun capture() = test.onNodeWithTag("root").captureToImage().toPixelMap()
  }

  internal companion object {
    fun ComposeUiTest.surface(
      style: GlassStyle,
      fallback: Boolean,
      expandLayerBounds: Boolean = false,
      sourceMatchesNode: Boolean = false,
    ): SurfaceFixture {
      val effect = GlassRuntimeEffect()
      if (fallback) {
        effect.runtimeEffectFactory = GlassRuntimeEffectFactory {
          throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime"))
        }
      }
      val source = MutableInteractionSource()
      val state = HazeState()
      setContent {
        Box(Modifier.size(300.dp, 240.dp).testTag("root").background(Color.White)) {
          val sourceModifier = if (sourceMatchesNode) {
            Modifier.offset(100.dp, 80.dp).size(100.dp, 80.dp)
          } else {
            Modifier.size(300.dp, 240.dp)
          }
          Box(sourceModifier.hazeSource(state).background(Color.White)) {
            repeat(30) { index ->
              Box(
                Modifier.offset((index * 10).dp, 0.dp).size(10.dp, 240.dp)
                  .background(if (index % 2 == 0) Color.Black else Color.White),
              )
            }
          }
          Box(
            Modifier.offset(100.dp, 80.dp).size(100.dp, 80.dp).hazeGlass(
              HazeEffectFactory { effect }, HazeInput.Sources(state),
              // A rounded shape makes Glass clip to its node bounds.
              containerTestStyle(Color.Red).then(GlassStyle { shape(RoundedCornerShape(8.dp)) }).then(style),
              HazePerformanceMode.Quality, expandLayerBounds, source, GlassTransformTarget.MaterialOnly,
              GlassTransformPivot.Center, GlassReducedMotionPolicy.Full,
            ),
          )
        }
      }
      waitForIdle()
      return SurfaceFixture(this, effect, source)
    }

    /** 10 px → 10% at 1.5: 10 px at 100 px, but square at 30 px and at a 1 × 1 px probe. */
    val mixedPxCorners = Interpolatable.lerp(ContinuousRoundedCornerShape(10f), ContinuousRoundedCornerShape(10), 1.5f)
      as ContinuousRoundedCornerShape

    /**
     * An opaque red surface at (50, 50) in a 200 × 200 px blue root, over a 20 × 20 px green source
     * at its centre. Sizes are in pixels, kept fixed across [density] changes.
     */
    fun ComposeUiTest.cornerClipSurface(shape: ContinuousRoundedCornerShape, sizePx: Int = 100, density: Float = 1f): CornerClipFixture {
      val effect = GlassRuntimeEffect()
      val size = mutableStateOf(sizePx)
      val densityState = mutableStateOf(density)
      val state = HazeState()
      val source = MutableInteractionSource()
      val style = containerTestStyle(Color.Transparent, background = Color.Red).then(GlassStyle { shape(shape) })
      setContent {
        Box(Modifier.size(200.dp).testTag("root").background(Color.Blue)) {
          val nodeSize = size.value
          val sourceOffset = 50 + nodeSize / 2 - 10
          // The root density is 1. Only the surface sees density changes.
          Box(Modifier.offset { IntOffset(sourceOffset, sourceOffset) }.size(20.dp).hazeSource(state).background(Color.Green))
          // The parent fixes the pixel size, so a density change leaves the surface's modifiers equal.
          Box(Modifier.offset { IntOffset(50, 50) }.size(nodeSize.dp)) {
            CompositionLocalProvider(LocalDensity provides Density(densityState.value)) {
              Box(
                Modifier.fillMaxSize().hazeGlass(
                  HazeEffectFactory { effect }, HazeInput.Sources(state), style,
                  HazePerformanceMode.Quality, false, source, GlassTransformTarget.MaterialOnly,
                  GlassTransformPivot.Center, GlassReducedMotionPolicy.Full,
                ),
              )
            }
          }
        }
      }
      waitForIdle()
      return CornerClipFixture(this, effect, size, densityState)
    }

    fun assertColor(pixels: PixelMap, x: Int, y: Int, expected: Color) {
      val color = pixels[x, y]
      assertThat(color.red, "($x, $y)").isCloseTo(expected.red, 0.02f)
      assertThat(color.green, "($x, $y)").isCloseTo(expected.green, 0.02f)
      assertThat(color.blue, "($x, $y)").isCloseTo(expected.blue, 0.02f)
    }

    fun assertGreen(pixels: PixelMap, x: Int, y: Int, name: String = "($x, $y)") {
      val color = pixels[x, y]
      assertThat(color.green, name).isGreaterThan(0.7f)
      assertThat(color.red, name).isLessThan(0.3f)
      assertThat(color.blue, name).isLessThan(0.3f)
    }

    fun regionDifference(first: PixelMap, second: PixelMap, left: Int, top: Int, right: Int, bottom: Int): Float {
      var difference = 0f
      var count = 0
      for (y in top until bottom) for (x in left until right) {
        difference += pixelDifference(first, second, x, y)
        count++
      }
      return difference / count
    }

    fun pixelDifference(first: PixelMap, second: PixelMap, x: Int, y: Int): Float {
      val a = first[x, y]
      val b = second[x, y]
      return (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3f
    }
  }
}
