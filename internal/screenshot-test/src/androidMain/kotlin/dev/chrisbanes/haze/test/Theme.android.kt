// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.chrisbanes.haze.internal.screenshot.R

@Composable
internal actual fun poppinsFontFamily(): FontFamily {
  return FontFamily(
    Font(R.font.poppins_regular),
  )
}
