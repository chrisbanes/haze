// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class HazeEffectInputSnapshotObservationTest {
  @Test fun optedInDescendantLayerChange_advancesInputWithoutRawParentRecording() = runComposeUiTest {
    val state = HazeState()
    val alpha = mutableStateOf(1f)
    val renderer = SnapshotRenderer(observesSourceSnapshotChanges = true)
    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state)) {
          Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color.Red))
        }
        Box(Modifier.fillMaxSize().hazeEffect(HazeEffectFactory { renderer }, HazeInput.Sources(state), Unit))
      }
    }
    waitForIdle()
    val version = state.areas.single().contentVersion
    val before = renderer.snapshot
    alpha.value = .5f
    Snapshot.sendApplyNotifications()
    waitForIdle()
    assertThat(state.areas.single().contentVersion).isEqualTo(version)
    assertThat(renderer.snapshot).isNotEqualTo(before)
    assertThat(checkNotNull(before).hasSameSourceGeometry(checkNotNull(renderer.snapshot))).isTrue()
  }

  @Test fun copiedSourceGeometry_rejectsTransformSizeIdentityAndSelectedOrderChanges() = runComposeUiTest {
    val state = HazeState()
    val color = mutableStateOf(Color.Red)
    val sourceSize = mutableStateOf(100.dp)
    val sourceOffset = mutableStateOf(0.dp)
    val identity = mutableStateOf(0)
    val zIndex = mutableStateOf(0f)
    val renderer = SnapshotRenderer(true)
    setContent {
      Box(Modifier.size(100.dp)) {
        key(identity.value) {
          Box(
            Modifier.size(sourceSize.value).offset(x = sourceOffset.value)
              .hazeSource(state, zIndex = zIndex.value).background(color.value),
          )
        }
        Box(Modifier.fillMaxSize().hazeSource(state, zIndex = 1f).background(Color.Blue))
        Box(Modifier.fillMaxSize().hazeEffect(HazeEffectFactory { renderer }, HazeInput.Sources(state), Unit))
      }
    }
    waitForIdle()
    var previous = checkNotNull(renderer.snapshot)
    color.value = Color.Green
    waitForIdle()
    val refreshed = checkNotNull(renderer.snapshot)
    assertThat(refreshed).isNotEqualTo(previous)
    assertThat(previous.hasSameSourceGeometry(refreshed)).isTrue()
    previous = refreshed
    sourceOffset.value = 10.dp
    waitForIdle()
    assertThat(previous.hasSameSourceGeometry(checkNotNull(renderer.snapshot))).isFalse()
    previous = checkNotNull(renderer.snapshot)
    sourceSize.value = 80.dp
    waitForIdle()
    assertThat(previous.hasSameSourceGeometry(checkNotNull(renderer.snapshot))).isFalse()
    previous = checkNotNull(renderer.snapshot)
    identity.value++
    waitForIdle()
    assertThat(previous.hasSameSourceGeometry(checkNotNull(renderer.snapshot))).isFalse()
    previous = checkNotNull(renderer.snapshot)
    zIndex.value = 2f
    waitForIdle()
    assertThat(previous.hasSameSourceGeometry(checkNotNull(renderer.snapshot))).isFalse()
  }

  @Test fun unknownSnapshot_hasNoGeometryCompatibilityByDefault() {
    val unknown = object : HazeEffectInputSnapshot {}
    assertThat(unknown.hasSameSourceGeometry(unknown)).isFalse()
  }

  @Test fun defaultRenderer_unrelatedApplyDoesNotInvalidateInput() = runComposeUiTest {
    val state = HazeState()
    val unrelated = mutableStateOf(0)
    val renderer = SnapshotRenderer(false)
    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(Modifier.fillMaxSize().hazeEffect(HazeEffectFactory { renderer }, HazeInput.Sources(state), Unit))
      }
    }
    waitForIdle()
    val before = renderer.snapshot
    val draws = renderer.draws
    unrelated.value++
    Snapshot.sendApplyNotifications()
    waitForIdle()
    assertThat(renderer.snapshot).isSameInstanceAs(before)
    assertThat(renderer.draws).isEqualTo(draws)
  }

  @Test fun optedInConservativeApply_requiresDemandAndStopsOnDetach() = runComposeUiTest {
    val state = HazeState()
    val attached = mutableStateOf(true)
    val unrelated = mutableStateOf(0)
    val renderer = SnapshotRenderer(true)
    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        if (attached.value) {
          Box(Modifier.fillMaxSize().hazeEffect(HazeEffectFactory { renderer }, HazeInput.Sources(state), Unit))
        }
      }
    }
    waitForIdle()
    val before = renderer.draws
    unrelated.value++
    Snapshot.sendApplyNotifications()
    waitForIdle()
    assertThat(renderer.draws).isGreaterThan(before)
    attached.value = false
    waitForIdle()
    val detachedDraws = renderer.draws
    unrelated.value++
    Snapshot.sendApplyNotifications()
    waitForIdle()
    assertThat(renderer.draws).isEqualTo(detachedDraws)
    assertThat(state.areas.single().captureConsumerCount).isEqualTo(0)
  }

  private class SnapshotRenderer(
    override val observesSourceSnapshotChanges: Boolean,
  ) : HazeEffectRenderer<Unit>, HazeEffectRendererDrawHooks<Unit> {
    var snapshot: HazeEffectInputSnapshot? = null
    var draws = 0
    override fun HazeEffectDrawScope.draw(style: Unit) {
      snapshot = (this as HazeEffectRuntimeDrawScope).inputSnapshot
      draws++
      drawInput()
    }
  }
}
