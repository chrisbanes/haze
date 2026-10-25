// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlin.test.Test

class CupertinoMaterialsTest {

  @Test
  fun cupertinoMaterialValues_useContainerColorAndLightEffects() {
    val containerColor = Color.White
    val lightBackgroundColor = Color(0xFF111111)
    val lightForegroundColor = Color(0xFF222222)

    val (backgroundColor, colorEffects) = cupertinoMaterialValues(
      containerColor = containerColor,
      isDark = false,
      lightBackgroundColor = lightBackgroundColor,
      lightForegroundColor = lightForegroundColor,
      darkBackgroundColor = Color(0xFF333333),
      darkForegroundColor = Color(0xFF444444),
    )

    assertThat(backgroundColor).isEqualTo(containerColor)
    assertThat(colorEffects).containsExactly(
      HazeColorEffect.tint(lightBackgroundColor, BlendMode.ColorDodge),
      HazeColorEffect.tint(lightForegroundColor),
    )
  }

  @Test
  fun cupertinoMaterialValues_useDarkEffects() {
    val darkBackgroundColor = Color(0xFF333333)
    val darkForegroundColor = Color(0xFF444444)

    val (_, colorEffects) = cupertinoMaterialValues(
      containerColor = Color.Black,
      isDark = true,
      lightBackgroundColor = Color(0xFF111111),
      lightForegroundColor = Color(0xFF222222),
      darkBackgroundColor = darkBackgroundColor,
      darkForegroundColor = darkForegroundColor,
    )

    assertThat(colorEffects).containsExactly(
      HazeColorEffect.tint(darkBackgroundColor, BlendMode.Overlay),
      HazeColorEffect.tint(darkForegroundColor),
    )
  }

  @Test
  fun cupertinoFallbackColorEffect_lightUsesForegroundOverContainer() {
    val containerColor = Color.White
    val lightForegroundColor = Color(0xFF222222).copy(alpha = 0.5f)

    val effect = cupertinoFallbackColorEffect(
      containerColor = containerColor,
      isDark = false,
      lightForegroundColor = lightForegroundColor,
      darkForegroundColor = Color(0xFF444444).copy(alpha = 0.5f),
    )

    assertThat(effect).isEqualTo(
      HazeColorEffect.tint(lightForegroundColor.compositeOver(containerColor)),
    )
  }

  @Test
  fun cupertinoFallbackColorEffect_darkUsesForegroundOverContainer() {
    val containerColor = Color.Black
    val darkForegroundColor = Color(0xFF444444).copy(alpha = 0.5f)

    val effect = cupertinoFallbackColorEffect(
      containerColor = containerColor,
      isDark = true,
      lightForegroundColor = Color(0xFF222222).copy(alpha = 0.5f),
      darkForegroundColor = darkForegroundColor,
    )

    assertThat(effect).isEqualTo(
      HazeColorEffect.tint(darkForegroundColor.compositeOver(containerColor)),
    )
  }

  @Test
  fun cupertinoFallbackColorEffect_isOpaqueEnoughToFrost() {
    assertThat(CupertinoPresets.all).hasSize(4)

    for (preset in CupertinoPresets.all) {
      for (isDark in listOf(false, true)) {
        val containerColor = if (isDark) Color.Black else Color.White
        val foreground = if (isDark) preset.darkForegroundColor else preset.lightForegroundColor
        val expected = foreground.compositeOver(containerColor)

        val effect = cupertinoFallbackColorEffect(
          containerColor = containerColor,
          isDark = isDark,
          lightForegroundColor = preset.lightForegroundColor,
          darkForegroundColor = preset.darkForegroundColor,
        )

        assertThat(effect, "${preset.name} isDark=$isDark").isEqualTo(HazeColorEffect.tint(expected))
        assertThat(effect, "${preset.name} isDark=$isDark")
          .isNotEqualTo(HazeColorEffect.tint(expected, BlendMode.ColorDodge))
        assertThat(effect, "${preset.name} isDark=$isDark")
          .isNotEqualTo(HazeColorEffect.tint(expected, BlendMode.Overlay))
      }
    }
  }
}
