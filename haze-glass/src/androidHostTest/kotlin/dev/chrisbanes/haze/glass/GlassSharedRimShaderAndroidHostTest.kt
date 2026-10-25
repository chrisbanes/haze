// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, InternalComposeUiApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.createScaledContentLayer
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class GlassSharedRimShaderAndroidHostTest : ScreenshotTest() {
  @Test
  fun createGlassRimBrushProvider_twiceForSameVariant_reusesCompiledEffect() {
    for (merged in listOf(false, true)) {
      val first = rimProvider(merged)
      val second = rimProvider(merged)
      assertThat(second).isNotSameInstanceAs(first)
      assertThat(second.shader).isSameInstanceAs(first.shader)
      assertThat(second(rimKey(Color.Red))).isSameInstanceAs(first(rimKey(Color.Blue)))
    }
  }

  @Test
  fun createGlassMergedBackgroundShaderProvider_twice_reusesCompiledEffect() {
    val first = mergedBackgroundProvider()
    val second = mergedBackgroundProvider()
    assertThat(second).isNotSameInstanceAs(first)
    assertThat(second.shader).isSameInstanceAs(first.shader)
  }

  @Test
  fun createGlassRimBrushProvider_mergedAndStandalone_useDistinctEffects() {
    assertThat(rimProvider(merged = true).shader).isNotSameInstanceAs(rimProvider(merged = false).shader)
  }

  @Test
  fun sharedRimShader_replayedLayer_drawsItsOwnUniforms() {
    val providers = listOf(rimProvider(merged = false), rimProvider(merged = false))
    assertThat(providers[1].shader).isSameInstanceAs(providers[0].shader)

    runReplayScenario(
      record = { index, layer, color ->
        recordGlassRimLayer(layer, size.toIntSize(), providers[index], rimKey(color, size), supportsGlassRimBrush())
      },
      // The rim fades towards the centre, so sample just inside the left edge.
      sample = { bounds -> this[(bounds.left + 2f).roundToInt(), bounds.center.y.roundToInt()] },
    )
  }

  @Test
  fun sharedMergedBackgroundShader_replayedScaledContentLayer_drawsItsOwnUniforms() {
    val providers = listOf(mergedBackgroundProvider(), mergedBackgroundProvider())
    assertThat(providers[1].shader).isSameInstanceAs(providers[0].shader)
    val context = CapturingContext()

    runReplayScenario(
      record = { index, layer, color ->
        createScaledContentLayer(
          context = context,
          backgroundColor = Color.Transparent,
          scaleFactor = 1f,
          layerSize = size,
          existingLayer = layer,
          backgroundShader = { providers[index](Offset.Zero, mergedField(mergedMember(0f, 0f, size.width, size.height, backgroundColor = color))) },
        )
      },
      sample = { bounds -> this[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()] },
    )
  }

  /**
   * Draws two boxes through retained layers that share one compiled program. Box A changes colour
   * and records again, then box B's display list is dropped so Compose replays its block. Box B must
   * keep drawing its own colour: a provider invoked outside the recorded block would show A's.
   */
  private fun runReplayScenario(
    record: DrawScope.(index: Int, layer: GraphicsLayer, color: Color) -> Unit,
    sample: PixelMap.(bounds: Rect) -> Color,
  ) {
    val phases = arrayOf(mutableIntStateOf(0), mutableIntStateOf(0))
    val redraws = arrayOf(mutableIntStateOf(0), mutableIntStateOf(0))
    val colors = arrayOf(mutableStateOf(Color.Red), mutableStateOf(Color.Green))
    lateinit var layerB: GraphicsLayer

    runScreenshotTest {
      setContent {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
          Row {
            val layerA = rememberGraphicsLayer()
            layerB = rememberGraphicsLayer()
            Box(
              Modifier.size(48.dp)
                .drawRetained(0, layerA, phases[0], redraws[0], colors[0]::value, record)
                .testTag("a"),
            )
            Box(
              Modifier.size(48.dp)
                .drawRetained(1, layerB, phases[1], redraws[1], colors[1]::value, record)
                .testTag("b"),
            )
          }
        }
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      var pixels = captureRootPixels()
      val firstA = pixels.sample(boundsOf("a"))
      val firstB = pixels.sample(boundsOf("b"))
      assertThat(firstA.red).isGreaterThan(0.8f)
      assertThat(firstB.green).isGreaterThan(0.8f)

      // Only A records again. B's retained layer keeps what it recorded.
      composeTestRule.runOnIdle {
        colors[0].value = Color.Blue
        phases[0].intValue++
        redraws[1].intValue++
      }
      waitForIdle()
      pixels = captureRootPixels()
      val secondA = pixels.sample(boundsOf("a"))
      assertThat(secondA.blue).isGreaterThan(0.8f)
      assertThat(secondA.red).isLessThan(0.2f)
      assertThat(pixels.sample(boundsOf("b"))).isEqualTo(firstB)

      // The platform drops B's display list, so Compose replays B's block on the next draw.
      composeTestRule.runOnIdle {
        discardDisplayList(layerB)
        redraws[1].intValue++
      }
      waitForIdle()
      assertThat(captureRootPixels().sample(boundsOf("b"))).isEqualTo(firstB)
    }
  }

  private fun ScreenshotUiTest.boundsOf(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

  /** Emulates the platform dropping a layer's display list, which makes Compose replay its block. */
  private fun discardDisplayList(layer: GraphicsLayer) {
    GraphicsLayer::class.java.methods.single { it.name.startsWith("emulateTrimMemory") }.invoke(layer)
  }

  // Records only when its own phase changes, as the delegate does. A redraw alone replays the layer.
  private fun Modifier.drawRetained(
    index: Int,
    layer: GraphicsLayer,
    phase: MutableIntState,
    redraw: MutableIntState,
    color: () -> Color,
    record: DrawScope.(index: Int, layer: GraphicsLayer, color: Color) -> Unit,
  ): Modifier {
    var recordedPhase = -1
    return drawBehind {
      redraw.intValue
      if (recordedPhase != phase.intValue) {
        record(index, layer, color())
        recordedPhase = phase.intValue
      }
      drawLayer(layer)
    }
  }

  private fun rimProvider(merged: Boolean): AndroidGlassRimBrushProvider {
    val provider = checkNotNull(createGlassRimBrushProvider(merged))
    assertThat(provider).isInstanceOf<AndroidGlassRimBrushProvider>()
    return provider as AndroidGlassRimBrushProvider
  }

  private fun mergedBackgroundProvider(): AndroidGlassMergedBackgroundShaderProvider {
    val provider = checkNotNull(createGlassMergedBackgroundShaderProvider())
    assertThat(provider).isInstanceOf<AndroidGlassMergedBackgroundShaderProvider>()
    return provider as AndroidGlassMergedBackgroundShaderProvider
  }

  private fun rimKey(edgeShadow: Color, size: Size = Size(48f, 48f)) = GlassRimEffectKey(
    coordinates = GlassCoordinates(size, Offset.Zero, size, 1f),
    specularIntensity = 0f,
    edgeShadow = edgeShadow,
    specularExponent = 1f,
    edgeSoftnessPx = 8f,
    cornerRadii = CornerRadii(0f, 0f, 0f, 0f),
    lightPosition = Offset.Zero,
    sampleStepPx = 1f,
  )

  /** A runtime draw scope with no input. The layer is supplied, so it never needs a graphics context. */
  private class CapturingContext(
    drawScope: CanvasDrawScope = CanvasDrawScope(),
  ) : HazeEffectRuntimeDrawScope,
    DrawScope by drawScope {
    override val modifierBounds: Rect = Rect.Zero
    override val sampling: HazeSampling = HazeSampling.FullResolution
    override val modifierSize: Size = Size.Zero
    override val layerSize: Size = Size.Zero
    override val layerOffset: Offset = Offset.Zero
    override val hasDrawableInput: Boolean = true
    override val inputSnapshot: HazeEffectInputSnapshot? = null
    override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

    override fun requirePlatformContext(): PlatformContext = error("Unused")

    override fun requireGraphicsContext(): GraphicsContext = error("Unused")

    @Suppress("UNCHECKED_CAST")
    override fun <T> currentValueOf(local: CompositionLocal<T>): T = LayoutDirection.Ltr as T

    override fun invalidateDraw() = Unit

    override fun drawInput() = Unit

    override fun DrawScope.drawInput() = Unit
  }
}
