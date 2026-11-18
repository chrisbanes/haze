// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Removes the built-in edge outline and rim highlight, for tests of geometry or pixels unrelated
 * to them.
 */
internal fun GlassStyleScope.noBuiltInEdges() {
  edgeOutline(Color.Transparent, 0.dp)
  rimHighlight(0f, 0.dp, 0f)
}
