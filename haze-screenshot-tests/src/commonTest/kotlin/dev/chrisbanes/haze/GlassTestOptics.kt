// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.glass.GlassOptics

internal fun GlassTestConfiguration.updateFixedOptics(
  transform: GlassOptics.Fixed.() -> GlassOptics.Fixed,
) {
  optics = (optics as GlassOptics.Fixed).transform()
}
