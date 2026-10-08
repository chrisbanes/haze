// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeDrawInvalidationTest : ContextTest() {

  @Test
  fun rendererRequestedBounds_beforeDrawRefreshesActualGeometry() = runComposeUiTest {
    val renderer = attachBoundsRenderer()
    renderer.assertGeometry(29f)
    runOnIdle {
      renderer.expansion = 16f
      renderer.scope.invalidateLayerBounds()
    }
    waitForIdle()
    renderer.assertGeometry(16f)
    assertStableDrawOnlyInvalidations(renderer)
  }

  @Test
  fun rendererRequestedBounds_duringPrepareSurviveUntilNextFrame() = runComposeUiTest {
    val renderer = attachBoundsRenderer()
    renderer.assertGeometry(29f)
    runOnIdle {
      renderer.requestDuringPrepare = true
      renderer.scope.invalidateDraw()
    }
    waitForIdle()
    assertThat(renderer.oldGeometryDuringRequest).isEqualTo(Size(renderer.scope.modifierSize.width + 58f, renderer.scope.modifierSize.height + 58f))
    mainClock.advanceTimeByFrame()
    waitForIdle()
    renderer.assertGeometry(16f)
    assertStableDrawOnlyInvalidations(renderer)
  }

  @Test
  fun rendererRequestedBounds_duringUpdateAreConsumedWithoutAnotherRefresh() = runComposeUiTest {
    val renderer = attachBoundsRenderer()
    val updates = renderer.updateCalls
    val calculations = renderer.layoutCalls
    val draws = renderer.drawCalls
    runOnIdle {
      renderer.requestDuringUpdate = true
      renderer.scope.invalidateLayerBounds()
    }
    waitForIdle()
    renderer.assertGeometry(16f)
    assertThat(renderer.updateCalls).isEqualTo(updates + 1)
    assertThat(renderer.layoutCalls).isEqualTo(calculations + 1)
    assertThat(renderer.drawCalls).isEqualTo(draws + 1)
    mainClock.advanceTimeByFrame()
    waitForIdle()
    assertThat(renderer.updateCalls).isEqualTo(updates + 1)
    assertThat(renderer.layoutCalls).isEqualTo(calculations + 1)
    assertThat(renderer.drawCalls).isEqualTo(draws + 1)
    assertStableDrawOnlyInvalidations(renderer)
  }

  @Test
  fun rendererRequestedBounds_updateThenPrepareDefersOnlyTheLaterRequest() = runComposeUiTest {
    val renderer = attachBoundsRenderer()
    val updates = renderer.updateCalls
    val calculations = renderer.layoutCalls
    runOnIdle {
      renderer.requestDuringUpdate = true
      renderer.requestDuringPrepare = true
      renderer.prepareExpansion = 8f
      renderer.scope.invalidateLayerBounds()
    }
    waitForIdle()
    assertThat(renderer.oldGeometryDuringRequest).isEqualTo(Size(renderer.scope.modifierSize.width + 32f, renderer.scope.modifierSize.height + 32f))
    mainClock.advanceTimeByFrame()
    waitForIdle()
    renderer.assertGeometry(8f)
    assertThat(renderer.updateCalls).isEqualTo(updates + 2)
    assertThat(renderer.layoutCalls).isEqualTo(calculations + 2)
    assertStableDrawOnlyInvalidations(renderer)
  }

  @Test
  fun rendererRequestedBounds_refreshPreservesStateAndCompositionLocalObservation() = runComposeUiTest {
    val renderer = BoundsInvalidatingRenderer().apply { observePadding = true }
    val localPadding = mutableStateOf(0f)
    val state = HazeState()
    val factory = HazeEffectFactory<Unit> { renderer }
    setContent {
      CompositionLocalProvider(LocalBoundsPadding provides localPadding.value) {
        Box(Modifier.size(384.dp)) {
          Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
          Box(
            Modifier.align(Alignment.Center).size(120.dp).hazeEffect(
              factory = factory,
              input = HazeInput.Sources(state),
              style = Unit,
              expandLayerBounds = true,
            ),
          )
        }
      }
    }
    waitForIdle()
    runOnIdle {
      renderer.expansion = 16f
      renderer.scope.invalidateLayerBounds()
    }
    waitForIdle()
    renderer.assertGeometry(16f)
    runOnIdle { renderer.observedPadding.value = 4f }
    waitForIdle()
    renderer.assertGeometry(20f)
    runOnIdle { localPadding.value = 4f }
    waitForIdle()
    renderer.assertGeometry(24f)
    assertStableDrawOnlyInvalidations(renderer)
  }

  @Test
  fun styleChangeBounds_computedByUpdateDoNotRefreshAgainDuringDraw() = runComposeUiTest {
    val renderer = BoundsInvalidatingRenderer()
    val state = HazeState()
    val factory = HazeEffectFactory<Unit> { renderer }
    val sampling = mutableStateOf<HazeSampling>(HazeSampling.Adaptive)
    setContent {
      Box(Modifier.size(384.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(
          Modifier.align(Alignment.Center).size(120.dp).hazeEffect(
            factory = factory,
            input = HazeInput.Sources(state),
            style = Unit,
            sampling = sampling.value,
            expandLayerBounds = true,
          ),
        )
      }
    }
    waitForIdle()
    val updates = renderer.updateCalls
    val calculations = renderer.layoutCalls
    val draws = renderer.drawCalls
    runOnIdle { sampling.value = HazeSampling.FullResolution }
    waitForIdle()
    assertThat(renderer.drawCalls).isEqualTo(draws + 1)
    assertThat(renderer.updateCalls).isEqualTo(updates + 1)
    assertThat(renderer.layoutCalls).isEqualTo(calculations + 1)
    assertStableDrawOnlyInvalidations(renderer)
  }

  private fun ComposeUiTest.attachBoundsRenderer(): BoundsInvalidatingRenderer {
    val renderer = BoundsInvalidatingRenderer()
    val state = HazeState()
    val factory = HazeEffectFactory<Unit> { renderer }
    setContent {
      Box(Modifier.size(384.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(
          Modifier.align(Alignment.Center).size(120.dp).hazeEffect(
            factory = factory,
            input = HazeInput.Sources(state),
            style = Unit,
            expandLayerBounds = true,
          ),
        )
      }
    }
    waitForIdle()
    return renderer
  }

  private fun ComposeUiTest.assertStableDrawOnlyInvalidations(renderer: BoundsInvalidatingRenderer) {
    val updates = renderer.updateCalls
    val calculations = renderer.layoutCalls
    val initialDraws = renderer.drawCalls
    repeat(3) {
      runOnIdle { renderer.scope.invalidateDraw() }
      waitForIdle()
    }
    assertThat(renderer.drawCalls).isEqualTo(initialDraws + 3)
    assertThat(renderer.updateCalls).isEqualTo(updates)
    assertThat(renderer.layoutCalls).isEqualTo(calculations)
  }

  @Test
  fun rendererRequestedInvalidateDraw_duringDrawSchedulesSubsequentFrame() = runComposeUiTest {
    val factory = DuringDrawInvalidatingRendererFactory()
    val shouldInvalidate = mutableStateOf(false)

    setContent {
      Spacer(
        Modifier
          .background(Color.Red)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = shouldInvalidate.value,
          )
          .size(100.dp),
      )
    }
    waitForIdle()

    val renderer = factory.renderer
    val initialDrawCalls = renderer.drawCalls
    shouldInvalidate.value = true
    waitForIdle()

    assertThat(renderer.drawCalls).isEqualTo(initialDrawCalls + 2)
  }
}

