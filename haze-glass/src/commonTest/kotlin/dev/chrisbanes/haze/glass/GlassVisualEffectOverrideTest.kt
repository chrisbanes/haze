// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test

class GlassVisualEffectOverrideTest {

  private val localSurfaceProfile = SurfaceProfile.Concave
  private val styleSurfaceProfile = SurfaceProfile.Squircle
  private val directSurfaceProfile = SurfaceProfile.Circle

  private val localChromaticAberrationMode = ChromaticAberrationMode.Full
  private val styleChromaticAberrationMode = ChromaticAberrationMode.Simple
  private val directChromaticAberrationMode = ChromaticAberrationMode.Full

  private val localShape = RoundedCornerShape(8.dp)
  private val styleShape = RoundedCornerShape(16.dp)
  private val directShape = RoundedCornerShape(24.dp)

  @Test
  fun objectOverrides_clearDirectValuesRestoreInheritedValues() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        shape = localShape,
        rendering = GlassRendering(
          surfaceProfile = localSurfaceProfile,
          chromaticAberrationMode = localChromaticAberrationMode,
        ),
      )
      style = GlassStyle(
        shape = styleShape,
        rendering = GlassRendering(
          surfaceProfile = styleSurfaceProfile,
          chromaticAberrationMode = styleChromaticAberrationMode,
        ),
      )
      surfaceProfile = directSurfaceProfile
      chromaticAberrationMode = directChromaticAberrationMode
      shape = directShape
    }

    assertThat(effect.surfaceProfile).isEqualTo(directSurfaceProfile)
    assertThat(effect.chromaticAberrationMode).isEqualTo(directChromaticAberrationMode)
    assertThat(effect.shape).isEqualTo(directShape)

    effect.clearSurfaceProfileOverride()
    effect.clearChromaticAberrationModeOverride()
    effect.clearShapeOverride()

    assertThat(effect.surfaceProfile).isEqualTo(styleSurfaceProfile)
    assertThat(effect.chromaticAberrationMode).isEqualTo(styleChromaticAberrationMode)
    assertThat(effect.shape).isEqualTo(styleShape)
  }

  @Test
  fun objectOverrides_clearDirectValuesRestoreLocalValuesWhenStyleUnspecified() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        shape = localShape,
        rendering = GlassRendering(
          surfaceProfile = localSurfaceProfile,
          chromaticAberrationMode = localChromaticAberrationMode,
        ),
      )
      surfaceProfile = directSurfaceProfile
      chromaticAberrationMode = styleChromaticAberrationMode
      shape = directShape
    }

    effect.clearSurfaceProfileOverride()
    effect.clearChromaticAberrationModeOverride()
    effect.clearShapeOverride()

    assertThat(effect.surfaceProfile).isEqualTo(localSurfaceProfile)
    assertThat(effect.chromaticAberrationMode).isEqualTo(localChromaticAberrationMode)
    assertThat(effect.shape).isEqualTo(localShape)
  }

  @Test
  fun objectOverrides_copyConstructorPreservesInheritedValues() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        shape = localShape,
        rendering = GlassRendering(
          surfaceProfile = localSurfaceProfile,
          chromaticAberrationMode = localChromaticAberrationMode,
        ),
      )
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.surfaceProfile).isEqualTo(localSurfaceProfile)
    assertThat(copy.chromaticAberrationMode).isEqualTo(localChromaticAberrationMode)
    assertThat(copy.shape).isEqualTo(localShape)

    copy.compositionLocalStyle = GlassStyle(
      shape = styleShape,
      rendering = GlassRendering(
        surfaceProfile = styleSurfaceProfile,
        chromaticAberrationMode = styleChromaticAberrationMode,
      ),
    )

    assertThat(copy.surfaceProfile).isEqualTo(styleSurfaceProfile)
    assertThat(copy.chromaticAberrationMode).isEqualTo(styleChromaticAberrationMode)
    assertThat(copy.shape).isEqualTo(styleShape)
  }

  @Test
  fun objectOverrides_copyConstructorPreservesDirectOverrides() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        shape = localShape,
        rendering = GlassRendering(
          surfaceProfile = localSurfaceProfile,
          chromaticAberrationMode = localChromaticAberrationMode,
        ),
      )
      surfaceProfile = directSurfaceProfile
      chromaticAberrationMode = directChromaticAberrationMode
      shape = directShape
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.surfaceProfile).isEqualTo(directSurfaceProfile)
    assertThat(copy.chromaticAberrationMode).isEqualTo(directChromaticAberrationMode)
    assertThat(copy.shape).isEqualTo(directShape)

    copy.compositionLocalStyle = GlassStyle(
      shape = styleShape,
      rendering = GlassRendering(
        surfaceProfile = styleSurfaceProfile,
        chromaticAberrationMode = styleChromaticAberrationMode,
      ),
    )

    assertThat(copy.surfaceProfile).isEqualTo(directSurfaceProfile)
    assertThat(copy.chromaticAberrationMode).isEqualTo(directChromaticAberrationMode)
    assertThat(copy.shape).isEqualTo(directShape)
  }

  @Test
  fun objectOverrides_clearDirectValuesMarkDirtyFields() {
    val effect = GlassVisualEffect().apply {
      surfaceProfile = directSurfaceProfile
      chromaticAberrationMode = directChromaticAberrationMode
      shape = directShape
    }

    effect.clearSurfaceProfileOverride()
    effect.clearChromaticAberrationModeOverride()
    effect.clearShapeOverride()

    val dirtyFields = GlassDirtyFields.stringify(effect.dirtyTracker)
    assertThat(dirtyFields).contains("SurfaceProfile")
    assertThat(dirtyFields).contains("ChromaticAberrationMode")
    assertThat(dirtyFields).contains("Shape")
  }

  @Test
  fun opticsOverride_copyAndClearPreserveExpectedSource() {
    val absolute = GlassOptics.Absolute(refractionStrength = 0.4f)
    val original = GlassVisualEffect().apply { optics = absolute }
    val copy = GlassVisualEffect(original)

    assertThat(copy.optics).isEqualTo(absolute)

    copy.clearOpticsOverride()

    assertThat(copy.optics).isEqualTo(GlassDefaults.optics)
    assertThat(GlassDirtyFields.stringify(copy.dirtyTracker)).contains("Optics")
  }

  @Test
  fun copyConstructor_copiesInteractionConfiguration() {
    val source = MutableInteractionSource()
    val positionSpec = tween<Offset>()
    val effect = GlassVisualEffect().apply {
      hovered()
      focused { lightingIntensity(0.3f) }
      pressed { scale(0.97f) }
      interactionSource = source
      interactionLightRadiusFraction = 1.2f
      interactionTransformTarget = GlassTransformTarget.MaterialAndContent
      interactionTransformPivot = GlassTransformPivot.Center
      interactionPositionAnimationSpec = positionSpec
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.hoveredSlot).isEqualTo(effect.hoveredSlot)
    assertThat(copy.focusedSlot).isEqualTo(effect.focusedSlot)
    assertThat(copy.pressedSlot).isEqualTo(effect.pressedSlot)
    assertThat(copy.interactionSource).isEqualTo(source)
    assertThat(copy.interactionLightRadiusFraction).isEqualTo(1.2f)
    assertThat(copy.interactionTransformTarget).isEqualTo(GlassTransformTarget.MaterialAndContent)
    assertThat(copy.interactionTransformPivot).isEqualTo(GlassTransformPivot.Center)
    assertThat(copy.interactionPositionAnimationSpec).isEqualTo(positionSpec)
    assertThat(copy.interactionReducedMotionPolicy).isEqualTo(GlassReducedMotionPolicy.Reduced)
  }

  @Test
  fun primitiveOverrides_replayingUnspecifiedValuesDoesNotMarkDirty() {
    scalarOverrideCases.forEach { case ->
      val effect = GlassVisualEffect()
      effect.resetDirtyTracker()

      case.set(effect, Float.NaN)

      assertThat(
        GlassDirtyFields.stringify(effect.dirtyTracker),
        case.name,
      ).isEqualTo("[]")
    }
  }

  @Test
  fun primitiveOverrides_equivalentNormalizedValuesDoNotMarkDirty() {
    scalarOverrideCases.forEach { case ->
      val effect = GlassVisualEffect()
      case.set(effect, case.firstEquivalentInput)
      effect.resetDirtyTracker()

      case.set(effect, case.secondEquivalentInput)

      assertThat(
        GlassDirtyFields.stringify(effect.dirtyTracker),
        case.name,
      ).isEqualTo("[]")
    }
  }

  @Test
  fun primitiveOverrides_equivalentNormalizedStyleValuesDirtyOnlyStyle() {
    scalarOverrideCases.forEach { case ->
      val effect = GlassVisualEffect().apply {
        style = case.style(case.firstEquivalentInput)
      }
      effect.resetDirtyTracker()

      effect.style = case.style(case.secondEquivalentInput)

      assertThat(
        GlassDirtyFields.stringify(effect.dirtyTracker),
        case.name,
      ).isEqualTo("[Style]")
    }
  }

  @Test
  fun primitiveOverrides_equivalentNormalizedCompositionLocalValuesDoNotMarkDirty() {
    scalarOverrideCases.forEach { case ->
      val effect = GlassVisualEffect().apply {
        compositionLocalStyle = case.style(case.firstEquivalentInput)
      }
      effect.resetDirtyTracker()

      effect.compositionLocalStyle = case.style(case.secondEquivalentInput)

      assertThat(
        GlassDirtyFields.stringify(effect.dirtyTracker),
        case.name,
      ).isEqualTo("[]")
    }
  }

  @Test
  fun primitiveOverrides_copyConstructorPreservesInheritedSources() {
    scalarOverrideCases.forEach { case ->
      val original = GlassVisualEffect().apply {
        style = case.style(0.2f)
      }
      val copy = GlassVisualEffect(original)

      copy.style = case.style(0.8f)

      assertThat(case.get(copy), case.name).isEqualTo(0.8f)
    }
  }

  @Test
  fun primitiveOverrides_copyConstructorPreservesCompositionLocalSources() {
    scalarOverrideCases.forEach { case ->
      val original = GlassVisualEffect().apply {
        compositionLocalStyle = case.style(0.2f)
      }
      val copy = GlassVisualEffect(original)

      copy.compositionLocalStyle = case.style(0.8f)

      assertThat(case.get(copy), case.name).isEqualTo(0.8f)
    }
  }

  @Test
  fun primitiveOverrides_copyConstructorPreservesDirectOverrides() {
    scalarOverrideCases.forEach { case ->
      val original = GlassVisualEffect().apply {
        style = case.style(0.2f)
      }
      case.set(original, 0.4f)
      val copy = GlassVisualEffect(original)

      copy.style = case.style(0.8f)

      assertThat(case.get(copy), case.name).isEqualTo(0.4f)
    }
  }

  @Test
  fun valueOverrides_copyConstructorPreservesInheritedSources() {
    val original = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        tint = Color.Red,
        lighting = GlassLighting(lightPosition = Offset(1f, 2f)),
        rendering = GlassRendering(edgeSoftness = 4.dp),
      )
    }
    val copy = GlassVisualEffect(original)

    copy.compositionLocalStyle = GlassStyle(
      tint = Color.Blue,
      lighting = GlassLighting(lightPosition = Offset(3f, 4f)),
      rendering = GlassRendering(edgeSoftness = 8.dp),
    )

    assertThat(copy.tint).isEqualTo(Color.Blue)
    assertThat(copy.lightPosition).isEqualTo(Offset(3f, 4f))
    assertThat(copy.edgeSoftness).isEqualTo(8.dp)
  }

  @Test
  fun valueOverrides_copyConstructorPreservesStyleSources() {
    val original = GlassVisualEffect().apply {
      style = GlassStyle(
        tint = Color.Red,
        lighting = GlassLighting(lightPosition = Offset(1f, 2f)),
        rendering = GlassRendering(edgeSoftness = 4.dp),
      )
    }
    val copy = GlassVisualEffect(original)

    copy.style = GlassStyle(
      tint = Color.Blue,
      lighting = GlassLighting(lightPosition = Offset(3f, 4f)),
      rendering = GlassRendering(edgeSoftness = 8.dp),
    )

    assertThat(copy.tint).isEqualTo(Color.Blue)
    assertThat(copy.lightPosition).isEqualTo(Offset(3f, 4f))
    assertThat(copy.edgeSoftness).isEqualTo(8.dp)
  }

  @Test
  fun valueOverrides_copyConstructorPreservesDirectOverrides() {
    val original = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        tint = Color.Red,
        lighting = GlassLighting(lightPosition = Offset(1f, 2f)),
        rendering = GlassRendering(edgeSoftness = 4.dp),
      )
      tint = Color.Green
      lightPosition = Offset(5f, 6f)
      edgeSoftness = 12.dp
    }
    val copy = GlassVisualEffect(original)

    copy.compositionLocalStyle = GlassStyle(
      tint = Color.Blue,
      lighting = GlassLighting(lightPosition = Offset(3f, 4f)),
      rendering = GlassRendering(edgeSoftness = 8.dp),
    )

    assertThat(copy.tint).isEqualTo(Color.Green)
    assertThat(copy.lightPosition).isEqualTo(Offset(5f, 6f))
    assertThat(copy.edgeSoftness).isEqualTo(12.dp)
  }
}

