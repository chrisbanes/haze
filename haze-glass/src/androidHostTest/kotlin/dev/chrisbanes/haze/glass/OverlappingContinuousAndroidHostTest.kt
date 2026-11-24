// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.math.abs
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Overlapping continuous corners on Robolectric SDK 35 (#1749), where a larger continuous program
 * draws nothing: every continuous program compiles, and the fused programs draw a smoothed leaf
 * alone and in a four-member merged field with the outline of [OverlapReference].
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlappingContinuousAndroidHostTest : ContextTest() {

  @Test
  @Config(sdk = [35])
  fun sdk35_everyContinuousProgramBuilds() {
    val flags = listOf(false, true)
    val sources = buildMap {
      for (interaction in flags) for (detail in flags) for (merged in flags) for (outset in flags) {
        val key = "interaction=$interaction detail=$detail merged=$merged outset=$outset"
        put("fused $key", GlassShaders.buildFused(interaction, detail, merged, continuous = true, overlapping = true, coverageOutset = outset))
        put("fused circular optics $key", GlassShaders.buildFusedWithCircularOptics(interaction, detail, merged, outset, overlapping = true))
      }
      for (interactive in flags) for (merged in flags) for (outset in flags) {
        val key = "interactive=$interactive merged=$merged outset=$outset"
        put("optical $key", GlassShaders.buildOptical(interactive, merged, continuous = true, overlapping = true, coverageOutset = outset))
        put("detail circular optics $key", GlassShaders.buildRefractionDetailWithCircularOptics(interactive, merged, outset, overlapping = true))
        for (coverageOnly in flags) {
          put("detail coverageOnly=$coverageOnly $key", GlassShaders.buildRefractionDetail(interactive, coverageOnly, merged, continuous = true, overlapping = true, coverageOutset = outset))
        }
      }
      for (merged in flags) {
        put("lighting merged=$merged", GlassShaders.buildInteractionLighting(merged, continuous = true, overlapping = true))
        put("rim merged=$merged", GlassShaders.buildRim(merged, continuous = true, overlapping = true))
      }
      put("merged background", GlassShaders.buildMergedBackground(continuous = true, overlapping = true))
    }
    // SDK 35 enforces its program size limit when it builds a render effect, not when it parses.
    val rejected = sources.mapNotNull { (name, source) ->
      runCatching {
        val effect = createRuntimeEffect(source)
        if ("uniform shader content;" in source) createRuntimeShaderRenderEffect(effect, arrayOf("content"), arrayOf(null)) {}
      }.exceptionOrNull()?.let { e -> "$name: ${generateSequence(e) { it.cause }.last().message?.take(200)}" }
    }
    assertThat(rejected).isEmpty()
  }

  @Test
  @Config(sdk = [35], qualifiers = "mdpi")
  fun sdk35_mergedFieldDrawsOverlappingLeaf() {
    val source = GlassShaders.buildFused(merged = true, continuous = true, overlapping = true)
    assertThat(render("merged-quality", source, merged = true, circularOptics = false)).isEmpty()
  }

  @Test
  @Config(sdk = [35], qualifiers = "mdpi")
  fun sdk35_mergedFieldWithCircularOpticsDrawsOverlappingLeaf() {
    val source = GlassShaders.buildFusedWithCircularOptics(merged = true, overlapping = true)
    assertThat(render("merged-circular-optics", source, merged = true, circularOptics = true)).isEmpty()
  }

  @Test
  @Config(sdk = [35], qualifiers = "mdpi")
  fun sdk35_standaloneLeafDraws() {
    val source = GlassShaders.buildFused(continuous = true, overlapping = true)
    assertThat(render("standalone", source, merged = false, circularOptics = false)).isEmpty()
  }

  /** Renders [source] and returns outline mismatches against [OverlapReference], or a blank failure. */
  private fun render(name: String, source: String, merged: Boolean, circularOptics: Boolean): List<String> {
    val failures = mutableListOf<String>()
    runAndroidComposeUiTest<ComponentActivity> {
      val density = runOnIdle { checkNotNull(activity).resources.displayMetrics.density }
      val node = if (merged) Size(220f * density, 220f * density) else Size(100f * density, 100f * density)
      // Members in dp: an overlapping smoothed leaf, the same leaf circular, and two disjoint
      // smoothed controls 6 dp apart, which bridge at a 16 dp merge distance.
      val leaf = OVERLAP_CASES.first { it.name == "leaf60-s1.0-ltr" }
      val circularLeaf = OVERLAP_CASES.first { it.name == "leaf60-s0.0-ltr" }
      val control = OverlapCase("control100r16", 100.0, 100.0, 16.0, 16.0, 16.0, 16.0, 1.0, rtl = false)
      val placed = if (merged) {
        // A 2x2 grid within Robolectric's 320 dp screen: 10 dp gaps, except 6 dp between the controls.
        listOf(leaf to Offset(5f, 5f), circularLeaf to Offset(115f, 5f), control to Offset(5f, 115f), control to Offset(111f, 115f))
      } else {
        listOf(leaf to Offset.Zero)
      }
      fun radii(case: OverlapCase) = CornerRadii(
        (case.radii[0] * density).toFloat(),
        (case.radii[1] * density).toFloat(),
        (case.radii[2] * density).toFloat(),
        (case.radii[3] * density).toFloat(),
        case.smoothing[0].toFloat(),
        case.smoothing[1].toFloat(),
        case.smoothing[2].toFloat(),
        case.smoothing[3].toFloat(),
      )
      val field = if (merged) {
        GlassMergedField(
          members = placed.map { (case, origin) ->
            val rect = Rect(origin * density, Size(case.width.toFloat(), case.height.toFloat()) * density)
            GlassMergedMember(rect, radii(case), Color.Green, Color.Transparent, layoutRect = rect)
          },
          mergeDistancePx = 16f * density,
          opticalSize = Size(100f * density, 100f * density),
        )
      } else {
        null
      }
      val effect = GlassRuntimeEffect().apply {
        style = GlassStyle {
          ambientResponse(0f)
          edgeSoftness(0.dp)
          specularIntensity(0f)
          tint(Color.Green)
        }
        mergedField = field
      }
      val style = resolveGlassStyle(effect, node, Density(density), LayoutDirection.Ltr)
      val params = buildGlassRenderParams(style, GlassCoordinates(node, Offset.Zero, node, 1f), circularCornerOptics = circularOptics)
      // Standalone: the node is the leaf; its smoothed radii bypass the production fit.
      val optical = params.opticalEffectKey().let { if (merged) it else it.copy(cornerRadii = radii(leaf)) }
      val detail = params.refractionDetailEffectKey().let { if (merged) it else it.copy(cornerRadii = radii(leaf)) }
      val renderEffect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) {
        setOpticalUniforms(optical)
        setRefractionDetailUniforms(detail)
        if (circularOptics) setCircularOpticsUniforms(optical.cornerRadii, optical.mergedField)
      }.asComposeRenderEffect()
      setContent {
        Box(
          Modifier
            .size(width = (node.width / density).dp, height = (node.height / density).dp)
            .graphicsLayer { this.renderEffect = renderEffect }
            .background(Color.Red)
            .testTag(TAG),
        )
      }
      waitForIdle()
      val image = onNodeWithTag(TAG).captureToImage()
      val pixels = image.toPixelMap()
      check(pixels.width >= node.width.toInt() && pixels.height >= node.height.toInt()) { "$name capture clipped: ${pixels.width}x${pixels.height}" }
      var green = 0
      var checked = 0
      val outline = mutableListOf<String>()
      for ((case, origin) in placed) {
        val reference = OverlapReference(case)
        // Every 2 dp, away from the boundary by more than 1.5 dp of antialiasing and refraction.
        for (y in 1 until case.height.toInt() step 2) {
          for (x in 1 until case.width.toInt() step 2) {
            val sd = reference.nearest(x + 0.5, y + 0.5).signedDistance
            if (abs(sd) < 1.5) continue
            // The smooth union changes the field within the merge distance of another member.
            val point = Offset(origin.x + x + 0.5f, origin.y + y + 0.5f)
            if (placed.any { (other, o) -> o != origin && Rect(o, Size(other.width.toFloat(), other.height.toFloat())).inflate(16f).contains(point) }) continue
            val px = ((origin.x + x + 0.5) * density).toInt()
            val py = ((origin.y + y + 0.5) * density).toInt()
            val color = pixels[px, py]
            val tinted = color.isTint()
            checked++
            if (tinted) green++
            if (tinted != (sd < 0)) {
              if (outline.size < 8) outline += "$name ${case.name} ($x, $y) sd ${"%.2f".format(sd)} color $color"
            }
          }
        }
      }
      // These centres are outside the smoothed leaf and more than one pixel inside its circular
      // control. Check both arcs: a circular silhouette or a missing second corner must show green.
      for (probe in listOf(Offset(52.5f, 1.5f), Offset(47.5f, 98.5f))) {
        val smoothed = placed.first().second + probe
        val smoothedPixel = pixels[(smoothed.x * density).toInt(), (smoothed.y * density).toInt()]
        if (smoothedPixel.isTint()) failures += "$name circular silhouette at $probe: $smoothedPixel"
        if (merged) {
          val circular = placed[1].second + probe
          val circularPixel = pixels[(circular.x * density).toInt(), (circular.y * density).toInt()]
          if (!circularPixel.isTint()) failures += "$name circular control missing tint at $probe: $circularPixel"
        }
      }
      if (merged) {
        // The 6 dp gap between the controls bridges below half the merge distance.
        val bridge = pixels[(108f * density).toInt(), (165f * density).toInt()]
        if (bridge.green <= 0.5f) failures += "$name bridge midpoint not tinted: $bridge"
        val separate = pixels[(110f * density).toInt(), (55f * density).toInt()]
        if (separate.isTint()) failures += "$name separate midpoint tinted: $separate"
      }
      assertThat(checked, name).isGreaterThan(0)
      if (green == 0) failures += "$name drew no tint (blank)"
      failures += outline
    }
    return failures
  }

  private fun Color.isTint() = green > 0.5f && red < 0.5f

  private companion object {
    const val TAG = "overlap-glass"
  }
}
