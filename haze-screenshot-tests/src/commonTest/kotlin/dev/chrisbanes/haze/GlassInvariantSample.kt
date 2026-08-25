// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("ktlint:standard:property-naming")
@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs
import kotlin.math.roundToInt

private const val InvariantRootWidth = 1080
private val InvariantRootHeightRange = 1919..1920
private const val InvariantSurfaceWidthPx = 770
private const val InvariantSurfaceHeightPx = 495
private val InvariantSourceColor = Color(0xFF101820)

internal enum class GlassContinuityCarrier {
  Horizontal,
  HorizontalExtended,
  Vertical,
  VerticalExtended,
  AsymmetricAxes,
}

@Composable
internal fun GlassInvariantSample(
  effect: GlassTestConfiguration,
  performanceMode: HazePerformanceMode,
  shape: RoundedCornerShape,
  enabled: Boolean = true,
  surfaceSize: DpSize = DpSize(280.dp, 180.dp),
  transparentRoot: Boolean = false,
  transparentRootBackground: Color = Color.Transparent,
  sourceAlpha: Float = 1f,
  drawGridLines: Boolean = true,
  gridSpacingPx: Int? = null,
  verticalCarrierFractionInsideLeftEdge: Float? = null,
  adversarialStripePeriodPx: Int? = null,
  horizontalStripes: Boolean = false,
  checkerStripes: Boolean = false,
  continuityCarrier: GlassContinuityCarrier? = null,
  showSource: Boolean = true,
  effectTestTag: String? = null,
) {
  val hazeState = rememberHazeState()
  Box(
    Modifier
      .fillMaxSize()
      .background(if (transparentRoot) transparentRootBackground else Color.Black),
  ) {
    if (showSource) {
      Canvas(
        Modifier
          .then(
            if (transparentRoot) {
              Modifier
                .align(Alignment.Center)
                .size(surfaceSize)
            } else {
              Modifier.fillMaxSize()
            },
          )
          .then(
            if (transparentRoot) Modifier.graphicsLayer { alpha = 0f } else Modifier,
          )
          .hazeSource(hazeState),
      ) {
        drawInvariantGrid(
          alpha = sourceAlpha,
          drawGridLines = drawGridLines,
          gridSpacingPx = gridSpacingPx,
          verticalCarrierFractionInsideLeftEdge = verticalCarrierFractionInsideLeftEdge,
          surfaceWidthPx = surfaceSize.width.toPx(),
          surfaceHeightPx = surfaceSize.height.toPx(),
          adversarialStripePeriodPx = adversarialStripePeriodPx,
          horizontalStripes = horizontalStripes,
          checkerStripes = checkerStripes,
          continuityCarrier = continuityCarrier,
        )
      }
    }
    Box(
      Modifier
        .align(Alignment.Center)
        .size(surfaceSize)
        .then(if (effectTestTag != null) Modifier.testTag(effectTestTag) else Modifier)
        .then(
          if (enabled) {
            Modifier.hazeGlass(
              input = HazeInput.Sources(hazeState),
              configuration = effect,
              performanceMode = performanceMode,
            )
          } else {
            Modifier
          },
        ),
    )
  }
}

@Composable
internal fun GlassChromaInvariantSample() {
  val hazeState = rememberHazeState()
  val effect = remember {
    GlassTestConfiguration().apply {
      tint = Color.Transparent
      optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
      specularIntensity = 0f
      ambientResponse = 0f
      edgeSoftness = 0.dp
      chromaMultiplier = 2f
      shape = RoundedCornerShape(0.dp)
    }
  }
  Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      listOf(Color.Red, Color.Green, Color.Blue).forEachIndexed { index, color ->
        drawRect(
          color = color,
          topLeft = Offset(size.width * index / 3f, 0f),
          size = Size(size.width / 3f, size.height),
        )
      }
    }
    Box(
      Modifier
        .fillMaxSize()
        .hazeGlass(
          input = HazeInput.Sources(hazeState),
          style = effect.resolvedStyle,
          performanceMode = HazePerformanceMode.Quality,
        ),
    )
  }
}

private fun DrawScope.drawInvariantGrid(
  alpha: Float,
  drawGridLines: Boolean,
  gridSpacingPx: Int? = null,
  verticalCarrierFractionInsideLeftEdge: Float?,
  surfaceWidthPx: Float,
  surfaceHeightPx: Float,
  adversarialStripePeriodPx: Int?,
  horizontalStripes: Boolean,
  checkerStripes: Boolean,
  continuityCarrier: GlassContinuityCarrier?,
) {
  val surfaceLeft = (size.width - surfaceWidthPx) * 0.5f
  val surfaceTop = (size.height - surfaceHeightPx) * 0.5f
  val continuityBrush = when (continuityCarrier) {
    GlassContinuityCarrier.Horizontal -> Brush.horizontalGradient(
      colors = listOf(Color.Black, Color.White),
      startX = surfaceLeft,
      endX = surfaceLeft + surfaceWidthPx,
    )
    GlassContinuityCarrier.HorizontalExtended -> Brush.horizontalGradient(
      colors = listOf(Color.Black, Color.White),
      startX = surfaceLeft - surfaceWidthPx * 0.25f,
      endX = surfaceLeft + surfaceWidthPx * 1.25f,
    )
    GlassContinuityCarrier.Vertical -> Brush.verticalGradient(
      colors = listOf(Color.Black, Color.White),
      startY = surfaceTop,
      endY = surfaceTop + surfaceHeightPx,
    )
    GlassContinuityCarrier.VerticalExtended -> Brush.verticalGradient(
      colors = listOf(Color.Black, Color.White),
      startY = surfaceTop - surfaceHeightPx * 0.25f,
      endY = surfaceTop + surfaceHeightPx * 1.25f,
    )
    GlassContinuityCarrier.AsymmetricAxes,
    null,
    -> null
  }
  if (continuityCarrier == GlassContinuityCarrier.AsymmetricAxes) {
    drawRect(
      brush = Brush.horizontalGradient(
        colors = listOf(Color.Black, Color.Red),
        startX = surfaceLeft - surfaceWidthPx * 0.25f,
        endX = surfaceLeft + surfaceWidthPx * 1.25f,
      ),
    )
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color.Black, Color.Green),
        startY = surfaceTop - surfaceHeightPx * 0.25f,
        endY = surfaceTop + surfaceHeightPx * 1.25f,
      ),
      blendMode = BlendMode.Plus,
    )
    return
  }
  if (continuityBrush != null) {
    drawRect(brush = continuityBrush)
    return
  }
  if (adversarialStripePeriodPx != null) {
    drawRect(Color.Black.copy(alpha = alpha))
    var position = 0f
    val period = adversarialStripePeriodPx.toFloat()
    val limit = if (horizontalStripes) size.height else size.width
    while (position < limit) {
      drawRect(
        color = Color.White.copy(alpha = alpha),
        topLeft = if (horizontalStripes) Offset(0f, position) else Offset(position, 0f),
        size = if (horizontalStripes) Size(size.width, period) else Size(period, size.height),
      )
      position += period * 2f
    }
    if (checkerStripes) {
      position = period
      while (position < size.height) {
        drawRect(
          color = Color.White,
          topLeft = Offset(0f, position),
          size = Size(size.width, period),
          blendMode = BlendMode.Difference,
        )
        position += period * 2f
      }
    }
    return
  }
  drawRect(InvariantSourceColor.copy(alpha = alpha))
  if (verticalCarrierFractionInsideLeftEdge != null) {
    val carrierX = surfaceLeft + surfaceWidthPx * verticalCarrierFractionInsideLeftEdge
    drawLine(
      color = Color.White.copy(alpha = alpha),
      start = Offset(carrierX, 0f),
      end = Offset(carrierX, size.height),
      strokeWidth = 2f,
    )
  }
  if (drawGridLines) {
    val spacing = gridSpacingPx?.toFloat() ?: 16.dp.toPx()
    var x = 0f
    while (x < size.width) {
      drawLine(Color.White.copy(alpha = alpha), Offset(x, 0f), Offset(x, size.height), 2f)
      x += spacing
    }
    var y = 0f
    while (y < size.height) {
      drawLine(Color.Cyan.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 2f)
      y += spacing
    }
  }
}

private data class GlassContinuityCase(
  val name: String,
  val size: DpSize,
  val shape: RoundedCornerShape,
  val checksOffCenterRefraction: Boolean = false,
)

private fun assertContinuousAt(
  derivative: List<Float>,
  boundaryIndex: Int,
  label: String,
) {
  try {
    assertBoundaryCurvatureContinuous(
      derivative = derivative,
      boundaryIndex = boundaryIndex,
    )
  } catch (failure: AssertionError) {
    throw AssertionError("$label derivative=$derivative", failure)
  }
}

