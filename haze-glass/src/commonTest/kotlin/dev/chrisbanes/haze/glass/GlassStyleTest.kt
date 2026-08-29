// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class GlassStyleTest {

  @Test
  fun lightPosition_defaultsToCenter() {
    assertThat(resolveGlassStyleValues(GlassStyle, GlassStyle).lightPosition)
      .isEqualTo(Alignment.Center)
  }

  @Test
  fun lightPosition_recordsAlignment() {
    val values = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle { lightPosition(Alignment.BottomEnd) },
    )

    assertThat(values.lightPosition).isEqualTo(Alignment.BottomEnd)
  }

  @Test
  fun lightPosition_followsStylePrecedence() {
    val local = GlassStyle { lightPosition(Alignment.TopStart) }
    val explicit = GlassStyle { lightPosition(Alignment.CenterEnd) }
      .then { lightPosition(Alignment.BottomCenter) }

    val values = resolveGlassStyleValues(local, explicit)

    assertThat(values.lightPosition).isEqualTo(Alignment.BottomCenter)
  }

  @Test
  fun directOptics_constructsTheCompleteFixedValue() {
    val progressive = HazeProgressive.verticalGradient()
    val style = GlassStyle {
      optics(
        refractionStrength = 0.8f,
        refractionHeightFraction = 0.4f,
        refractionDisplacement = 24.dp,
        depth = 0.6f,
        blurRadius = 20.dp,
        progressive = progressive,
        refractionFoldStrength = 0.65f,
      )
    }

    assertThat(resolveGlassStyleValues(GlassStyle, style).optics).isEqualTo(
      GlassOptics(
        refractionStrength = 0.8f,
        refractionHeightFraction = 0.4f,
        refractionDisplacement = 24.dp,
        depth = SizeValue.Fixed(0.6f),
        blurRadius = SizeValue.Fixed(20.dp),
        progressive = progressive,
        refractionFoldStrength = 0.65f,
      ),
    )
  }

  @Test
  fun directOptics_usesFixedValidation() {
    assertFixedAndDirectOpticsFailure(
      "refractionStrength must be finite and in 0f..1f",
      fixed = { GlassOptics(refractionStrength = Float.NaN) },
      direct = { optics(refractionStrength = Float.NaN) },
    )
    assertFixedAndDirectOpticsFailure(
      "refractionFoldStrength must be finite and in 0f..1f",
      fixed = { GlassOptics(refractionFoldStrength = Float.NaN) },
      direct = { optics(refractionFoldStrength = Float.NaN) },
    )
    assertFixedAndDirectOpticsFailure(
      "refractionHeightFraction must be finite and in 0f..1f",
      fixed = { GlassOptics(refractionHeightFraction = 1.1f) },
      direct = { optics(refractionHeightFraction = 1.1f) },
    )
    assertFixedAndDirectOpticsFailure(
      "refractionDisplacement must be specified, finite, and non-negative",
      fixed = { GlassOptics(refractionDisplacement = Dp.Unspecified) },
      direct = { optics(refractionDisplacement = Dp.Unspecified) },
    )
    assertFixedAndDirectOpticsFailure(
      "depth must be finite and in 0f..1f",
      fixed = { GlassOptics(depth = SizeValue.Fixed(Float.NEGATIVE_INFINITY)) },
      direct = { optics(depth = Float.NEGATIVE_INFINITY) },
    )
    assertFixedAndDirectOpticsFailure(
      "blurRadius must be specified, finite, and non-negative",
      fixed = { GlassOptics(blurRadius = SizeValue.Fixed((-1).dp)) },
      direct = { optics(blurRadius = (-1).dp) },
    )
  }

  @Test
  fun completeOptics_preservesResponsiveAndProgrammaticValues() {
    val sharedOptics = GlassOptics(depth = SizeValue.Fixed(0.4f))
    val programmaticallySelected: GlassOptics = listOf(GlassDefaults.optics, sharedOptics).last()

    val responsive = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle { optics(GlassDefaults.optics) },
    )
    val selected = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle { optics(programmaticallySelected) },
    )

    assertThat(responsive.optics).isSameInstanceAs(GlassDefaults.optics)
    assertThat(selected.optics).isSameInstanceAs(sharedOptics)
  }

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
        }.apply {
          isInstanceOf<IllegalArgumentException>()
          hasMessage("interactionLightRadiusFraction must be finite and in 0f..2f")
        }
      }
  }

  @Test
  fun interactionLightRadiusFraction_acceptsBoundaries() {
    val minimum = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle { interactionLightRadiusFraction(0f) },
    )
    val maximum = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle { interactionLightRadiusFraction(2f) },
    )

    assertThat(minimum.interactionLightRadiusFraction).isEqualTo(0f)
    assertThat(maximum.interactionLightRadiusFraction).isEqualTo(2f)
  }

  @Test
  fun interactionResponses_rejectInvalidValuesAtConstruction() {
    assertInvalidFloatWrites(
      invalidUnitInterval("lightingIntensity") { hovered { lightingIntensity(it) } },
      invalidDoubleInterval("refractionMultiplier") { hovered { refractionMultiplier(it) } },
      invalidSignedUnitInterval("whitePointDelta") { hovered { whitePointDelta(it) } },
      invalidPositiveAtMostOne("scaleX") { hovered { scale(scaleX = it, scaleY = 1f) } },
      invalidPositiveAtMostOne("scaleY") { hovered { scale(scaleX = 1f, scaleY = it) } },
    )
  }

  @Test
  fun uniformScale_delegatesToAxisValidation() {
    assertInvalidFloatWrites(
      invalidPositiveAtMostOne("scaleX") { hovered { scale(it) } },
    )
  }

  @Test
  fun interactionResponses_acceptBoundaries() {
    val lowerValues = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle {
        hovered {
          lightingIntensity(0f)
          refractionMultiplier(0f)
          whitePointDelta(-1f)
          scale(scaleX = Float.MIN_VALUE, scaleY = 1f)
        }
      },
    )
    val upperValues = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle {
        focused {
          lightingIntensity(1f)
          refractionMultiplier(2f)
          whitePointDelta(1f)
          scale(1f)
        }
      },
    )

    assertThat(lowerValues.hoveredInteraction?.lightingIntensity?.value).isEqualTo(0f)
    assertThat(lowerValues.hoveredInteraction?.refractionMultiplier?.value).isEqualTo(0f)
    assertThat(lowerValues.hoveredInteraction?.whitePointDelta?.value).isEqualTo(-1f)
    assertThat(lowerValues.hoveredInteraction?.scaleX?.value).isEqualTo(Float.MIN_VALUE)
    assertThat(lowerValues.hoveredInteraction?.scaleY?.value).isEqualTo(1f)
    assertThat(upperValues.focusedInteraction?.lightingIntensity?.value).isEqualTo(1f)
    assertThat(upperValues.focusedInteraction?.refractionMultiplier?.value).isEqualTo(2f)
    assertThat(upperValues.focusedInteraction?.whitePointDelta?.value).isEqualTo(1f)
    assertThat(upperValues.focusedInteraction?.scaleX?.value).isEqualTo(1f)
    assertThat(upperValues.focusedInteraction?.scaleY?.value).isEqualTo(1f)
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
      optics(depth = 0.2f)
      pressed {
        lightingIntensity(0.2f)
        refractionMultiplier(1.2f)
      }
    }
    val override = GlassStyle {
      overrideExecutions++
      optics(depth = 0.6f)
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
    assertThat(first.optics).isEqualTo(GlassOptics(depth = SizeValue.Fixed(0.6f)))
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
      backgroundColor(Color.Red)
      tint(Color.Red)
      alpha(0.25f)
    }.then {
      backgroundColor(Color.Green)
      tint(Color.Blue)
      alpha(0.75f)
    }

    val resolved = resolveGlassStyleValues(
      localStyle = GlassStyle,
      explicitStyle = style,
    )

    assertThat(resolved.backgroundColor).isEqualTo(Color.Green)
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
  fun staticPropertyWrites_rejectInvalidValuesAtConstruction() {
    assertInvalidFloatWrites(
      invalidUnitInterval("specularIntensity") { specularIntensity(it) },
      invalidUnitInterval("ambientResponse") { ambientResponse(it) },
      invalidUnitInterval("chromaticAberrationStrength") { chromaticAberrationStrength(it) },
      invalidUnitInterval("alpha") { alpha(it) },
      invalidUnitInterval("contentNormalBlend") { contentNormalBlend(it) },
      invalidSignedUnitInterval("contrast") { contrast(it) },
      invalidSignedUnitInterval("whitePoint") { whitePoint(it) },
      invalidDoubleInterval("chromaMultiplier") { chromaMultiplier(it) },
      invalidNonNegative("specularExponent") { specularExponent(it) },
      invalidNonNegative("fresnelExponent") { fresnelExponent(it) },
    )
  }

  @Test
  fun staticPropertyWrites_acceptBoundariesAndLargeValues() {
    val lowerStyle = GlassStyle {
      specularIntensity(0f)
      ambientResponse(0f)
      chromaticAberrationStrength(0f)
      alpha(0f)
      contentNormalBlend(0f)
      contrast(-1f)
      whitePoint(-1f)
      chromaMultiplier(0f)
      edgeSoftness(0.dp)
      specularExponent(0f)
      fresnelExponent(0f)
      tint(Color.Transparent)
    }
    val upperStyle = GlassStyle {
      specularIntensity(1f)
      ambientResponse(1f)
      chromaticAberrationStrength(1f)
      alpha(1f)
      contentNormalBlend(1f)
      contrast(1f)
      whitePoint(1f)
      chromaMultiplier(2f)
      edgeSoftness(Float.MAX_VALUE.dp)
      specularExponent(Float.MAX_VALUE)
      fresnelExponent(Float.MAX_VALUE)
    }
    val lower = resolveGlassStyleValues(GlassStyle, lowerStyle)
    val upper = resolveGlassStyleValues(GlassStyle, upperStyle)

    assertThat(lower.specularIntensity).isEqualTo(0f)
    assertThat(lower.ambientResponse).isEqualTo(0f)
    assertThat(lower.chromaticAberrationStrength).isEqualTo(0f)
    assertThat(lower.alpha).isEqualTo(0f)
    assertThat(lower.contentNormalBlend).isEqualTo(0f)
    assertThat(lower.contrast).isEqualTo(-1f)
    assertThat(lower.whitePoint).isEqualTo(-1f)
    assertThat(lower.chromaMultiplier).isEqualTo(0f)
    assertThat(lower.edgeSoftness).isEqualTo(0.dp)
    assertThat(lower.specularExponent).isEqualTo(0f)
    assertThat(lower.fresnelExponent).isEqualTo(0f)
    assertThat(lower.tint).isEqualTo(Color.Transparent)
    assertThat(upper.specularIntensity).isEqualTo(1f)
    assertThat(upper.ambientResponse).isEqualTo(1f)
    assertThat(upper.chromaticAberrationStrength).isEqualTo(1f)
    assertThat(upper.alpha).isEqualTo(1f)
    assertThat(upper.contentNormalBlend).isEqualTo(1f)
    assertThat(upper.contrast).isEqualTo(1f)
    assertThat(upper.whitePoint).isEqualTo(1f)
    assertThat(upper.chromaMultiplier).isEqualTo(2f)
    assertThat(upper.edgeSoftness).isEqualTo(Float.MAX_VALUE.dp)
    assertThat(upper.specularExponent).isEqualTo(Float.MAX_VALUE)
    assertThat(upper.fresnelExponent).isEqualTo(Float.MAX_VALUE)
  }

  @Test
  fun edgeSoftness_rejectsInvalidValuesAtConstruction() {
    listOf(Dp.Unspecified, Float.NaN.dp, Float.NEGATIVE_INFINITY.dp, (-1).dp, Float.POSITIVE_INFINITY.dp)
      .forEach { invalid ->
        assertFailure { GlassStyle { edgeSoftness(invalid) } }.apply {
          isInstanceOf<IllegalArgumentException>()
          hasMessage("edgeSoftness must be specified, finite, and non-negative")
        }
      }
  }

  @Test
  fun tint_rejectsUnspecifiedAtConstruction() {
    assertFailure { GlassStyle { tint(Color.Unspecified) } }.apply {
      isInstanceOf<IllegalArgumentException>()
      hasMessage("tint must be specified")
    }
  }

  @Test
  fun backgroundColor_rejectsUnspecifiedAtConstruction() {
    assertFailure { GlassStyle { backgroundColor(Color.Unspecified) } }.apply {
      isInstanceOf<IllegalArgumentException>()
      hasMessage("backgroundColor must be specified")
    }
  }

  @Test
  fun emptyStyles_resolveToGlassDefaults() {
    val values = resolveGlassStyleValues(GlassStyle, GlassStyle)

    assertThat(values.specularIntensity).isEqualTo(GlassDefaults.specularIntensity)
    assertThat(values.ambientResponse).isEqualTo(GlassDefaults.ambientResponse)
    assertThat(values.backgroundColor).isEqualTo(GlassDefaults.backgroundColor)
    assertThat(values.tint).isEqualTo(GlassDefaults.tint)
    assertThat(values.edgeSoftness).isEqualTo(GlassDefaults.edgeSoftness)
    assertThat(values.chromaticAberrationStrength)
      .isEqualTo(GlassDefaults.chromaticAberrationStrength)
    assertThat(values.alpha).isEqualTo(GlassDefaults.alpha)
    assertThat(values.contrast).isEqualTo(GlassDefaults.contrast)
    assertThat(values.whitePoint).isEqualTo(GlassDefaults.whitePoint)
    assertThat(values.chromaMultiplier).isEqualTo(GlassDefaults.chromaMultiplier)
    assertThat(values.contentNormalBlend).isEqualTo(GlassDefaults.contentNormalBlend)
    assertThat(values.specularExponent).isEqualTo(GlassDefaults.specularExponent)
    assertThat(values.fresnelExponent).isEqualTo(GlassDefaults.fresnelExponent)
    assertThat(values.interactionLightRadiusFraction)
      .isEqualTo(GlassDefaults.interactionLightRadiusFraction)
  }

  @Test
  fun builtInStyles_exposeRegularAndClearMaterialResponses() {
    val regular = resolveGlassStyleValues(GlassStyle, GlassStyle.regular)
    val clear = resolveGlassStyleValues(GlassStyle, GlassStyle.clear)

    assertThat(regular.optics).isSameInstanceAs(GlassDefaults.optics)
    assertThat(clear.optics).isSameInstanceAs(GlassStyle.clearOptics)
    assertThat(clear.optics).isNotEqualTo(regular.optics)
    assertThat(clear.edgeSoftness).isNotEqualTo(regular.edgeSoftness)
    assertThat(clear.specularIntensity).isNotEqualTo(regular.specularIntensity)
  }

  @Test
  fun regular_matchesTheDefaultMaterialResponse() {
    val defaults = resolveGlassStyleValues(GlassStyle, GlassStyle)
    val regular = resolveGlassStyleValues(GlassStyle, GlassStyle.regular)

    assertThat(regular.optics).isEqualTo(defaults.optics)
    assertThat(regular.surfaceProfile).isEqualTo(defaults.surfaceProfile)
    assertThat(regular.edgeSoftness).isEqualTo(defaults.edgeSoftness)
    assertThat(regular.specularIntensity).isEqualTo(defaults.specularIntensity)
    assertThat(regular.specularExponent).isEqualTo(defaults.specularExponent)
    assertThat(regular.fresnelExponent).isEqualTo(defaults.fresnelExponent)
    assertThat(regular.ambientResponse).isEqualTo(defaults.ambientResponse)
    assertThat(regular.chromaticAberrationStrength)
      .isEqualTo(defaults.chromaticAberrationStrength)
    assertThat(regular.chromaticAberrationMode).isEqualTo(defaults.chromaticAberrationMode)
    assertThat(regular.contrast).isEqualTo(defaults.contrast)
    assertThat(regular.whitePoint).isEqualTo(defaults.whitePoint)
    assertThat(regular.chromaMultiplier).isEqualTo(defaults.chromaMultiplier)
    assertThat(regular.contentNormalBlend).isEqualTo(defaults.contentNormalBlend)
  }

  @Test
  fun builtInStyles_replaceEveryMaterialResponseWithoutReplacingPresentation() {
    val inheritedInteraction = GlassStyle { pressed { lightingIntensity(0.8f) } }
    val local = GlassStyle {
      shape(RoundedCornerShape(28.dp))
      backgroundColor(Color.Red)
      tint(Color.Blue)
      alpha(0.45f)
      lightPosition(Alignment.TopEnd)
    }.then(inheritedInteraction).then {
      optics(GlassOptics(depth = SizeValue.Fixed(0.9f), blurRadius = SizeValue.Fixed(30.dp)))
      surfaceProfile(SurfaceProfile.Squircle)
      edgeSoftness(12.dp)
      specularIntensity(0.1f)
      specularExponent(8f)
      fresnelExponent(1f)
      ambientResponse(0.1f)
      chromaticAberrationStrength(0.3f)
      chromaticAberrationMode(ChromaticAberrationMode.Full)
      contrast(-0.2f)
      whitePoint(-0.1f)
      chromaMultiplier(0.6f)
      contentNormalBlend(0.8f)
    }

    listOf(GlassStyle.regular, GlassStyle.clear).forEach { builtInStyle ->
      val expected = resolveGlassStyleValues(GlassStyle, builtInStyle)
      val resolved = resolveGlassStyleValues(local, builtInStyle)

      assertThat(resolved.optics).isEqualTo(expected.optics)
      assertThat(resolved.surfaceProfile).isEqualTo(expected.surfaceProfile)
      assertThat(resolved.edgeSoftness).isEqualTo(expected.edgeSoftness)
      assertThat(resolved.specularIntensity).isEqualTo(expected.specularIntensity)
      assertThat(resolved.specularExponent).isEqualTo(expected.specularExponent)
      assertThat(resolved.fresnelExponent).isEqualTo(expected.fresnelExponent)
      assertThat(resolved.ambientResponse).isEqualTo(expected.ambientResponse)
      assertThat(resolved.chromaticAberrationStrength)
        .isEqualTo(expected.chromaticAberrationStrength)
      assertThat(resolved.chromaticAberrationMode).isEqualTo(expected.chromaticAberrationMode)
      assertThat(resolved.contrast).isEqualTo(expected.contrast)
      assertThat(resolved.whitePoint).isEqualTo(expected.whitePoint)
      assertThat(resolved.chromaMultiplier).isEqualTo(expected.chromaMultiplier)
      assertThat(resolved.contentNormalBlend).isEqualTo(expected.contentNormalBlend)
      assertThat(resolved.shape).isEqualTo(RoundedCornerShape(28.dp))
      assertThat(resolved.backgroundColor).isEqualTo(Color.Red)
      assertThat(resolved.tint).isEqualTo(Color.Blue)
      assertThat(resolved.alpha).isEqualTo(0.45f)
      assertThat(resolved.lightPosition).isEqualTo(Alignment.TopEnd)
      assertThat(resolved.pressedInteraction?.lightingIntensity?.value).isEqualTo(0.8f)
    }
  }

  @Test
  fun builtInStyle_allowsLaterCustomMaterialWritesToWin() {
    val customOptics = GlassOptics(depth = SizeValue.Fixed(0.7f), blurRadius = SizeValue.Fixed(24.dp))
    val resolved = resolveGlassStyleValues(
      GlassStyle,
      GlassStyle.clear.then {
        optics(customOptics)
        contrast(-0.3f)
      },
    )

    assertThat(resolved.optics).isSameInstanceAs(customOptics)
    assertThat(resolved.contrast).isEqualTo(-0.3f)
  }

  @Test
  fun lightPosition_rejectsNonFiniteKnownBiasesAtConstruction() {
    val cases = listOf<Pair<String, (Float) -> Alignment>>(
      "horizontalBias" to { invalid: Float -> BiasAlignment(invalid, 0f) },
      "verticalBias" to { invalid: Float -> BiasAlignment(0f, invalid) },
      "horizontalBias" to { invalid: Float -> BiasAbsoluteAlignment(invalid, 0f) },
      "verticalBias" to { invalid: Float -> BiasAbsoluteAlignment(0f, invalid) },
    )

    cases.forEach { (axis, createAlignment) ->
      listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).forEach { invalid ->
        assertFailure { GlassStyle { lightPosition(createAlignment(invalid)) } }.apply {
          isInstanceOf<IllegalArgumentException>()
          hasMessage("lightPosition.$axis must be finite")
        }
      }
    }
  }

  @Test
  fun lightPosition_acceptsFiniteOutsideBiasesAndArbitraryAlignment() {
    val relative = BiasAlignment(2.5f, -3.5f)
    val absolute = BiasAbsoluteAlignment(-4.5f, 5.5f)
    val custom = object : Alignment {
      override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset =
        IntOffset(7, 9)
    }

    assertThat(resolveGlassStyleValues(GlassStyle, GlassStyle { lightPosition(relative) }).lightPosition)
      .isSameInstanceAs(relative)
    assertThat(resolveGlassStyleValues(GlassStyle, GlassStyle { lightPosition(absolute) }).lightPosition)
      .isSameInstanceAs(absolute)
    assertThat(resolveGlassStyleValues(GlassStyle, GlassStyle { lightPosition(custom) }).lightPosition)
      .isSameInstanceAs(custom)
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

private class InvalidFloatWrite(
  val property: String,
  val domain: String,
  val invalidValues: List<Float>,
  val write: GlassStyleScope.(Float) -> Unit,
)

private fun assertFixedAndDirectOpticsFailure(
  message: String,
  fixed: () -> Unit,
  direct: GlassStyleScope.() -> Unit,
) {
  listOf(fixed, { GlassStyle(direct) }).forEach { invalidWrite ->
    assertFailure { invalidWrite() }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(message)
  }
}

private fun assertInvalidFloatWrites(vararg cases: InvalidFloatWrite) {
  cases.forEach { case ->
    case.invalidValues.forEach { invalid ->
      assertFailure { GlassStyle { case.write(this, invalid) } }.apply {
        isInstanceOf<IllegalArgumentException>()
        hasMessage("${case.property} must be ${case.domain}")
      }
    }
  }
}

private fun invalidUnitInterval(
  property: String,
  write: GlassStyleScope.(Float) -> Unit,
): InvalidFloatWrite = InvalidFloatWrite(
  property,
  "finite and in 0f..1f",
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 1.1f, Float.POSITIVE_INFINITY),
  write,
)

private fun invalidSignedUnitInterval(
  property: String,
  write: GlassStyleScope.(Float) -> Unit,
): InvalidFloatWrite = InvalidFloatWrite(
  property,
  "finite and in -1f..1f",
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -1.1f, 1.1f, Float.POSITIVE_INFINITY),
  write,
)

private fun invalidDoubleInterval(
  property: String,
  write: GlassStyleScope.(Float) -> Unit,
): InvalidFloatWrite = InvalidFloatWrite(
  property,
  "finite and in 0f..2f",
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 2.1f, Float.POSITIVE_INFINITY),
  write,
)

private fun invalidNonNegative(
  property: String,
  write: GlassStyleScope.(Float) -> Unit,
): InvalidFloatWrite = InvalidFloatWrite(
  property,
  "finite and non-negative",
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, Float.POSITIVE_INFINITY),
  write,
)

private fun invalidPositiveAtMostOne(
  property: String,
  write: GlassStyleScope.(Float) -> Unit,
): InvalidFloatWrite = InvalidFloatWrite(
  property,
  "finite and in 0f < value <= 1f",
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 0f, 1.1f, Float.POSITIVE_INFINITY),
  write,
)

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
