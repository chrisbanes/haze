// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import haze_root.internal.screenshot_test.generated.resources.Res
import haze_root.internal.screenshot_test.generated.resources.poppins_regular
import org.jetbrains.compose.resources.Font

internal expect fun useCustomTypography(): Boolean

@Composable
fun ScreenshotTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    typography = when {
      useCustomTypography() -> Typography
      else -> MaterialTheme.typography
    },
  ) {
    content()
  }
}

val FontFamily.Companion.Poppins: FontFamily
  @Composable get() = FontFamily(Font(Res.font.poppins_regular))

private val Typography: Typography
  @Composable get() {
    // Eugh, this is gross but there is no defaultFontFamily property in M3
    val default = Typography()
    val fontFamily = FontFamily.Poppins
    return Typography(
      displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
      displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
      displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
      headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
      headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
      headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
      titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
      titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
      titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
      bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
      bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
      bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
      labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
      labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
      labelSmall = default.labelSmall.copy(fontFamily = fontFamily),
    )
  }
