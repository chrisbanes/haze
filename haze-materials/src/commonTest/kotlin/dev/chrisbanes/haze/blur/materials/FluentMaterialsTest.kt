// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class FluentMaterialsTest {

  @Test
  fun acrylicBaseUsesOpaqueContainerAndFallbackColors() {
    val lightStyle = FluentMaterials.acrylicBaseStyle(isDark = false)
    val darkStyle = FluentMaterials.acrylicBaseStyle(isDark = true)

    assertThat(lightStyle.backgroundColor.alpha).isEqualTo(1f)
    assertThat(darkStyle.backgroundColor.alpha).isEqualTo(1f)
    assertThat(lightStyle.fallbackColorEffect.color.alpha).isEqualTo(1f)
    assertThat(darkStyle.fallbackColorEffect.color.alpha).isEqualTo(1f)
  }
}

private val dev.chrisbanes.haze.blur.HazeColorEffect.color: Color
  get() = (this as dev.chrisbanes.haze.blur.HazeColorEffect.TintColor).color
