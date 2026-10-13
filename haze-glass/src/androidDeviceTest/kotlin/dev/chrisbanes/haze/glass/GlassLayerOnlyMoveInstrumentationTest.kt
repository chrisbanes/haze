// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

/**
 * Moving fallback Glass members and their input together with only an ancestor layer translation
 * must redraw them, standalone or merged, including the part the move reveals (#1534).
 */
// Runtime-shader Glass replaces the fallback from API 33. API 27 draws no fallback Glass outside
// the translated ancestor layer's bounds even without a move, so it cannot show this redraw.
@SdkSuppress(minSdkVersion = 30, maxSdkVersion = 32)
class GlassLayerOnlyMoveInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val shift = mutableFloatStateOf(0f)
  private var density = 0f

  // The member's left edge, 150 dp short of the window's right edge.
  private var memberLeft = 0.dp

  @Test
  fun sourcesMember_movedByAncestorLayer_drawsGlassInRevealedPart() {
    val state = HazeState()
    setScene {
      Box(
        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
          .requiredSize(2000.dp, 400.dp)
          .hazeSource(state)
          .background(Color.Red),
      )
      Box(Modifier.member().hazeGlass(HazeInput.Sources(state), style))
    }
    assertLayerOnlyMovesDrawGlass()
  }

  @Test
  fun mergedMembers_movedByAncestorLayer_drawsGlassInRevealedPart() {
    val state = HazeState()
    val effects = List(2) { GlassRuntimeEffect() }
    setScene {
      Box(
        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
          .requiredSize(2000.dp, 400.dp)
          .hazeSource(state)
          .background(Color.Red),
      )
      // The container draws the merged members, so it must redraw too.
      Box(Modifier.fillMaxSize().hazeGlassContainer(40.dp)) {
        Box(Modifier.offset(memberLeft - 100.dp, 100.dp).size(120.dp).hazeGlass(effects[0], state))
        Box(Modifier.member().hazeGlass(effects[1], state))
      }
    }
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces, "merged surfaces").hasSize(1)
    assertLayerOnlyMovesDrawGlass()
  }

  @Test
  fun contentMember_movedByAncestorLayer_drawsGlassInRevealedPart() {
    setScene {
      Box(Modifier.member().hazeGlass(HazeInput.Content, style)) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    assertLayerOnlyMovesDrawGlass()
  }

  private fun setScene(content: @Composable () -> Unit) {
    composeTestRule.setContent {
      density = LocalDensity.current.density
      BoxWithConstraints(Modifier.fillMaxSize().testTag(ROOT_TAG).background(Color.White)) {
        memberLeft = maxWidth - 150.dp
        // The input moves with the member, so only the member's window position changes.
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shift.floatValue }) {
          content()
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun Modifier.hazeGlass(effect: GlassRuntimeEffect, state: HazeState): Modifier = hazeGlass(
    factory = HazeEffectFactory { effect },
    input = HazeInput.Sources(state),
    style = style,
    performanceMode = null,
    expandLayerBounds = true,
    interactionSource = null,
  )

  private fun Modifier.member(): Modifier = offset(memberLeft, 100.dp).size(300.dp, 120.dp)

  private fun assertLayerOnlyMovesDrawGlass() {
    val initial = capture()
    // 50 dp into the member: on screen at every shift.
    val alwaysVisible = initial.memberPixel(50.dp, shiftDp = 0.dp)
    assertGlass(alwaysVisible, "always-visible pixel before moving")

    repeat(2) { round ->
      moveTo((-200).dp)
      val moved = capture()
      // 225 dp into the member: past the window's right edge before the move.
      assertGlass(moved.memberPixel(225.dp, shiftDp = (-200).dp), "revealed pixel, move ${round + 1}")
      assertSame(alwaysVisible, moved.memberPixel(50.dp, shiftDp = (-200).dp), "always-visible pixel, move ${round + 1}")

      moveTo(0.dp)
      assertSame(alwaysVisible, capture().memberPixel(50.dp, shiftDp = 0.dp), "always-visible pixel, back ${round + 1}")
    }
  }

  private fun moveTo(value: Dp) {
    shift.floatValue = value.value * density
    composeTestRule.waitForIdle()
  }

  private fun capture(): PixelMap = composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage().toPixelMap()

  private fun PixelMap.memberPixel(x: Dp, shiftDp: Dp): Color =
    this[px(memberLeft + x + shiftDp), px(160.dp)]

  private fun px(value: Dp): Int = (value.value * density).toInt()

  /** Bare red input has no blue; the tint adds blue and the input keeps red above green. */
  private fun assertGlass(color: Color, name: String) {
    assertThat(color.blue, "$name: blue from the tint ($color)").isGreaterThan(0.2f)
    assertThat(color.red - color.green, "$name: red input over green ($color)").isGreaterThan(0.3f)
  }

  private fun assertSame(expected: Color, actual: Color, name: String) {
    val diff = maxOf(abs(expected.red - actual.red), abs(expected.green - actual.green), abs(expected.blue - actual.blue))
    assertThat(diff, "$name: $actual differs from $expected").isLessThan(0.05f)
  }

  private companion object {
    const val ROOT_TAG = "root"

    // Opaque white under a half-blue tint: Glass is bluish over red, bare source is pure red.
    val style = GlassStyle {
      backgroundColor(Color.White)
      tint(Color.Blue.copy(alpha = 0.5f))
      specularIntensity(0f)
      edgeSoftness(0.dp)
      edgeShadow(Color.Transparent)
      ambientResponse(0f)
    }
  }
}
