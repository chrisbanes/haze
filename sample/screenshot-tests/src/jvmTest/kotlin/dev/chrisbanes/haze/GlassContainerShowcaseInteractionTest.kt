// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.sample.GlassContainerShowcaseSample
import dev.chrisbanes.haze.sample.GlassContainerShowcaseSampleContent
import dev.chrisbanes.haze.sample.GlassContainerShowcaseState
import dev.chrisbanes.haze.sample.SamplesTheme
import kotlin.math.abs
import kotlin.test.Test

class GlassContainerShowcaseInteractionTest {
  @Test fun landscapeLargeTextLabelsFitWithoutClipping() = checkLargeTextLandscape(Size(760f, 360f))

  @Test fun landscapeLargeTextLabelsFitAtLayoutBoundary() = checkLargeTextLandscape(Size(760f, 448f))

  private fun checkLargeTextLandscape(size: Size) = runSkikoComposeUiTest(size = size, density = Density(1f)) {
    val state = GlassContainerShowcaseState().apply {
      pause()
      seek(4f / 14)
    }
    setContent {
      SamplesTheme {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
          GlassContainerShowcaseSampleContent(state, onBack = {})
        }
      }
    }
    listOf("INK", "Brush", "Stroke").forEach { label ->
      val node = onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
      val results = mutableListOf<TextLayoutResult>()
      node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
      assertThat(results.single().lineCount, "$label must fit on one line").isEqualTo(1)
      val bounds = node.fetchSemanticsNode().boundsInRoot
      assertThat(bounds.height, "$label must not be clipped by its control").isGreaterThan(results.single().getLineBottom(0) - 1f)
    }
    state.seek(7f / 14)
    onNodeWithContentDescription("Graphite ink").assertIsDisplayed().performClick()
    assertThat(state.colorIndex).isEqualTo(3)
  }

  @Test fun noMotionSpeedChangesKeepClosedPaletteClosed() = runSkikoComposeUiTest(
    size = Size(688f, 560f),
    density = Density(1f),
    effectContext = object : MotionDurationScale {
      override val scaleFactor = 0f
    },
  ) {
    setContent { SamplesTheme { GlassContainerShowcaseSample(rememberNavController()) } }
    onNodeWithText("Stroke").assertIsDisplayed()
    onNodeWithText("Brush").performClick()
    onNodeWithText("Stroke").assertDoesNotExist()
    onNodeWithContentDescription("Slow motion").performClick().assertIsOn()
    onNodeWithText("Stroke").assertDoesNotExist()
    onNodeWithContentDescription("Slow motion").performClick().assertIsOff()
    onNodeWithText("Stroke").assertDoesNotExist()
  }

  @Test fun rejoinAccessibilityActionCancelsActiveReturn() = runSkikoComposeUiTest(size = Size(688f, 560f), density = Density(1f)) {
    val state = GlassContainerShowcaseState().apply {
      pause()
      seek(7f / 14)
    }
    setContent { SamplesTheme { GlassContainerShowcaseSampleContent(state, onBack = {}) } }
    mainClock.autoAdvance = false
    val colors = onNodeWithContentDescription("Ink colors")
    colors.performTouchInput {
      down(center)
      moveBy(Offset(0f, 40f), delayMillis = 16)
      up()
    }
    repeat(3) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    assertThat(state.colorOffset).isGreaterThan(0f)
    assertThat(state.colorOffset).isLessThan(1f)
    val action = colors.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
    assertThat(action.label).isEqualTo("Rejoin palette")
    runOnIdle { action.action() }
    repeat(20) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
      assertThat(state.colorOffset).isEqualTo(0f)
    }
  }

  @Test fun largeTextControlsFitInShortViewport() = runSkikoComposeUiTest(size = Size(360f, 560f), density = Density(1f)) {
    val state = GlassContainerShowcaseState().apply {
      pause()
      seek(4f / 14)
    }
    setContent {
      SamplesTheme {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
          GlassContainerShowcaseSampleContent(state, onBack = {})
        }
      }
    }
    onNodeWithText("Brush").assertIsDisplayed()
    onNodeWithText("Stroke").assertIsDisplayed()
    onNodeWithContentDescription("Ink colors").assertIsDisplayed()
    onNodeWithContentDescription("Blue ink").assertIsDisplayed()
    onNodeWithContentDescription("Graphite ink").assertIsDisplayed()
    state.seek(7f / 14)
    onNodeWithContentDescription("Ink colors").assertIsDisplayed()
    onNodeWithContentDescription("Graphite ink").assertIsDisplayed().performClick()
    assertThat(state.colorIndex).isEqualTo(3)
  }

  @Test fun speedButtonTogglesWithoutResumingAndResetRestoresNormalSpeed() = runSkikoComposeUiTest(size = Size(688f, 560f), density = Density(1f)) {
    val state = GlassContainerShowcaseState().apply { pause() }
    setContent {
      SamplesTheme {
        GlassContainerShowcaseSampleContent(state, onBack = {})
      }
    }
    onNodeWithContentDescription("Slow motion").assertIsOff().performClick().assertIsOn()
    onNodeWithText("0.5×").assertExists()
    assertThat(state.isPlaying).isFalse()
    onNodeWithContentDescription("Reset demo").performClick()
    onNodeWithContentDescription("Slow motion").assertIsOff()
    onNodeWithText("1×").assertExists()
  }

  @Test fun indicationsStayInsideRoundedControls() = runSkikoComposeUiTest(size = Size(1280f, 720f), density = Density(1f)) {
    val state = GlassContainerShowcaseState().apply {
      pause()
      updateRecordingMode(true)
      seek(4f / 14)
    }
    setContent {
      SamplesTheme {
        CompositionLocalProvider(LocalIndication provides SolidIndication, LocalDensity provides Density(1f)) {
          GlassContainerShowcaseSampleContent(state, onBack = {})
        }
      }
    }
    waitForIdle()
    val controls = listOf(onNodeWithText("Brush"), onNodeWithText("Stroke"), onNodeWithContentDescription("Blue ink"))
    controls.forEach { control ->
      control.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
      waitForIdle()
      val pixels = onRoot().captureToImage().toPixelMap()
      val bounds = control.fetchSemanticsNode().boundsInRoot
      assertThat(pixels[bounds.center.x.toInt(), bounds.center.y.toInt()], "Indication must be present").isEqualTo(Color.Magenta)
      assertThat(pixels[bounds.left.toInt() + 2, bounds.top.toInt() + 2], "Indication must not fill the rounded corner").isNotEqualTo(Color.Magenta)
    }
  }

  @Test fun horizontalCollapseHasNoFinalPositionJump() = checkCollapse(Size(1280f, 720f), vertical = false)

  @Test fun verticalCollapseHasNoFinalPositionJump() = checkCollapse(Size(688f, 560f), vertical = true)

  private fun checkCollapse(size: Size, vertical: Boolean) = runSkikoComposeUiTest(size = size, density = Density(1f)) {
    val state = GlassContainerShowcaseState().apply {
      pause()
      updateRecordingMode(true)
      seek(4f / 14)
    }
    setContent {
      SamplesTheme {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          GlassContainerShowcaseSampleContent(state, onBack = {})
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    val positions = mutableListOf<Float>()
    state.seek(12f / 14)
    repeat(100) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
      val center = onNodeWithText("Brush").fetchSemanticsNode().boundsInRoot.center
      positions += if (vertical) center.y else center.x
    }
    val movements = positions.zipWithNext { a, b -> abs(b - a) }.filter { it > 0.01f }
    assertThat(abs(positions.last() - positions.first()), "Must exercise the collapse").isGreaterThan(80f)
    assertThat(movements.last(), "Final visible movement in pixels").isLessThanOrEqualTo(2f)
  }
}

// Make the indication's full drawing bounds visible without pointer timing or ripple fades.
private object SolidIndication : IndicationNodeFactory {
  override fun equals(other: Any?): Boolean = other === this
  override fun hashCode(): Int = 0

  override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
      drawContent()
      drawRect(Color.Magenta)
    }
  }
}
