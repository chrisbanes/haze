// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
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
}