private data class ScalarOverrideCase(
  val name: String,
  val set: (GlassVisualEffect, Float) -> Unit,
  val get: (GlassVisualEffect) -> Float,
  val style: (Float) -> GlassStyle,
  val firstEquivalentInput: Float = 2f,
  val secondEquivalentInput: Float = 3f,
)

private val scalarOverrideCases = listOf(
  ScalarOverrideCase(
    name = "specularIntensity",
    set = { effect, value -> effect.specularIntensity = value },
    get = { it.specularIntensity },
    style = { GlassStyle(lighting = GlassLighting(specularIntensity = it)) },
  ),
  ScalarOverrideCase(
    name = "ambientResponse",
    set = { effect, value -> effect.ambientResponse = value },
    get = { it.ambientResponse },
    style = { GlassStyle(lighting = GlassLighting(ambientResponse = it)) },
  ),
  ScalarOverrideCase(
    name = "chromaticAberrationStrength",
    set = { effect, value -> effect.chromaticAberrationStrength = value },
    get = { it.chromaticAberrationStrength },
    style = {
      GlassStyle(rendering = GlassRendering(chromaticAberrationStrength = it))
    },
  ),
  ScalarOverrideCase(
    name = "alpha",
    set = { effect, value -> effect.alpha = value },
    get = { it.alpha },
    style = { GlassStyle(color = GlassColor(alpha = it)) },
  ),
  ScalarOverrideCase(
    name = "contrast",
    set = { effect, value -> effect.contrast = value },
    get = { it.contrast },
    style = { GlassStyle(color = GlassColor(contrast = it)) },
  ),
  ScalarOverrideCase(
    name = "whitePoint",
    set = { effect, value -> effect.whitePoint = value },
    get = { it.whitePoint },
    style = { GlassStyle(color = GlassColor(whitePoint = it)) },
  ),
  ScalarOverrideCase(
    name = "chromaMultiplier",
    set = { effect, value -> effect.chromaMultiplier = value },
    get = { it.chromaMultiplier },
    style = { GlassStyle(color = GlassColor(chromaMultiplier = it)) },
    firstEquivalentInput = 3f,
    secondEquivalentInput = 4f,
  ),
  ScalarOverrideCase(
    name = "contentNormalBlend",
    set = { effect, value -> effect.contentNormalBlend = value },
    get = { it.contentNormalBlend },
    style = { GlassStyle(rendering = GlassRendering(contentNormalBlend = it)) },
  ),
  ScalarOverrideCase(
    name = "specularExponent",
    set = { effect, value -> effect.specularExponent = value },
    get = { it.specularExponent },
    style = { GlassStyle(lighting = GlassLighting(specularExponent = it)) },
    firstEquivalentInput = -1f,
    secondEquivalentInput = -2f,
  ),
  ScalarOverrideCase(
    name = "fresnelExponent",
    set = { effect, value -> effect.fresnelExponent = value },
    get = { it.fresnelExponent },
    style = { GlassStyle(lighting = GlassLighting(fresnelExponent = it)) },
    firstEquivalentInput = -1f,
    secondEquivalentInput = -2f,
  ),
)