internal fun ScreenshotUiTest.assertGlassMedialAxesContinuous() {
  val cases = listOf(
    GlassContinuityCase("square", DpSize(220.dp, 220.dp), RoundedCornerShape(28.dp)),
    GlassContinuityCase("wide", DpSize(280.dp, 160.dp), RoundedCornerShape(28.dp)),
    GlassContinuityCase("tall", DpSize(160.dp, 280.dp), RoundedCornerShape(28.dp)),
    GlassContinuityCase(
      name = "pill",
      size = DpSize(320.dp, 64.dp),
      shape = RoundedCornerShape(32.dp),
      checksOffCenterRefraction = true,
    ),
  )
  val profiles = listOf(SurfaceProfile.Circle, SurfaceProfile.Squircle)
  val opticsCases = listOf(
    GlassOptics.Adaptive,
    GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionHeightFraction = 0.75f,
      refractionDisplacement = 48.dp,
      depth = 0f,
      blurRadius = 0.dp,
    ),
  )
  var currentCase by mutableStateOf(cases.first())
  var carrier by mutableStateOf(GlassContinuityCarrier.Horizontal)
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    shape = currentCase.shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = currentCase.shape,
        surfaceSize = currentCase.size,
        drawGridLines = false,
        continuityCarrier = carrier,
      )
    }
  }

  for (case in cases) {
    currentCase = case
    effect.shape = case.shape
    for (profile in profiles) {
      effect.surfaceProfile = profile
      for (optics in opticsCases) {
        effect.optics = optics

        carrier = GlassContinuityCarrier.Horizontal
        waitForIdle()
        val horizontal = captureInvariantPixels()
        val bounds = horizontal.centeredSurfaceBounds(case.size)
        val centerXRange = (bounds.center.x - 16)..(bounds.center.x + 16)
        assertContinuousAt(
          derivative = horizontal.scanlineDerivative(bounds.center.y, centerXRange),
          boundaryIndex = bounds.center.x - centerXRange.first,
          label = "${case.name}/$profile/$optics horizontal center",
        )
        val cornerOffset = minOf(bounds.width, bounds.height) / 4
        val diagonalX = bounds.left + cornerOffset
        val diagonalRange = (diagonalX - 16)..(diagonalX + 16)
        assertContinuousAt(
          derivative = horizontal.scanlineDerivative(bounds.top + cornerOffset, diagonalRange),
          boundaryIndex = diagonalX - diagonalRange.first,
          label = "${case.name}/$profile/$optics diagonal",
        )

        carrier = GlassContinuityCarrier.Vertical
        waitForIdle()
        val vertical = captureInvariantPixels()
        val centerYRange = (bounds.center.y - 16)..(bounds.center.y + 16)
        assertContinuousAt(
          derivative = vertical.verticalScanlineDerivative(bounds.center.x, centerYRange),
          boundaryIndex = bounds.center.y - centerYRange.first,
          label = "${case.name}/$profile/$optics vertical center",
        )
        if (case.checksOffCenterRefraction) {
          assertContinuousAt(
            derivative = vertical.verticalScanlineDerivative(
              x = bounds.center.x + bounds.width / 4,
              yRange = centerYRange,
            ),
            boundaryIndex = bounds.center.y - centerYRange.first,
            label = "${case.name}/$profile/$optics vertical off-center",
          )
          if (profile == SurfaceProfile.Circle && optics is GlassOptics.Fixed) {
            val probeX = bounds.center.x + bounds.width / 4
            val probeY = bounds.center.y
            val refractedLuminance = horizontal[probeX, probeY].luminance()
            effect.updateFixedOptics { copy(refractionStrength = 0f) }
            carrier = GlassContinuityCarrier.Horizontal
            waitForIdle()
            val unrefractedLuminance =
              captureInvariantPixels()[probeX, probeY].luminance()
            assertThat(abs(refractedLuminance - unrefractedLuminance))
              .isGreaterThan(2f / 255f)
          }
        }
      }
    }
  }
}

internal fun ScreenshotUiTest.assertGlassAsymmetricCornerNormalsContinuous() {
  val surfaceSize = DpSize(120.dp, 100.dp)
  val shape = RoundedCornerShape(
    topStart = 100.dp,
    topEnd = 100.dp,
    bottomEnd = 0.dp,
    bottomStart = 0.dp,
  )
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionHeightFraction = 0.5f,
      refractionDisplacement = 48.dp,
      depth = 0f,
      blurRadius = 0.dp,
    )
    specularIntensity = 0f
    ambientResponse = 0f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        surfaceSize = surfaceSize,
        drawGridLines = false,
        continuityCarrier = GlassContinuityCarrier.Vertical,
      )
    }
  }

  waitForIdle()
  val enabled = captureInvariantPixels()
  val bounds = enabled.centeredSurfaceBounds(surfaceSize)
  val density = bounds.width / surfaceSize.width.value
  val probeX = bounds.left + (10f * density).roundToInt()
  val centerYRange = (bounds.center.y - 16)..(bounds.center.y + 16)
  val enabledSignal = enabled.verticalScanlineLuminance(probeX, centerYRange)
  val enabledDerivative = enabled.verticalScanlineDerivative(probeX, centerYRange)

  effect.updateFixedOptics { copy(refractionStrength = 0f) }
  waitForIdle()
  val disabled = captureInvariantPixels()
  val disabledSignal = disabled.verticalScanlineLuminance(probeX, centerYRange)
  val refractionDelta = enabledSignal.zip(disabledSignal).maxOf { (first, second) ->
    abs(first - second)
  }
  assertThat(refractionDelta).isGreaterThan(1f / 255f)
  assertBoundaryContinuous(
    derivative = enabledDerivative,
    boundaryIndex = bounds.center.y - centerYRange.first,
  )
}

internal fun ScreenshotUiTest.assertGlassSquircleInteriorContinuous() {
  val cases = listOf(
    DpSize(220.dp, 220.dp),
    DpSize(280.dp, 160.dp),
    DpSize(160.dp, 280.dp),
  )
  val shape = RoundedCornerShape(28.dp)
  var surfaceSize by mutableStateOf(cases.first())
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionHeightFraction = 0.25f,
      refractionDisplacement = 48.dp,
      depth = 0f,
      blurRadius = 0.dp,
    )
    surfaceProfile = SurfaceProfile.Squircle
    specularIntensity = 0f
    ambientResponse = 0f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        surfaceSize = surfaceSize,
        drawGridLines = false,
        continuityCarrier = GlassContinuityCarrier.Horizontal,
      )
    }
  }

  for ((index, case) in cases.withIndex()) {
    surfaceSize = case
    waitForIdle()
    val rendered = captureInvariantPixels()
    val bounds = rendered.centeredSurfaceBounds(case)
    val refractionHeightPx = (minOf(bounds.width, bounds.height) * 0.25f).roundToInt()
    val cutoffs = listOf(
      bounds.left + refractionHeightPx,
      bounds.right - refractionHeightPx,
    )
    for (cutoff in cutoffs) {
      val range = (cutoff - 16)..(cutoff + 16)
      assertContinuousAt(
        derivative = rendered.scanlineDerivative(bounds.center.y, range),
        boundaryIndex = cutoff - range.first,
        label = "$case cutoff=$cutoff",
      )
    }
    if (index == 0) {
      val activeSignal = rendered.scanlineLuminance(
        y = bounds.center.y,
        xRange = bounds.left until bounds.right,
      )
      effect.updateFixedOptics { copy(refractionStrength = 0f) }
      waitForIdle()
      val disabledSignal = captureInvariantPixels().scanlineLuminance(
        y = bounds.center.y,
        xRange = bounds.left until bounds.right,
      )
      val refractionDelta = activeSignal.zip(disabledSignal) { first, second ->
        abs(first - second)
      }
      assertThat(refractionDelta.max()).isGreaterThan(1f / 255f)
      val centerIndex = bounds.width / 2
      listOf(
        refractionDelta.subList(0, centerIndex).take(refractionHeightPx),
        refractionDelta.subList(centerIndex, refractionDelta.size)
          .takeLast(refractionHeightPx)
          .reversed(),
      ).forEachIndexed { edgeIndex, edgeToInterior ->
        val peak = edgeToInterior.max()
        val probeRadius = 3
        val endpointInset = refractionHeightPx / 10
        val maxLocalSlope = (
          probeRadius + endpointInset until edgeToInterior.size - probeRadius - endpointInset
          ).maxOf { sampleIndex ->
          abs(
            edgeToInterior[sampleIndex + probeRadius] -
              edgeToInterior[sampleIndex - probeRadius],
          ) / (probeRadius * 2f * peak)
        }
        val slopeConcentration = maxLocalSlope * (edgeToInterior.size - 1)
        assertThat(slopeConcentration, "edge $edgeIndex refraction slope concentration")
          .isLessThanOrEqualTo(3.5f)
      }
      effect.updateFixedOptics { copy(refractionStrength = 1f) }
    }
  }
}

internal fun ScreenshotUiTest.assertGlassSquircleAmbientDoesNotGlowInside() {
  val surfaceSize = DpSize(220.dp, 220.dp)
  val shape = RoundedCornerShape(28.dp)
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionHeightFraction = 0.25f,
      refractionDisplacement = 48.dp,
      depth = 0f,
      blurRadius = 0.dp,
    )
    surfaceProfile = SurfaceProfile.Squircle
    specularIntensity = 0f
    ambientResponse = 1f
    whitePoint = 0f
    contrast = 0f
    chromaMultiplier = 1f
    contentNormalBlend = 0f
    fresnelExponent = 3f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        surfaceSize = surfaceSize,
        drawGridLines = false,
      )
    }
  }

  waitForIdle()
  val withAmbient = captureInvariantPixels()
  val bounds = withAmbient.centeredSurfaceBounds(surfaceSize)
  val ambientSignal = withAmbient.scanlineLuminance(
    y = bounds.center.y,
    xRange = bounds.left until bounds.right,
  )
  effect.ambientResponse = 0f
  waitForIdle()
  val neutralSignal = captureInvariantPixels().scanlineLuminance(
    y = bounds.center.y,
    xRange = bounds.left until bounds.right,
  )

  val refractionHeightPx = (minOf(bounds.width, bounds.height) * 0.25f).roundToInt()
  val glowDelta = ambientSignal.zip(neutralSignal) { ambient, neutral ->
    (ambient - neutral).coerceAtLeast(0f)
  }
  listOf(
    "left" to glowDelta.take(refractionHeightPx),
    "right" to glowDelta.takeLast(refractionHeightPx).reversed(),
  ).forEach { (edge, edgeToInterior) ->
    val peak = edgeToInterior.max()
    assertThat(peak, "$edge ambient response signal").isGreaterThan(1f / 1024f)
    val peakIndex = edgeToInterior.indexOf(peak)
    assertThat(peakIndex, "$edge ambient glow peak index")
      .isLessThanOrEqualTo(refractionHeightPx / 4)
  }
}

