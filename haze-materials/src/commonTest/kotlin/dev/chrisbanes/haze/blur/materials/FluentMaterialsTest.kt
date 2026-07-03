// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FluentMaterialsTest {

  @Test
  fun acrylicBaseUsesOpaqueContainerAndFallbackColors() = runComposeUiTest {
    lateinit var lightStyle: dev.chrisbanes.haze.blur.HazeBlurStyle
    lateinit var darkStyle: dev.chrisbanes.haze.blur.HazeBlurStyle

    setContent {
      lightStyle = FluentMaterials.acrylicBase(isDark = false)
      darkStyle = FluentMaterials.acrylicBase(isDark = true)
    }

    waitForIdle()

    assertThat(lightStyle.backgroundColor.alpha).isEqualTo(1f)
    assertThat(darkStyle.backgroundColor.alpha).isEqualTo(1f)
    assertThat(lightStyle.fallbackColorEffect.color.alpha).isEqualTo(1f)
    assertThat(darkStyle.fallbackColorEffect.color.alpha).isEqualTo(1f)
  }
}

private val dev.chrisbanes.haze.blur.HazeColorEffect.color: Color
  get() = (this as dev.chrisbanes.haze.blur.HazeColorEffect.TintColor).color
