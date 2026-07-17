// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect

internal fun GlassVisualEffect.updateAbsoluteOptics(
  transform: GlassOptics.Absolute.() -> GlassOptics.Absolute,
) {
  optics = (optics as GlassOptics.Absolute).transform()
}
