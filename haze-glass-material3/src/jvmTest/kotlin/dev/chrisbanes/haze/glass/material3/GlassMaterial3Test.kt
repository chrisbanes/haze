// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.LocalGlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassMaterial3Test {

  @Test
  fun material3OnStyle_appliesSurfaceBeforeReceiverWrites() = runComposeUiTest {
    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = Color.Red)) {
        Column {
          GlassMaterial3Content(
            style = GlassStyle.regular.material3(),
            testTag = "surface",
          )
          GlassMaterial3Content(
            style = GlassStyle { backgroundColor(Color.Green) }.material3(),
            testTag = "background",
          )
          GlassMaterial3Content(
            style = GlassStyle { tint(Color.Blue) }.material3(),
            testTag = "tint",
          )
        }
      }
    }

    assertThat(onNodeWithTag("surface").captureToImage().toPixelMap()[20, 20])
      .isEqualTo(Color.Red)
    assertThat(onNodeWithTag("background").captureToImage().toPixelMap()[20, 20])
      .isEqualTo(Color.Green)
    assertThat(onNodeWithTag("tint").captureToImage().toPixelMap()[20, 20])
      .isEqualTo(Color.Blue)
  }

  @Test
  fun material3_capturesThemeSurfaceAndRecomposesWithReplacementStyle() = runComposeUiTest {
    val colorScheme = mutableStateOf(lightColorScheme(surface = Color.Red))

    setContent {
      MaterialTheme(colorScheme = colorScheme.value) {
        GlassMaterial3Content(GlassStyle.Material3())
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
    lateinit var initialStyle: GlassStyle
    lateinit var recomposedStyle: GlassStyle

    setContent {
      val style = GlassStyle.Material3()
      val isRecomposed = recomposed.value
      SideEffect {
        if (isRecomposed) recomposedStyle = style else initialStyle = style
      }
      GlassMaterial3Content(style)
    }

    recomposed.value = true
    waitForIdle()

    assertThat(recomposedStyle).isSameInstanceAs(initialStyle)
  }

  @Test
  fun material3_explicitAndBlockWritesOverrideEarlierWrites() = runComposeUiTest {
    val blockOverridesTint = mutableStateOf(false)

    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = Color.Green)) {
        GlassMaterial3Content(
          GlassStyle.Material3(containerColor = Color.Red, tint = Color.Blue) {
            if (blockOverridesTint.value) tint(Color.Yellow)
          },
        )
      }
    }

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Blue)

    blockOverridesTint.value = true
    waitForIdle()

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Yellow)
  }

  @Test
  fun material3_nullTintPreservesLocalTintWithoutReadingContentColor() = runComposeUiTest {
    val localStyle = GlassStyle { tint(Color.Blue) }

    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = Color.Red)) {
        CompositionLocalProvider(
          LocalGlassStyle provides localStyle,
          LocalContentColor provides Color.Green,
        ) {
          GlassMaterial3Content(GlassStyle.Material3(tint = null))
        }
      }
    }

    assertThat(onNodeWithTag("material").captureToImage().toPixelMap()[20, 20]).isEqualTo(Color.Blue)
  }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun GlassMaterial3Content(
  style: GlassStyle,
  testTag: String = "material",
) {
  Box(
    Modifier
      .size(40.dp)
      .testTag(testTag)
      .hazeGlass(
        input = HazeInput.Content,
        style = style,
      ),
  )
}