private class DuringDrawInvalidatingRendererFactory : HazeEffectFactory<Boolean> {
  val renderer = DuringDrawInvalidatingRenderer()

  override fun createRenderer(): HazeEffectRenderer<Boolean> = renderer
}

@OptIn(InternalHazeApi::class)
private class DuringDrawInvalidatingRenderer :
  HazeEffectRenderer<Boolean>,
  HazeEffectRendererDrawHooks<Boolean> {
  var drawCalls = 0
  private var invalidationRequested = false

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: Boolean) {
    if (style && !invalidationRequested) {
      invalidationRequested = true
      invalidateDraw()
    }
  }

  override fun HazeEffectDrawScope.draw(style: Boolean) {
    drawCalls++
  }
}

@OptIn(InternalHazeApi::class)
private class BoundsInvalidatingRenderer :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererLifecycle<Unit>,
  HazeEffectRendererDrawHooks<Unit> {
  lateinit var scope: HazeEffectLifecycleScope
  var expansion = 29f
  var requestDuringPrepare = false
  var prepareExpansion = 16f
  var requestDuringUpdate = false
  var observePadding = false
  val observedPadding = mutableStateOf(0f)
  private var resolvedExpansion = 29f
  var oldGeometryDuringRequest = Size.Unspecified
  var updateCalls = 0
  var layoutCalls = 0
  var drawCalls = 0
  var preparedSize = Size.Unspecified
  var preparedOffset = Offset.Unspecified
  var drawableInput = false
  var snapshot: HazeEffectInputSnapshot? = null

  override fun attach(scope: HazeEffectLifecycleScope) {
    this.scope = scope
  }
  override fun update(scope: HazeEffectLifecycleScope, style: Unit, sampling: HazeSampling) {
    updateCalls++
    if (requestDuringUpdate) {
      requestDuringUpdate = false
      expansion = 16f
      scope.invalidateLayerBounds()
    }
    resolvedExpansion = expansion + if (observePadding) observedPadding.value + scope.currentValueOf(LocalBoundsPadding) else 0f
  }
  override fun HazeEffectLayoutScope.calculateLayerBounds(style: Unit): Rect {
    layoutCalls++
    return modifierBounds.inflate(resolvedExpansion)
  }
  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: Unit) {
    preparedSize = layerSize
    preparedOffset = layerOffset
    drawableInput = hasDrawableInput
    snapshot = inputSnapshot
    if (requestDuringPrepare) {
      requestDuringPrepare = false
      oldGeometryDuringRequest = layerSize
      expansion = prepareExpansion
      scope.invalidateLayerBounds()
    }
  }
  override fun HazeEffectDrawScope.draw(style: Unit) {
    drawCalls++
    drawInput()
  }
  fun assertGeometry(padding: Float) {
    assertThat(preparedSize).isEqualTo(Size(scope.modifierSize.width + padding * 2, scope.modifierSize.height + padding * 2))
    assertThat(preparedOffset).isEqualTo(Offset(padding, padding))
    assertThat(drawableInput).isTrue()
    assertThat(snapshot).isNotNull()
  }
}

private val LocalBoundsPadding = compositionLocalOf { 0f }
