// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
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
  fun positionOnlyChange_runsOneUpdatePerFrame() = runComposeUiTest {
    val fixture = setPlacementFixtureContent()
    val updates = fixture.renderer.updateCalls

    fixture.offsetPx = 20
    waitForIdle()

    // onGloballyPositioned no longer repeats onPlaced's update for the same placement (#1437).
    assertThat(fixture.renderer.updateCalls - updates).isEqualTo(1)
  }

  @Test
  fun transformOnlyChange_runsOneUpdatePerFrame() = runComposeUiTest {
    val fixture = setPlacementFixtureContent()
    val updates = fixture.renderer.updateCalls

    fixture.scale = 1.5f
    waitForIdle()

    // onGloballyPositioned no longer repeats onPlaced's update for the same placement (#1437).
    assertThat(fixture.renderer.updateCalls - updates).isEqualTo(1)
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
  fun unchangedSourcesUpdate_retainsMembershipList() = runComposeUiTest {
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
    val membership = node.lastSeenStateAreas
    val updates = renderer.updateCalls

    // Moving the source and effect together re-runs the update without changing the sources.
    groupX = 30
    waitForIdle()

    assertThat(renderer.updateCalls).isGreaterThan(updates)
    assertThat(node.lastSeenStateAreas).isSameInstanceAs(membership)
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
        },
      ) {
        Box(
          Modifier
            .offset { IntOffset(fixture.offsetPx, 0) }
            .size(fixture.sizeDp.dp)
            .hazeEffect(factory = factory, input = HazeInput.Sources(hazeState), style = Unit),
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
