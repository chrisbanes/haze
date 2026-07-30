// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class FluentMaterialsTest {

  @Test
  fun acrylicBaseUsesOpaqueContainerAndFallbackColors() {
    val (lightContainer, lightFallback) = FluentMaterials.acrylicBaseColors(isDark = false)
    val (darkContainer, darkFallback) = FluentMaterials.acrylicBaseColors(isDark = true)

    assertThat(lightContainer.alpha).isEqualTo(1f)
    assertThat(lightFallback.alpha).isEqualTo(1f)
    assertThat(darkContainer.alpha).isEqualTo(1f)
    assertThat(darkFallback.alpha).isEqualTo(1f)
  }
}
