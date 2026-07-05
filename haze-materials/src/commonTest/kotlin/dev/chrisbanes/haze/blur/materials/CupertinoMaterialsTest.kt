// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
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
}