internal fun ScreenshotUiTest.assertGlassBlurInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(depth = 1f, blurRadius = 0.dp)
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(effect, HazePerformanceMode.Quality, shape)
    }
  }

  val sharp = captureInvariantSnapshot()
  effect.updateFixedOptics { copy(blurRadius = 32.dp) }
  waitForIdle()
  val blurred = captureInvariantSnapshot()

  assertBlurReducesHighFrequencyEnergy(sharp, blurred, sharp.invariantGeometry().interiorBounds)
}

internal fun ScreenshotUiTest.assertGlassBackgroundColorBlurInvariant() {
  val effect = GlassTestConfiguration().apply {
    style = GlassStyle { backgroundColor(Color.White) }
    optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 1f, blurRadius = 0.dp)
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    shape = RoundedCornerShape(0.dp)
  }
  setContent {
    ScreenshotTheme {
      val hazeState = rememberHazeState()
      Box(Modifier.fillMaxSize().background(Color.White)) {
        Canvas(
          Modifier
            .align(Alignment.Center)
            .size(280.dp, 180.dp)
            .hazeSource(hazeState),
        ) {
          for (x in 0 until size.width.toInt() step 12) {
            drawRect(
              color = Color.Black,
              topLeft = Offset(x.toFloat(), 0f),
              size = Size(6f, size.height),
            )
          }
        }
        Box(
          Modifier
            .align(Alignment.Center)
            .size(280.dp, 180.dp)
            .hazeGlass(
              input = HazeInput.Sources(hazeState),
              configuration = effect,
              performanceMode = HazePerformanceMode.Quality,
            ),
        )
      }
    }
  }

  val sharp = captureInvariantSnapshot()
  effect.updateFixedOptics { copy(blurRadius = 32.dp) }
  waitForIdle()
  val blurred = captureInvariantSnapshot()

  assertBlurReducesHighFrequencyEnergy(sharp, blurred, sharp.invariantGeometry().interiorBounds)
}

internal fun ScreenshotUiTest.assertGlassRefractionDetailPreservesSharpSourceInvariant(
  withInteraction: Boolean = false,
) {
  val shape = RoundedCornerShape(0.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionDisplacement = 6.dp,
      depth = 1f,
      blurRadius = 24.dp,
    )
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    if (withInteraction) {
      pressed {
        animate(toSpec = snap(), fromSpec = snap()) {
          refractionMultiplier(1.8f)
          whitePointDelta(0.2f)
        }
      }
      interactionPositionAnimationSpec = snap()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        drawGridLines = false,
        adversarialStripePeriodPx = 2,
        effectTestTag = "glass".takeIf { withInteraction },
      )
    }
  }
  waitForIdle()
  val idle = captureInvariantSnapshot()
  val blurred = if (withInteraction) {
    onNodeWithTag("glass").performTouchInput {
      down(Offset(50f, InvariantSurfaceHeightPx / 2f))
    }
    waitForIdle()
    captureInvariantSnapshot().also { interactive ->
      assertThat(
        idle.crop(idle.invariantGeometry().surfaceBounds)
          .meanAbsoluteDifference(interactive.crop(interactive.invariantGeometry().surfaceBounds)),
      ).isGreaterThan(1f / 255f)
    }
  } else {
    idle
  }
  val geometry = blurred.invariantGeometry()
  val verticalInset = 48
  val edgeSearch = (geometry.surfaceBounds.left + 1)..(geometry.surfaceBounds.left + 120)
  val edgePeak = edgeSearch.maxOf { x ->
    blurred.highFrequencyEnergy(
      IntRect(
        left = x,
        top = geometry.surfaceBounds.top + verticalInset,
        right = x + 6,
        bottom = geometry.surfaceBounds.bottom - verticalInset,
      ),
    )
  }
  val blurredInteriorEnergy = blurred.highFrequencyEnergy(geometry.interiorBounds)

  effect.updateFixedOptics { copy(blurRadius = 0.dp) }
  waitForIdle()
  val sharpInteriorEnergy =
    captureInvariantSnapshot().highFrequencyEnergy(geometry.interiorBounds)

  assertThat(blurredInteriorEnergy).isLessThan(sharpInteriorEnergy * 0.1f)
  assertThat(edgePeak).isGreaterThan(sharpInteriorEnergy * 0.04f)

  if (withInteraction) {
    onNodeWithTag("glass").performTouchInput { up() }
    waitForIdle()
  }
}

internal fun ScreenshotUiTest.assertGlassSemanticBlurHfInvariant() {
  data class Case(
    val name: String,
    val size: DpSize,
    val shape: RoundedCornerShape,
  )

  val cases = listOf(
    Case("capsule", DpSize(280.dp, 96.dp), RoundedCornerShape(percent = 50)),
    Case("card", DpSize(280.dp, 180.dp), RoundedCornerShape(28.dp)),
    Case("panel", DpSize(280.dp, 300.dp), RoundedCornerShape(16.dp)),
  )
  var currentCase by mutableStateOf(cases.first())
  val effect = invariantEffect(currentCase.shape).apply {
    optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 1f)
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = currentCase.shape,
        surfaceSize = currentCase.size,
      )
    }
  }

  cases.forEach { case ->
    currentCase = case
    effect.shape = case.shape
    effect.updateFixedOptics { copy(blurRadius = 0.dp) }
    waitForIdle()
    val sharp = captureInvariantSnapshot()
    val pxPerDp = sharp.width / 393f
    val materialWidth = (case.size.width.value * pxPerDp).toInt()
    val materialHeight = (case.size.height.value * pxPerDp).toInt()
    val left = (sharp.width - materialWidth) / 2
    val top = (sharp.height - materialHeight) / 2
    val inset = minOf(materialWidth, materialHeight) / 4
    val bounds = IntRect(
      left = left + inset,
      top = top + inset,
      right = left + materialWidth - inset,
      bottom = top + materialHeight - inset,
    )

    effect.updateFixedOptics { copy(blurRadius = 0.1.dp) }
    waitForIdle()
    val subpixelEnergy = captureInvariantSnapshot().highFrequencyEnergy(bounds)
    assertThat(kotlin.math.abs(subpixelEnergy - 0.0237f)).isLessThanOrEqualTo(0.0004f)

    var previousEnergy = subpixelEnergy
    listOf(4.dp, 8.dp, 14.dp, 14.1.dp).forEach { radius ->
      effect.updateFixedOptics { copy(blurRadius = radius) }
      waitForIdle()
      val energy = captureInvariantSnapshot().highFrequencyEnergy(bounds)
      val (expected, tolerance) = when (radius) {
        4.dp -> 0.001121f to 0.000012f
        8.dp -> 0.000265f to 0.000004f
        14.dp -> 0.000035f to 0.000002f
        else -> 0.000035f to 0.000002f
      }
      assertThat(kotlin.math.abs(energy - expected)).isLessThanOrEqualTo(tolerance)
      assertThat(energy).isLessThan(sharp.highFrequencyEnergy(bounds) * 0.9f)
      assertThat(energy).isLessThanOrEqualTo(previousEnergy + 0.000002f)
      previousEnergy = energy
    }
  }
}

internal fun ScreenshotUiTest.assertGlassAdversarialDownsampleInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 1f)
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
  }
  var stripePeriodPx by mutableStateOf(1)
  var horizontalStripes by mutableStateOf(false)
  var checkerStripes by mutableStateOf(false)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        surfaceSize = DpSize(280.dp, 400.dp),
        drawGridLines = false,
        adversarialStripePeriodPx = stripePeriodPx,
        horizontalStripes = horizontalStripes,
        checkerStripes = checkerStripes,
      )
    }
  }
  waitForIdle()
  val initial = captureInvariantSnapshot()
  val pxPerDp = initial.width / 393f
  val thresholdPx = (40f / 3f - 0.5f) / 0.57735f
  val epsilonPx = 0.01f
  val bounds = IntRect(
    left = initial.width / 2 - 100,
    top = initial.height / 2 - 100,
    right = initial.width / 2 + 100,
    bottom = initial.height / 2 + 100,
  )

  listOf(1 to false, 1 to true, 2 to false, 2 to true).forEach { (period, checker) ->
    stripePeriodPx = period
    horizontalStripes = false
    checkerStripes = checker
    effect.updateFixedOptics {
      copy(blurRadius = ((thresholdPx - epsilonPx) / pxPerDp).dp)
    }
    waitForIdle()
    val below = captureInvariantSnapshot()
    effect.updateFixedOptics { copy(blurRadius = (thresholdPx / pxPerDp).dp) }
    waitForIdle()
    val at = captureInvariantSnapshot()
    effect.updateFixedOptics {
      copy(blurRadius = ((thresholdPx + epsilonPx) / pxPerDp).dp)
    }
    waitForIdle()
    val above = captureInvariantSnapshot()

    listOf(below, at, above).forEach { snapshot ->
      val (mean, deviation) = snapshot.lumaMeanAndDeviation(bounds)
      assertThat(kotlin.math.abs(mean - Color(0.5f, 0.5f, 0.5f).luminance()))
        .isLessThanOrEqualTo(0.03f)
      assertThat(deviation).isLessThanOrEqualTo(0.01f)
    }
    assertThat(below.meanAbsoluteDifference(above, bounds)).isLessThanOrEqualTo(0.005f)
    assertThat(at.meanAbsoluteDifference(above, bounds)).isLessThanOrEqualTo(0.005f)
  }
}

