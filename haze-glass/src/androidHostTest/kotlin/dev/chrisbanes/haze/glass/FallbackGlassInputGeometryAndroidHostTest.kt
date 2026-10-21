// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import org.junit.Test
import org.robolectric.annotation.Config

/** Physical geometry and calibrated colour are independent controls for the SDK 31 fallback. */
@Config(sdk = [31])
class FallbackGlassInputGeometryAndroidHostTest : ScreenshotTest() {
  @Test
  fun standalone_defaultOptics_preservesCalibratedSourceGeometry() = proof(merged = false)

  @Test
  fun merged_defaultOptics_preservesCalibratedSourceGeometry() = proof(merged = true)

  @Test
  fun standalone_movedMembers_preserveFixedSourceGeometry() = proof(merged = false, moved = true)

  @Test
  fun merged_movedMembers_preserveFixedSourceGeometry() = proof(merged = true, moved = true)

  @Test
  fun standalone_qualitySampling_preservesCalibratedSourceGeometry() = proof(merged = false, quality = true)

  @Test
  fun merged_qualitySampling_preservesCalibratedSourceGeometry() = proof(merged = true, quality = true)

  @Test
  fun merged_disabledOverlays_isolatesRawGradientResponse() = runScreenshotTest(size = ROOT_SIZE) {
    val scene = Scene(merged = true)
    install(scene)
    val reference = capturePatterns(scene, visible = false)
    val defaults = capturePatterns(scene, visible = true)
    validate(scene, reference, defaults, "default")
    val sourceSlope = slope(reference.originalGradient, 330)
    val ratios = mutableMapOf(Overlay.Default to sourceSlope / slope(defaults.originalGradient, 330))
    for (overlay in listOf(Overlay.NoHighlight, Overlay.NoAmbient, Overlay.None)) {
      scene.overlay.value = overlay
      val captures = capturePatterns(scene, visible = true)
      validate(scene, reference, captures, overlay.name)
      ratios[overlay] = sourceSlope / slope(captures.originalGradient, 330)
    }
    assertThat(abs(ratios.getValue(Overlay.Default) - 1f), "default original raw slope differs").isGreaterThan(.1f)
    assertThat(abs(ratios.getValue(Overlay.NoHighlight) - 1f), "removing highlight restores raw slope").isBetween(0f, .01f)
    assertThat(abs(ratios.getValue(Overlay.None) - 1f), "removing all overlays restores raw slope").isBetween(0f, .01f)
    assertThat(abs(ratios.getValue(Overlay.NoAmbient) - ratios.getValue(Overlay.Default)), "ambient edge is not the interior slope cause").isBetween(0f, .01f)
    println("OVERLAY_CAUSE originalRamp=0..900 rawRow=330 formula=sourceSlope/observedSlope ratios=$ratios implicated=highlight")
  }

  @Test
  fun standalone_precisionAndSensitivity_rejectsMissingAndMisregisteredInput() = precisionAndSensitivity(merged = false)

  @Test
  fun merged_precisionAndSensitivity_rejectsMissingAndMisregisteredInput() = precisionAndSensitivity(merged = true)

  private fun precisionAndSensitivity(merged: Boolean) = runScreenshotTest(size = ROOT_SIZE) {
    val scene = Scene(merged)
    install(scene)
    val reference = capturePatterns(scene, visible = false)
    validate(scene, reference, capturePatterns(scene, visible = true), "positive sensitivity")

    // Only captured gradient/features move. The independent positive reference remains frozen.
    scene.patternShift.value = 4
    val misregistered = capturePatterns(scene, visible = true)
    val measurement = measure(reference, misregistered, "known +4px shift")
    for (x in listOf(340, 380, 440)) {
      assertThat(abs(measurement.decoded.getValue(x) - x), "known shift rejects x=$x").isGreaterThan(2f)
    }
    for (feature in FEATURES) {
      val expected = featureBounds(reference, feature)
      val actual = featureBounds(misregistered, feature)
      assertThat(actual.left - expected.left, "known shift left $feature").isBetween(3f, 5f)
      assertThat(actual.right - expected.right, "known shift right $feature").isBetween(3f, 5f)
      assertThat(abs(actual.center.x - expected.center.x), "known shift exceeds geometry tolerance").isGreaterThan(1f)
      assertThat(actual.width).isEqualTo(expected.width)
      assertThat(actual.height).isEqualTo(expected.height)
    }
    println("SENSITIVITY merged=$merged shift=4 probeErrors=" + listOf(340, 380, 440).map { it to abs(measurement.decoded.getValue(it) - it) })
    scene.patternShift.value = 0
    validate(scene, reference, capturePatterns(scene, visible = true), "restored shift")

    // Empty selected state omits captured input; opaque member backgrounds conceal the source.
    scene.omitInput.value = true
    val omitted = capturePatterns(scene, visible = true)
    val failure = runCatching { validate(scene, reference, omitted, "omitted input") }.exceptionOrNull()
    assertThat(failure).isNotNull()
    assertThat(checkNotNull(failure).message.orEmpty()).contains("response")
    assertThat(abs(omitted.white[399, 329].red - omitted.black[399, 329].red), "omitted response").isBetween(0f, 1f / 255f)
    for (feature in FEATURES) {
      var maxContrast = 0f
      for (y in feature.top.toInt() until feature.bottom.toInt()) {
        for (x in feature.left.toInt() until feature.right.toInt()) {
          maxContrast = maxOf(maxContrast, abs(omitted.features[x, y].red - omitted.black[x, y].red))
        }
      }
      assertThat(maxContrast, "omitted features absent $feature").isBetween(0f, 1f / 255f)
    }
    println("SENSITIVITY merged=$merged omittedResponse=${omitted.white[399, 329].red - omitted.black[399, 329].red} featuresAbsent=true rejection=response")
    scene.omitInput.value = false
    validate(scene, reference, capturePatterns(scene, visible = true), "restored omission")
  }

