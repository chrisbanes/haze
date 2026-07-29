// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

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
class GlassStyleTest {

  @Test
  fun styleChange_dirtiesOnlyTheSpecifiedScalarAndStyleFields() {
    val effect = GlassVisualEffect()
    effect.resetDirtyTracker()

    effect.style = GlassStyle(color = GlassColor(alpha = 0.5f))

    assertThat(GlassDirtyFields.stringify(effect.dirtyTracker))
      .isEqualTo("[Alpha, Style]")
  }

  @Test
  fun defaultsStyle_resolvesToGlassDefaults() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassDefaults.style
    }

    assertThat(effect.tint).isEqualTo(GlassDefaults.tint)
    assertThat(effect.shape).isEqualTo(GlassDefaults.shape)
    assertThat(effect.optics).isEqualTo(GlassDefaults.optics)
    assertThat(effect.specularIntensity).isEqualTo(GlassDefaults.specularIntensity)
    assertThat(effect.specularExponent).isEqualTo(GlassDefaults.specularExponent)
    assertThat(effect.fresnelExponent).isEqualTo(GlassDefaults.fresnelExponent)
    assertThat(effect.ambientResponse).isEqualTo(GlassDefaults.ambientResponse)
    assertThat(effect.alpha).isEqualTo(GlassDefaults.alpha)
    assertThat(effect.contrast).isEqualTo(GlassDefaults.contrast)
    assertThat(effect.whitePoint).isEqualTo(GlassDefaults.whitePoint)
    assertThat(effect.chromaMultiplier).isEqualTo(GlassDefaults.chromaMultiplier)
    assertThat(effect.edgeSoftness).isEqualTo(GlassDefaults.edgeSoftness)
    assertThat(effect.contentNormalBlend).isEqualTo(GlassDefaults.contentNormalBlend)
    assertThat(effect.surfaceProfile).isEqualTo(GlassDefaults.surfaceProfile)
    assertThat(effect.chromaticAberrationStrength).isEqualTo(GlassDefaults.chromaticAberrationStrength)
    assertThat(effect.chromaticAberrationMode).isEqualTo(GlassDefaults.chromaticAberrationMode)
  }

  @Test
  fun optics_resolvesDirectStyleLocalAndDefaultPrecedence() {
    val localOptics = GlassOptics.Absolute(refractionStrength = 0.2f)
    val directOptics = GlassOptics.Absolute(refractionStrength = 0.9f)
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(optics = localOptics)
    }
    assertThat(effect.optics).isEqualTo(localOptics)

    effect.style = GlassStyle(optics = GlassOptics.Adaptive)
    assertThat(effect.optics).isEqualTo(GlassOptics.Adaptive)

    effect.optics = directOptics
    assertThat(effect.optics).isEqualTo(directOptics)

    effect.clearOpticsOverride()
    assertThat(effect.optics).isEqualTo(GlassOptics.Adaptive)

    effect.style = GlassStyle.Unspecified
    effect.compositionLocalStyle = GlassStyle.Unspecified
    assertThat(effect.optics).isEqualTo(GlassDefaults.optics)
  }

  @Test
  fun groupedStyle_partiallySpecifiedValuesInheritFromCompositionLocal() {
    val localStyle = GlassStyle(
      tint = Color.Blue,
      shape = RoundedCornerShape(12.dp),
      optics = GlassOptics.Absolute(
        refractionStrength = 0.2f,
        refractionDisplacement = 8.dp,
        depth = 0.3f,
      ),
      lighting = GlassLighting(
        specularIntensity = 0.25f,
        lightPosition = Offset(4f, 8f),
      ),
      color = GlassColor(alpha = 0.7f, contrast = 0.4f),
      rendering = GlassRendering(
        edgeSoftness = 6.dp,
        surfaceProfile = SurfaceProfile.Concave,
      ),
    )
    val directOptics = GlassOptics.Absolute(refractionStrength = 0.9f)
    val directStyle = GlassStyle(
      optics = directOptics,
      lighting = GlassLighting(ambientResponse = 0.8f),
      color = GlassColor(whitePoint = 0.1f),
      rendering = GlassRendering(chromaticAberrationStrength = 0.5f),
    )
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = localStyle
      style = directStyle
    }

    assertThat(effect.optics).isEqualTo(directOptics)
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
    val effect = GlassVisualEffect().apply {
      style = GlassStyle(
        tint = Color.Blue,
        optics = GlassOptics.Absolute(refractionStrength = 0.2f),
        lighting = GlassLighting(ambientResponse = 0.3f),
        color = GlassColor(alpha = 0.4f),
        rendering = GlassRendering(edgeSoftness = 6.dp),
      )
      tint = Color.Red
      optics = GlassOptics.Absolute(refractionStrength = 0.8f)
      ambientResponse = 0.9f
      alpha = 0.5f
      edgeSoftness = 10.dp
    }

    assertThat(effect.tint).isEqualTo(Color.Red)
    assertThat(effect.optics).isEqualTo(GlassOptics.Absolute(refractionStrength = 0.8f))
    assertThat(effect.ambientResponse).isEqualTo(0.9f)
    assertThat(effect.alpha).isEqualTo(0.5f)
    assertThat(effect.edgeSoftness).isEqualTo(10.dp)
  }

  @Test
  fun fallbackEdgeAlpha_clampsRawStyleAmbientResponse() {
    val effect = GlassVisualEffect().apply {
      style = GlassStyle(
        lighting = GlassLighting(ambientResponse = 2f),
      )
    }

    assertThat(fallbackEdgeAlpha(effect.ambientResponse)).isEqualTo(0.18f)
    assertThat(fallbackEdgeAlpha(-1f)).isEqualTo(0f)
  }

  @Test
  fun retainedOutputAvailabilityReflectsDelegate() {
    val effect = GlassRuntimeEffect()
    val delegate = RetainedTrackingGlassDelegate()
    effect.delegate = delegate

    assertThat(effect.canDrawRetainedOutput(FakeGlassContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeGlassContext)).isFalse()

    delegate.retainedOutputAvailable = true

    assertThat(effect.canDrawRetainedOutput(FakeGlassContext)).isTrue()
    assertThat(effect.shouldDrawRetainedOutput(FakeGlassContext)).isTrue()

    delegate.retainedOutputAvailable = false
    delegate.pendingRetainedOutput = true

    assertThat(effect.canDrawRetainedOutput(FakeGlassContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeGlassContext)).isTrue()

    effect.clearRetainedOutput()

    assertThat(delegate.clearCount).isEqualTo(1)
    assertThat(effect.canDrawRetainedOutput(FakeGlassContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeGlassContext)).isFalse()
  }
}

private data object FakeGlassContext : VisualEffectContext {
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

private class RetainedTrackingGlassDelegate :
  GlassRuntimeEffect.Delegate,
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