internal fun ScreenshotUiTest.assertGlassProgressiveBlurInvariant() {
  var radius by mutableStateOf(14.dp)
  setContent {
    ScreenshotTheme {
      val panelHeight = 100.dp
      val panelHeightPx = with(LocalDensity.current) { panelHeight.toPx() }
      val allZero = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 0f)
      val allOne = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 1f)
      val gradient = HazeProgressive.verticalGradient(
        startY = panelHeightPx * 0.35f,
        startIntensity = 0f,
        endY = panelHeightPx * 0.65f,
        endIntensity = 1f,
      )
      val effects = listOf(
        0.dp to null,
        radius to null,
        radius to allZero,
        radius to allOne,
        radius to gradient,
      ).map { (blurRadius, progressive) ->
        remember(radius, blurRadius, progressive) {
          invariantEffect(RoundedCornerShape(0.dp)).apply {
            optics = GlassOptics.Fixed(
              refractionStrength = 0f,
              depth = 1f,
              blurRadius = blurRadius,
              progressive = progressive,
            )
            tint = Color.Transparent
            specularIntensity = 0f
            ambientResponse = 0f
            edgeSoftness = 0.dp
          }
        }
      }
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        effects.forEach { effect ->
          ProgressiveInvariantPanel(effect = effect, height = panelHeight)
        }
      }
    }
  }
  waitForIdle()
  listOf(14.dp, 8.dp).forEach { requestedRadius ->
    radius = requestedRadius
    waitForIdle()
    val snapshot = captureInvariantSnapshot()
    val panelHeightPx = kotlin.math.round(snapshot.width / 393f * 100f).toInt()
    val panelsTop = (snapshot.height - panelHeightPx * 5) / 2
    fun bounds(index: Int, topFraction: Float, bottomFraction: Float): IntRect = IntRect(
      left = snapshot.width / 2 - 180,
      top = panelsTop + index * panelHeightPx + (panelHeightPx * topFraction).toInt(),
      right = snapshot.width / 2 + 180,
      bottom = panelsTop + index * panelHeightPx + (panelHeightPx * bottomFraction).toInt(),
    )
    val sharpTop = bounds(0, 0.1f, 0.3f)
    val sharpBottom = bounds(0, 0.7f, 0.9f)
    val uniformBottom = bounds(1, 0.7f, 0.9f)
    val zeroTop = bounds(2, 0.1f, 0.3f)
    val oneBottom = bounds(3, 0.7f, 0.9f)
    val gradientTop = bounds(4, 0.1f, 0.3f)
    val gradientBottom = bounds(4, 0.7f, 0.9f)

    assertThat(snapshot.regionMeanAbsoluteDifference(sharpTop, zeroTop)).isLessThanOrEqualTo(0.0001f)
    assertThat(snapshot.regionMeanAbsoluteDifference(uniformBottom, oneBottom))
      .isLessThanOrEqualTo(0.01f)
    assertThat(snapshot.regionMeanAbsoluteDifference(sharpTop, gradientTop))
      .isLessThanOrEqualTo(0.0001f)
    assertThat(snapshot.regionMeanAbsoluteDifference(oneBottom, gradientBottom))
      .isLessThanOrEqualTo(0.0005f)
    assertThat(snapshot.highFrequencyEnergy(gradientTop))
      .isGreaterThan(snapshot.highFrequencyEnergy(gradientBottom))
  }
}

internal fun ScreenshotUiTest.assertGlassProgressiveMaskScaleInvariant() {
  val shape = RoundedCornerShape(0.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 1f, blurRadius = 18.dp)
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
  }
  var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = performanceMode,
        shape = shape,
        surfaceSize = DpSize(280.dp, 180.dp),
      )
    }
  }

  fun capture(progressive: HazeProgressive, scale: HazePerformanceMode): PixelSnapshot {
    effect.updateFixedOptics { copy(progressive = progressive) }
    performanceMode = scale
    waitForIdle()
    return captureInvariantSnapshot()
  }

  fun assertWithinOnePixel(unscaled: Float, scaled: Float) {
    assertThat(abs(unscaled - scaled)).isLessThanOrEqualTo(1f)
  }

  val vertical = HazeProgressive.verticalGradient(startY = 35f, endY = 75f)
  val verticalUnscaled = capture(vertical, HazePerformanceMode.Quality)
  val verticalBounds = verticalUnscaled.invariantGeometry().surfaceBounds
  val verticalUnscaledGeometry = verticalUnscaled.detectVerticalBlurGeometry(
    bounds = verticalBounds,
    startY = 35,
    endY = 75,
  )
  val verticalScaledGeometry = capture(vertical, HazePerformanceMode.Fixed(0.5f))
    .detectVerticalBlurGeometry(bounds = verticalBounds, startY = 35, endY = 75)
  assertWithinOnePixel(verticalUnscaledGeometry.firstBoundary, verticalScaledGeometry.firstBoundary)
  assertWithinOnePixel(verticalUnscaledGeometry.secondBoundary, verticalScaledGeometry.secondBoundary)

  val radial = HazeProgressive.RadialGradient(center = Offset(84f, 62f), radius = 48f)
  val radialUnscaled = capture(radial, HazePerformanceMode.Quality)
  val radialBounds = radialUnscaled.invariantGeometry().surfaceBounds
  val radialUnscaledGeometry = radialUnscaled.detectRadialBlurGeometry(
    bounds = radialBounds,
    centerX = 84f,
    centerY = 62f,
    radius = 48f,
  )
  val radialScaledGeometry = capture(radial, HazePerformanceMode.Fixed(0.5f))
    .detectRadialBlurGeometry(
      bounds = radialBounds,
      centerX = 84f,
      centerY = 62f,
      radius = 48f,
    )
  assertWithinOnePixel(radialUnscaledGeometry.centerX, radialScaledGeometry.centerX)
  assertWithinOnePixel(radialUnscaledGeometry.centerY, radialScaledGeometry.centerY)
  assertWithinOnePixel(radialUnscaledGeometry.radius, radialScaledGeometry.radius)
}

private data class VerticalBlurGeometry(
  val firstBoundary: Float,
  val secondBoundary: Float,
)

private data class RadialBlurGeometry(
  val centerX: Float,
  val centerY: Float,
  val radius: Float,
)

private fun PixelSnapshot.detectVerticalBlurGeometry(
  bounds: IntRect,
  startY: Int,
  endY: Int,
): VerticalBlurGeometry {
  val start = bounds.top + startY
  val end = bounds.top + endY
  val probeXs = listOf(
    bounds.left + bounds.width / 4,
    bounds.left + bounds.width / 2,
    bounds.left + bounds.width * 3 / 4,
  )
  val energy = (start..end).associateWith { y ->
    probeXs.sumOf { probeX ->
      localHighFrequencyEnergy(probeX, y, bounds, halfWidth = 24, halfHeight = 4).toDouble()
    }
      .toFloat() / probeXs.size
  }
  val sharp = energy.getValue(start)
  val blurred = energy.getValue(end)
  val profileRange = energy.values.max() - energy.values.min()
  require(kotlin.math.abs(blurred - sharp) > profileRange * 0.01f) {
    "Vertical progressive mask has insufficient endpoint energy contrast"
  }
  fun boundary(quantile: Float): Float {
    val target = sharp + (blurred - sharp) * quantile
    return energy.minBy { (_, value) -> kotlin.math.abs(value - target) }.key.toFloat()
  }
  val firstBoundary = boundary(0.25f)
  val secondBoundary = boundary(0.75f)
  require(firstBoundary > start && secondBoundary < end && firstBoundary < secondBoundary) {
    "Vertical progressive mask boundaries must be ordered and strictly interior"
  }
  require(secondBoundary - firstBoundary >= 2f) {
    "Vertical progressive mask boundaries must span a meaningful finite interval"
  }
  return VerticalBlurGeometry(firstBoundary = firstBoundary, secondBoundary = secondBoundary)
}

private fun PixelSnapshot.detectRadialBlurGeometry(
  bounds: IntRect,
  centerX: Float,
  centerY: Float,
  radius: Float,
): RadialBlurGeometry {
  val expectedCenterX = bounds.left + centerX
  val expectedCenterY = bounds.top + centerY
  val startDistance = (radius * 0.4f).toInt()
  val endDistance = (radius * 1.6f).toInt()
  fun boundary(dx: Int, dy: Int): Float {
    val rayEndDistance = minOf(
      endDistance,
      when {
        dx < 0 -> expectedCenterX.toInt() - bounds.left - 2
        dx > 0 -> bounds.right - expectedCenterX.toInt() - 2
        dy < 0 -> expectedCenterY.toInt() - bounds.top - 2
        else -> bounds.bottom - expectedCenterY.toInt() - 2
      },
    )
    require(rayEndDistance > startDistance + 1) {
      "Radial progressive mask ray does not fit inside the material"
    }
    val energy = (startDistance..rayEndDistance).associateWith { distance ->
      localHighFrequencyEnergy(
        x = (expectedCenterX + dx * distance).toInt(),
        y = (expectedCenterY + dy * distance).toInt(),
        bounds = bounds,
        halfWidth = if (dx == 0) 24 else 4,
        halfHeight = if (dy == 0) 24 else 4,
      )
    }
    val startEnergy = energy.getValue(startDistance)
    val endEnergy = energy.getValue(rayEndDistance)
    val profileRange = energy.values.max() - energy.values.min()
    require(kotlin.math.abs(endEnergy - startEnergy) > profileRange * 0.01f) {
      "Radial progressive mask ray has insufficient endpoint energy contrast: start=$startEnergy end=$endEnergy range=$profileRange"
    }
    val target = (startEnergy + endEnergy) * 0.5f
    val transition = energy.minBy { (_, value) -> kotlin.math.abs(value - target) }.key
    require(transition > startDistance && transition < rayEndDistance) {
      "Radial progressive mask transition must be strictly inside its scan ray"
    }
    return transition.toFloat()
  }
  val left = expectedCenterX - boundary(-1, 0)
  val right = expectedCenterX + boundary(1, 0)
  val top = expectedCenterY - boundary(0, -1)
  val bottom = expectedCenterY + boundary(0, 1)
  val recoveredCenterX = (left + right) * 0.5f
  val recoveredCenterY = (top + bottom) * 0.5f
  val recoveredRadius = ((right - left) + (bottom - top)) * 0.25f
  require(
    recoveredCenterX.isFinite() && recoveredCenterY.isFinite() && recoveredRadius.isFinite() &&
      recoveredCenterX in bounds.left.toFloat()..bounds.right.toFloat() &&
      recoveredCenterY in bounds.top.toFloat()..bounds.bottom.toFloat() &&
      recoveredRadius > startDistance && recoveredRadius < endDistance,
  ) {
    "Recovered radial progressive mask geometry is not plausible for the material scan"
  }
  return RadialBlurGeometry(
    centerX = recoveredCenterX,
    centerY = recoveredCenterY,
    radius = recoveredRadius,
  )
}

