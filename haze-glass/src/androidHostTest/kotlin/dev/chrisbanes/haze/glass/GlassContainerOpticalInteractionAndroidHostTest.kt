// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.annotation.Config

class GlassContainerOpticalInteractionAndroidHostTest : ScreenshotTest() {
  @Test
  @Config(sdk = [35])
  fun sdk35_fusedWhitePointDelta_isMemberLocalAndCancelRestoresBase() = assertWhitePointDelta()

  @Test
  @Config(sdk = [37])
  fun sdk37_fusedWhitePointDelta_isMemberLocalAndCancelRestoresBase() = assertWhitePointDelta()

  @Test
  @Config(sdk = [35])
  fun sdk35_fusedRefractionMultiplier_refreshesOpticsWithoutChangingTopology() = assertRefractionMultiplier()

  @Test
  @Config(sdk = [37])
  fun sdk37_fusedRefractionMultiplier_refreshesOpticsWithoutChangingTopology() = assertRefractionMultiplier()

  @Test
  @Config(sdk = [35])
  fun sdk35_refractionMultiplier_survivesSeparateMergedAndSplitLayouts() = assertOpticalChannelSurvivesLayouts(refractionMultiplier = true)

  @Test
  @Config(sdk = [37])
  fun sdk37_refractionMultiplier_survivesSeparateMergedAndSplitLayouts() = assertOpticalChannelSurvivesLayouts(refractionMultiplier = true)

  @Test
  @Config(sdk = [35])
  fun sdk35_whitePointDelta_survivesSeparateMergedAndSplitLayouts() = assertOpticalChannelSurvivesLayouts(refractionMultiplier = false)

  @Test
  @Config(sdk = [37])
  fun sdk37_whitePointDelta_survivesSeparateMergedAndSplitLayouts() = assertOpticalChannelSurvivesLayouts(refractionMultiplier = false)

