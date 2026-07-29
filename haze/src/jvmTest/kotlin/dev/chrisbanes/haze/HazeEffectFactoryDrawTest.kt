// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeEffectFactoryDrawTest {

  @Test
  fun drawInput_drawsSelectedSourceContent() = runComposeUiTest {
    val state = HazeState()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state)
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testTag("effect")
            .hazeEffect(
              factory = InputDrawingFactory,
              input = HazeInput.Sources(state),
              style = DrawStyle(),
            ),
        )
      }
    }

    val pixels = onNodeWithTag("effect").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
  }

  @Test
  fun drawInput_drawsModifierOwnContent() = runComposeUiTest {
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("effect")
          .hazeEffect(
            factory = InputDrawingFactory,
            input = HazeInput.Content,
            style = DrawStyle(),
          )
          .background(Color.Red),
      )
    }

    val pixels = onNodeWithTag("effect").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
  }

  @Test
  fun styleReplacement_updatesOutputWithoutRecreatingRenderer() = runComposeUiTest {
    val factory = RecordingDrawFactory()
    val style = mutableStateOf(DrawStyle(overlay = Color.Red))

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("effect")
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = style.value,
          )
          .background(Color.White),
      )
    }

    var pixels = onNodeWithTag("effect").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)

    style.value = DrawStyle(overlay = Color.Blue)
    waitForIdle()

    pixels = onNodeWithTag("effect").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Blue)
    assertThat(factory.createCalls).isEqualTo(1)
  }

  @Test
  fun drawScope_exposesExpandedModifierBoundsAndEverySamplingValue() = runComposeUiTest {
    val factory = RecordingDrawFactory(expandBy = 10f)
    val sampling = mutableStateOf<HazeSampling>(HazeSampling.Default)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = DrawStyle(),
            sampling = sampling.value,
          ),
      )
    }
    waitForIdle()

    val renderer = factory.renderer
    assertThat(renderer.lastModifierBounds).isEqualTo(Rect(10f, 10f, 110f, 110f))
    assertThat(renderer.lastSampling).isEqualTo(HazeSampling.Default)

    val choices = listOf(
      HazeSampling.FullResolution,
      HazeSampling.Adaptive,
      HazeSampling.Fixed(0.6f),
    )
    for (choice in choices) {
      sampling.value = choice
      waitForIdle()
      assertThat(renderer.lastSampling).isEqualTo(choice)
    }
    assertThat(factory.createCalls).isEqualTo(1)
  }

  @Test
  fun drawScopeReads_invalidateOnlyDraw() = runComposeUiTest {
    val drawState = mutableIntStateOf(0)
    val drawLocal = mutableIntStateOf(0)
    val factory = ObservationFactory(
      drawState = { drawState.intValue },
      drawLocal = { currentValueOf(LocalDrawObservation) },
    )

    setContent {
      CompositionLocalProvider(LocalDrawObservation provides drawLocal.intValue) {
        Box(
          Modifier
            .size(100.dp)
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = DrawStyle(),
            ),
        )
      }
    }
    waitForIdle()

    val renderer = factory.renderer
    val initialDraws = renderer.drawCalls
    val initialLayouts = renderer.layoutCalls

    drawState.intValue++
    waitForIdle()

    assertThat(renderer.drawCalls).isGreaterThan(initialDraws)
    assertThat(renderer.layoutCalls).isEqualTo(initialLayouts)

    val drawsBeforeLocalChange = renderer.drawCalls
    drawLocal.intValue++
    waitForIdle()

    assertThat(renderer.drawCalls).isGreaterThan(drawsBeforeLocalChange)
    assertThat(renderer.layoutCalls).isEqualTo(initialLayouts)
  }

  @Test
  fun layoutScopeReads_recalculateBoundsAndDependentDraw() = runComposeUiTest {
    val layoutState = mutableIntStateOf(5)
    val layoutLocal = mutableIntStateOf(7)
    val factory = ObservationFactory(
      layoutState = { layoutState.intValue },
      layoutLocal = { currentValueOf(LocalLayoutObservation) },
    )

    setContent {
      CompositionLocalProvider(LocalLayoutObservation provides layoutLocal.intValue) {
        Box(
          Modifier
            .size(100.dp)
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = DrawStyle(),
            ),
        )
      }
    }
    waitForIdle()

    val renderer = factory.renderer
    val initialLayouts = renderer.layoutCalls
    val initialDraws = renderer.drawCalls

    layoutState.intValue++
    waitForIdle()

    assertThat(renderer.layoutCalls).isGreaterThan(initialLayouts)
    assertThat(renderer.drawCalls).isGreaterThan(initialDraws)
    assertThat(renderer.lastExpansion).isEqualTo(13f)

    val layoutsBeforeLocalChange = renderer.layoutCalls
    layoutLocal.intValue++
    waitForIdle()

    assertThat(renderer.layoutCalls).isGreaterThan(layoutsBeforeLocalChange)
    assertThat(renderer.lastExpansion).isEqualTo(14f)
  }

  @Test
  fun styleReplacement_recalculatesBoundsWithoutRecreatingRenderer() = runComposeUiTest {
    val factory = RecordingDrawFactory()
    val style = mutableStateOf(DrawStyle(expandBy = 5f))

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = style.value,
          ),
      )
    }
    waitForIdle()
    val renderer = factory.renderer
    assertThat(renderer.lastLayoutStyle).isEqualTo(DrawStyle(expandBy = 5f))

    style.value = DrawStyle(expandBy = 12f)
    waitForIdle()

    assertThat(factory.renderer).isEqualTo(renderer)
    assertThat(factory.createCalls).isEqualTo(1)
    assertThat(renderer.lastLayoutStyle).isEqualTo(DrawStyle(expandBy = 12f))
  }

  @Test
  fun expandLayerBoundsFalse_skipsRendererExpansion() = runComposeUiTest {
    val factory = RecordingDrawFactory(expandBy = 10f)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = DrawStyle(),
            expandLayerBounds = false,
          ),
      )
    }
    waitForIdle()

    assertThat(factory.renderer.layoutCalls).isEqualTo(0)
    assertThat(factory.renderer.lastModifierBounds).isEqualTo(Rect(0f, 0f, 100f, 100f))
  }
}

