// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.lang.reflect.Modifier
import kotlin.test.Test

/**
 * Prepared-render reuse compares inputs field by field so animations skip rebuilding keys.
 * A field added to one of these classes without updating its comparison silently reuses a stale
 * key, so these counts fail first.
 */
class GlassReuseComparisonFieldsTest {
  @Test
  fun effectKeyFields_matchGlassRenderParamsReuseComparisons() {
    // Update hasSame*EffectInputs in GlassRenderParams.kt before changing these counts.
    assertThat(fieldCount<GlassBlurEffectKey>()).isEqualTo(7)
    assertThat(fieldCount<GlassOpticalEffectKey>()).isEqualTo(20)
    // coverageOutset is compared through coordinates in hasSameRefractionDetailEffectInputs (#1775).
    assertThat(fieldCount<GlassRefractionDetailEffectKey>()).isEqualTo(17)
    assertThat(fieldCount<GlassRimEffectKey>()).isEqualTo(11)
  }

  @Test
  fun resolvedStyleFields_matchGlassRuntimeEffectReuseComparison() {
    // Audit ResolvedGlassStyle.hasSameRenderParams in GlassRuntimeEffect.kt before changing this.
    // fallbackBaseColor and fallbackColor are deliberately excluded: fallback-only, never a GlassRenderParams input.
    // drawOutsetPx is compared in hasSameBudgetParams: it sizes the layer, clip and group composite.
    // edgeOutline is compared in hasSameRenderParams; its visibility also in hasSameBudgetParams.
    assertThat(fieldCount<ResolvedGlassStyle>()).isEqualTo(25)
  }

  private inline fun <reified T> fieldCount(): Int =
    T::class.java.declaredFields.count { !Modifier.isStatic(it.modifiers) }
}