private fun PixelSnapshot.localHighFrequencyEnergy(
  x: Int,
  y: Int,
  bounds: IntRect,
  halfWidth: Int,
  halfHeight: Int,
): Float {
  var total = 0f
  var samples = 0
  for (probeY in (y - halfHeight).coerceAtLeast(bounds.top + 1)..(y + halfHeight).coerceAtMost(bounds.bottom - 2)) {
    for (probeX in (x - halfWidth).coerceAtLeast(bounds.left + 1)..(x + halfWidth).coerceAtMost(bounds.right - 2)) {
      total += kotlin.math.abs(this[probeX + 1, probeY].luminance() - this[probeX - 1, probeY].luminance())
      total += kotlin.math.abs(this[probeX, probeY + 1].luminance() - this[probeX, probeY - 1].luminance())
      samples += 2
    }
  }
  return total / samples
}

@Composable
private fun ProgressiveInvariantPanel(effect: GlassTestConfiguration, height: androidx.compose.ui.unit.Dp) {
  val hazeState = remember { HazeState() }
  Box(Modifier.size(280.dp, height)) {
    Canvas(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState),
    ) {
      drawInvariantGrid(
        alpha = 1f,
        drawGridLines = true,
        verticalCarrierFractionInsideLeftEdge = null,
        surfaceWidthPx = size.width,
        surfaceHeightPx = size.height,
        adversarialStripePeriodPx = null,
        horizontalStripes = false,
        checkerStripes = false,
        continuityCarrier = null,
      )
    }
    Box(
      Modifier
        .fillMaxSize()
        .hazeGlass(
          input = HazeInput.Sources(hazeState),
          configuration = effect,
          performanceMode = HazePerformanceMode.Quality,
        ),
    )
  }
}

private fun PixelSnapshot.regionMeanAbsoluteDifference(first: IntRect, second: IntRect): Float {
  require(first.width == second.width && first.height == second.height)
  var total = 0f
  var samples = 0
  for (y in 0 until first.height) {
    for (x in 0 until first.width) {
      total += kotlin.math.abs(
        this[first.left + x, first.top + y].luminance() -
          this[second.left + x, second.top + y].luminance(),
      )
      samples++
    }
  }
  return total / samples
}

private fun PixelSnapshot.lumaMeanAndDeviation(bounds: IntRect): Pair<Float, Float> {
  val values = buildList(bounds.width * bounds.height) {
    for (y in bounds.top until bounds.bottom) {
      for (x in bounds.left until bounds.right) add(this@lumaMeanAndDeviation[x, y].luminance())
    }
  }
  val mean = values.average().toFloat()
  val variance = values.sumOf { value ->
    val delta = value - mean
    (delta * delta).toDouble()
  }.toFloat() / values.size
  return mean to kotlin.math.sqrt(variance)
}

private fun PixelSnapshot.meanAbsoluteDifference(other: PixelSnapshot, bounds: IntRect): Float {
  var total = 0f
  var samples = 0
  for (y in bounds.top until bounds.bottom) {
    for (x in bounds.left until bounds.right) {
      total += kotlin.math.abs(this[x, y].luminance() - other[x, y].luminance())
      samples++
    }
  }
  return total / samples
}

internal fun ScreenshotUiTest.assertGlassPaddingPreservesSourceInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionDisplacement = 0.dp,
      depth = 1f,
      blurRadius = 0.dp,
    )
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
  }
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      performanceMode = HazePerformanceMode.Quality,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
    )
  }

  matte = Color.Black
  waitForIdle()
  val sharpOverBlack = captureInvariantSnapshot()
  effect.updateFixedOptics { copy(blurRadius = 16.dp) }
  waitForIdle()
  val smallOverBlack = captureInvariantSnapshot()
  matte = Color.White
  waitForIdle()
  val smallPadding = recoverPremultipliedSnapshot(
    overBlack = smallOverBlack,
    overWhite = captureInvariantSnapshot(),
  )
  effect.updateFixedOptics { copy(refractionDisplacement = 96.dp) }
  matte = Color.Black
  waitForIdle()
  val largeOverBlack = captureInvariantSnapshot()
  matte = Color.White
  waitForIdle()
  val largePadding = recoverPremultipliedSnapshot(
    overBlack = largeOverBlack,
    overWhite = captureInvariantSnapshot(),
  )
  val geometry = smallPadding.invariantGeometry()

  assertInvariantMaterialSilhouette(smallPadding, geometry)
  assertInvariantMaterialSilhouette(largePadding, geometry)
  assertEquivalentAlphaEdgePosition(
    smallPadding,
    largePadding,
    y = geometry.centerY,
    xRange = geometry.leftEdgeRange,
  )
  assertBlurReducesHighFrequencyEnergy(
    sharpOverBlack,
    smallOverBlack,
    geometry.interiorBounds,
  )
  assertThat(
    smallOverBlack.crop(geometry.interiorBounds)
      .meanAbsoluteDifference(largeOverBlack.crop(geometry.interiorBounds)),
  ).isLessThan(1f / 255f)
}

internal fun ScreenshotUiTest.assertGlassHardClipInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    edgeSoftness = 0.dp
  }
  var enabled by mutableStateOf(false)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        enabled = enabled,
      )
    }
  }

  val background = captureInvariantSnapshot()
  enabled = true
  waitForIdle()
  val rendered = captureInvariantSnapshot()

  assertOutsideMatchesBackground(
    rendered = rendered,
    background = background,
    outsidePoints = rendered.invariantGeometry().outsidePoints,
  )
}

internal fun ScreenshotUiTest.assertGlassTransparentOutputInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      performanceMode = HazePerformanceMode.Quality,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
    )
  }

  effect.shape = RoundedCornerShape(0.dp)
  waitForIdle()
  val rectangular = captureTransparentSnapshot { matte = it }
  val outsidePoints = rectangular.invariantGeometry().outsidePoints
  outsidePoints.forEach { point ->
    assertThat(rectangular[point.x, point.y].alpha).isGreaterThan(1f - 1f / 255f)
  }

  effect.shape = shape
  waitForIdle()
  val rendered = captureTransparentSnapshot { matte = it }
  rendered.assertZeroAlphaHasZeroRgb()
  rendered.assertTransparentAt(outsidePoints)
}

