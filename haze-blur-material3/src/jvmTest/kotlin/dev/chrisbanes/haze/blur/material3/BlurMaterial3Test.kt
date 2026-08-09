// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BlurMaterial3Test {

  @Test
  fun material3_capturesThemeSurfaceAndRecomposesWithReplacementStyle() = runComposeUiTest {
    val colorScheme = mutableStateOf(lightColorScheme(surface = Color.Red))

    setContent {
      MaterialTheme(colorScheme = colorScheme.value) {
        BlurMaterial3Content(HazeBlurStyle.Material3())
      }
    }

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Red)

    colorScheme.value = lightColorScheme(surface = Color.Blue)
    waitForIdle()

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Blue)
  }

  @Test
  fun material3_withoutBlockReusesStyleAcrossUnrelatedRecompositions() = runComposeUiTest {
    val recomposed = mutableStateOf(false)
    lateinit var initialStyle: HazeBlurStyle
    lateinit var recomposedStyle: HazeBlurStyle

    setContent {
      val style = HazeBlurStyle.Material3()
      val isRecomposed = recomposed.value
      SideEffect {
        if (isRecomposed) recomposedStyle = style else initialStyle = style
      }
      BlurMaterial3Content(style)
    }

    recomposed.value = true
    waitForIdle()

    assertThat(recomposedStyle).isSameInstanceAs(initialStyle)
  }

  @Test
  fun material3_explicitAndBlockBackgroundsOverrideThemeSurface() = runComposeUiTest {
    val blockOverridesBackground = mutableStateOf(false)

    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = Color.Blue)) {
        BlurMaterial3Content(
          HazeBlurStyle.Material3(containerColor = Color.Red) {
            if (blockOverridesBackground.value) backgroundColor(Color.Green)
          },
        )
      }
    }

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Red)

    blockOverridesBackground.value = true
    waitForIdle()

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Green)
  }

  @Test
  fun material3_preservesCompositionLocalColorEffects() = runComposeUiTest {
    val localStyle = HazeBlurStyle {
      colorEffects(listOf(HazeColorEffect.tint(Color.Blue)))
    }

    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = Color.Red)) {
        CompositionLocalProvider(LocalHazeBlurStyle provides localStyle) {
          BlurMaterial3Content(HazeBlurStyle.Material3())
        }
      }
    }

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Blue)
  }
}

@Composable
private fun BlurMaterial3Content(style: HazeBlurStyle) {
  Box(
    Modifier
      .size(40.dp)
      .testTag("material")
      .hazeBlur(
        input = HazeInput.Content,
        style = style,
      ),
  )
}
