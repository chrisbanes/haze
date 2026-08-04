// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class GlassStyleTest {

  @Test
  fun interactionPresentation_recordsAndComposesPerProperty() {
    val localPositionSpec = tween<Offset>(100)
    val explicitPositionSpec = tween<Offset>(200)
    val local = GlassStyle {
      hovered { lightingIntensity(0.1f) }
      focused { lightingIntensity(0.2f) }
      pressed { lightingIntensity(0.3f) }
      interactionLightRadiusFraction(0.8f)
      interactionPositionAnimationSpec(localPositionSpec)
    }
    val explicit = GlassStyle {
      hovered { lightingIntensity(0.4f) }
      interactionLightRadiusFraction(0.9f)
    }.then {
      focused { lightingIntensity(0.5f) }
      interactionPositionAnimationSpec(explicitPositionSpec)
    }

    val defaults = resolveGlassStyleValues(GlassStyle, GlassStyle)
    val resolved = resolveGlassStyleValues(local, explicit)

    assertThat(defaults.interactionLightRadiusFraction)
      .isEqualTo(GlassDefaults.interactionLightRadiusFraction)
    assertThat(defaults.interactionPositionAnimationSpec)
      .isEqualTo(GlassDefaults.positionAnimationSpec)
    assertThat(resolved.hoveredInteraction?.lightingIntensity?.value).isEqualTo(0.4f)
    assertThat(resolved.focusedInteraction?.lightingIntensity?.value).isEqualTo(0.5f)
    assertThat(resolved.pressedInteraction?.lightingIntensity?.value).isEqualTo(0.3f)
    assertThat(resolved.interactionLightRadiusFraction).isEqualTo(0.9f)
    assertThat(resolved.interactionPositionAnimationSpec).isEqualTo(explicitPositionSpec)
  }

  @Test
  fun interactionLightRadiusFraction_rejectsInvalidValues() {
    listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 2.1f, Float.POSITIVE_INFINITY)
      .forEach { invalid ->
        assertFailure {
          GlassStyle { interactionLightRadiusFraction(invalid) }
        }.isInstanceOf<IllegalArgumentException>()
      }
  }

  @Test
  fun construction_recordsWritesOnceAndResolutionCreatesFreshValues() {
    var styleExecutions = 0
    var interactionExecutions = 0
    val style = GlassStyle {
      styleExecutions++
      alpha(0.4f)
      pressed {
        interactionExecutions++
        lightingIntensity(0.6f)
      }
    }

    assertThat(styleExecutions).isEqualTo(1)
    assertThat(interactionExecutions).isEqualTo(1)

    val first = resolveGlassStyleValues(GlassStyle, style)
    val second = resolveGlassStyleValues(GlassStyle, style)

    assertThat(styleExecutions).isEqualTo(1)
    assertThat(interactionExecutions).isEqualTo(1)
    assertThat(first).isNotSameInstanceAs(second)
    assertThat(first.alpha).isEqualTo(0.4f)
    assertThat(second.alpha).isEqualTo(0.4f)
    assertThat(first.pressedInteraction?.lightingIntensity?.value).isEqualTo(0.6f)
    assertThat(second.pressedInteraction?.lightingIntensity?.value).isEqualTo(0.6f)
  }

  @Test
  fun then_composesRecordedStylesWithoutRerunningBuilders() {
    var baseExecutions = 0
    var overrideExecutions = 0
    var appendedExecutions = 0
    val base = GlassStyle {
      baseExecutions++
      optics(GlassOptics.Absolute(depth = 0.2f))
      pressed {
        lightingIntensity(0.2f)
        refractionMultiplier(1.2f)
      }
    }
    val override = GlassStyle {
      overrideExecutions++
      optics(GlassOptics.Absolute(depth = 0.6f))
      pressed { lightingIntensity(0.7f) }
    }
    val combined = base.then(override).then {
      appendedExecutions++
      alpha(0.5f)
    }

    val first = resolveGlassStyleValues(GlassStyle, combined)
    val second = resolveGlassStyleValues(GlassStyle, combined)

    assertThat(baseExecutions).isEqualTo(1)
    assertThat(overrideExecutions).isEqualTo(1)
    assertThat(appendedExecutions).isEqualTo(1)
    assertThat(first.optics).isEqualTo(GlassOptics.Absolute(depth = 0.6f))
    assertThat(first.pressedInteraction?.lightingIntensity?.value).isEqualTo(0.7f)
    assertThat(first.pressedInteraction?.refractionMultiplier).isNull()
    assertThat(first.alpha).isEqualTo(0.5f)
    assertThat(second).isEqualTo(first)
  }

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

    assertThat(effect.canDrawRetainedOutput()).isFalse()
    assertThat(effect.shouldDrawRetainedOutput()).isFalse()

    delegate.retainedOutputAvailable = true

    assertThat(effect.canDrawRetainedOutput()).isTrue()
    assertThat(effect.shouldDrawRetainedOutput()).isTrue()

    delegate.retainedOutputAvailable = false
    delegate.pendingRetainedOutput = true

    assertThat(effect.canDrawRetainedOutput()).isFalse()
    assertThat(effect.shouldDrawRetainedOutput()).isTrue()

    effect.clearRetainedOutput()

    assertThat(delegate.clearCount).isEqualTo(1)
    assertThat(effect.canDrawRetainedOutput()).isFalse()
    assertThat(effect.shouldDrawRetainedOutput()).isFalse()
  }
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

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) = Unit
}
