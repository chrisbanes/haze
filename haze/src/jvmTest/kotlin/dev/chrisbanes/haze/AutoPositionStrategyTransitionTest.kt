// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class AutoPositionStrategyTransitionTest : ContextTest() {

  @Test
  fun auto_sourceAreaJoiningAndLeavingAnotherWindowUpdatesGeometryAtomically() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = PositionStrategyCapturingVisualEffect()
    val effectNode = HazeEffectNode(hazeState) {
      retainOutputWhenSourceUnavailable = true
      visualEffect = effect
    }
    var effectCoordinates: LayoutCoordinates? = null

    setContent {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier
            .hazeSource(hazeState)
            .size(100.dp)
            .background(Color.Red),
        )
        Box(
          Modifier
            .onGloballyPositioned { effectCoordinates = it }
            .testHazeEffectNode(effectNode)
            .testTag(EFFECT_TAG)
            .size(60.dp),
        )
      }
    }
    waitForIdle()

    assertThat(effectCoordinates).isNotNull()
    val actualEffectCoordinates = checkNotNull(effectCoordinates)
    val actualRootCoordinates = actualEffectCoordinates.findRootCoordinates()
    val fakeRootCoordinates = TestLayoutCoordinates(
      delegate = actualRootCoordinates,
      parent = null,
      localPosition = Offset.Zero,
      screenPosition = Offset(200f, 300f),
    )
    var firstCoordinatesAttached = true
    val fakeEffectCoordinates = TestLayoutCoordinates(
      delegate = actualEffectCoordinates,
      parent = fakeRootCoordinates,
      localPosition = Offset(20f, 30f),
      screenPosition = Offset(220f, 330f),
      isAttachedProvider = { firstCoordinatesAttached },
    )

    effect.clearSamples()
    runOnIdle {
      effectNode.onPlaced(fakeEffectCoordinates)
    }
    waitForIdle()
    onNodeWithTag(EFFECT_TAG).captureToImage()

    assertGeometrySamples(
      effect = effect,
      strategy = HazePositionStrategy.Local,
      effectCoordinates = fakeEffectCoordinates,
      sourceAreas = effectNode.areas,
    )

    val dialogArea = HazeArea().apply {
      coordinates.localPosition = Offset(40f, 50f)
      coordinates.screenPosition = Offset(540f, 650f)
      size = Size(80f, 80f)
      windowId = Any()
    }
    effect.clearSamples()
    runOnIdle {
      hazeState.addArea(dialogArea)
    }
    waitForIdle()
    onNodeWithTag(EFFECT_TAG).captureToImage()

    assertGeometrySamples(
      effect = effect,
      strategy = HazePositionStrategy.Screen,
      effectCoordinates = fakeEffectCoordinates,
      sourceAreas = effectNode.areas,
    )

    val replacementEffectCoordinates = TestLayoutCoordinates(
      delegate = actualEffectCoordinates,
      parent = fakeRootCoordinates,
      localPosition = Offset(25f, 35f),
      screenPosition = Offset(225f, 335f),
    )
    val updateCount = effect.updateSamples.size
    runOnIdle {
      effectNode.onPlaced(replacementEffectCoordinates)
    }
    waitForIdle()
    onNodeWithTag(EFFECT_TAG).captureToImage()

    assertThat(effect.updateSamples.size).isEqualTo(updateCount)
    assertGeometrySample(
      sample = effect.drawSamples.last(),
      strategy = HazePositionStrategy.Screen,
      effectCoordinates = fakeEffectCoordinates,
      sourceAreas = effectNode.areas,
    )

    firstCoordinatesAttached = false
    effect.clearSamples()
    runOnIdle {
      hazeState.removeArea(dialogArea)
    }
    waitForIdle()
    onNodeWithTag(EFFECT_TAG).captureToImage()

    assertGeometrySamples(
      effect = effect,
      strategy = HazePositionStrategy.Local,
      effectCoordinates = replacementEffectCoordinates,
      sourceAreas = effectNode.areas,
    )
  }

  private companion object {
    const val EFFECT_TAG = "effect"
  }
}

@Poko
private class PositionStrategyGeometrySample(
  val strategy: HazePositionStrategy,
  val effectPosition: Offset,
  val rootBounds: Rect,
  val areaPositions: List<Offset>,
  val areaOffsets: List<Offset>,
  val layerSize: Size,
  val layerOffset: Offset,
)

