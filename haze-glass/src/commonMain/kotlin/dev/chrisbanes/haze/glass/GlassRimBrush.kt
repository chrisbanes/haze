// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope

internal fun interface GlassRimBrushProvider {
  operator fun invoke(key: GlassRimEffectKey): Brush?
}

internal expect fun createGlassRimBrushProvider(): GlassRimBrushProvider?

internal expect fun DrawScope.supportsGlassRimBrush(): Boolean

internal fun DrawScope.drawGlassRimWithBrush(brush: Brush): Boolean {
  if (!supportsGlassRimBrush()) return false
  // Only this optional built-in brush draw is guarded; caller content is drawn separately.
  return runCatchingGlassRim { drawRect(brush) }.isSuccess
}

/** Keep optional rim fallback limited to runtime exceptions; propagate other failures. */
internal inline fun <T> runCatchingGlassRim(block: () -> T): Result<T> =
  runCatching(block).onFailure { if (it !is RuntimeException) throw it }