internal fun ScreenshotUiTest.assertGlassTranslucentSourceInvariant(
  withInteraction: Boolean = false,
  verifyInteractionRgb: Boolean = false,
) {
  val shape = RoundedCornerShape(28.dp)
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = GlassOptics.Fixed(
      depth = 0f,
      blurRadius = 0.dp,
    )
    chromaticAberrationStrength = 0f
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    if (withInteraction) {
      pressed {
        animate(toSpec = snap(), fromSpec = snap()) {
          refractionMultiplier(1.2f)
          whitePointDelta(0.2f)
        }
      }
      interactionPositionAnimationSpec = snap()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    this.shape = shape
  }
  var showSource by mutableStateOf(true)
  var sourceAlpha by mutableStateOf(0.5f)
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      performanceMode = HazePerformanceMode.Quality,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
      sourceAlpha = sourceAlpha,
      drawGridLines = false,
      showSource = showSource,
      effectTestTag = "glass".takeIf { withInteraction },
    )
  }

  val live = captureTransparentSnapshot { matte = it }
  val geometry = live.invariantGeometry()
  val center = IntOffset(
    x = (geometry.surfaceBounds.left + geometry.surfaceBounds.right) / 2,
    y = (geometry.surfaceBounds.top + geometry.surfaceBounds.bottom) / 2,
  )
  val detailBandColors = (
    geometry.surfaceBounds.left + 2..<geometry.surfaceBounds.right - 2
    ).map { x -> live[x, geometry.centerY] }
  val expectedAlpha = 0.5f
  val expectedRed = InvariantSourceColor.red * expectedAlpha
  val expectedGreen = InvariantSourceColor.green * expectedAlpha
  val expectedBlue = InvariantSourceColor.blue * expectedAlpha
  val maximumDetailBandAlphaError = detailBandColors.maxOf { color ->
    abs(color.alpha - expectedAlpha)
  }
  val maximumDetailBandPremultipliedRgbError = detailBandColors.maxOf { color ->
    maxOf(
      abs(color.red - expectedRed),
      abs(color.green - expectedGreen),
      abs(color.blue - expectedBlue),
    )
  }
  val maximumAdjacentAlphaDelta = detailBandColors
    .zipWithNext { first, second -> abs(first.alpha - second.alpha) }
    .max()
  assertThat(maximumDetailBandAlphaError).isLessThanOrEqualTo(2f / 255f)
  assertThat(maximumDetailBandPremultipliedRgbError).isLessThanOrEqualTo(2f / 255f)
  assertThat(maximumAdjacentAlphaDelta).isLessThanOrEqualTo(1.001f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].alpha - 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].red - InvariantSourceColor.red * 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].green - InvariantSourceColor.green * 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].blue - InvariantSourceColor.blue * 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  live.assertTransparentAt(geometry.outsidePoints)

  if (withInteraction) {
    val glassNode = onNodeWithTag("glass")
    glassNode.performTouchInput {
      down(Offset(50f, InvariantSurfaceHeightPx / 2f))
    }
    waitForIdle()
    val interactive = captureTransparentSnapshot { matte = it }
    val interactionDetailBand = (
      geometry.surfaceBounds.left + 2..geometry.surfaceBounds.left + 160
      ).map { x -> interactive[x, geometry.centerY] }
    assertThat(
      interactive.crop(geometry.surfaceBounds)
        .meanAbsoluteDifference(live.crop(geometry.surfaceBounds)),
    ).isGreaterThan(1f / 255f)
    assertThat(
      interactionDetailBand.maxOf { color -> abs(color.alpha - expectedAlpha) },
    ).isLessThanOrEqualTo(2f / 255f)

    if (verifyInteractionRgb) {
      sourceAlpha = 1f
      waitForIdle()
      val opaqueInteractive = captureTransparentSnapshot { matte = it }
      val opaqueInteractionDetailBand = (
        geometry.surfaceBounds.left + 2..geometry.surfaceBounds.left + 160
        ).map { x -> opaqueInteractive[x, geometry.centerY] }
      assertThat(
        interactionDetailBand.zip(opaqueInteractionDetailBand).maxOf { (translucent, opaque) ->
          maxOf(
            abs(translucent.red - opaque.red * expectedAlpha),
            abs(translucent.green - opaque.green * expectedAlpha),
            abs(translucent.blue - opaque.blue * expectedAlpha),
          )
        },
      ).isLessThanOrEqualTo(2f / 255f)

      sourceAlpha = expectedAlpha
      waitForIdle()
    }
    glassNode.performTouchInput { up() }
    waitForIdle()
  }
  showSource = false
  waitForIdle()
  val retained = captureTransparentSnapshot { matte = it }
  assertThat(
    live.crop(geometry.surfaceBounds)
      .meanAbsoluteDifference(retained.crop(geometry.surfaceBounds)),
  ).isLessThan(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].alpha - 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].red - InvariantSourceColor.red * 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].green - InvariantSourceColor.green * 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].blue - InvariantSourceColor.blue * 0.5f))
    .isLessThanOrEqualTo(1f / 255f)
  retained.assertZeroAlphaHasZeroRgb()
  retained.assertTransparentAt(geometry.outsidePoints)
}

internal fun ScreenshotUiTest.assertGlassChromaMultiplierFiniteInvariant() {
  setContent {
    ScreenshotTheme {
      GlassChromaInvariantSample()
    }
  }

  val snapshot = captureInvariantSnapshot()
  listOf(Color.Red, Color.Green, Color.Blue).forEachIndexed { index, expected ->
    val pixel = snapshot[snapshot.width * (index * 2 + 1) / 6, snapshot.height / 2]
    listOf(pixel.red, pixel.green, pixel.blue, pixel.alpha).forEach { component ->
      assertThat(component.isFinite()).isTrue()
    }
    assertThat(kotlin.math.abs(pixel.red - expected.red)).isLessThanOrEqualTo(1f / 255f)
    assertThat(kotlin.math.abs(pixel.green - expected.green)).isLessThanOrEqualTo(1f / 255f)
    assertThat(kotlin.math.abs(pixel.blue - expected.blue)).isLessThanOrEqualTo(1f / 255f)
    assertThat(kotlin.math.abs(pixel.alpha - expected.alpha)).isLessThanOrEqualTo(1f / 255f)
  }
}

internal fun ScreenshotUiTest.assertGlassPaddingAndScaleInvariants() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    // Preserve the previous effective values while making the literal contract explicit.
    optics = GlassOptics.Fixed(
      refractionStrength = 0.2f,
      refractionDisplacement = 24.dp,
      depth = 0.5f,
      blurRadius = 0.dp,
    )
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
  }
  var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      performanceMode = performanceMode,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
    )
  }

  val smallPadding = captureTransparentSnapshot { matte = it }
  effect.updateFixedOptics {
    copy(
      refractionStrength = 0.85f,
      refractionDisplacement = 53.25.dp,
      blurRadius = 35.2.dp,
    )
  }
  effect.chromaticAberrationStrength = 0.3f
  waitForIdle()
  val largePadding = captureTransparentSnapshot { matte = it }
  val geometry = largePadding.invariantGeometry()
  assertEquivalentAlphaEdgePosition(
    smallPadding,
    largePadding,
    y = geometry.centerY,
    xRange = geometry.leftEdgeRange,
  )

  performanceMode = HazePerformanceMode.Fixed(0.5f)
  waitForIdle()
  val fixedScale = captureTransparentSnapshot { matte = it }
  assertInvariantMaterialSilhouette(largePadding, geometry)
  assertInvariantMaterialSilhouette(fixedScale, geometry)
  assertEquivalentAlphaEdgePosition(
    largePadding,
    fixedScale,
    y = geometry.centerY,
    xRange = geometry.leftEdgeRange,
  )
  assertContentAlignedAcrossInputScales(
    largePadding,
    fixedScale,
    y = geometry.centerY,
    range = (geometry.interiorBounds.left + 8)..(geometry.interiorBounds.right - 9),
  )
  // A 0.5 pixel fraction resamples both into and out of the retained layer.
  assertThat(
    largePadding.crop(geometry.interiorBounds)
      .meanAbsoluteDifference(fixedScale.crop(geometry.interiorBounds)),
  ).isLessThan(3f / 255f)
}

internal fun ScreenshotUiTest.assertGlassFirstEnabledFrameInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(depth = 0.6f, blurRadius = 32.dp)
  }
  var enabled by mutableStateOf(false)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        enabled = enabled,
      )
    }
  }

  val disabled = captureInvariantSnapshot()
  enabled = true
  val firstEnabled = captureInvariantSnapshot()
  waitForIdle()
  val settled = captureInvariantSnapshot()

  assertFirstEnabledFrameStable(disabled, firstEnabled, settled)
}

internal fun ScreenshotUiTest.assertGlassProfileBranchContinuous() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Fixed(refractionStrength = 0.85f, depth = 0.6f, blurRadius = 32.dp)
    edgeSoftness = 0.dp
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(effect, HazePerformanceMode.Quality, shape)
    }
  }

  val rendered = captureInvariantSnapshot()
  val geometry = rendered.invariantGeometry()
  val xRange = (geometry.surfaceBounds.left + 35)..(geometry.surfaceBounds.left + 120)
  val cornerBranchX = geometry.surfaceBounds.left + 77
  val derivative = rendered.scanlineDerivative(
    y = geometry.surfaceBounds.top + 39,
    xRange = xRange,
  )
  assertBoundaryContinuous(
    derivative = derivative,
    boundaryIndex = cornerBranchX - xRange.first,
  )
}

internal fun ScreenshotUiTest.assertGlassMonotonicRefractionVisibleInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val performanceMode = HazePerformanceMode.Quality
  val effect = GlassTestConfiguration().apply {
    style = GlassDefaults.style
    this.shape = shape
    optics = GlassOptics.Fixed(refractionStrength = 0f)
  }
  var drawCarrier by mutableStateOf(true)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = performanceMode,
        shape = shape,
        drawGridLines = false,
        verticalCarrierFractionInsideLeftEdge = if (drawCarrier) .045f else null,
      )
    }
  }

  val disabledCarrier = captureInvariantSnapshot()
  drawCarrier = false
  waitForIdle()
  val disabledUniform = captureInvariantSnapshot()

  effect.optics = GlassOptics.Fixed()
  waitForIdle()
  drawCarrier = true
  waitForIdle()
  val enabledCarrier = captureInvariantSnapshot()
  drawCarrier = false
  waitForIdle()
  val enabledUniform = captureInvariantSnapshot()

  val geometry = enabledCarrier.invariantGeometry()
  val carrierX = geometry.surfaceBounds.left + geometry.surfaceBounds.width * .045f
  val searchRadiusPx = 64
  val searchRange = maxOf(0, carrierX.toInt() - searchRadiusPx)..minOf(
    enabledCarrier.width - 1,
    carrierX.toInt() + searchRadiusPx,
  )
  val centerY = (geometry.surfaceBounds.top + geometry.surfaceBounds.bottom) / 2
  val enabledMinusDisabledDisplacement = pairedCarrierDisplacementDeltaPx(
    disabledCarrier = disabledCarrier,
    disabledUniform = disabledUniform,
    enabledCarrier = enabledCarrier,
    enabledUniform = enabledUniform,
    scanY = centerY,
    range = searchRange,
    expected = carrierX,
  )
  val metrics = measureRefractionStrengthMetrics(
    disabledDisplacementPx = 0f,
    enabledDisplacementPx = enabledMinusDisabledDisplacement,
    disabledCarrier,
    disabledUniform,
    enabledCarrier,
    enabledUniform,
    IntRect(
      geometry.surfaceBounds.left + 16,
      geometry.surfaceBounds.top + 64,
      geometry.surfaceBounds.left + 112,
      geometry.surfaceBounds.bottom - 64,
    ),
  )
  println("Glass monotonic refraction invariant: $metrics")
  val failures = buildList {
    if (abs(metrics.directionalDisplacementDeltaPx) < 1f) {
      add(
        "directional displacement magnitude=" +
          "${abs(metrics.directionalDisplacementDeltaPx)}px is below 1px",
      )
    }
    if (metrics.edgeBandResidualChangedPixelRatio <= .01f) {
      add("edge-band residual changed ratio=${metrics.edgeBandResidualChangedPixelRatio} is not above 1%")
    }
  }
  check(failures.isEmpty()) { failures.joinToString("; ") }
}

