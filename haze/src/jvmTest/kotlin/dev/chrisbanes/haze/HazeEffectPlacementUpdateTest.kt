// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import java.awt.Frame
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class, ExperimentalComposeUiApi::class)
class HazeEffectPlacementUpdateTest : ContextTest() {

  @Test
  fun sizeOnlyChange_runsOneUpdatePerFrame() = runComposeUiTest {
    val fixture = setPlacementFixtureContent()
    val updates = fixture.renderer.updateCalls

    fixture.sizeDp = 120
    waitForIdle()

    assertThat(fixture.renderer.updateCalls - updates).isEqualTo(1)
  }

  @Test
  fun effectMoveWithoutResize_updatesGeometryWithoutRendererUpdate() = runComposeUiTest {
    val fixture = setPlacementFixtureContent()
    val node = checkNotNull(fixture.effectNode)
    val area = node.areas.single()
    val updates = fixture.renderer.updateCalls
    val initialOffset = node.sourceTransformInEffect(area).map(Offset.Zero)

    fixture.offsetPx = 20
    waitForIdle()

    assertThat(node.sourceTransformInEffect(area).map(Offset.Zero)).isEqualTo(initialOffset - Offset(20f, 0f))
    assertThat(fixture.renderer.updateCalls).isEqualTo(updates)
  }

  @Test
  fun effectTransformWithoutResize_updatesGeometryWithoutRendererUpdate() = runComposeUiTest {
    val fixture = setPlacementFixtureContent()
    val node = checkNotNull(fixture.effectNode)
    val area = node.areas.single()
    val updates = fixture.renderer.updateCalls

    fixture.scale = 2f
    waitForIdle()

    // The effect's local space is scaled, so a 10 px source offset maps to 5 px in it.
    assertThat(node.sourceTransformInEffect(area).map(Offset(10f, 0f)).x - node.sourceTransformInEffect(area).map(Offset.Zero).x)
      .isEqualTo(5f)
    assertThat(fixture.renderer.updateCalls).isEqualTo(updates)
  }

  @Test
  fun sourceScroll_updatesGeometryWithoutRendererUpdate() = runComposeUiTest {
    val hazeState = HazeState()
    var sourceX by mutableIntStateOf(0)
    var effectNode: HazeEffectNode? = null
    val renderer = CountingUpdateRenderer()
    val factory = HazeEffectFactory<Unit> { renderer }
    setContent {
      Box(Modifier.size(200.dp)) {
        // Placement-only movement, as a scrolling container moves its children.
        Box(Modifier.offset { IntOffset(sourceX, 0) }.size(100.dp).hazeSource(hazeState))
        Spacer(
          Modifier
            .size(50.dp)
            .hazeEffect(factory = factory, input = HazeInput.Sources(hazeState), style = Unit)
            .captureEffectNode { effectNode = it },
        )
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    val node = checkNotNull(effectNode)
    val area = hazeState.areas.single()
    val updates = renderer.updateCalls
    val initialOffset = node.sourceTransformInEffect(area).map(Offset.Zero)

    repeat(3) { frame ->
      sourceX = 10 * (frame + 1)
      mainClock.advanceTimeByFrame()
      assertThat(node.sourceTransformInEffect(area).map(Offset.Zero))
        .isEqualTo(initialOffset + Offset(10f * (frame + 1), 0f))
    }
    assertThat(renderer.updateCalls).isEqualTo(updates)
  }

  @Test
  fun sourcePlacedAfterEffect_effectMoveWithoutResize_positionedCallbacksSeeMovedSource() = runComposeUiTest {
    val hazeState = HazeState()
    var moved by mutableStateOf(false)
    var effectNode: HazeEffectNode? = null
    val positionedSourceOffsets = mutableListOf<Offset>()

    setContent {
      // One placement pass moves both, placing the effect before its source: the effect's onPlaced
      // geometry pass sees the old source, and only its onGloballyPositioned pass sees the new one.
      Layout(
        content = {
          Box(
            Modifier
              .size(100.dp)
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              )
              .captureEffectNode { effectNode = it },
          )
          Box(Modifier.size(200.dp).hazeSource(hazeState)) {
            Spacer(
              Modifier.size(10.dp).onGloballyPositioned {
                val node = checkNotNull(effectNode)
                positionedSourceOffsets +=
                  node.sourceTransformInEffect(node.areas.single()).map(Offset.Zero)
              },
            )
          }
        },
        modifier = Modifier.size(300.dp),
      ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
          placeables[0].place(if (moved) 10 else 0, 0)
          placeables[1].place(if (moved) 40 else 0, 0)
        }
      }
    }
    waitForIdle()
    positionedSourceOffsets.clear()

    moved = true
    waitForIdle()

    assertThat(positionedSourceOffsets.first()).isEqualTo(Offset(30f, 0f))
  }

