// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Same-window sources observe snapshot applies only on API 31 (see
 * `observesSameWindowSourceSnapshots`), so only there do unrelated and descendant-layer applies
 * refresh the effect's input capture.
 */
@Config(sdk = [31, 35])
class HazeSourceSnapshotApplyAndroidTest : ScreenshotTest() {
  private val state = HazeState()
  private val unrelated = mutableIntStateOf(0)
  private val childAlpha = mutableFloatStateOf(1f)
  private var effect: HazeEffectNode? = null

  @Test
  fun sameWindowUnrelatedApplyBurst_refreshesOnlyOnApi31() {
    assertThat(refreshesAfter { repeat(10) { Snapshot.withMutableSnapshot { unrelated.intValue++ } } })
      .isRefreshedOnlyOnApi31()
  }

  @Test
  fun sameWindowRelevantLayerWrite_refreshesOnApi31() {
    assertThat(refreshesAfter { Snapshot.withMutableSnapshot { childAlpha.floatValue = 0.5f } })
      .isRefreshedOnlyOnApi31()
  }

  private fun refreshesAfter(applies: () -> Unit): Long {
    composeTestRule.setContent {
      Box(Modifier.size(100.dp).hazeSource(state)) {
        Box(
          Modifier.size(50.dp)
            .graphicsLayer { alpha = childAlpha.floatValue }
            .background(Color.Red),
        )
      }
      Spacer(
        Modifier.size(100.dp)
          .testHazeEffect(state, HazeSourceSelection.All)
          .then(SnapshotApplyEffectCaptureElement { effect = it }),
      )
    }
    composeTestRule.waitForIdle()
    val generation = composeTestRule.runOnIdle {
      checkNotNull(effect).inputCaptureGeneration.also { applies() }
    }
    composeTestRule.waitForIdle()
    return composeTestRule.runOnIdle { checkNotNull(effect).inputCaptureGeneration - generation }
  }

  private fun Assert<Long>.isRefreshedOnlyOnApi31() {
    // Robolectric frame timing is uncalibrated, so API 31 asserts only that a refresh happened.
    if (Build.VERSION.SDK_INT == 31) isGreaterThan(0L) else isEqualTo(0L)
  }
}

private data class SnapshotApplyEffectCaptureElement(
  val onEffect: (HazeEffectNode) -> Unit,
) : ModifierNodeElement<SnapshotApplyEffectCaptureNode>() {
  override fun create() = SnapshotApplyEffectCaptureNode(onEffect)
  override fun update(node: SnapshotApplyEffectCaptureNode) {
    node.onEffect = onEffect
  }
  override fun InspectorInfo.inspectableProperties() {
    name = "captureEffect"
  }
}

private class SnapshotApplyEffectCaptureNode(var onEffect: (HazeEffectNode) -> Unit) : Modifier.Node() {
  override fun onAttach() {
    (findNearestAncestor(HazeTraversableNodeKeys.Effect) as? HazeEffectNode)?.let(onEffect)
  }
}
