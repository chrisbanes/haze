// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
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
  fun presets_withEqualInputsProduceEqualStyles() = runComposeUiTest {
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

      assertThat(recomposed, name).isEqualTo(initial)
      assertThat(recomposed.hashCode(), name).isEqualTo(initial.hashCode())
    }
  }

  @Test
  fun presets_areDistinctFromEachOther() = runComposeUiTest {
    val styles = mutableListOf<HazeBlurStyle>()
    setContent { presets.values.forEach { styles += it() } }
    waitForIdle()

    assertThat(styles.toSet().size).isEqualTo(presets.size)
  }

  @Test
  fun preset_producesUnequalStyleWhenItsInputChanges() = runComposeUiTest {
    val surface = mutableStateOf(Color.White)
    val seen = mutableListOf<HazeBlurStyle>()

    setContent {
      MaterialTheme(colorScheme = lightColorScheme(surface = surface.value)) {
        val style = HazeMaterials.regular()
        SideEffect { seen += style }
      }
    }
    surface.value = Color.Black
    waitForIdle()

    assertThat(seen.first()).isNotEqualTo(seen.last())
  }

  @Test
  fun presets_areCallableFromReadOnlyComposables() = runComposeUiTest {
    lateinit var styles: List<HazeBlurStyle>
    setContent { styles = readOnlyPresets() }
    waitForIdle()

    assertThat(styles.size).isEqualTo(presets.size)
  }
}

@Composable
@ReadOnlyComposable
private fun readOnlyPresets(): List<HazeBlurStyle> = listOf(
  HazeMaterials.ultraThin(),
  HazeMaterials.thin(),
  HazeMaterials.regular(),
  HazeMaterials.thick(),
  HazeMaterials.ultraThick(),
  CupertinoMaterials.ultraThin(),
  CupertinoMaterials.thin(),
  CupertinoMaterials.regular(),
  CupertinoMaterials.thick(),
  FluentMaterials.thinAcrylic(),
  FluentMaterials.accentAcrylicBase(),
  FluentMaterials.accentAcrylicDefault(),
  FluentMaterials.acrylicBase(),
  FluentMaterials.acrylicDefault(),
  FluentMaterials.mica(),
  FluentMaterials.micaAlt(),
)
