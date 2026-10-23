// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeSourceCaptureDemandTest {

  @Test
  fun reusedSource_rebuildsReleasedCapture() = runComposeUiTest {
    val state = HazeState()
    val reuseKey = mutableIntStateOf(0)
    val factory = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        ReusableContent(reuseKey.intValue) {
          Source(state)
        }
        Effect(state, factory)
      }
    }
    waitForIdle()
    val area = state.areas.single()
    val originalLayer = requireNotNull(area.contentLayer)

    reuseKey.intValue++
    waitForIdle()

    assertThat(state.areas.single()).isSameInstanceAs(area)
    assertThat(originalLayer.isReleased).isTrue()
    assertThat(area.contentLayer).isNotNull()
    assertThat(requireNotNull(area.contentLayer).isReleased).isFalse()
    assertThat(factory.renderer.snapshots.last()).isNotNull()
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingAlways_rendersTheContentLayerOffscreen() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()
    val sourceColor = mutableStateOf(Color.Red)
    val inputSharing = mutableStateOf(HazeInputSharing.Always)

    setContent {
      Box(Modifier.size(100.dp).testTag("root")) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, inputSharing = inputSharing.value)
            .drawBehind { drawRect(sourceColor.value) },
        )
        // Hides the source, so only the effect's sampled input can show its color.
        Box(Modifier.fillMaxSize().background(Color.Black))
        Effect(state, factory)
      }
    }
    waitForIdle()
    val area = state.areas.single()
    val layer = requireNotNull(area.contentLayer)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
    assertThat(area.drawableContentLayer).isSameInstanceAs(layer)
    assertThat(onNodeWithTag("root").captureToImage().centerPixel()).isEqualTo(Color.Red)

    // Never restores normal compositing on the same layer.
    inputSharing.value = HazeInputSharing.Never
    sourceColor.value = Color.Blue
    waitForIdle()
    assertThat(area.contentLayer).isSameInstanceAs(layer)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Auto)
    assertThat(onNodeWithTag("root").captureToImage().centerPixel()).isEqualTo(Color.Blue)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingAlways_releasesTheContentLayerWithLastConsumer() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()
    val showEffect = mutableStateOf(true)
    val outsets = LayerOutsets(4.dp)

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, inputSharing = HazeInputSharing.Always, inputSharingOutsets = outsets)
            .background(Color.Red),
        )
        if (showEffect.value) Effect(state, factory)
      }
    }
    waitForIdle()
    val area = state.areas.single()
    val layer = requireNotNull(area.contentLayer)
    assertThat(area.inputBounds).isNotNull()

    showEffect.value = false
    waitForIdle()
    assertThat(layer.isReleased).isTrue()
    assertThat(area.contentLayer).isNull()
    assertThat(area.inputBounds).isNull()
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingAuto_doesNotShareOnSkia() = runComposeUiTest {
    val state = HazeState()
    val first = DemandRecordingFactory()
    val second = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier.fillMaxSize().hazeSource(
            state,
            inputSharing = HazeInputSharing.Auto,
            inputSharingOutsets = LayerOutsets(4.dp),
          ),
        )
        Effect(state, first)
        Effect(state, second)
      }
    }
    waitForIdle()
    val area = state.areas.single()
    assertThat(area.captureConsumerCount).isEqualTo(2)
    assertThat(requireNotNull(area.contentLayer).compositingStrategy).isEqualTo(CompositingStrategy.Auto)
    assertThat(area.inputBounds).isNull()
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun hazeSource_defaultsToNeverSharingInput() {
    assertThat(HazeSourceElement(HazeState()).inputSharing).isEqualTo(HazeInputSharing.Never)
    assertThat(HazeSourceNode(HazeState()).inputSharing).isEqualTo(HazeInputSharing.Never)
    assertThat(HazeSourceElement(HazeState()).inputSharingOutsets).isEqualTo(LayerOutsets.Zero)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingNever_preservesOverflowAndIgnoresOutsets() = runComposeUiTest {
    val state = HazeState()

    setContent { OutsetFixture(state, HazeInputSharing.Never, LayerOutsets(left = 10.dp, right = 20.dp, top = 0.dp, bottom = 0.dp)) }
    waitForIdle()

    val area = state.areas.single()
    assertThat(requireNotNull(area.contentLayer).compositingStrategy).isEqualTo(CompositingStrategy.Auto)
    assertThat(area.inputBounds).isNull()
    assertThat(probeOutsetFixture()).isEqualTo(UNCLIPPED_PROBES)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingToggle_restoresOverflowAndRefreshesSnapshotsOnTheSameLayer() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()
    val inputSharing = mutableStateOf(HazeInputSharing.Always)

    setContent { OutsetFixture(state, inputSharing.value, LayerOutsets.Zero, factory) }
    waitForIdle()
    val area = state.areas.single()
    val layer = requireNotNull(area.contentLayer)
    assertThat(probeOutsetFixture()).isEqualTo(CLIPPED_PROBES)
    val shared = factory.renderer.currentSnapshot()
    assertThat(shared).isNotNull()

    inputSharing.value = HazeInputSharing.Never
    waitForIdle()
    assertThat(area.contentLayer).isSameInstanceAs(layer)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Auto)
    assertThat(probeOutsetFixture()).isEqualTo(UNCLIPPED_PROBES)
    val unshared = factory.renderer.currentSnapshot()
    assertThat(unshared).isNotEqualTo(shared)

    inputSharing.value = HazeInputSharing.Always
    waitForIdle()
    assertThat(area.contentLayer).isSameInstanceAs(layer)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
    assertThat(probeOutsetFixture()).isEqualTo(CLIPPED_PROBES)
    assertThat(factory.renderer.currentSnapshot()).isNotEqualTo(unshared)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingOutsets_extendEffectsClippedToInputBounds() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory(clipToInputBounds = true)
    val outsets = mutableStateOf(LayerOutsets(left = 10.dp, top = 0.dp, right = 20.dp, bottom = 0.dp))

    setContent { OutsetFixture(state, HazeInputSharing.Always, outsets.value, factory) }
    waitForIdle()
    val renderer = factory.renderer
    val layer = requireNotNull(state.areas.single().contentLayer)
    // The effect's layer reaches the source's right outset, so its input keeps those pixels.
    assertThat(probeOutsetFixture().insideRight).isEqualTo(Color.Red)
    val outsetLayerWidth = renderer.layerSize.width
    val outsetSnapshot = renderer.currentSnapshot()

    outsets.value = LayerOutsets.Zero
    waitForIdle()
    assertThat(state.areas.single().contentLayer).isSameInstanceAs(layer)
    assertThat(probeOutsetFixture()).isEqualTo(CLIPPED_PROBES)
    assertThat(renderer.layerSize.width).isEqualTo(outsetLayerWidth - with(density) { 20.dp.toPx() })
    assertThat(renderer.currentSnapshot()).isNotEqualTo(outsetSnapshot)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingOutsetChange_redrawsEffectsWithAFreshSnapshot() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()
    val inputSharing = mutableStateOf(HazeInputSharing.Always)
    val outsets = mutableStateOf(LayerOutsets(left = 10.dp, top = 0.dp, right = 20.dp, bottom = 0.dp))

    setContent { OutsetFixture(state, inputSharing.value, outsets.value, factory) }
    waitForIdle()
    val renderer = factory.renderer

    // Skia applies a layer's outsets when a consumer records it, so retained effect output must
    // be redrawn, with a fresh snapshot, even when the effect's own bounds are unchanged.
    var drawn = renderer.snapshots.last()
    outsets.value = LayerOutsets(left = 20.dp, top = 0.dp, right = 10.dp, bottom = 0.dp)
    waitForIdle()
    assertThat(renderer.snapshots.last()).isNotEqualTo(drawn)

    drawn = renderer.snapshots.last()
    inputSharing.value = HazeInputSharing.Never
    waitForIdle()
    assertThat(renderer.snapshots.last()).isNotEqualTo(drawn)

    drawn = renderer.snapshots.last()
    inputSharing.value = HazeInputSharing.Always
    waitForIdle()
    assertThat(renderer.snapshots.last()).isNotEqualTo(drawn)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingOutsets_keepEachDeclaredEdgeInPlace() = runComposeUiTest {
    val state = HazeState()
    val sampledOnly = mutableStateOf(false)
    val factory = DemandRecordingFactory()
    // Each edge differs, so a swapped or shifted edge lands on the wrong probe.
    val outsets = LayerOutsets(left = 4.dp, top = 8.dp, right = 12.dp, bottom = 16.dp)

    setContent {
      Box(Modifier.size(100.dp).background(Color.Black).testTag(OUTSET_ROOT_TAG)) {
        Box(
          Modifier
            .offset(30.dp, 30.dp)
            .size(40.dp)
            .hazeSource(state, inputSharing = HazeInputSharing.Always, inputSharingOutsets = outsets)
            .drawBehind {
              val overflow = 30.dp.toPx()
              drawRect(Color.Blue)
              drawRect(Color.Green, topLeft = Offset(-overflow, 0f), size = Size(overflow, size.height))
              drawRect(Color.Yellow, topLeft = Offset(0f, -overflow), size = Size(size.width, overflow))
              drawRect(Color.Red, topLeft = Offset(size.width, 0f), size = Size(overflow, size.height))
              drawRect(Color.Magenta, topLeft = Offset(0f, size.height), size = Size(size.width, overflow))
            },
        )
        // When sampledOnly, the effect alone shows the source, over an opaque cover.
        if (sampledOnly.value) Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .size(if (sampledOnly.value) 100.dp else 1.dp)
            .hazeEffect(factory = factory, input = HazeInput.Sources(state), style = Unit),
        )
      }
    }

    for (sampled in listOf(false, true)) {
      sampledOnly.value = sampled
      waitForIdle()
      val pixels = onNodeWithTag(OUTSET_ROOT_TAG).captureToImage().toPixelMap()
      fun at(x: Int, y: Int): Color = with(density) { pixels[x.dp.roundToPx(), y.dp.roundToPx()] }
      // Left edge at x = 30, outset 4.
      assertThat(at(32, 50)).isEqualTo(Color.Blue)
      assertThat(at(28, 50)).isEqualTo(Color.Green)
      assertThat(at(24, 50)).isEqualTo(Color.Black)
      // Top edge at y = 30, outset 8.
      assertThat(at(50, 32)).isEqualTo(Color.Blue)
      assertThat(at(50, 24)).isEqualTo(Color.Yellow)
      assertThat(at(50, 20)).isEqualTo(Color.Black)
      // Right edge at x = 70, outset 12.
      assertThat(at(68, 50)).isEqualTo(Color.Blue)
      assertThat(at(80, 50)).isEqualTo(Color.Red)
      assertThat(at(84, 50)).isEqualTo(Color.Black)
      // Bottom edge at y = 70, outset 16.
      assertThat(at(50, 68)).isEqualTo(Color.Blue)
      assertThat(at(50, 84)).isEqualTo(Color.Magenta)
      assertThat(at(50, 88)).isEqualTo(Color.Black)
    }
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingOutsets_convertWithTheDrawDensityRoundingOutward() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()

    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1.5f)) {
        Box(Modifier.size(100.dp)) {
          Box(
            Modifier.size(40.dp).hazeSource(
              state,
              inputSharing = HazeInputSharing.Always,
              inputSharingOutsets = LayerOutsets(left = 1.dp, top = 2.dp, right = 0.1.dp, bottom = 0.dp),
            ),
          )
          Box(Modifier.size(1.dp).hazeEffect(factory = factory, input = HazeInput.Sources(state), style = Unit))
        }
      }
    }
    waitForIdle()

    // 1.5 px rounds to 2, 3 px stays 3, 0.15 px rounds to 1 and zero stays zero.
    assertThat(state.areas.single().inputBounds).isEqualTo(Rect(-2f, -3f, 61f, 60f))
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun hazeSource_rejectsInvalidInputSharingOutsets() {
    for (edge in listOf((-1).dp, Dp.Unspecified, Dp.Infinity, Dp(Float.NEGATIVE_INFINITY))) {
      val outsets = listOf(
        { LayerOutsets(left = edge, top = 0.dp, right = 0.dp, bottom = 0.dp) },
        { LayerOutsets(left = 0.dp, top = edge, right = 0.dp, bottom = 0.dp) },
        { LayerOutsets(left = 0.dp, top = 0.dp, right = edge, bottom = 0.dp) },
        { LayerOutsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = edge) },
      )
      for (outset in outsets) {
        // Compose rejects some edges itself; hazeSource rejects the rest.
        assertFailure {
          Modifier.hazeSource(HazeState(), inputSharing = HazeInputSharing.Always, inputSharingOutsets = outset())
        }.isInstanceOf<IllegalArgumentException>()
      }
    }
    // Zero is valid.
    Modifier.hazeSource(HazeState(), inputSharing = HazeInputSharing.Always, inputSharingOutsets = LayerOutsets.Zero)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingResize_leavesNoStalePixelsOrCaptures() = runComposeUiTest {
    val state = HazeState()
    val sourceWidth = mutableStateOf(40.dp)

    setContent { OutsetFixture(state, HazeInputSharing.Always, LayerOutsets.Zero, sourceSize = sourceWidth.value) }
    waitForIdle()
    val area = state.areas.single()
    assertThat(probeOutsetFixture().effectBody).isEqualTo(Color.Blue)

    // The source now ends at x = 50, so its old body and its overflow there must not show.
    sourceWidth.value = 20.dp
    waitForIdle()
    assertThat(probeOutsetFixture().effectBody).isEqualTo(Color.Black)

    sourceWidth.value = 0.dp
    waitForIdle()
    assertThat(area.contentLayer).isNull()
    assertThat(area.inputBounds).isNull()
    assertThat(probeOutsetFixture().effectBody).isEqualTo(Color.Black)

    sourceWidth.value = 40.dp
    waitForIdle()
    assertThat(requireNotNull(area.contentLayer).compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
    assertThat(probeOutsetFixture()).isEqualTo(CLIPPED_PROBES)
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun reusedSharingSource_rebuildsAUsableOffscreenLayer() = runComposeUiTest {
    val state = HazeState()
    val reuseKey = mutableIntStateOf(0)
    val outsets = LayerOutsets(left = 10.dp, top = 0.dp, right = 20.dp, bottom = 0.dp)

    setContent {
      ReusableContent(reuseKey.intValue) {
        OutsetFixture(state, HazeInputSharing.Always, outsets)
      }
    }
    waitForIdle()
    val area = state.areas.single()
    val originalLayer = requireNotNull(area.contentLayer)

    reuseKey.intValue++
    waitForIdle()

    assertThat(state.areas.single()).isSameInstanceAs(area)
    assertThat(originalLayer.isReleased).isTrue()
    val layer = requireNotNull(area.contentLayer)
    assertThat(layer.isReleased).isFalse()
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
    assertThat(area.inputBounds).isNotNull()
    assertThat(probeOutsetFixture()).isEqualTo(OUTSET_PROBES)
  }

  @Test
  fun capturedSource_preservesDrawingOutsideLayoutBounds() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp).background(Color.Black).testTag("root")) {
        Box(
          Modifier.size(40.dp).hazeSource(state).drawBehind {
            drawRect(Color.Red, size = Size(size.width * 2f, size.height))
          },
        )
        Effect(state, factory)
      }
    }
    waitForIdle()
    assertThat(state.areas.single().contentLayer).isNotNull()
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[pixels.width * 3 / 5, pixels.height / 5])
      .isEqualTo(Color.Red)
  }

  @Test
  fun siblingRedraw_keepsUnchangedSourceCapture() = runComposeUiTest {
    val state = HazeState()
    val siblingFrame = mutableIntStateOf(0)
    val factory = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(state)
        Effect(state, factory)
        Box(
          Modifier.size(1.dp).drawBehind {
            drawRect(if (siblingFrame.intValue % 2 == 0) Color.White else Color.Black)
          },
        )
      }
    }
    waitForIdle()
    val area = state.areas.single()
    val initialVersion = area.contentVersion
    assertThat(initialVersion).isGreaterThan(0L)

    repeat(5) {
      siblingFrame.intValue++
      waitForIdle()
    }

    assertThat(area.contentVersion).isEqualTo(initialVersion)
  }

  @Test
  fun sourceDrawChange_refreshesCaptureAndEffect() = runComposeUiTest {
    val state = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val factory = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier.fillMaxSize().hazeSource(state).drawBehind {
            drawRect(sourceColor.value)
          },
        )
        Effect(state, factory)
      }
    }
    waitForIdle()
    val area = state.areas.single()
    val initialVersion = area.contentVersion
    assertThat(onNodeWithTag(EFFECT_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(area.contentVersion).isGreaterThan(initialVersion)
    assertThat(onNodeWithTag(EFFECT_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
  }

  @Test
  fun sourceWithoutAttachedEffect_drawsDirectlyWithoutCapture() = runComposeUiTest {
    val state = HazeState()

    setContent {
      Source(state)
    }
    waitForIdle()

    val area = state.areas.single()
    assertThat(area.contentLayer).isNull()
    assertThat(area.contentVersion).isEqualTo(0L)
    assertThat(onNodeWithTag(SOURCE_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun firstAttachedEffect_hasDrawableInputOnFirstDraw() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(state)
        Effect(state, factory)
      }
    }
    waitForIdle()

    val renderer = factory.renderer
    assertThat(renderer.snapshots.first()).isNotNull()
    assertThat(onNodeWithTag(EFFECT_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun lastEffectDetaches_releasesCapture() = runComposeUiTest {
    val state = HazeState()
    val showFirst = mutableStateOf(true)
    val showSecond = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(state)
        if (showFirst.value) Effect(state, DemandRecordingFactory())
        if (showSecond.value) Effect(state, DemandRecordingFactory())
      }
    }
    waitForIdle()
    val area = state.areas.single()
    assertThat(area.contentLayer).isNotNull()

    showFirst.value = false
    waitForIdle()
    assertThat(area.contentLayer).isNotNull()

    showSecond.value = false
    waitForIdle()
    assertThat(area.contentLayer).isNull()
    assertThat(onNodeWithTag(SOURCE_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun effectStateChange_transfersCaptureDemand() = runComposeUiTest {
    val firstState = HazeState()
    val secondState = HazeState()
    val effectState = mutableStateOf(firstState)

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(firstState)
        Source(secondState)
        Effect(effectState.value, DemandRecordingFactory())
      }
    }
    waitForIdle()
    val firstArea = firstState.areas.first()
    val secondArea = secondState.areas.first()
    assertThat(firstArea.contentLayer).isNotNull()
    assertThat(secondArea.contentLayer).isNull()

    effectState.value = secondState
    waitForIdle()

    assertThat(firstArea.contentLayer).isNull()
    assertThat(secondArea.contentLayer).isNotNull()
  }

  @Test
  fun sourceStateChange_rebindsCaptureDemand() = runComposeUiTest {
    val activeState = HazeState()
    val dormantState = HazeState()
    val sourceState = mutableStateOf(activeState)

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(sourceState.value)
        Effect(activeState, DemandRecordingFactory())
      }
    }
    waitForIdle()
    val activeArea = activeState.areas.single()
    assertThat(activeArea.contentLayer).isNotNull()

    sourceState.value = dormantState
    waitForIdle()
    val dormantArea = dormantState.areas.single()
    assertThat(activeArea.contentLayer).isNull()
    assertThat(dormantArea.contentLayer).isNull()

    sourceState.value = activeState
    waitForIdle()
    assertThat(activeArea.contentLayer).isNotNull()
  }

  @OptIn(ExperimentalHazeApi::class)
  @Test
  fun inputSharingAlways_clipsSourceAndInputToDeclaredOutsets() = runComposeUiTest {
    val state = HazeState()
    val outsets = mutableStateOf(LayerOutsets.Zero)

    setContent { OutsetFixture(state, HazeInputSharing.Always, outsets.value) }
    waitForIdle()
    // Zero outsets clip the on-screen source (left) and the effect's input (right).
    assertThat(probeOutsetFixture()).isEqualTo(CLIPPED_PROBES)

    outsets.value = LayerOutsets(left = 10.dp, top = 0.dp, right = 20.dp, bottom = 0.dp)
    waitForIdle()
    assertThat(probeOutsetFixture()).isEqualTo(OUTSET_PROBES)
  }

  @Composable
  private fun Source(state: HazeState) {
    Box(
      Modifier
        .fillMaxSize()
        .testTag(SOURCE_TAG)
        .hazeSource(state)
        .background(Color.Red),
    )
  }

  @Composable
  private fun Effect(state: HazeState, factory: DemandRecordingFactory) {
    Box(
      Modifier
        .fillMaxSize()
        .testTag(EFFECT_TAG)
        .hazeEffect(
          factory = factory,
          input = HazeInput.Sources(state),
          style = Unit,
        ),
    )
  }

  private companion object {
    const val SOURCE_TAG = "demand-source"
    const val EFFECT_TAG = "demand-effect"
  }
}

private class DemandRecordingFactory(
  private val clipToInputBounds: Boolean = false,
) : HazeEffectFactory<Unit> {
  lateinit var renderer: DemandRecordingRenderer
    private set

  override fun createRenderer(): HazeEffectRenderer<Unit> = DemandRecordingRenderer(clipToInputBounds).also {
    renderer = it
  }
}

@OptIn(InternalHazeApi::class)
private class DemandRecordingRenderer(
  private val clipToInputBounds: Boolean,
) : HazeEffectRenderer<Unit>,
  HazeEffectRendererDrawHooks<Unit> {
  val snapshots = mutableListOf<HazeEffectInputSnapshot?>()
  var layerSize: Size = Size.Unspecified
    private set

  /** Reads the input snapshot that the effect's next draw would see. */
  var currentSnapshot: () -> HazeEffectInputSnapshot? = { null }
    private set

  override fun HazeEffectDrawScope.draw(style: Unit) {
    val runtimeScope = this as HazeEffectRuntimeDrawScope
    snapshots += runtimeScope.inputSnapshot
    currentSnapshot = { runtimeScope.inputSnapshot }
    val effectLayerSize = layerSize
    val effectLayerOffset = layerOffset
    this@DemandRecordingRenderer.layerSize = effectLayerSize
    // Draws like a renderer with an output layer: input in layer space, clipped to the layer.
    translate(-effectLayerOffset.x, -effectLayerOffset.y) {
      clipRect(0f, 0f, effectLayerSize.width, effectLayerSize.height) { this@draw.drawInput() }
    }
  }

  override fun shouldPreferClipToInputBounds(): Boolean = clipToInputBounds
}

private fun ImageBitmap.centerPixel(): Color = toPixelMap().let { it[it.width / 2, it.height / 2] }

/**
 * A 40 dp source at (30, 30) in a black 100 dp root, drawing green 30 dp beyond its left edge, blue
 * in its bounds and red 30 dp beyond its right edge. The right half is covered and sampled by a
 * passthrough effect, so pixels there come only from the effect's input.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun OutsetFixture(
  state: HazeState,
  inputSharing: HazeInputSharing,
  outsets: LayerOutsets,
  factory: DemandRecordingFactory = remember { DemandRecordingFactory() },
  sourceSize: Dp = 40.dp,
) {
  Box(Modifier.size(100.dp).background(Color.Black).testTag(OUTSET_ROOT_TAG)) {
    Box(
      Modifier
        .offset(30.dp, 30.dp)
        .size(sourceSize, 40.dp)
        .hazeSource(state, inputSharing = inputSharing, inputSharingOutsets = outsets)
        .drawBehind {
          val overflow = 30.dp.toPx()
          drawRect(Color.Green, topLeft = Offset(-overflow, 0f), size = Size(overflow, size.height))
          drawRect(Color.Blue)
          drawRect(Color.Red, topLeft = Offset(size.width, 0f), size = Size(overflow, size.height))
        },
    )
    Box(Modifier.offset(x = 50.dp).size(50.dp, 100.dp).background(Color.Black))
    Box(
      Modifier
        .offset(x = 50.dp)
        .size(50.dp, 100.dp)
        .hazeEffect(factory = factory, input = HazeInput.Sources(state), style = Unit),
    )
  }
}

private data class OutsetProbes(
  val beyondLeft: Color,
  val insideLeft: Color,
  val onScreenBody: Color,
  val effectBody: Color,
  val insideRight: Color,
  val beyondRight: Color,
)

/** Samples [OutsetFixture] along y = 50 dp, at the centre of each 10 dp column of interest. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.probeOutsetFixture(): OutsetProbes {
  val pixels = onNodeWithTag(OUTSET_ROOT_TAG).captureToImage().toPixelMap()
  fun at(x: Int): Color = with(density) { pixels[x.dp.roundToPx(), 50.dp.roundToPx()] }
  return OutsetProbes(
    beyondLeft = at(15),
    insideLeft = at(25),
    onScreenBody = at(35),
    effectBody = at(65),
    insideRight = at(85),
    beyondRight = at(95),
  )
}

private const val OUTSET_ROOT_TAG = "outset-root"

/** [OutsetFixture] clipped to the source bounds. */
private val CLIPPED_PROBES = OutsetProbes(
  beyondLeft = Color.Black,
  insideLeft = Color.Black,
  onScreenBody = Color.Blue,
  effectBody = Color.Blue,
  insideRight = Color.Black,
  beyondRight = Color.Black,
)

/** [OutsetFixture] clipped to 10 dp left and 20 dp right outsets. */
private val OUTSET_PROBES = CLIPPED_PROBES.copy(insideLeft = Color.Green, insideRight = Color.Red)

/** [OutsetFixture] drawing all of its overflow. */
private val UNCLIPPED_PROBES = OUTSET_PROBES.copy(beyondLeft = Color.Green, beyondRight = Color.Red)
