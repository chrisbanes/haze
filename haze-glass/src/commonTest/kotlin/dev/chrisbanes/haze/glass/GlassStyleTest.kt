// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
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
  fun interactionBlocks_chainAndReplacePerState() {
    val style = GlassStyle {
      hovered { lightingIntensity(0.2f) }
      pressed {
        animate(tween(100), tween(200)) {
          refractionMultiplier(1.1f)
        }
      }
    }.then {
      hovered { lightingIntensity(0.6f) }
    }

    val values = resolveGlassStyleValues(GlassStyle, style)

    assertThat(values.hoveredInteraction?.lightingIntensity?.value).isEqualTo(0.6f)
    assertThat(values.pressedInteraction?.refractionMultiplier?.value).isEqualTo(1.1f)
    assertThat(values.pressedInteraction?.refractionMultiplier?.toSpec).isEqualTo(tween(100))
    assertThat(values.focusedInteraction).isEqualTo(null)
  }

  @Test
  fun styleChain_appliesWritesInOrder() {
    val style = GlassStyle {
      tint(Color.Red)
      alpha(0.25f)
    }.then {
      tint(Color.Blue)
      alpha(0.75f)
    }

    val resolved = resolveGlassStyleValues(
      localStyle = GlassStyle,
      explicitStyle = style,
    )

    assertThat(resolved.tint).isEqualTo(Color.Blue)
    assertThat(resolved.alpha).isEqualTo(0.75f)
  }

  @Test
  fun resolution_appliesDefaultsLocalAndExplicitStyle() {
    val local = GlassStyle {
      tint(Color.Red)
      alpha(0.5f)
      contrast(0.25f)
    }
    val explicit = GlassStyle {
      tint(Color.Blue)
      alpha(0.75f)
    }

    val resolved = resolveGlassStyleValues(
      localStyle = local,
      explicitStyle = explicit,
    )

    assertThat(resolved.tint).isEqualTo(Color.Blue)
    assertThat(resolved.alpha).isEqualTo(0.75f)
    assertThat(resolved.contrast).isEqualTo(0.25f)
    assertThat(resolved.shape).isEqualTo(GlassDefaults.shape)
  }

  @Test
  fun evaluation_startsFromFreshAccumulator() {
    val style = GlassStyle {
      tint(Color.Blue)
      alpha(0.5f)
    }
    val first = resolveGlassStyleValues(GlassStyle, style)
    val replacement = resolveGlassStyleValues(GlassStyle, GlassStyle { contrast(0.4f) })
    val second = resolveGlassStyleValues(GlassStyle, style)

    assertThat(first).isEqualTo(second)
    assertThat(replacement.tint).isEqualTo(GlassDefaults.tint)
    assertThat(replacement.alpha).isEqualTo(GlassDefaults.alpha)
    assertThat(replacement.contrast).isEqualTo(0.4f)
  }

  @Test
  fun staticPropertyWrites_preserveCanonicalization() {
    val optics = GlassOptics.Absolute(refractionStrength = 0.3f)
    val shape = RoundedCornerShape(12.dp)
    val style = GlassStyle {
      shape(shape)
      optics(optics)
      specularIntensity(2f)
      ambientResponse(-1f)
      tint(Color.Blue)
      edgeSoftness(6.dp)
      lightPosition(Offset(4f, 8f))
      chromaticAberrationStrength(2f)
      surfaceProfile(SurfaceProfile.Concave)
      chromaticAberrationMode(ChromaticAberrationMode.Full)
      alpha(2f)
      contrast(-2f)
      whitePoint(2f)
      chromaMultiplier(3f)
      contentNormalBlend(-1f)
      specularExponent(-1f)
      fresnelExponent(-1f)
    }
    val resolved = resolveGlassStyleValues(GlassStyle, style)

    assertThat(resolved.shape).isEqualTo(shape)
    assertThat(resolved.optics).isEqualTo(optics)
    assertThat(resolved.specularIntensity).isEqualTo(1f)
    assertThat(resolved.ambientResponse).isEqualTo(0f)
    assertThat(resolved.tint).isEqualTo(Color.Blue)
    assertThat(resolved.edgeSoftness).isEqualTo(6.dp)
    assertThat(resolved.lightPosition).isEqualTo(Offset(4f, 8f))
    assertThat(resolved.chromaticAberrationStrength).isEqualTo(1f)
    assertThat(resolved.surfaceProfile).isEqualTo(SurfaceProfile.Concave)
    assertThat(resolved.chromaticAberrationMode).isEqualTo(ChromaticAberrationMode.Full)
    assertThat(resolved.alpha).isEqualTo(1f)
    assertThat(resolved.contrast).isEqualTo(-1f)
    assertThat(resolved.whitePoint).isEqualTo(1f)
    assertThat(resolved.chromaMultiplier).isEqualTo(2f)
    assertThat(resolved.contentNormalBlend).isEqualTo(0f)
    assertThat(resolved.specularExponent).isEqualTo(0f)
    assertThat(resolved.fresnelExponent).isEqualTo(0f)
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
