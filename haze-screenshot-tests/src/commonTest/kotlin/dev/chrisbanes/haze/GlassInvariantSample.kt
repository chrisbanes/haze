// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("ktlint:standard:property-naming")

package dev.chrisbanes.haze

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs

private const val InvariantRootWidth = 1080
private val InvariantRootHeightRange = 1919..1920
private const val InvariantSurfaceWidthPx = 770
private const val InvariantSurfaceHeightPx = 495
private val InvariantSourceColor = Color(0xFF101820)

@Composable
internal fun GlassInvariantSample(
  effect: GlassVisualEffect,
  inputScale: HazeInputScale,
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
  showSource: Boolean = true,
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
          adversarialStripePeriodPx = adversarialStripePeriodPx,
          horizontalStripes = horizontalStripes,
          checkerStripes = checkerStripes,
        )
      }
    }
    Box(
      Modifier
        .align(Alignment.Center)
        .size(surfaceSize)
        .then(
          if (enabled) {
            Modifier.hazeEffect(hazeState) {
              this.inputScale = inputScale
              visualEffect = effect
            }
          } else {
            Modifier
          },
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
  adversarialStripePeriodPx: Int?,
  horizontalStripes: Boolean,
  checkerStripes: Boolean,
) {
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
    val surfaceLeft = (size.width - surfaceWidthPx) * 0.5f
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

internal fun ScreenshotUiTest.assertGlassBlurInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Absolute(depth = 1f, blurRadius = 0.dp)
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(effect, HazeInputScale.None, shape)
    }
  }

  val sharp = captureInvariantSnapshot()
  effect.updateAbsoluteOptics { copy(blurRadius = 32.dp) }
  waitForIdle()
  val blurred = captureInvariantSnapshot()

  assertBlurReducesHighFrequencyEnergy(sharp, blurred, sharp.invariantGeometry().interiorBounds)
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
    optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 1f)
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        inputScale = HazeInputScale.None,
        shape = currentCase.shape,
        surfaceSize = currentCase.size,
      )
    }
  }

  cases.forEach { case ->
    currentCase = case
    effect.shape = case.shape
    effect.updateAbsoluteOptics { copy(blurRadius = 0.dp) }
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

    effect.updateAbsoluteOptics { copy(blurRadius = 0.1.dp) }
    waitForIdle()
    val subpixelEnergy = captureInvariantSnapshot().highFrequencyEnergy(bounds)
    assertThat(kotlin.math.abs(subpixelEnergy - 0.0239f)).isLessThanOrEqualTo(0.0004f)

    var previousEnergy = subpixelEnergy
    listOf(4.dp, 8.dp, 14.dp, 14.1.dp).forEach { radius ->
      effect.updateAbsoluteOptics { copy(blurRadius = radius) }
      waitForIdle()
      val energy = captureInvariantSnapshot().highFrequencyEnergy(bounds)
      val (expected, tolerance) = when (radius) {
        4.dp -> 0.001121f to 0.000012f
        8.dp -> 0.000265f to 0.000004f
        14.dp -> 0.000017f to 0.000001f
        else -> 0.000017f to 0.000001f
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
    optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 1f)
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
        inputScale = HazeInputScale.None,
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
    effect.updateAbsoluteOptics {
      copy(blurRadius = ((thresholdPx - epsilonPx) / pxPerDp).dp)
    }
    waitForIdle()
    val below = captureInvariantSnapshot()
    effect.updateAbsoluteOptics { copy(blurRadius = (thresholdPx / pxPerDp).dp) }
    waitForIdle()
    val at = captureInvariantSnapshot()
    effect.updateAbsoluteOptics {
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
            optics = GlassOptics.Absolute(
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

@Composable
private fun ProgressiveInvariantPanel(effect: GlassVisualEffect, height: androidx.compose.ui.unit.Dp) {
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
        adversarialStripePeriodPx = null,
        horizontalStripes = false,
        checkerStripes = false,
      )
    }
    Box(
      Modifier
        .fillMaxSize()
        .hazeEffect(hazeState) {
          inputScale = HazeInputScale.None
          visualEffect = effect
        },
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
    optics = GlassOptics.Absolute(
      refractionStrength = 1f,
      refractionScale = 0f,
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
      inputScale = HazeInputScale.None,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
    )
  }

  matte = Color.Black
  waitForIdle()
  val sharpOverBlack = captureInvariantSnapshot()
  effect.updateAbsoluteOptics { copy(blurRadius = 16.dp) }
  waitForIdle()
  val smallOverBlack = captureInvariantSnapshot()
  matte = Color.White
  waitForIdle()
  val smallPadding = recoverPremultipliedSnapshot(
    overBlack = smallOverBlack,
    overWhite = captureInvariantSnapshot(),
  )
  effect.updateAbsoluteOptics { copy(refractionScale = 96f) }
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
        inputScale = HazeInputScale.None,
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
  val effect = GlassVisualEffect().apply {
    tint = Color.Transparent
    optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      inputScale = HazeInputScale.None,
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

internal fun ScreenshotUiTest.assertGlassTranslucentSourceInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = GlassVisualEffect().apply {
    tint = Color.Transparent
    optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 8.dp
    this.shape = shape
  }
  var showSource by mutableStateOf(true)
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      inputScale = HazeInputScale.None,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
      sourceAlpha = 0.5f,
      drawGridLines = false,
      showSource = showSource,
    )
  }

  val live = captureTransparentSnapshot { matte = it }
  val geometry = live.invariantGeometry()
  val center = IntOffset(
    x = (geometry.surfaceBounds.left + geometry.surfaceBounds.right) / 2,
    y = (geometry.surfaceBounds.top + geometry.surfaceBounds.bottom) / 2,
  )
  assertThat(kotlin.math.abs(live[center.x, center.y].alpha - 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].red - InvariantSourceColor.red * 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].green - InvariantSourceColor.green * 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(live[center.x, center.y].blue - InvariantSourceColor.blue * 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  live.assertTransparentAt(geometry.outsidePoints)

  showSource = false
  waitForIdle()
  val retained = captureTransparentSnapshot { matte = it }
  assertThat(
    live.crop(geometry.surfaceBounds)
      .meanAbsoluteDifference(retained.crop(geometry.surfaceBounds)),
  ).isLessThan(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].alpha - 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].red - InvariantSourceColor.red * 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].green - InvariantSourceColor.green * 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  assertThat(kotlin.math.abs(retained[center.x, center.y].blue - InvariantSourceColor.blue * 0.75f))
    .isLessThanOrEqualTo(1f / 255f)
  retained.assertZeroAlphaHasZeroRgb()
  retained.assertTransparentAt(geometry.outsidePoints)
}