internal fun ScreenshotUiTest.assertGlassRefractionFoldInvertsIncomingContentInvariant() {
  val cases = listOf(
    GlassContinuityCase("rounded", DpSize(280.dp, 180.dp), RoundedCornerShape(28.dp)),
    GlassContinuityCase(
      "asymmetric-corners",
      DpSize(280.dp, 180.dp),
      RoundedCornerShape(72.dp, 16.dp, 48.dp, 32.dp),
    ),
    GlassContinuityCase("pill", DpSize(320.dp, 64.dp), RoundedCornerShape(32.dp)),
  )
  val profiles = listOf(
    SurfaceProfile.Circle,
    SurfaceProfile.Squircle,
    SurfaceProfile.Lip,
    SurfaceProfile.Concave,
  )
  var currentCase by mutableStateOf(cases.first())
  var carrier by mutableStateOf(GlassContinuityCarrier.Horizontal)
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = foldInvariantOptics(0f)
    specularIntensity = 0f
    ambientResponse = 0f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    shape = currentCase.shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = currentCase.shape,
        surfaceSize = currentCase.size,
        drawGridLines = false,
        continuityCarrier = carrier,
      )
    }
  }

  cases.forEach { case ->
    profiles.forEach { profile ->
      val reversals = listOf(
        GlassContinuityCarrier.HorizontalExtended,
        GlassContinuityCarrier.VerticalExtended,
      ).map { direction ->
        currentCase = case
        carrier = direction
        effect.shape = case.shape
        effect.surfaceProfile = profile
        effect.optics = foldInvariantOptics(0f)
        waitForIdle()
        val unfolded = captureInvariantSnapshot()

        effect.optics = foldInvariantOptics(1f)
        waitForIdle()
        val folded = captureInvariantSnapshot()
        val bounds = folded.centeredSurfaceBounds(case.size)
        val strongestReversal = listOf(1, 6).maxOf { step ->
          val unfoldedSlopes = unfolded.foldProbeSlopes(case, bounds, direction, step)
          val foldedSlopes = folded.foldProbeSlopes(case, bounds, direction, step)
          foldedSlopes.zip(unfoldedSlopes).maxOf { (foldedSlope, unfoldedSlope) ->
            if (foldedSlope * unfoldedSlope < 0f) {
              minOf(abs(foldedSlope), abs(unfoldedSlope))
            } else {
              0f
            }
          }
        }
        println(
          "Glass refraction fold invariant ${case.name}/$profile/$direction: " +
            "reversal=$strongestReversal, changed=${unfolded.changedPixelRatio(folded)}",
        )

        assertThat(
          unfolded.changedPixelRatioOutside(folded, bounds),
          "${case.name}/$profile/$direction pixels outside the glass bounds",
        ).isEqualTo(0f)
        assertThat(
          unfolded.changedPixelRatio(folded),
          "${case.name}/$profile/$direction fold-changed pixel ratio",
        ).isGreaterThan(0.01f)
        strongestReversal
      }
      assertThat(reversals.max(), "${case.name}/$profile opposed signed slope magnitude")
        .isGreaterThan(0.1f / 255f)
    }
  }
}

internal fun ScreenshotUiTest.assertGlassRefractionFoldPreservesTangentOrientationInvariant() {
  val surfaceSize = DpSize(280.dp, 180.dp)
  val shape = RoundedCornerShape(0.dp)
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = foldInvariantOptics(1f)
    specularIntensity = 0f
    ambientResponse = 0f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    surfaceProfile = SurfaceProfile.Circle
    this.shape = shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        surfaceSize = surfaceSize,
        drawGridLines = false,
        continuityCarrier = GlassContinuityCarrier.AsymmetricAxes,
      )
    }
  }

  val folded = captureInvariantSnapshot()
  val bounds = folded.centeredSurfaceBounds(surfaceSize)
  val orientation = folded.foldOrientationAtTopEdge(bounds)
  println("Glass refraction fold axis orientation: $orientation")

  assertThat(orientation.tangentSlope, "folded tangent-axis slope")
    .isGreaterThan(0.1f / 255f)
  assertThat(orientation.strongestNormalSlope, "folded normal-axis slope")
    .isLessThan(-0.1f / 255f)
}

internal fun ScreenshotUiTest.assertGlassRefractionFoldDoesNotFormSeparateEdgeBandInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val unfoldedOptics = GlassOptics.Fixed(
    refractionStrength = 0.8f,
    refractionHeightFraction = 0.3f,
    refractionDisplacement = 18.dp,
    refractionFoldStrength = 0f,
    depth = 0f,
    blurRadius = 0.dp,
  )
  val effect = GlassTestConfiguration().apply {
    tint = Color.Transparent
    optics = unfoldedOptics
    specularIntensity = 0f
    ambientResponse = 0f
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
    surfaceProfile = SurfaceProfile.Circle
    this.shape = shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        drawGridLines = false,
        continuityCarrier = GlassContinuityCarrier.HorizontalExtended,
      )
    }
  }

  val unfolded = captureInvariantSnapshot()
  effect.optics = unfoldedOptics.copy(refractionFoldStrength = 0.65f)
  waitForIdle()
  val folded = captureInvariantSnapshot()
  val bounds = folded.invariantGeometry().surfaceBounds
  val density = InvariantSurfaceWidthPx / 280f
  val previousFoldBandEndPx =
    (unfoldedOptics.refractionDisplacement.value * density * unfoldedOptics.refractionStrength)
      .roundToInt()
  val probeRange = (bounds.left + previousFoldBandEndPx + 2)..(bounds.left + previousFoldBandEndPx + 6)
  val centerY = bounds.center.y
  val foldDelta = probeRange
    .map { x -> abs(folded[x, centerY].luminance() - unfolded[x, centerY].luminance()) }
    .average()
    .toFloat()
  println("Glass refraction fold inner-boundary delta: $foldDelta")

  assertThat(foldDelta, "fold influence immediately inside the previous edge-band boundary")
    .isGreaterThan(0.25f / 255f)
}

private fun foldInvariantOptics(strength: Float) = GlassOptics.Fixed(
  refractionStrength = 1f,
  refractionHeightFraction = 0.75f,
  refractionDisplacement = 48.dp,
  refractionFoldStrength = strength,
  depth = 0f,
  blurRadius = 0.dp,
)

private fun PixelSnapshot.foldProbeSlopes(
  case: GlassContinuityCase,
  bounds: IntRect,
  carrier: GlassContinuityCarrier,
  step: Int,
): List<Float> {
  require(step > 0)
  val exercisesCorner = case.name == "asymmetric-corners"
  return when (carrier) {
    GlassContinuityCarrier.Horizontal,
    GlassContinuityCarrier.HorizontalExtended,
    -> {
      val y = if (exercisesCorner) bounds.top + bounds.height / 3 else bounds.center.y
      val xRange = (bounds.left + 2)..(bounds.left + minOf(160, bounds.width / 2))
      require(xRange.first >= 0 && xRange.last < width)
      require(y in 0 until height)
      (xRange.first..xRange.last - step).map { x ->
        this[x + step, y].luminance() - this[x, y].luminance()
      }
    }
    GlassContinuityCarrier.Vertical,
    GlassContinuityCarrier.VerticalExtended,
    -> {
      val x = if (exercisesCorner) bounds.left + bounds.width / 6 else bounds.center.x
      val yRange = (bounds.top + 2)..(bounds.top + minOf(120, bounds.height / 2))
      require(x in 0 until width)
      require(yRange.first >= 0 && yRange.last < height)
      (yRange.first..yRange.last - step).map { y ->
        this[x, y + step].luminance() - this[x, y].luminance()
      }
    }
    GlassContinuityCarrier.AsymmetricAxes -> error("Asymmetric axes use channel-specific probes")
  }
}

@Poko
private class FoldOrientation(
  val tangentSlope: Float,
  val strongestNormalSlope: Float,
)

private fun PixelSnapshot.foldOrientationAtTopEdge(
  bounds: IntRect,
  step: Int = 6,
): FoldOrientation {
  require(step > 0)
  val xRange = (bounds.center.x - 80)..(bounds.center.x + 80 - step)
  val yRange = (bounds.top + 2)..(bounds.top + minOf(120, bounds.height / 2) - step)
  require(xRange.first >= 0 && xRange.last + step < width)
  require(yRange.first >= 0 && yRange.last + step < height)

  var tangentSlopeTotal = 0f
  var tangentSlopeCount = 0
  for (y in yRange) {
    for (x in xRange) {
      tangentSlopeTotal += this[x + step, y].red - this[x, y].red
      tangentSlopeCount++
    }
  }
  val strongestNormalSlope = yRange.minOf { y ->
    this[bounds.center.x, y + step].green - this[bounds.center.x, y].green
  }
  return FoldOrientation(
    tangentSlope = tangentSlopeTotal / tangentSlopeCount,
    strongestNormalSlope = strongestNormalSlope,
  )
}

internal fun ScreenshotUiTest.assertGlassOversizedAsymmetricCornersInvariant() {
  assertGlassCornersMatchComposeClipInvariant(
    surfaceSize = DpSize(280.dp, 180.dp),
    shape = RoundedCornerShape(
      topStart = 200.dp,
      topEnd = 130.dp,
      bottomEnd = 80.dp,
      bottomStart = 180.dp,
    ),
  )
}

