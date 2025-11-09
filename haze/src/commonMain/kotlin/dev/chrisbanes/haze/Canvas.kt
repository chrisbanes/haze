// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.ContentDrawScope

internal fun ContentDrawScope.drawContentSafely() {
  try {
    drawContent()
  } catch (e: Exception) {
    val message = e.message.orEmpty()
    // Issues: 641 and 706
    if ("mViewFlags" in message || "LayoutNode" in message) {
      HazeLogger.d("ContentDrawScope", e) { "Error whilst drawing content" }
    } else {
      throw e
    }
  }
}
