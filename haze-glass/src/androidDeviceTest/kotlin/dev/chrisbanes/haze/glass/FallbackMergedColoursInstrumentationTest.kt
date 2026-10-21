// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

/**
 * Draws [drawMergedColours] into a hardware canvas. The legacy renderer on API 23-27 applied
 * path-masked `Src` across a member's whole bounding box, which Skia, Robolectric and desktop
 * do not reproduce, so only a device can catch that regression.
 */
@OptIn(ExperimentalTestApi::class)
class FallbackMergedColoursInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun drawMergedColours_withinBudget_coversOnlyEachMembersShape() {
    val paths = FallbackMergedPaths(
      field(
        member(Rect(0f, 0f, 120f, 120f), radius = 60f, tint = Color.Red.copy(alpha = 0.5f)),
        member(Rect(80f, 0f, 280f, 120f), radius = 10f, tint = Color.Green.copy(alpha = 0.5f)),
      ),
    )
    assertThat(paths.layerBounds(null), "layerBounds").isNotNull()
    val pixels = capture(widthPx = 300, heightPx = 140) { drawMergedColours(paths, 1f) { it.tint } }

    // Red over blue where only member 0 is.
    pixels.assertColor(60, 60, 0.5f, 0f, 0.5f, "member 0 only")
    // The overlap shows member 0 once.
    pixels.assertColor(100, 60, 0.5f, 0f, 0.5f, "overlap")
    // Inside member 0's bounding box but outside its circle: member 1 must survive.
    pixels.assertColor(115, 8, 0f, 0.5f, 0.5f, "member 1 inside member 0's box")
    pixels.assertColor(200, 60, 0f, 0.5f, 0.5f, "member 1 only")
    pixels.assertColor(5, 5, 0f, 0f, 1f, "outside both")
  }

  @Test
  fun drawMergedColours_withinBudget_hasNoSeamAlongInternalEdge() {
    val paths = FallbackMergedPaths(
      field(
        member(Rect(0f, 0f, 120f, 120f), radius = 60f, tint = Color.Green.copy(alpha = 0.5f)),
        member(Rect(80f, 0f, 280f, 120f), radius = 10f, tint = Color.Green.copy(alpha = 0.5f)),
      ),
    )
    val pixels = capture(widthPx = 300, heightPx = 140) { drawMergedColours(paths, 1f) { it.tint } }
    pixels.assertNoSeam()
  }

  @Test
  fun drawMergedColours_overBudgetUnionWithVisiblePartInBudget_hasNoSeamAlongInternalEdge() {
    val paths = FallbackMergedPaths(
      field(
        member(Rect(0f, 0f, 120f, 120f), radius = 60f, tint = Color.Green.copy(alpha = 0.5f)),
        member(Rect(80f, 0f, MAX_GLASS_LAYER_DIMENSION_PX + 100f, 120f), radius = 10f, tint = Color.Green.copy(alpha = 0.5f)),
      ),
    )
    assertThat(paths.layerBounds(null), "union layerBounds").isNull()
    val pixels = capture(widthPx = 300, heightPx = 140) {
      drawMergedColours(paths, 1f, visible = Rect(0f, 0f, 300f, 140f)) { it.tint }
    }
    pixels.assertNoSeam()
  }

  /** Member 0's arc runs through this window inside member 1. */
  private fun PixelMap.assertNoSeam() {
    val interior = this[90, 100]
    for (x in 96..108) {
      for (y in 96..108) {
        val c = this[x, y]
        listOf(c.red to interior.red, c.green to interior.green, c.blue to interior.blue).forEach { (a, b) ->
          assertThat(abs(a - b), "seam at ($x, $y)").isCloseTo(0f, 2f / 255f)
        }
      }
    }
  }

  private fun capture(widthPx: Int, heightPx: Int, draw: DrawScope.() -> Unit): PixelMap {
    composeTestRule.setContent {
      val size = with(LocalDensity.current) { DpSize(widthPx.toDp(), heightPx.toDp()) }
      Canvas(Modifier.testTag(TAG).size(size)) {
        drawRect(Color.Blue)
        draw()
      }
    }
    composeTestRule.waitForIdle()
    return composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
  }

  private fun PixelMap.assertColor(x: Int, y: Int, r: Float, g: Float, b: Float, name: String) {
    val c = this[x, y]
    assertThat(c.red, "$name red at ($x, $y)").isCloseTo(r, TOLERANCE)
    assertThat(c.green, "$name green at ($x, $y)").isCloseTo(g, TOLERANCE)
    assertThat(c.blue, "$name blue at ($x, $y)").isCloseTo(b, TOLERANCE)
  }

  private fun member(rect: Rect, radius: Float, tint: Color) = GlassMergedMember(
    rect = rect,
    cornerRadii = CornerRadii(radius, radius, radius, radius),
    tint = tint,
    backgroundColor = Color.Transparent,
  )

  private fun field(vararg members: GlassMergedMember) = GlassMergedField(
    members = members.toList(),
    mergeDistancePx = 40f,
    opticalSize = Size(80f, 80f),
  )

  private companion object {
    const val TAG = "canvas"
    const val TOLERANCE = 0.02f
  }
}