  private fun proof(merged: Boolean, moved: Boolean = false, quality: Boolean = false) = runScreenshotTest(size = ROOT_SIZE) {
    val scene = Scene(merged, quality)
    install(scene)
    val reference = capturePatterns(scene, visible = false)
    validate(scene, reference, capturePatterns(scene, visible = true), "initial")
    if (moved) {
      scene.shift.value = IntOffset(20, -20)
      validate(scene, reference, capturePatterns(scene, visible = true), "moved (+20,-20)")
    }
  }

  private class Scene(val merged: Boolean, val quality: Boolean = false) {
    val state = HazeState()
    val emptyState = HazeState()
    val effects = List(if (merged) 2 else 1) { GlassRuntimeEffect() }
    val pattern = mutableStateOf(Pattern.Black)
    val patternShift = mutableStateOf(0)
    val visible = mutableStateOf(false)
    val shift = mutableStateOf(IntOffset.Zero)
    val overlay = mutableStateOf(Overlay.Default)
    val omitInput = mutableStateOf(false)
    var density = 0f
  }

  private enum class Pattern { Black, White, Gray, Gradient, OriginalGradient, Features }
  private enum class Overlay { Default, NoHighlight, NoAmbient, None }
  private class Captures(val black: PixelMap, val white: PixelMap, val gray: PixelMap, val gradient: PixelMap, val originalGradient: PixelMap, val features: PixelMap)

