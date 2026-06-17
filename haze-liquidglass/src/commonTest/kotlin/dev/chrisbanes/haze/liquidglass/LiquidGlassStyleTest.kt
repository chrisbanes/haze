// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalHazeApi::class)
class LiquidGlassStyleTest {

  @Test
  fun defaultsStyle_resolvesToLiquidGlassDefaults() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassDefaults.style
    }

    assertThat(effect.tint).isEqualTo(LiquidGlassDefaults.tint)
    assertThat(effect.shape).isEqualTo(LiquidGlassDefaults.shape)
    assertThat(effect.refractionStrength).isEqualTo(LiquidGlassDefaults.refractionStrength)
    assertThat(effect.refractionHeight).isEqualTo(LiquidGlassDefaults.refractionHeight)
    assertThat(effect.refractionScale).isEqualTo(LiquidGlassDefaults.refractionScale)
    assertThat(effect.depth).isEqualTo(LiquidGlassDefaults.depth)
    assertThat(effect.blurRadius).isEqualTo(LiquidGlassDefaults.blurRadius)
    assertThat(effect.specularIntensity).isEqualTo(LiquidGlassDefaults.specularIntensity)
    assertThat(effect.specularExponent).isEqualTo(LiquidGlassDefaults.specularExponent)
    assertThat(effect.fresnelExponent).isEqualTo(LiquidGlassDefaults.fresnelExponent)
    assertThat(effect.ambientResponse).isEqualTo(LiquidGlassDefaults.ambientResponse)
    assertThat(effect.alpha).isEqualTo(LiquidGlassDefaults.alpha)
    assertThat(effect.contrast).isEqualTo(LiquidGlassDefaults.contrast)
    assertThat(effect.whitePoint).isEqualTo(LiquidGlassDefaults.whitePoint)
    assertThat(effect.chromaMultiplier).isEqualTo(LiquidGlassDefaults.chromaMultiplier)
    assertThat(effect.edgeSoftness).isEqualTo(LiquidGlassDefaults.edgeSoftness)
    assertThat(effect.contentNormalBlend).isEqualTo(LiquidGlassDefaults.contentNormalBlend)
    assertThat(effect.surfaceProfile).isEqualTo(LiquidGlassDefaults.surfaceProfile)
    assertThat(effect.chromaticAberrationStrength).isEqualTo(LiquidGlassDefaults.chromaticAberrationStrength)
    assertThat(effect.chromaticAberrationMode).isEqualTo(LiquidGlassDefaults.chromaticAberrationMode)
  }

  @Test
  fun groupedStyle_partiallySpecifiedValuesInheritFromCompositionLocal() {
    val localStyle = LiquidGlassStyle(
      tint = Color.Blue,
      shape = RoundedCornerShape(12.dp),
      optics = LiquidGlassOptics(
        refractionStrength = 0.2f,
        refractionScale = 8f,
        depth = 0.3f,
      ),
      lighting = LiquidGlassLighting(
        specularIntensity = 0.25f,
        lightPosition = Offset(4f, 8f),
      ),
      color = LiquidGlassColor(alpha = 0.7f, contrast = 0.4f),
      rendering = LiquidGlassRendering(
        edgeSoftness = 6.dp,
        surfaceProfile = SurfaceProfile.Concave,
      ),
    )
    val directStyle = LiquidGlassStyle(
      optics = LiquidGlassOptics(refractionStrength = 0.9f),
      lighting = LiquidGlassLighting(ambientResponse = 0.8f),
      color = LiquidGlassColor(whitePoint = 0.1f),
      rendering = LiquidGlassRendering(chromaticAberrationStrength = 0.5f),
    )
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = localStyle
      style = directStyle
    }

    assertThat(effect.refractionStrength).isEqualTo(0.9f)
    assertThat(effect.refractionScale).isEqualTo(8f)
    assertThat(effect.depth).isEqualTo(0.3f)
    assertThat(effect.ambientResponse).isEqualTo(0.8f)
    assertThat(effect.specularIntensity).isEqualTo(0.25f)
    assertThat(effect.lightPosition).isEqualTo(Offset(4f, 8f))
    assertThat(effect.alpha).isEqualTo(0.7f)
    assertThat(effect.contrast).isEqualTo(0.4f)
    assertThat(effect.whitePoint).isEqualTo(0.1f)
    assertThat(effect.edgeSoftness).isEqualTo(6.dp)
    assertThat(effect.surfaceProfile).isEqualTo(SurfaceProfile.Concave)
    assertThat(effect.chromaticAberrationStrength).isEqualTo(0.5f)
  }

  @Test
  fun directPropertiesOverrideGroupedStyle() {
    val effect = LiquidGlassVisualEffect().apply {
      style = LiquidGlassStyle(
        tint = Color.Blue,
        optics = LiquidGlassOptics(refractionStrength = 0.2f),
        lighting = LiquidGlassLighting(ambientResponse = 0.3f),
        color = LiquidGlassColor(alpha = 0.4f),
        rendering = LiquidGlassRendering(edgeSoftness = 6.dp),
      )
      tint = Color.Red
      refractionStrength = 0.8f
      ambientResponse = 0.9f
      alpha = 0.5f
      edgeSoftness = 10.dp
    }

    assertThat(effect.tint).isEqualTo(Color.Red)
    assertThat(effect.refractionStrength).isEqualTo(0.8f)
    assertThat(effect.ambientResponse).isEqualTo(0.9f)
    assertThat(effect.alpha).isEqualTo(0.5f)
    assertThat(effect.edgeSoftness).isEqualTo(10.dp)
  }

  @Test
  fun retainedOutputAvailabilityReflectsDelegate() {
    val effect = LiquidGlassVisualEffect()
    val delegate = RetainedTrackingLiquidGlassDelegate()
    effect.delegate = delegate

    assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()

    delegate.retainedOutputAvailable = true

    assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isTrue()
    assertThat(effect.shouldDrawRetainedOutput(FakeLiquidGlassContext)).isTrue()

    delegate.retainedOutputAvailable = false
    delegate.pendingRetainedOutput = true

    assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeLiquidGlassContext)).isTrue()

    effect.clearRetainedOutput()

    assertThat(delegate.clearCount).isEqualTo(1)
    assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()
  }
}

private data object FakeLiquidGlassContext : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = Size.Zero
  override val layerSize: Size = Size.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun positionOf(area: HazeArea): Offset = area.coordinates.localPosition

  override fun boundsOf(area: HazeArea): Rect? {
    val position = area.coordinates.localPosition
    return if (position.isSpecified && area.size.isSpecified) Rect(position, area.size) else null
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in lifecycle tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in lifecycle tests")
  override fun invalidateDraw() = Unit
}

private class RetainedTrackingLiquidGlassDelegate :
  LiquidGlassVisualEffect.Delegate,
  RetainedOutputDelegate {

  var retainedOutputAvailable = false
  var pendingRetainedOutput = false
  var clearCount = 0

  override fun canDrawRetainedOutput(): Boolean = retainedOutputAvailable

  override fun shouldDrawRetainedOutput(): Boolean = retainedOutputAvailable || pendingRetainedOutput

  override fun clearRetainedOutput() {
    clearCount++
    retainedOutputAvailable = false
    pendingRetainedOutput = false
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