private class PositionStrategyCapturingVisualEffect : VisualEffect {
  val updateSamples = mutableListOf<PositionStrategyGeometrySample>()
  val drawSamples = mutableListOf<PositionStrategyGeometrySample>()

  override fun update(context: VisualEffectContext) {
    if (context.position.isSpecified && context.areas.isNotEmpty()) {
      updateSamples += context.positionStrategyGeometrySample()
    }
  }

  override fun androidx.compose.ui.graphics.drawscope.DrawScope.draw(context: VisualEffectContext) {
    if (context.position.isSpecified && context.areas.isNotEmpty()) {
      drawSamples += context.positionStrategyGeometrySample()
    }
  }

  fun clearSamples() {
    updateSamples.clear()
    drawSamples.clear()
  }
}

private fun VisualEffectContext.positionStrategyGeometrySample() = PositionStrategyGeometrySample(
  strategy = positionStrategy,
  effectPosition = position,
  rootBounds = rootBounds,
  areaPositions = areas.map(::positionOf),
  areaOffsets = areas.map { position - positionOf(it) },
  layerSize = layerSize,
  layerOffset = layerOffset,
)

private fun assertGeometrySamples(
  effect: PositionStrategyCapturingVisualEffect,
  strategy: HazePositionStrategy,
  effectCoordinates: LayoutCoordinates,
  sourceAreas: List<HazeArea>,
) {
  assertGeometrySample(
    sample = effect.updateSamples.last(),
    strategy = strategy,
    effectCoordinates = effectCoordinates,
    sourceAreas = sourceAreas,
  )
  assertGeometrySample(
    sample = effect.drawSamples.last(),
    strategy = strategy,
    effectCoordinates = effectCoordinates,
    sourceAreas = sourceAreas,
  )
}

private fun assertGeometrySample(
  sample: PositionStrategyGeometrySample,
  strategy: HazePositionStrategy,
  effectCoordinates: LayoutCoordinates,
  sourceAreas: List<HazeArea>,
) {
  val root = effectCoordinates.findRootCoordinates()
  val expectedEffectPosition = effectCoordinates.positionForHaze(strategy)
  val expectedAreaPositions = sourceAreas.map { it.coordinates.positionFor(strategy) }

  assertThat(sample.strategy).isEqualTo(strategy)
  assertThat(sample.effectPosition).isEqualTo(expectedEffectPosition)
  assertThat(sample.rootBounds).isEqualTo(
    Rect(
      offset = root.positionForHaze(strategy),
      size = root.size.toSize(),
    ),
  )
  assertThat(sample.areaPositions).containsExactly(*expectedAreaPositions.toTypedArray())
  assertThat(sample.areaOffsets).containsExactly(
    *expectedAreaPositions.map { expectedEffectPosition - it }.toTypedArray(),
  )
  assertThat(sample.layerSize).isEqualTo(effectCoordinates.size.toSize())
  assertThat(sample.layerOffset).isEqualTo(Offset.Zero)
}

private class TestLayoutCoordinates(
  private val delegate: LayoutCoordinates,
  private val parent: LayoutCoordinates?,
  private val localPosition: Offset,
  private val screenPosition: Offset,
  private val isAttachedProvider: () -> Boolean = { delegate.isAttached },
) : LayoutCoordinates by delegate {
  override val isAttached: Boolean
    get() = isAttachedProvider()

  override val parentLayoutCoordinates: LayoutCoordinates?
    get() = parent

  override fun localToRoot(relativeToLocal: Offset): Offset = localPosition + relativeToLocal

  override fun localToScreen(relativeToLocal: Offset): Offset = screenPosition + relativeToLocal
}

private fun Modifier.testHazeEffectNode(
  node: HazeEffectNode,
): Modifier = this then TestHazeEffectNodeElement(node)

@Poko
private class TestHazeEffectNodeElement(
  val node: HazeEffectNode,
) : ModifierNodeElement<HazeEffectNode>() {

  override fun create(): HazeEffectNode = node

  override fun update(node: HazeEffectNode) = Unit

  override fun InspectorInfo.inspectableProperties() {
    name = "testHazeEffectNode"
  }
}
