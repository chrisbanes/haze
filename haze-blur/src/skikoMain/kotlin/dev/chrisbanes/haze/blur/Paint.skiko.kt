// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint

internal actual fun Paint.reset() {
  skiaPaint.reset()
}