  private fun assertOpticalChannelSurvivesLayouts(refractionMultiplier: Boolean) = runScreenshotTest(size = Size(360f, 240f)) {
    val state = HazeState()
    val gap = mutableIntStateOf(0)
    val firstSource = MutableInteractionSource()
    val secondSource = MutableInteractionSource()
    val firstEffect = GlassRuntimeEffect()
    val secondEffect = GlassRuntimeEffect()
    val responseStyle = GlassStyle {
      pressed {
        if (refractionMultiplier) refractionMultiplier(1.8f) else whitePointDelta(0.3f)
      }
    }
    val baseStyle = opticalStyle()
    val interactiveStyle = baseStyle.then(responseStyle)
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White)) {
          repeat(36) { index ->
            Box(Modifier.offset((index * 10).dp, 0.dp).size(10.dp, 240.dp).background(if (index % 2 == 0) Color.Black else Color.White))
          }
        }
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(gap.intValue.dp)) {
          Box(
            Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp)
              .hazeGlass(HazeEffectFactory { firstEffect }, HazeInput.Sources(state), interactiveStyle, HazePerformanceMode.Quality, true, firstSource),
          )
          Box(
            Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp)
              .hazeGlass(HazeEffectFactory { secondEffect }, HazeInput.Sources(state), baseStyle, HazePerformanceMode.Quality, true, secondSource),
          )
        }
      }
    }
    val bounds = onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
    fun PixelMap.diff(other: PixelMap, left: Float, right: Float, top: Float = 60f, bottom: Float = 140f): Float = regionDifference(
      this,
      other,
      (bounds.left + left * bounds.width / 360f).roundToInt(),
      (bounds.top + top * bounds.height / 240f).roundToInt(),
      (bounds.left + right * bounds.width / 360f).roundToInt(),
      (bounds.top + bottom * bounds.height / 240f).roundToInt(),
    )
    fun assertWhitePointDifference(active: PixelMap, idle: PixelMap) {
      assertThat(active.diff(idle, 40f, 140f)).isGreaterThan(0.005f)
    }
    fun assertMultiplierDifference(active: PixelMap, idle: PixelMap) {
      assertThat(maxOf(active.diff(idle, 40f, 55f, 90f, 110f), active.diff(idle, 125f, 140f, 90f, 110f))).isGreaterThan(0.005f)
    }
    val owner = checkNotNull(firstEffect.memberBindingForTest?.node?.container)
    val firstNode = checkNotNull(firstEffect.memberBindingForTest).node
    val secondNode = checkNotNull(secondEffect.memberBindingForTest).node
    assertThat(owner.model.surfaces.size).isEqualTo(2)
    assertThat(owner.renderer.runtimeSurfaces).isEmpty()
    val idleSeparate = captureRootPixels()

    val firstPress = PressInteraction.Press(Offset(50f, 40f))
    runBlocking { firstSource.emit(firstPress) }
    waitForIdle()
    val activeSeparate = captureRootPixels()
    if (refractionMultiplier) assertMultiplierDifference(activeSeparate, idleSeparate) else assertWhitePointDifference(activeSeparate, idleSeparate)
    assertThat(firstEffect.currentInteractionState.refractionMultiplier).isEqualTo(if (refractionMultiplier) 1.8f else 1f)
    assertThat(firstEffect.currentInteractionState.whitePointDelta).isEqualTo(if (refractionMultiplier) 0f else 0.3f)
    assertThat(activeSeparate.diff(idleSeparate, 160f, 260f)).isLessThan(1f / 255f)

    composeTestRule.runOnIdle { gap.intValue = 40 }
    waitForIdle()
    val activeMerged = captureRootPixels()
    assertThat(owner.model.surfaces.size).isEqualTo(1)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    val mergedRuntime = owner.renderer.runtimeSurfaces.single()
    val mergedMembers = checkNotNull(mergedRuntime.preparedRender).params.mergedField!!.members
    assertThat(mergedMembers.first().refractionMultiplier).isEqualTo(if (refractionMultiplier) 1.8f else 1f)
    assertThat(mergedMembers.first().whitePointDelta).isEqualTo(if (refractionMultiplier) 0f else 0.3f)
    assertThat(mergedMembers.last().refractionMultiplier).isEqualTo(1f)
    assertThat(mergedMembers.last().whitePointDelta).isEqualTo(0f)
    assertThat(firstEffect.memberBindingForTest?.node).isSameInstanceAs(firstNode)
    assertThat(secondEffect.memberBindingForTest?.node).isSameInstanceAs(secondNode)

    runBlocking { firstSource.emit(PressInteraction.Release(firstPress)) }
    waitForIdle()
    val idleMerged = captureRootPixels()
    if (refractionMultiplier) assertMultiplierDifference(activeMerged, idleMerged) else assertWhitePointDifference(activeMerged, idleMerged)
    assertThat(activeMerged.diff(idleMerged, 160f, 260f)).isLessThan(1f / 255f)

    composeTestRule.runOnIdle { gap.intValue = 0 }
    waitForIdle()
    val idleSplit = captureRootPixels()
    assertThat(owner.model.surfaces.size).isEqualTo(2)
    assertThat(owner.renderer.runtimeSurfaces).isEmpty()
    composeTestRule.runOnIdle { gap.intValue = 40 }
    waitForIdle()
    assertThat(owner.model.surfaces.size).isEqualTo(1)
    val splitPress = PressInteraction.Press(Offset(50f, 40f))
    runBlocking { firstSource.emit(splitPress) }
    waitForIdle()
    composeTestRule.runOnIdle { gap.intValue = 0 }
    waitForIdle()
    val activeSplit = captureRootPixels()
    assertThat(owner.model.surfaces.size).isEqualTo(2)
    assertThat(owner.renderer.runtimeSurfaces).isEmpty()
    if (refractionMultiplier) assertMultiplierDifference(activeSplit, idleSplit) else assertWhitePointDifference(activeSplit, idleSplit)
    assertThat(firstEffect.currentInteractionState.refractionMultiplier).isEqualTo(if (refractionMultiplier) 1.8f else 1f)
    assertThat(firstEffect.currentInteractionState.whitePointDelta).isEqualTo(if (refractionMultiplier) 0f else 0.3f)
    assertThat(activeSplit.diff(idleSplit, 160f, 260f)).isLessThan(1f / 255f)
    assertThat(firstEffect.memberBindingForTest?.node).isSameInstanceAs(firstNode)
    assertThat(secondEffect.memberBindingForTest?.node).isSameInstanceAs(secondNode)
    runBlocking { firstSource.emit(PressInteraction.Cancel(splitPress)) }
    waitForIdle()
    assertThat(captureRootPixels().diff(idleSplit, 40f, 260f)).isLessThan(0.01f)
  }

  private fun assertWhitePointDelta() = runScreenshotTest(size = Size(360f, 240f)) {
    val state = HazeState()
    val sources = List(2) { MutableInteractionSource() }
    val effects = List(2) { GlassRuntimeEffect() }
    val pressed = opticalStyle().then(GlassStyle { pressed { whitePointDelta(0.3f) } })
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White)) {
          repeat(36) { index ->
            Box(Modifier.offset((index * 10).dp, 0.dp).size(10.dp, 240.dp).background(if (index % 2 == 0) Color.Black else Color.White))
          }
        }
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
          effects.forEachIndexed { index, effect ->
            Box(
              Modifier.offset((40 + index * 110).dp, 60.dp).size(100.dp, 80.dp)
                .hazeGlass(HazeEffectFactory { effect }, HazeInput.Sources(state), pressed, HazePerformanceMode.Quality, true, sources[index]),
            )
          }
        }
      }
    }
    val bounds = onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
    fun PixelMap.diff(other: PixelMap, left: Float, right: Float): Float = regionDifference(
      this,
      other,
      (bounds.left + left * bounds.width / 360f).roundToInt(),
      (bounds.top + 60f * bounds.height / 240f).roundToInt(),
      (bounds.left + right * bounds.width / 360f).roundToInt(),
      (bounds.top + 140f * bounds.height / 240f).roundToInt(),
    )
    val idle = captureRootPixels()
    val firstPress = PressInteraction.Press(Offset(50f, 40f))
    runBlocking { sources[0].emit(firstPress) }
    waitForIdle()
    val firstActive = captureRootPixels()
    assertThat(firstActive.diff(idle, 40f, 140f)).isGreaterThan(0.005f)
    assertThat(firstActive.diff(idle, 160f, 260f)).isLessThan(1f / 255f)
    val secondPress = PressInteraction.Press(Offset(50f, 40f))
    runBlocking { sources[1].emit(secondPress) }
    waitForIdle()
    val bothActive = captureRootPixels()
    assertThat(bothActive.diff(idle, 160f, 260f)).isGreaterThan(0.005f)
    runBlocking { sources[0].emit(PressInteraction.Release(firstPress)) }
    waitForIdle()
    val secondOnly = captureRootPixels()
    assertThat(secondOnly.diff(idle, 40f, 140f)).isLessThan(1f / 255f)
    assertThat(secondOnly.diff(idle, 160f, 260f)).isGreaterThan(0.005f)
    runBlocking { sources[1].emit(PressInteraction.Cancel(secondPress)) }
    waitForIdle()
    assertThat(captureRootPixels().diff(idle, 40f, 260f)).isLessThan(0.01f)
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    val runtime = owner.renderer.runtimeSurfaces.single()
    val delegate = runtime.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.fusedShader != null).isEqualTo(true)
    assertThat(checkNotNull(runtime.preparedRender).params.mergedField?.members?.size).isEqualTo(2)
  }

  private fun assertRefractionMultiplier() = runScreenshotTest(size = Size(360f, 240f)) {
    val state = HazeState()
    val source = MutableInteractionSource()
    val effect = GlassRuntimeEffect()
    val secondEffect = GlassRuntimeEffect()
    val secondSource = MutableInteractionSource()
    val pressed = opticalStyle().then(GlassStyle { pressed { refractionMultiplier(1.8f) } })
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White)) {
          repeat(36) { index ->
            Box(Modifier.offset((index * 10).dp, 0.dp).size(10.dp, 240.dp).background(if (index % 2 == 0) Color.Black else Color.White))
          }
        }
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
          Box(
            Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp)
              .hazeGlass(HazeEffectFactory { effect }, HazeInput.Sources(state), pressed, HazePerformanceMode.Quality, true, source),
          )
          Box(
            Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp)
              .hazeGlass(HazeEffectFactory { secondEffect }, HazeInput.Sources(state), opticalStyle(), HazePerformanceMode.Quality, true, secondSource),
          )
        }
      }
    }
    val bounds = onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
    fun PixelMap.diff(other: PixelMap, left: Float, right: Float): Float = regionDifference(
      this,
      other,
      (bounds.left + left * bounds.width / 360f).roundToInt(),
      (bounds.top + 60f * bounds.height / 240f).roundToInt(),
      (bounds.left + right * bounds.width / 360f).roundToInt(),
      (bounds.top + 140f * bounds.height / 240f).roundToInt(),
    )
    val idle = captureRootPixels()
    val owner = checkNotNull(effect.memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    val runtime = owner.renderer.runtimeSurfaces.single()
    val delegate = runtime.delegate as RuntimeShaderGlassDelegate
    val initialOpticalKey = checkNotNull(runtime.preparedRender).opticalKey
    val initialPlan = checkNotNull(runtime.preparedRender).plan
    val press = PressInteraction.Press(Offset(50f, 40f))
    runBlocking { source.emit(press) }
    waitForIdle()
    val active = captureRootPixels()
    val activePreparedRender = checkNotNull(runtime.preparedRender)
    val mergedMember = activePreparedRender.params.mergedField!!.members.first()
    assertThat(mergedMember.refractionMultiplier).isEqualTo(1.8f)
    assertThat(mergedMember.opticalRadius).isGreaterThan(0f)
    assertThat(activePreparedRender.opticalKey).isNotEqualTo(initialOpticalKey)
    val activeRuntime = owner.renderer.runtimeSurfaces.single()
    assertThat(activeRuntime).isSameInstanceAs(runtime)
    val activeDelegate = activeRuntime.delegate as RuntimeShaderGlassDelegate
    assertThat(activeDelegate.lastSuccessfulStageInputs?.optical).isEqualTo(activePreparedRender.opticalKey)
    assertThat(activeDelegate.opticalRecordCount).isGreaterThan(0)
    assertThat(activePreparedRender.plan).isEqualTo(initialPlan)
    assertThat(activePreparedRender.params.mergedField.members.size).isEqualTo(2)
    val edgeDifference = maxOf(
      active.diff(idle, 40f, 55f),
      active.diff(idle, 125f, 140f),
    )
    assertThat(edgeDifference).isGreaterThan(0.005f)
    assertThat(active.diff(idle, 160f, 260f)).isLessThan(1f / 255f)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    runBlocking { source.emit(PressInteraction.Release(press)) }
    waitForIdle()
    assertThat(captureRootPixels().diff(idle, 40f, 140f)).isLessThan(0.01f)
  }

  private fun opticalStyle(lightRadiusFraction: Float = 2f) = GlassStyle {
    optics(GlassOptics(refractionStrength = 0.4f, refractionDisplacement = 40.dp, refractionHeightFraction = 0.5f, refractionDetailIntensity = 0.76f))
    interactionLightRadiusFraction(lightRadiusFraction)
    tint(Color.Transparent)
    backgroundColor(Color.Transparent)
    edgeSoftness(0.dp)
    ambientResponse(0f)
    specularIntensity(0f)
  }
}

private fun regionDifference(first: PixelMap, second: PixelMap, left: Int, top: Int, right: Int, bottom: Int): Float {
  var difference = 0f
  var count = 0
  for (y in top until bottom) for (x in left until right) {
    val a = first[x, y]
    val b = second[x, y]
    difference += abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
    count += 3
  }
  return difference / count
}