  @Test
  fun sourcePlacedAfterEffect_positionedCallbacksSeeMovedSource() = runComposeUiTest {
    val hazeState = HazeState()
    var moved by mutableStateOf(false)
    var effectNode: HazeEffectNode? = null
    val positionedSourceOffsets = mutableListOf<Offset>()

    setContent {
      Box(Modifier.size(300.dp)) {
        // The effect is placed before its source, so its onPlaced update sees the old source.
        Box(
          Modifier
            .size(if (moved) 120.dp else 100.dp)
            .hazeEffect(
              factory = TestEffectFactory,
              input = HazeInput.Sources(hazeState),
              style = Unit,
            )
            .captureEffectNode { effectNode = it },
        )
        Box(
          Modifier
            .offset { IntOffset(if (moved) 40 else 0, 0) }
            .size(200.dp)
            .hazeSource(hazeState),
        ) {
          // Deeper nodes are dispatched after the effect, but before snapshot apply notifications
          // that desktop delivers ahead of draw. Android can draw with this state.
          Spacer(
            Modifier.size(10.dp).onGloballyPositioned {
              val node = checkNotNull(effectNode)
              positionedSourceOffsets +=
                node.sourceTransformInEffect(node.areas.single()).map(Offset.Zero)
            },
          )
        }
      }
    }
    waitForIdle()
    positionedSourceOffsets.clear()

    moved = true
    waitForIdle()

    assertThat(positionedSourceOffsets.first()).isEqualTo(Offset(40f, 0f))
  }

  @Test
  fun unchangedSourcesUpdate_retainsMembershipListAndTransform() = runComposeUiTest {
    val hazeState = HazeState()
    var groupX by mutableIntStateOf(0)
    var effectNode: HazeEffectNode? = null
    val renderer = CountingUpdateRenderer()
    val factory = HazeEffectFactory<Unit> { renderer }
    setContent {
      Box(Modifier.offset { IntOffset(groupX, 0) }.size(200.dp)) {
        Box(Modifier.size(100.dp).hazeSource(hazeState))
        Spacer(
          Modifier
            .offset { IntOffset(10, 10) }
            .size(50.dp)
            .hazeEffect(factory = factory, input = HazeInput.Sources(hazeState), style = Unit)
            .captureEffectNode { effectNode = it },
        )
      }
    }
    waitForIdle()
    val node = checkNotNull(effectNode)
    val area = hazeState.areas.single()
    val membership = node.lastSeenStateAreas
    val transformValues = node.sourceTransformInEffect(area).values
    val updates = renderer.updateCalls

    // Moving the source and effect together re-runs the update without changing either input.
    groupX = 30
    waitForIdle()

    // Placement runs the geometry pass only.
    assertThat(renderer.updateCalls).isEqualTo(updates)
    // Guards against reassigning the membership list; the transform identity guards allocation.
    assertThat(node.lastSeenStateAreas).isSameInstanceAs(membership)
    assertThat(node.sourceTransformInEffect(area).values).isSameInstanceAs(transformValues)
  }

  @Test
  fun changedSourceTransform_replacesRetainedTransformWithoutMutatingIt() = runComposeUiTest {
    val hazeState = HazeState()
    var sourceX by mutableIntStateOf(0)
    var effectNode: HazeEffectNode? = null
    setContent {
      Box(Modifier.size(200.dp)) {
        Box(Modifier.offset { IntOffset(sourceX, 0) }.size(100.dp).hazeSource(hazeState))
        Spacer(
          Modifier
            .size(50.dp)
            .hazeEffect(factory = TestEffectFactory, input = HazeInput.Sources(hazeState), style = Unit)
            .captureEffectNode { effectNode = it },
        )
      }
    }
    waitForIdle()
    val node = checkNotNull(effectNode)
    val area = hazeState.areas.single()
    val previous = node.sourceTransformInEffect(area)
    val previousValues = previous.values.copyOf()

    sourceX = 40
    waitForIdle()

    val current = node.sourceTransformInEffect(area)
    assertThat(current.values).isNotSameInstanceAs(previous.values)
    assertThat(previous.values.toList()).isEqualTo(previousValues.toList())
    assertThat(current.map(Offset.Zero)).isEqualTo(previous.map(Offset.Zero) + Offset(40f, 0f))
  }

