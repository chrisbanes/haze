// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

/**
 * The authored background sits beneath captured input before fallback optics and tone on the
 * Android 12 and 12L hardware path, so tone reaches it wherever the input is transparent.
 */
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class FallbackGlassToneInstrumentationTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val whitePoint = mutableFloatStateOf(0f)
  private val blurRadius = mutableFloatStateOf(BLUR_DP)
  private val mergedScene = mutableStateOf(false)
  private var scrim = Color.Transparent
  private var hardware = false

  // Two merged members, then the standalone material, so each node keeps its own effect.
  private val effects = List(3) { GlassRuntimeEffect() }

  @Test fun standalone_toneIncludesAuthoredBackground() = exerciseTone(merged = false, blurred = true)

  @Test fun merged_toneIncludesEachMembersBackground() = exerciseTone(merged = true, blurred = true)

  @Test fun standalone_toneOnlyIncludesAuthoredBackground() = exerciseTone(merged = false, blurred = false)

  @Test fun merged_toneOnlyIncludesEachMembersBackground() = exerciseTone(merged = true, blurred = false)

  @Test
  fun merged_blurSamplesAuthoredBackgroundBeforeTone() {
    blurRadius.floatValue = 0f
    setScene(merged = true)
    val sharp = capture()
    rule.runOnIdle { blurRadius.floatValue = PROOF_BLUR_DP }
    val blurred = capture()
    val delegate = activeDelegate(merged = true)
    assertThat(delegate.filteredInputDrawnForTest).isTrue()
    assertThat(delegate.filteredInputEffectForTest).isNotNull()

    // The winning blue member ends at x = 180dp, deep inside the union and clear of the patch.
    val samples = mapOf(
      "sharpBlueSide" to sharp.at(177f, 70f),
      "sharpGreenSide" to sharp.at(183f, 70f),
      "blurBlueSide" to blurred.at(177f, 70f),
      "blurGreenSide" to blurred.at(183f, 70f),
      "blurDeepBlue" to blurred.at(60f, 70f),
      "blurDeepGreen" to blurred.at(250f, 70f),
      // Two dp inside the straight top edge (y = 20dp), over transparent input.
      "sharpNearEdge" to sharp.at(60f, 22f),
      "blurNearEdge" to blurred.at(60f, 22f),
      "exterior" to blurred.at(60f, 10f),
    )
    val message = samples.describe()
    assertColor(samples.getValue("exterior"), Magenta, "exterior", message)
    assertColor(samples.getValue("sharpBlueSide"), Color.Blue, "sharpBlueSide", message)
    assertColor(samples.getValue("sharpGreenSide"), Color.Green, "sharpGreenSide", message)
    assertColor(samples.getValue("blurDeepBlue"), Color.Blue, "blurDeepBlue", message)
    assertColor(samples.getValue("blurDeepGreen"), Color.Green, "blurDeepGreen", message)
    for (side in listOf("blurBlueSide", "blurGreenSide")) {
      val color = samples.getValue(side)
      assertThat(color.blue, "$side blue: $message").isGreaterThan(0.1f)
      assertThat(color.green, "$side green: $message").isGreaterThan(0.1f)
    }
    // Red can only come from the magenta underlay; a pre-blur background must stay opaque.
    assertThat(samples.getValue("sharpNearEdge").red, "sharpNearEdge red: $message").isLessThan(TOLERANCE)
    assertThat(samples.getValue("blurNearEdge").red, "blurNearEdge red: $message").isLessThan(TOLERANCE)
  }

  @Test
  fun toneOnly_explicitScrimCoversAuthoredBackground() {
    blurRadius.floatValue = 0f
    whitePoint.floatValue = 1f
    scrim = Color.Red.copy(alpha = 0.5f)
    // White tone, then the half-red scrim over it.
    val expected = Color(1f, 0.5f, 0.5f)
    setScene(merged = false)
    for (merged in listOf(false, true)) {
      rule.runOnIdle { mergedScene.value = merged }
      val image = capture()
      assertProcessed(activeDelegate(merged), blurred = false)
      val phase = if (merged) "merged" else "standalone"
      val points = if (merged) {
        listOf(Sample("blueMember", 50f, 70f, expected), Sample("greenMember", 250f, 70f, expected))
      } else {
        listOf(Sample("background", 80f, 90f, expected))
      }
      val patch = Sample("patch", 70f, 175f, expected)
      val exterior = Sample("exterior", 10f, 260f, Magenta)
      val values = (points + patch + exterior).associate { "$phase-${it.label}" to image.at(it.x, it.y) }
      val message = "density=${rule.density.density} " + values.describe()
      for (point in listOf(patch, exterior) + points) {
        assertColor(values.getValue("$phase-${point.label}"), point.color, "$phase-${point.label}", message)
      }
    }
  }

  private fun exerciseTone(merged: Boolean, blurred: Boolean) {
    if (!blurred) blurRadius.floatValue = 0f
    setScene(merged)

    val neutral = capture()
    val neutralDelegate = activeDelegate(merged)
    if (blurred) {
      assertProcessed(neutralDelegate, blurred = true)
    } else {
      assertThat(neutralDelegate.filteredInputDrawnForTest, "neutral tone-only takes the natural path").isFalse()
    }

    rule.runOnIdle { whitePoint.floatValue = 1f }
    val toned = capture()
    assertProcessed(activeDelegate(merged), blurred)

    val points = if (merged) {
      listOf(
        Sample("blueMember", 50f, 70f, Color.Blue),
        Sample("greenMember", 250f, 70f, Color.Green),
        Sample("overlap", 150f, 70f, Color.Blue),
      )
    } else {
      listOf(Sample("background", 80f, 90f, Color.Blue))
    }
    val patch = Sample("patch", 70f, 175f, Color.Red)
    val exterior = Sample("exterior", 10f, 260f, Magenta)
    val values = (points + patch + exterior).flatMap {
      listOf("neutral-${it.label}" to neutral.at(it.x, it.y), "toned-${it.label}" to toned.at(it.x, it.y))
    }.toMap()
    val message = "density=${rule.density.density} " + values.describe()

    // Controls first, so a failure in the background samples diagnoses colour ordering.
    assertColor(values.getValue("neutral-patch"), Color.Red, "neutral-patch", message)
    assertColor(values.getValue("toned-patch"), Color.White, "toned-patch", message)
    assertColor(values.getValue("neutral-exterior"), Magenta, "neutral-exterior", message)
    assertColor(values.getValue("toned-exterior"), Magenta, "toned-exterior", message)
    for (point in points) {
      assertColor(values.getValue("neutral-${point.label}"), point.color, "neutral-${point.label}", message)
    }
    for (point in points) {
      assertColor(values.getValue("toned-${point.label}"), Color.White, "toned-${point.label}", message)
    }
  }

  private fun setScene(merged: Boolean) {
    mergedScene.value = merged
    rule.setContent {
      val source = remember { HazeState() }
      val radius = blurRadius.floatValue
      val white = whitePoint.floatValue
      val style = GlassStyle {
        shape(RoundedCornerShape(0.dp))
        optics(
          GlassOptics(
            blurRadius = OpticalSizeValue.Fixed(radius.dp),
            depth = OpticalSizeValue.Fixed(if (radius > 0f) 1f else 0f),
          ),
        )
        backgroundColor(Color.Blue)
        tint(Color.Transparent)
        fallbackColor(scrim)
        specularIntensity(0f)
        edgeSoftness(0.dp)
        edgeShadow(Color.Transparent)
        edgeOutline(Color.Transparent, 0.dp)
        rimHighlight(0f, 0.dp, 0f)
        ambientResponse(0f)
        contrast(0f)
        whitePoint(white)
        chromaMultiplier(1f)
      }
      // The magenta underlay lies outside the registered source.
      Box(Modifier.size(300.dp, 300.dp).background(Magenta).testTag("root")) {
        Canvas(
          Modifier.size(300.dp, 300.dp).hazeSource(source).drawBehind {
            hardware = drawContext.canvas.nativeCanvas.isHardwareAccelerated
          },
        ) {
          // Everything but the opaque patch is genuinely transparent.
          drawRect(Color.Red, Offset(40.dp.toPx(), 140.dp.toPx()), Size(60.dp.toPx(), 70.dp.toPx()))
        }
        if (mergedScene.value) {
          Box(Modifier.size(300.dp, 300.dp).hazeGlassContainer(16.dp)) {
            listOf(20 to Color.Blue, 120 to Color.Green).forEachIndexed { index, (x, color) ->
              Box(
                Modifier.offset(x = x.dp, y = 20.dp).size(160.dp, 200.dp).hazeGlass(
                  factory = HazeEffectFactory { effects[index] },
                  input = HazeInput.Sources(source),
                  style = style.then { backgroundColor(color) },
                  performanceMode = null,
                  expandLayerBounds = true,
                  interactionSource = null,
                  memberTransition = GlassMemberTransition.None,
                ),
              )
            }
          }
        } else {
          Box(
            Modifier.offset(x = 20.dp, y = 20.dp).size(260.dp, 200.dp).hazeGlass(
              factory = HazeEffectFactory { effects[2] },
              input = HazeInput.Sources(source),
              style = style,
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
  }

  private fun capture(): PixelMap {
    rule.waitForIdle()
    return rule.onNodeWithTag("root").captureToImage().toPixelMap()
  }

  private fun PixelMap.at(x: Float, y: Float): Color {
    val density = rule.density.density
    return this[(x * density).toInt(), (y * density).toInt()]
  }

  private fun activeDelegate(merged: Boolean): FallbackGlassDelegate {
    assertThat(Build.VERSION.SDK_INT).isBetween(31, 32)
    assertThat(hardware, "hardware-accelerated source canvas").isTrue()
    val runtime = if (merged) {
      val surfaces = checkNotNull(effects[0].memberBindingForTest?.node?.container).renderer.runtimeSurfaces
      assertThat(surfaces.size, "one configured merged runtime").isEqualTo(1)
      surfaces.single()
    } else {
      effects[2]
    }
    return runtime.delegate as FallbackGlassDelegate
  }

  private fun assertProcessed(delegate: FallbackGlassDelegate, blurred: Boolean) {
    assertThat(delegate.filteredInputDrawnForTest, "filtered delegate drawn").isTrue()
    assertThat(delegate.filteredInputRecordCountForTest).isGreaterThan(0)
    if (blurred) {
      assertThat(delegate.filteredInputEffectForTest, "native blur effect").isNotNull()
    } else {
      assertThat(delegate.filteredInputLayerForTest, "filtered layer").isNotNull()
      assertThat(delegate.filteredInputEffectForTest, "native blur effect").isNull()
    }
  }

  private fun assertColor(actual: Color, expected: Color, label: String, message: String) {
    assertThat(actual.red, "$label red: $message").isCloseTo(expected.red, TOLERANCE)
    assertThat(actual.green, "$label green: $message").isCloseTo(expected.green, TOLERANCE)
    assertThat(actual.blue, "$label blue: $message").isCloseTo(expected.blue, TOLERANCE)
  }

  private fun Map<String, Color>.describe(): String = entries.joinToString { (label, color) ->
    "$label=(%.3f, %.3f, %.3f)".format(color.red, color.green, color.blue)
  }

  private data class Sample(val label: String, val x: Float, val y: Float, val color: Color)

  private companion object {
    const val BLUR_DP = 4f
    const val PROOF_BLUR_DP = 8f
    const val TOLERANCE = 3f / 255f
    val Magenta = Color(0xFFFF00FF)
  }
}