internal fun ScreenshotUiTest.assertGlassCrossEdgeCornersInvariant() {
  assertGlassCornersMatchComposeClipInvariant(
    surfaceSize = DpSize(120.dp, 100.dp),
    shape = RoundedCornerShape(
      topStart = 100.dp,
      topEnd = 100.dp,
      bottomEnd = 0.dp,
      bottomStart = 0.dp,
    ),
  )
}

internal fun ScreenshotUiTest.assertGlassZeroExponentLightingInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = GlassTestConfiguration().apply {
    tint = Color.White.copy(alpha = 0.12f)
    optics = GlassOptics.Fixed(refractionStrength = 0.5f, depth = 0.5f, blurRadius = 16.dp)
    specularIntensity = 0.6f
    specularExponent = 0f
    fresnelExponent = 0f
    ambientResponse = 0.5f
    edgeSoftness = 8.dp
    this.shape = shape
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
      )
    }
  }

  val snapshot = captureInvariantSnapshot()
  val center = snapshot.invariantGeometry().surfaceBounds.center
  assertThat(snapshot[center.x, center.y].alpha).isGreaterThan(0.9f)
  captureRoot()
}

private fun ScreenshotUiTest.assertGlassCornersMatchComposeClipInvariant(
  surfaceSize: DpSize,
  shape: RoundedCornerShape,
) {
  val effect = GlassTestConfiguration().apply {
    tint = Color.White
    optics = GlassOptics.Fixed(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  var showGlass by mutableStateOf(true)
  var matte by mutableStateOf(Color.Black)
  setContent {
    if (showGlass) {
      GlassInvariantSample(
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
        shape = shape,
        surfaceSize = surfaceSize,
        transparentRoot = true,
        transparentRootBackground = matte,
        showSource = true,
      )
    } else {
      Box(Modifier.fillMaxSize().background(matte)) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(surfaceSize)
            .background(Color.White, shape),
        )
      }
    }
  }

  val glass = captureTransparentSnapshot { matte = it }
  val bounds = glass.centeredSurfaceBounds(surfaceSize)
  val outsidePoint = IntOffset(bounds.left + 5, bounds.top + 5)
  assertThat(glass[outsidePoint.x, outsidePoint.y].alpha).isLessThanOrEqualTo(1f / 255f)
  showGlass = false
  waitForIdle()
  val compose = captureTransparentSnapshot { matte = it }
  val visibleAlphaThreshold = 1f / 255f
  var firstSupportMismatch: IntOffset? = null
  var mismatchReason = ""
  for (y in bounds.top until bounds.bottom) {
    for (x in bounds.left until bounds.right) {
      val glassVisible = glass[x, y].alpha > visibleAlphaThreshold
      val composeAlpha = compose[x, y].alpha
      // Compose anti-aliases its outline while Glass is binary when edgeSoftness is zero.
      // Compare resolved silhouette support and allow only a one-output-pixel fractional
      // Compose fringe to differ; this still rejects Glass output in a transparent corner.
      val supportMismatch = when {
        composeAlpha <= visibleAlphaThreshold -> glassVisible.also {
          if (it) mismatchReason = "Glass is visible where Compose is fully transparent"
        }
        composeAlpha >= 1f - visibleAlphaThreshold -> (!glassVisible).also {
          if (it) mismatchReason = "Glass is transparent where Compose is fully opaque"
        }
        else -> {
          var touchesGlass = glassVisible
          for (neighborY in maxOf(bounds.top, y - 1)..minOf(bounds.bottom - 1, y + 1)) {
            for (neighborX in maxOf(bounds.left, x - 1)..minOf(bounds.right - 1, x + 1)) {
              touchesGlass = touchesGlass || glass[neighborX, neighborY].alpha > visibleAlphaThreshold
            }
          }
          (!touchesGlass).also {
            if (it) mismatchReason = "Compose support is more than one output pixel from Glass"
          }
        }
      }
      if (supportMismatch) {
        firstSupportMismatch = IntOffset(x, y)
        break
      }
    }
    if (firstSupportMismatch != null) break
  }
  check(firstSupportMismatch == null) {
    val mismatch = checkNotNull(firstSupportMismatch)
    val localPoint = IntOffset(
      mismatch.x - bounds.left,
      mismatch.y - bounds.top,
    )
    "Glass and Compose silhouette support differ at $mismatch: " +
      "$mismatchReason; " +
      "glass=${glass[mismatch.x, mismatch.y].alpha}, " +
      "compose=${compose[mismatch.x, mismatch.y].alpha}, " +
      "visibleAlphaThreshold=$visibleAlphaThreshold; " +
      "surfaceSize=$surfaceSize, shape=$shape, materialBounds=$bounds, " +
      "firstDifferingLocalPoint=$localPoint; " +
      "center glass=${glass[bounds.center.x, bounds.center.y].alpha}, " +
      "compose=${compose[bounds.center.x, bounds.center.y].alpha}"
  }
}

private fun PixelSnapshot.centeredSurfaceBounds(surfaceSize: DpSize): IntRect {
  return centeredSurfaceBounds(width, height, surfaceSize)
}

private fun PixelMap.centeredSurfaceBounds(surfaceSize: DpSize): IntRect {
  return centeredSurfaceBounds(width, height, surfaceSize)
}

private fun centeredSurfaceBounds(
  width: Int,
  height: Int,
  surfaceSize: DpSize,
): IntRect {
  val density = InvariantSurfaceWidthPx / 280f
  val surfaceWidthPx = (surfaceSize.width.value * density).roundToInt()
  val surfaceHeightPx = (surfaceSize.height.value * density).roundToInt()
  val left = (width - surfaceWidthPx) / 2
  val top = (height - surfaceHeightPx) / 2
  return IntRect(left, top, left + surfaceWidthPx, top + surfaceHeightPx)
}

private fun invariantEffect(shape: RoundedCornerShape) = GlassTestConfiguration().apply {
  tint = Color.White.copy(alpha = 0.12f)
  optics = GlassOptics.Fixed(depth = 0.5f, blurRadius = 16.dp)
  specularIntensity = 0.4f
  ambientResponse = 0.5f
  edgeSoftness = 8.dp
  this.shape = shape
}

private fun ScreenshotUiTest.captureInvariantSnapshot(): PixelSnapshot {
  return captureInvariantPixels().snapshot()
}

private fun ScreenshotUiTest.captureInvariantPixels(): PixelMap {
  val pixels = captureRootPixels()
  require(pixels.width == InvariantRootWidth && pixels.height in InvariantRootHeightRange) {
    "Invariant root must be width $InvariantRootWidth and height in " +
      "$InvariantRootHeightRange, " +
      "but capture was ${pixels.width}x${pixels.height}"
  }
  return pixels
}

private fun ScreenshotUiTest.captureTransparentSnapshot(
  setMatte: (Color) -> Unit,
): PixelSnapshot {
  setMatte(Color.Black)
  waitForIdle()
  val overBlack = captureInvariantSnapshot()
  setMatte(Color.White)
  waitForIdle()
  val overWhite = captureInvariantSnapshot()
  return recoverPremultipliedSnapshot(overBlack, overWhite)
}

private data class InvariantGeometry(
  val surfaceBounds: IntRect,
  val interiorBounds: IntRect,
  val centerY: Int,
  val leftEdgeRange: IntRange,
  val contentEdgeRange: IntRange,
  val outsidePoints: List<IntOffset>,
)

private fun assertInvariantMaterialSilhouette(
  snapshot: PixelSnapshot,
  geometry: InvariantGeometry,
) {
  assertThat(snapshot.alphaCoverage(geometry.surfaceBounds)).isGreaterThan(0.8f)
  val center = geometry.surfaceBounds.center
  val centerColor = snapshot[center.x, center.y]
  assertThat(centerColor.alpha).isGreaterThan(0.9f)
  assertThat(maxOf(centerColor.red, centerColor.green, centerColor.blue)).isGreaterThan(0.05f)
  val span = requireNotNull(snapshot.horizontalAlphaSpan(geometry.centerY)) {
    "Material silhouette must have visible alpha coverage"
  }
  assertThat(kotlin.math.abs(span.first - geometry.surfaceBounds.left)).isLessThanOrEqualTo(1)
  assertThat(kotlin.math.abs(span.last - (geometry.surfaceBounds.right - 1))).isLessThanOrEqualTo(1)
}

private fun PixelSnapshot.invariantGeometry(): InvariantGeometry {
  val left = (width - InvariantSurfaceWidthPx) / 2
  val top = (height - InvariantSurfaceHeightPx) / 2
  val surfaceBounds = IntRect(
    left = left,
    top = top,
    right = left + InvariantSurfaceWidthPx,
    bottom = top + InvariantSurfaceHeightPx,
  )
  return InvariantGeometry(
    surfaceBounds = surfaceBounds,
    interiorBounds = IntRect(
      left = surfaceBounds.left + 95,
      top = surfaceBounds.top + 88,
      right = surfaceBounds.right - 95,
      bottom = surfaceBounds.bottom - 88,
    ),
    centerY = surfaceBounds.top + InvariantSurfaceHeightPx / 2,
    leftEdgeRange = (surfaceBounds.left - 15)..(surfaceBounds.left + 15),
    contentEdgeRange = (surfaceBounds.left + 80)..(surfaceBounds.left + 96),
    outsidePoints = listOf(
      IntOffset(surfaceBounds.left + 5, surfaceBounds.top + 5),
      IntOffset(surfaceBounds.right - 5, surfaceBounds.top + 5),
      IntOffset(surfaceBounds.left + 5, surfaceBounds.bottom - 5),
      IntOffset(surfaceBounds.right - 5, surfaceBounds.bottom - 5),
    ),
  )
}