  private fun ScreenshotUiTest.install(scene: Scene) {
    setContent {
      scene.density = LocalDensity.current.density
      Box(Modifier.pxSize(900, 600).testTag("root").background(Color.White)) {
        Box(
          Modifier.pxSize(900, 600).hazeSource(scene.state).drawBehind {
            when (scene.pattern.value) {
              Pattern.Black -> drawRect(Color.Black)
              Pattern.White -> drawRect(Color.White)
              Pattern.Gray -> drawRect(Color(0.5f, 0.5f, 0.5f))
              Pattern.Gradient -> drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.White), startX = 310f + scene.patternShift.value, endX = 490f + scene.patternShift.value))
              Pattern.OriginalGradient -> drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.White), startX = 0f, endX = 900f))
              Pattern.Features -> {
                drawRect(Color.Black)
                FEATURES.forEach { drawRect(Color.White, it.topLeft + Offset(scene.patternShift.value.toFloat(), 0f), it.size) }
              }
            }
          },
        )
        if (scene.visible.value) {
          // These siblings conceal direct sources before the container draws its material. A
          // background in a member's replayed children would instead cover the merged material.
          scene.effects.indices.forEach { index ->
            Box(Modifier.offset { IntOffset(if (index == 0) 300 else 510, 250) + scene.shift.value }.pxSize(200, 160).background(Color.White))
          }
          if (scene.merged) {
            val mergeDistance = with(LocalDensity.current) { 40.toDp() }
            Box(Modifier.pxSize(900, 600).hazeGlassContainer(mergeDistance)) {
              Member(scene, 0, 300)
              Member(scene, 1, 510)
            }
          } else {
            Member(scene, 0, 300)
          }
        }
      }
    }
  }

  @Composable
  private fun Member(scene: Scene, index: Int, x: Int) {
    val overlay = scene.overlay.value
    val style = GlassStyle {
      backgroundColor(Color.White)
      tint(Color.Transparent)
      alpha(1f)
      if (overlay == Overlay.NoHighlight || overlay == Overlay.None) specularIntensity(0f)
      if (overlay == Overlay.NoAmbient || overlay == Overlay.None) ambientResponse(0f)
      if (overlay == Overlay.None) {
        edgeSoftness(0.dp)
        edgeShadow(Color.Transparent)
      }
    }
    Box(
      Modifier.offset { IntOffset(x, 250) + scene.shift.value }.pxSize(200, 160)
        .hazeGlass(
          factory = HazeEffectFactory { scene.effects[index] },
          input = HazeInput.Sources(if (scene.omitInput.value) scene.emptyState else scene.state),
          style = style,
          performanceMode = if (scene.quality) HazePerformanceMode.Quality else null,
          expandLayerBounds = true,
          interactionSource = null,
        ),
    )
  }

  private fun ScreenshotUiTest.capturePatterns(scene: Scene, visible: Boolean): Captures {
    scene.visible.value = visible
    fun capture(pattern: Pattern): PixelMap {
      scene.pattern.value = pattern
      waitForIdle()
      return captureRootPixels()
    }
    return Captures(capture(Pattern.Black), capture(Pattern.White), capture(Pattern.Gray), capture(Pattern.Gradient), capture(Pattern.OriginalGradient), capture(Pattern.Features))
  }

  private fun validate(scene: Scene, reference: Captures, actual: Captures, label: String) {
    scene.effects.forEach { assertThat(it.delegate).isInstanceOf<FallbackGlassDelegate>() }
    if (scene.merged) {
      val owner = checkNotNull(scene.effects.first().memberBindingForTest?.node?.container)
      assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
      owner.renderer.runtimeSurfaces.forEach { assertThat(it.delegate).isInstanceOf<FallbackGlassDelegate>() }
    }
    val expanded = scene.effects.first().calculateLayerBounds(Rect(0f, 0f, 200f, 160f), Density(scene.density))
    assertThat(expanded.left, "default optics padding").isLessThanZero()
    assertThat(expanded.right).isGreaterThan(200f)
    assertThat(expanded.bottom).isGreaterThan(160f)

    val measurement = measure(reference, actual, label)
    val originalMeasurement = measure(reference, actual, "$label original shallow ramp", original = true)
    println("ORIGINAL_LIMIT label=$label selfMaxPx=${originalMeasurement.maxReferenceError} observedMaxPx=${originalMeasurement.maxError}")
    for (x in 320..479) {
      assertThat(abs(measurement.referenceDecoded.getValue(x) - x), "$label source self-decoding at $x").isBetween(0f, 2f)
      assertThat(abs(measurement.decoded.getValue(x) - x), "$label observed decoding at $x").isBetween(0f, 2f)
    }
    for (feature in FEATURES) {
      val referenceBounds = featureBounds(reference, feature)
      val actualBounds = featureBounds(actual, feature)
      for ((name, a, b) in listOf(
        Triple("left", actualBounds.left, referenceBounds.left),
        Triple("top", actualBounds.top, referenceBounds.top),
        Triple("right", actualBounds.right, referenceBounds.right),
        Triple("bottom", actualBounds.bottom, referenceBounds.bottom),
        Triple("width", actualBounds.width, referenceBounds.width),
        Triple("height", actualBounds.height, referenceBounds.height),
        Triple("centre x", actualBounds.center.x, referenceBounds.center.x),
        Triple("centre y", actualBounds.center.y, referenceBounds.center.y),
      )) assertThat(abs(a - b), "$label feature $feature $name").isBetween(0f, 1f)
    }
    val sourceSlope = slope(reference.originalGradient, 330)
    val rawSlope = slope(actual.originalGradient, 330)
    val outsideSlope = slope(actual.originalGradient, 100)
    println("GEOMETRY merged=${scene.merged} quality=${scene.quality} shift=${scene.shift.value} overlay=${scene.overlay.value} label=$label density=${scene.density} padding=${-expanded.left} interval=320..479 rawRow=330 sourceSlope=$sourceSlope rawSlope=$rawSlope outsideSlope=$outsideSlope ratio(sourceSlope/rawSlope)=${sourceSlope / rawSlope} minResponse=${measurement.minResponse} maxGrayError=${measurement.maxGrayError} verticalRows=326..334 maxSelfDecodedErrorPx=${measurement.maxReferenceError} maxDecodedErrorPx=${measurement.maxError} fitSlope=${measurement.sourceSlope} fitIntercept=${measurement.sourceIntercept} features=${FEATURES.map { featureBounds(actual, it) }} originalRawEndpoints=${actual.originalGradient[320, 330].red},${actual.originalGradient[479, 330].red}")
  }

  private class Measurement(
    val referenceDecoded: Map<Int, Float>,
    val decoded: Map<Int, Float>,
    val minResponse: Float,
    val maxGrayError: Float,
    val sourceSlope: Float,
    val sourceIntercept: Float,
  ) {
    val maxReferenceError = referenceDecoded.maxOf { (x, decoded) -> abs(decoded - x) }
    val maxError = decoded.maxOf { (x, decoded) -> abs(decoded - x) }
  }

  private fun measure(reference: Captures, actual: Captures, label: String, original: Boolean = false): Measurement {
    var minResponse = 1f
    var maxGrayError = 0f
    fun columns(captures: Captures, source: Boolean): Map<Int, Float> = (320..479).associateWith { x ->
      (326..334).map { y ->
        val black = captures.black[x, y].red
        val response = captures.white[x, y].red - black
        minResponse = minOf(minResponse, response)
        assertThat(response, "$label ${if (source) "reference" else "observed"} response at ($x,$y)").isGreaterThanOrEqualTo(.25f)
        val expectedGray = black + reference.gray[x, y].red * response
        val grayError = abs(captures.gray[x, y].red - expectedGray)
        maxGrayError = maxOf(maxGrayError, grayError)
        assertThat(grayError, "$label intermediate gray at ($x,$y)").isBetween(0f, 2f / 255f)
        ((if (original) captures.originalGradient else captures.gradient)[x, y].red - black) / response
      }.average().toFloat()
    }
    val source = columns(reference, source = true)
    val observed = columns(actual, source = false)
    val meanX = (320f + 479f) / 2f
    val meanY = source.values.average().toFloat()
    val fitSlope = source.entries.sumOf { ((it.key - meanX) * (it.value - meanY)).toDouble() }.toFloat() /
      source.keys.sumOf { ((it - meanX) * (it - meanX)).toDouble() }.toFloat()
    val fitIntercept = meanY - fitSlope * meanX
    assertThat(fitSlope.isFinite(), "source-only fit finite").isTrue()
    assertThat(fitSlope, "source-only fit positive").isGreaterThan(0f)
    val result = Measurement(
      referenceDecoded = source.mapValues { (_, value) -> (value - fitIntercept) / fitSlope },
      decoded = observed.mapValues { (_, value) -> (value - fitIntercept) / fitSlope },
      minResponse = minResponse,
      maxGrayError = maxGrayError,
      sourceSlope = fitSlope,
      sourceIntercept = fitIntercept,
    )
    println("PRECISION label=$label selfMaxErrorPx=${result.maxReferenceError} observedMaxErrorPx=${result.maxError} minResponse=$minResponse maxGrayError=$maxGrayError fitSlope=$fitSlope fitIntercept=$fitIntercept probes=" + listOf(340, 380, 440).map { it to result.decoded.getValue(it) })
    return result
  }

  private fun featureBounds(calibration: Captures, search: Rect): Rect {
    val points = mutableListOf<Offset>()
    for (y in search.top.toInt() - 6 until search.bottom.toInt() + 6) {
      for (x in search.left.toInt() - 6 until search.right.toInt() + 6) {
        if (normalized(calibration, calibration.features, x, y) > 0.5f) points += Offset(x.toFloat(), y.toFloat())
      }
    }
    assertThat(points.isNotEmpty(), "captured feature at $search").isTrue()
    return Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x } + 1f, points.maxOf { it.y } + 1f)
  }

  private fun normalized(calibration: Captures, pixels: PixelMap, x: Int, y: Int): Float =
    (pixels[x, y].red - calibration.black[x, y].red) / (calibration.white[x, y].red - calibration.black[x, y].red)

  private fun slope(pixels: PixelMap, y: Int): Float {
    val meanX = (320f + 479f) / 2f
    val meanY = (320..479).map { pixels[it, y].red }.average().toFloat()
    return (320..479).sumOf { ((it - meanX) * (pixels[it, y].red - meanY)).toDouble() }.toFloat() /
      (320..479).sumOf { ((it - meanX) * (it - meanX)).toDouble() }.toFloat()
  }

  private fun assertk.Assert<Float>.isLessThanZero() = isBetween(-1000f, -0.001f)

  @Composable
  private fun Modifier.pxSize(width: Int, height: Int): Modifier = with(LocalDensity.current) { size(width.toDp(), height.toDp()) }

  private companion object {
    val ROOT_SIZE = Size(900f, 600f)
    val FEATURES = listOf(Rect(340f, 290f, 360f, 350f), Rect(380f, 310f, 420f, 350f), Rect(450f, 280f, 470f, 360f))
  }
}
