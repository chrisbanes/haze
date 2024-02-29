// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun poppinsFontFamily(): FontFamily {
  return FontFamily(
    Font(FontResource("font/poppins_regular.ttf")),
  )
}