@Poko
private class DrawStyle(
  val overlay: Color = Color.Transparent,
  val expandBy: Float = 0f,
)

private val LocalDrawObservation = compositionLocalOf { 0 }
private val LocalLayoutObservation = compositionLocalOf { 0 }

private object InputDrawingFactory : HazeEffectFactory<DrawStyle> {
  override fun createRenderer(): HazeEffectRenderer<DrawStyle> = DrawingRenderer()
}

private class RecordingDrawFactory(
  private val expandBy: Float = 0f,
) : HazeEffectFactory<DrawStyle> {
  var createCalls = 0
  lateinit var renderer: DrawingRenderer

  override fun createRenderer(): HazeEffectRenderer<DrawStyle> {
    createCalls++
    return DrawingRenderer(expandBy).also { renderer = it }
  }
}

private class DrawingRenderer(
  private val expandBy: Float = 0f,
) : HazeEffectRenderer<DrawStyle> {
  var lastModifierBounds: Rect? = null
  var lastSampling: HazeSampling? = null
  var lastLayoutStyle: DrawStyle? = null
  var drawCalls = 0
  var layoutCalls = 0

  override fun HazeEffectDrawScope.draw(style: DrawStyle) {
    drawCalls++
    lastModifierBounds = modifierBounds
    lastSampling = sampling
    drawInput()
    if (style.overlay.isSpecified) {
      drawRect(style.overlay)
    }
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: DrawStyle): Rect {
    layoutCalls++
    lastLayoutStyle = style
    return modifierBounds.inflate(maxOf(expandBy, style.expandBy))
  }
}

private class ObservationFactory(
  private val drawState: () -> Int = { 0 },
  private val drawLocal: HazeEffectDrawScope.() -> Int = { 0 },
  private val layoutState: () -> Int = { 0 },
  private val layoutLocal: HazeEffectLayoutScope.() -> Int = { 0 },
) : HazeEffectFactory<DrawStyle> {
  lateinit var renderer: ObservationRenderer

  override fun createRenderer(): HazeEffectRenderer<DrawStyle> {
    return ObservationRenderer(
      drawState = drawState,
      drawLocal = drawLocal,
      layoutState = layoutState,
      layoutLocal = layoutLocal,
    ).also { renderer = it }
  }
}

private class ObservationRenderer(
  private val drawState: () -> Int,
  private val drawLocal: HazeEffectDrawScope.() -> Int,
  private val layoutState: () -> Int,
  private val layoutLocal: HazeEffectLayoutScope.() -> Int,
) : HazeEffectRenderer<DrawStyle> {
  var drawCalls = 0
  var layoutCalls = 0
  var lastExpansion = 0f

  override fun HazeEffectDrawScope.draw(style: DrawStyle) {
    drawCalls++
    drawState()
    drawLocal()
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: DrawStyle): Rect {
    layoutCalls++
    lastExpansion = (layoutState() + layoutLocal()).toFloat()
    return modifierBounds.inflate(lastExpansion)
  }
}
