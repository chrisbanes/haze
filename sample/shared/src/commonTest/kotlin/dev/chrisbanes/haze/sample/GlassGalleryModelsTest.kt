// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import kotlin.test.Test

class GlassGalleryModelsTest {
  @Test
  fun regularBuiltInStyle_usesResponsiveOptics() {
    assertThat(GlassLabState().styleValues.optics)
      .isEqualTo(GlassDefaults.optics)
  }

  @Test
  fun clearBuiltInStyle_usesFixedShallowDiffusion() {
    val optics = GlassLabState(styleId = GlassLabStyleId.Clear).styleValues.optics

    assertThat(optics).isEqualTo(
      GlassOptics(
        refractionStrength = 0.85f,
        refractionHeightFraction = 0.35f,
        refractionDisplacement = 56.dp,
        refractionProfile = RefractionProfile.Edge(28.dp),
        depth = OpticalSizeValue.Fixed(1f),
        blurRadius = OpticalSizeValue.Fixed(1.25.dp),
      ),
    )
  }

  @Test
  fun editingBuiltInStyle_changesSelectionToCustom() {
    val edited = GlassLabState().editStyle { values ->
      values.copy(optics = GlassOptics(refractionStrength = 0.6f))
    }

    assertThat(edited.styleId).isEqualTo(GlassLabStyleId.Custom)
  }

  @Test
  fun editingRegularMaterialResponse_preservesResponsiveOpticsUntilOpticsAreEdited() {
    val edited = GlassLabState().editStyle { values ->
      values.copy(specularIntensity = 0.8f)
    }

    assertThat(edited.styleId).isEqualTo(GlassLabStyleId.Custom)
    assertThat(edited.styleValues.optics).isEqualTo(GlassDefaults.optics)
  }

  @Test
  fun interactionModes_produceDeclarativeStyles() {
    assertThat(GlassLabInteractionMode.Off.style).isEqualTo(GlassStyle)
    assertThat(GlassLabInteractionMode.Pressed.style).isNotEqualTo(GlassStyle)
    assertThat(GlassLabInteractionMode.All.style).isNotEqualTo(GlassStyle)
    assertThat(GlassLabInteractionMode.All.includesFocusedResponse).isTrue()
    assertThat(GlassLabInteractionMode.Pressed.includesFocusedResponse).isFalse()
  }

  @Test
  fun reset_restoresCompleteInitialLabState() {
    val changed = GlassLabState(
      styleId = GlassLabStyleId.Clear,
      backdrop = GlassGalleryBackdropId.Grid,
      interaction = GlassLabInteractionMode.Off,
      advancedExpanded = true,
      styleValues = GlassLabStyleValues(optics = GlassOptics()),
    )

    assertThat(changed.reset()).isEqualTo(GlassLabState())
  }
}
