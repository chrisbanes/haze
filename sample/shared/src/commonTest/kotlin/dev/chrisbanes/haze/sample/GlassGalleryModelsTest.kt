// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import kotlin.test.Test

class GlassGalleryModelsTest {
  @Test
  fun regularBuiltInStyle_usesAdaptiveOptics() {
    assertThat(GlassLabState().styleValues.optics)
      .isEqualTo(GlassDefaults.optics)
  }

  @Test
  fun clearBuiltInStyle_usesVisibleFixedOptics() {
    val optics = GlassLabState(styleId = GlassLabStyleId.Clear).styleValues.optics

    assertThat(optics).isEqualTo(
      GlassOptics(
        refractionStrength = 0.85f,
        refractionHeightFraction = 0.22f,
        refractionDisplacement = 18.dp,
        depth = GlassOptics.SizeValue.Fixed(0.1f),
        blurRadius = GlassOptics.SizeValue.Fixed(2.dp),
      ),
    )
  }

  @Test
  fun editingBuiltInStyle_changesSelectionToCustomAndLiteralOptics() {
    val edited = GlassLabState().editStyle { values ->
      values.copy(optics = GlassOptics(refractionStrength = 0.6f))
    }

    assertThat(edited.styleId).isEqualTo(GlassLabStyleId.Custom)
    assertThat(edited.styleValues.optics).isInstanceOf<GlassOptics>()
  }

  @Test
  fun editingRegularMaterialResponse_preservesAdaptiveOpticsUntilOpticsAreEdited() {
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