  @Test
  fun scaledEffect_clipsLayerToRootBoundsInEffectCoordinates() = runComposeUiTest {
    var effectNode: HazeEffectNode? = null
    setContent {
      Box(
        Modifier.graphicsLayer {
          scaleX = 2f
          scaleY = 2f
          transformOrigin = TransformOrigin(0f, 0f)
        },
      ) {
        Spacer(
          Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(4000.dp)
            .hazeEffect(factory = TestEffectFactory, input = HazeInput.Backdrop(), style = Unit)
            .captureEffectNode { effectNode = it },
        )
      }
    }
    waitForIdle()
    val node = checkNotNull(effectNode)

    // The scaled root clips the layer to half the root's size in the effect's coordinates.
    assertThat(node.layerSize.width).isEqualTo(node.rootBounds.width / 2f)
  }

  @Test
  fun screenStrategyMove_globallyPositionedWithoutPlacementRecord_updates() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    var effectX by mutableIntStateOf(0)
    var effectNode: HazeEffectNode? = null

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(Modifier.size(100.dp).hazeSource(hazeState))
        }
        CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
          Spacer(
            Modifier
              .offset { IntOffset(effectX, 0) }
              .size(100.dp)
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              )
              .captureEffectNode { effectNode = it },
          )
        }
      }
      waitForIdle()

      val node = checkNotNull(effectNode)
      val area = hazeState.areas.single()
      // Under the Screen strategy onPlaced does not position, so no placement record exists.
      assertThat(node.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Screen)
      val initialOffset = node.sourceTransformInEffect(area).map(Offset.Zero)

      effectX = 40
      waitForIdle()

      assertThat(node.sourceTransformInEffect(area).map(Offset.Zero))
        .isEqualTo(initialOffset - Offset(40f, 0f))
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }

  @Test
  fun onPositioned_unchangedTransform_refreshesDroppedSourceReads() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    var sourceX by mutableIntStateOf(0)
    var showEffect by mutableStateOf(false)
    var effectNode: HazeEffectNode? = null

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(
            Modifier
              .offset { IntOffset(sourceX, 0) }
              .size(100.dp)
              .hazeSource(hazeState),
          )
        }

        if (showEffect) {
          CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
            Spacer(
              Modifier
                .size(100.dp)
                .hazeEffect(
                  factory = TestEffectFactory,
                  input = HazeInput.Sources(hazeState),
                  style = Unit,
                )
                .captureEffectNode { effectNode = it },
            )
          }
        }
      }
      waitForIdle()

      // The source already has a window, so the effect's first observed update promotes to the
      // Screen strategy before it has coordinates and exits early, dropping its source reads.
      showEffect = true
      waitForIdle()

      val node = checkNotNull(effectNode)
      val area = hazeState.areas.single()
      assertThat(node.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Screen)
      val initialOffset = node.sourceTransformInEffect(area).map(Offset.Zero)

      // Only the source moves: the effect's own placement and transform stay unchanged.
      sourceX = 40
      waitForIdle()

      assertThat(node.sourceTransformInEffect(area).map(Offset.Zero))
        .isEqualTo(initialOffset + Offset(40f, 0f))
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }
}

@OptIn(InternalHazeApi::class)
private class PlacementFixture {
  var sizeDp by mutableIntStateOf(100)
  var offsetPx by mutableIntStateOf(0)
  var scale by mutableFloatStateOf(1f)
  var effectNode: HazeEffectNode? = null
  val renderer = CountingUpdateRenderer()
}

/** A source-backed effect whose size, offset and transform are each driven by their own state. */
@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
private fun ComposeUiTest.setPlacementFixtureContent(): PlacementFixture {
  val fixture = PlacementFixture()
  val hazeState = HazeState()
  val factory = HazeEffectFactory<Unit> { fixture.renderer }
  setContent {
    Box(Modifier.size(300.dp)) {
      Box(Modifier.fillMaxSize().hazeSource(hazeState).drawBehind { drawRect(Color.Red) })
      Box(
        Modifier.graphicsLayer {
          scaleX = fixture.scale
          scaleY = fixture.scale
          transformOrigin = TransformOrigin(0f, 0f)
        },
      ) {
        Box(
          Modifier
            .offset { IntOffset(fixture.offsetPx, 0) }
            .size(fixture.sizeDp.dp)
            .hazeEffect(factory = factory, input = HazeInput.Sources(hazeState), style = Unit)
            .captureEffectNode { fixture.effectNode = it },
        )
      }
    }
  }
  waitForIdle()
  return fixture
}

@OptIn(InternalHazeApi::class)
private class CountingUpdateRenderer :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererLifecycle<Unit> {
  var updateCalls = 0

  override fun update(scope: HazeEffectLifecycleScope, style: Unit, sampling: HazeSampling) {
    updateCalls++
  }

  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
}
