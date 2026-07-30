// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

internal fun GlassRuntimeEffect.testHoverResponse() {
  hovered { testHoverOrFocusResponse() }
}

internal fun GlassRuntimeEffect.testFocusResponse() {
  focused { testHoverOrFocusResponse() }
}

private fun GlassInteractionScope.testHoverOrFocusResponse() {
  animate(
    toSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
    fromSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
  ) {
    lightingIntensity(0.35f)
    refractionMultiplier(1.02f)
    whitePointDelta(0.01f)
    scale(1f)
  }
}

internal fun GlassRuntimeEffect.testPressResponse() {
  pressed {
    animate(
      toSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
      fromSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
    ) {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
      scale(0.98f)
    }
  }
}
