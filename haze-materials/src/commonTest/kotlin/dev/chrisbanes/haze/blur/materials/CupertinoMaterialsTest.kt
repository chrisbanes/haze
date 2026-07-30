// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
}
