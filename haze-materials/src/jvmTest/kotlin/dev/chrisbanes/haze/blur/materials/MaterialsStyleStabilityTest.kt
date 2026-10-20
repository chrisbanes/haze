// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.blur.HazeBlurStyle
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MaterialsStyleStabilityTest {

  private val presets: Map<String, @Composable () -> HazeBlurStyle> = mapOf(
    "HazeMaterials.ultraThin" to { HazeMaterials.ultraThin() },
    "HazeMaterials.thin" to { HazeMaterials.thin() },
    "HazeMaterials.regular" to { HazeMaterials.regular() },
    "HazeMaterials.thick" to { HazeMaterials.thick() },
    "HazeMaterials.ultraThick" to { HazeMaterials.ultraThick() },
    "CupertinoMaterials.ultraThin" to { CupertinoMaterials.ultraThin() },
    "CupertinoMaterials.thin" to { CupertinoMaterials.thin() },
    "CupertinoMaterials.regular" to { CupertinoMaterials.regular() },
    "CupertinoMaterials.thick" to { CupertinoMaterials.thick() },
    "FluentMaterials.thinAcrylic" to { FluentMaterials.thinAcrylic() },
    "FluentMaterials.accentAcrylicBase" to { FluentMaterials.accentAcrylicBase() },
    "FluentMaterials.accentAcrylicDefault" to { FluentMaterials.accentAcrylicDefault() },
    "FluentMaterials.acrylicBase" to { FluentMaterials.acrylicBase() },
    "FluentMaterials.acrylicDefault" to { FluentMaterials.acrylicDefault() },
    "FluentMaterials.mica" to { FluentMaterials.mica() },
    "FluentMaterials.micaAlt" to { FluentMaterials.micaAlt() },
  )

  @Test
  fun presets_reuseTheSameStyleAcrossUnrelatedRecompositions() = runComposeUiTest {
    for ((name, preset) in presets) {
      val tick = mutableStateOf(false)
      lateinit var initial: HazeBlurStyle
      lateinit var recomposed: HazeBlurStyle

      setContent {
        val style = preset()
        val ticked = tick.value
        SideEffect { if (ticked) recomposed = style else initial = style }
      }
      tick.value = true
      waitForIdle()

      assertThat(recomposed, name).isSameInstanceAs(initial)
    }
  }

  @Test
  fun preset_producesNewStyleWhenItsInputChanges() = runComposeUiTest {
    val surface = mutableStateOf(Color.White)
    lateinit var latest: HazeBlurStyle
    val seen = mutableListOf<HazeBlurStyle>()

    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = surface.value)) {
        val style = HazeMaterials.regular()
        SideEffect {
          latest = style
          seen += style
        }
      }
    }
    surface.value = Color.Black
    waitForIdle()

    assertThat(seen.first()).isNotSameInstanceAs(latest)
  }
}