internal fun ScreenshotUiTest.assertGlassPaddingAndScaleInvariants() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    // Preserve the previous effective values while making the literal contract explicit.
    optics = GlassOptics.Absolute(
      refractionStrength = 0.2f,
      refractionScale = 24f,
      depth = 0.5f,
      blurRadius = 0.dp,
    )
    chromaticAberrationStrength = 0f
    edgeSoftness = 0.dp
  }
  var inputScale by mutableStateOf<HazeInputScale>(HazeInputScale.None)
  var matte by mutableStateOf(Color.Black)
  setContent {
    GlassInvariantSample(
      effect = effect,
      inputScale = inputScale,
      shape = shape,
      transparentRoot = true,
      transparentRootBackground = matte,
    )
  }

  val smallPadding = captureTransparentSnapshot { matte = it }
  effect.updateAbsoluteOptics {
    copy(
      refractionStrength = 0.85f,
      refractionScale = 53.25f,
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

  inputScale = HazeInputScale.Fixed(0.75f)
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
  // A 0.75 input scale resamples both into and out of the retained layer.
  assertThat(
    largePadding.crop(geometry.interiorBounds)
      .meanAbsoluteDifference(fixedScale.crop(geometry.interiorBounds)),
  ).isLessThan(2f / 255f)
}

internal fun ScreenshotUiTest.assertGlassFirstEnabledFrameInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val effect = invariantEffect(shape).apply {
    optics = GlassOptics.Absolute(depth = 0.6f, blurRadius = 32.dp)
  }
  var enabled by mutableStateOf(false)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        inputScale = HazeInputScale.None,
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
    optics = GlassOptics.Absolute(refractionStrength = 0.85f, depth = 0.6f, blurRadius = 32.dp)
    edgeSoftness = 0.dp
  }
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(effect, HazeInputScale.None, shape)
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

internal fun ScreenshotUiTest.assertGlassDefaultRefractionVisibleInvariant() {
  val shape = RoundedCornerShape(28.dp)
  val inputScale = HazeInputScale.None
  val effect = GlassVisualEffect().apply {
    style = GlassDefaults.style
    this.shape = shape
    optics = GlassOptics.Absolute(refractionStrength = 0f)
  }
  var drawCarrier by mutableStateOf(true)
  setContent {
    ScreenshotTheme {
      GlassInvariantSample(
        effect = effect,
        inputScale = inputScale,
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

  effect.optics = GlassOptics.Adaptive
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
  println("Glass default refraction invariant: $metrics")
  val failures = buildList {
    if (metrics.directionalDisplacementDeltaPx > -1f) {
      add("directional displacement delta=${metrics.directionalDisplacementDeltaPx}px is above -1px")
    }
    if (metrics.edgeBandResidualChangedPixelRatio <= .01f) {
      add("edge-band residual changed ratio=${metrics.edgeBandResidualChangedPixelRatio} is not above 1%")
    }
  }
  check(failures.isEmpty()) { failures.joinToString("; ") }
}

private fun invariantEffect(shape: RoundedCornerShape) = GlassVisualEffect().apply {
  tint = Color.White.copy(alpha = 0.12f)
  optics = GlassOptics.Absolute(depth = 0.5f, blurRadius = 16.dp)
  specularIntensity = 0.4f
  ambientResponse = 0.5f
  edgeSoftness = 8.dp
  this.shape = shape
}

private fun ScreenshotUiTest.captureInvariantSnapshot(): PixelSnapshot {
  val snapshot = captureRootPixels().snapshot()
  require(snapshot.width == InvariantRootWidth && snapshot.height in InvariantRootHeightRange) {
    "Invariant root must be width $InvariantRootWidth and height in " +
      "$InvariantRootHeightRange, " +
      "but capture was ${snapshot.width}x${snapshot.height}"
  }
  return snapshot
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
