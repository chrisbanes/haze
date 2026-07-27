// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlin.test.Test

class CupertinoMaterialsTest {

  @Test
  fun cupertinoMaterialStyleUsesContainerColorAsBackground() {
    val containerColor = Color(0xFF123456)

    val style = cupertinoMaterialStyle(
      containerColor = containerColor,
      lightBackgroundColor = Color(0xFF111111),
      lightForegroundColor = Color(0xFF222222),
      darkBackgroundColor = Color(0xFF333333),
      darkForegroundColor = Color(0xFF444444),
    )

    assertThat(style.backgroundColor).isEqualTo(containerColor)
  }

  @Test
  fun cupertinoMaterialStyle_usesLightEffectsForLightContainer() {
    val lightBackgroundColor = Color(0xFF111111)
    val lightForegroundColor = Color(0xFF222222)

    val style = cupertinoMaterialStyle(
      containerColor = Color.White,
      lightBackgroundColor = lightBackgroundColor,
      lightForegroundColor = lightForegroundColor,
      darkBackgroundColor = Color(0xFF333333),
      darkForegroundColor = Color(0xFF444444),
    )

    assertThat(style.colorEffects).isEqualTo(
      listOf(
        HazeColorEffect.tint(lightBackgroundColor, BlendMode.ColorDodge),
        HazeColorEffect.tint(lightForegroundColor),
      ),
    )
  }

  @Test
  fun cupertinoMaterialStyle_usesDarkEffectsForDarkContainer() {
    val darkBackgroundColor = Color(0xFF333333)
    val darkForegroundColor = Color(0xFF444444)

    val style = cupertinoMaterialStyle(
      containerColor = Color.Black,
      lightBackgroundColor = Color(0xFF111111),
      lightForegroundColor = Color(0xFF222222),
      darkBackgroundColor = darkBackgroundColor,
      darkForegroundColor = darkForegroundColor,
    )

    assertThat(style.colorEffects).isEqualTo(
      listOf(
        HazeColorEffect.tint(darkBackgroundColor, BlendMode.Overlay),
        HazeColorEffect.tint(darkForegroundColor),
      ),
    )
  }
}
