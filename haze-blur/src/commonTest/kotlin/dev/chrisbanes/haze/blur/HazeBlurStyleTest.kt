// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

@Suppress("DEPRECATION")
class HazeBlurStyleTest {

  @Test
  fun defaultStyle_isShared() {
    assertThat(HazeBlurDefaults.style).isSameInstanceAs(HazeBlurDefaults.style)
  }

  @Test
  fun equivalentStylePrograms_areEqual() {
    val first = HazeBlurStyle {
      blurEnabled(true)
      blurRadius(24.dp)
      backgroundColor(Color.Black)
      colorEffects(listOf(HazeColorEffect.tint(Color.Red)))
      alpha(0.8f)
    }.then {
      noiseFactor(0.2f)
    }
    val second = HazeBlurStyle {
      blurEnabled(true)
      blurRadius(24.dp)
      backgroundColor(Color.Black)
      colorEffects(listOf(HazeColorEffect.tint(Color.Red)))
      alpha(0.8f)
    }.then {
      noiseFactor(0.2f)
    }

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun differentStylePrograms_areNotEqual() {
    val first = HazeBlurStyle { blurRadius(24.dp) }
    val second = HazeBlurStyle { blurRadius(25.dp) }

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun deprecatedEmptyStyles_matchTheEmptyStyle() {
    val empty = resolveHazeBlurStyle(HazeBlurStyle, HazeBlurStyle)

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, HazeBlurStyle()))
      .isEqualTo(empty)
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, HazeBlurStyle.Unspecified))
      .isEqualTo(empty)
  }

  @Test
  fun deprecatedSingularFactory_matchesReplacementStyle() {
    val effect = HazeColorEffect.tint(Color.Red)
    val fallback = HazeColorEffect.tint(Color.Blue)
    val legacy = HazeBlurStyle(
      backgroundColor = Color.Black,
      colorEffect = effect,
      blurRadius = 24.dp,
      noiseFactor = 0.4f,
      fallbackColorEffect = fallback,
    )
    val replacement = HazeBlurStyle {
      backgroundColor(Color.Black)
      colorEffects(listOf(effect))
      blurRadius(24.dp)
      noiseFactor(0.4f)
      fallbackColorEffect(fallback)
    }

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
  }

  @Test
  fun deprecatedDefaultsBuilder_matchesCanonicalDefaultsWithLegacyDefaults() {
    val backgroundColor = Color.Magenta
    val legacy = HazeBlurDefaults.style(backgroundColor)
    val replacement = HazeBlurDefaults.style.then {
      backgroundColor(backgroundColor)
      colorEffects(listOf(HazeBlurDefaults.tint(backgroundColor)))
      blurRadius(HazeBlurDefaults.blurRadius)
      noiseFactor(HazeBlurDefaults.noiseFactor)
    }

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
  }

  @Test
  fun deprecatedPluralFactory_matchesReplacementStyle() {
    val effect = HazeColorEffect.tint(Color.Red)
    val fallback = HazeColorEffect.tint(Color.Blue)
    val legacy = HazeBlurStyle(
      backgroundColor = Color.Black,
      colorEffects = listOf(effect),
      blurRadius = 24.dp,
      noiseFactor = 0.4f,
      fallbackColorEffect = fallback,
    )
    val replacement = HazeBlurStyle {
      backgroundColor(Color.Black)
      colorEffects(listOf(effect))
      blurRadius(24.dp)
      noiseFactor(0.4f)
      fallbackColorEffect(fallback)
    }

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
  }

  @Test
  fun deprecatedPluralFactory_nullEffectsOmitWrite() {
    val effect = HazeColorEffect.tint(Color.Red)
    val inherited = HazeBlurStyle {
      colorEffects(listOf(effect))
    }
    val legacy = HazeBlurStyle(colorEffects = null)
    val replacement = HazeBlurStyle {}

    assertThat(resolveHazeBlurStyle(inherited, legacy))
      .isEqualTo(resolveHazeBlurStyle(inherited, replacement))
    assertThat(resolveHazeBlurStyle(inherited, legacy).colorEffects)
      .containsExactly(effect)
  }

  @Test
  fun deprecatedSingularFactory_nullEffectOmitsWrite() {
    val effect = HazeColorEffect.tint(Color.Red)
    val inherited = HazeBlurStyle {
      colorEffects(listOf(effect))
    }
    val legacy = HazeBlurStyle(colorEffect = null)
    val replacement = HazeBlurStyle {}

    assertThat(resolveHazeBlurStyle(inherited, legacy))
      .isEqualTo(resolveHazeBlurStyle(inherited, replacement))
    assertThat(resolveHazeBlurStyle(inherited, legacy).colorEffects)
      .containsExactly(effect)
  }

  @Test
  fun deprecatedFactories_unspecifiedBackgroundColorOmitsWrite() {
    val inherited = HazeBlurStyle {
      backgroundColor(Color.Red)
    }
    val replacement = HazeBlurStyle {}
    val expected = resolveHazeBlurStyle(inherited, replacement)

    assertThat(resolveHazeBlurStyle(inherited, HazeBlurStyle(backgroundColor = Color.Unspecified)))
      .isEqualTo(expected)
    assertThat(
      resolveHazeBlurStyle(
        inherited,
        HazeBlurStyle(backgroundColor = Color.Unspecified, colorEffect = null),
      ),
    ).isEqualTo(expected)
  }

  @Test
  fun deprecatedFactories_unspecifiedBlurRadiusOmitsWrite() {
    val inherited = HazeBlurStyle {
      blurRadius(12.dp)
    }
    val replacement = HazeBlurStyle {}
    val expected = resolveHazeBlurStyle(inherited, replacement)

    assertThat(resolveHazeBlurStyle(inherited, HazeBlurStyle(blurRadius = Dp.Unspecified)))
      .isEqualTo(expected)
    assertThat(
      resolveHazeBlurStyle(
        inherited,
        HazeBlurStyle(colorEffect = null, blurRadius = Dp.Unspecified),
      ),
    ).isEqualTo(expected)
  }

  @Test
  fun deprecatedFactories_unspecifiedFallbackEffectOmitsWrite() {
    val inherited = HazeBlurStyle {
      fallbackColorEffect(HazeColorEffect.tint(Color.Blue))
    }
    val replacement = HazeBlurStyle {}
    val expected = resolveHazeBlurStyle(inherited, replacement)

    assertThat(
      resolveHazeBlurStyle(
        inherited,
        HazeBlurStyle(fallbackColorEffect = HazeColorEffect.Unspecified),
      ),
    ).isEqualTo(expected)
    assertThat(
      resolveHazeBlurStyle(
        inherited,
        HazeBlurStyle(
          colorEffect = null,
          fallbackColorEffect = HazeColorEffect.Unspecified,
        ),
      ),
    ).isEqualTo(expected)
  }

  @Test
  fun deprecatedFactories_negativeNoiseOmitsWrite() {
    val inherited = HazeBlurStyle {
      noiseFactor(0.3f)
    }
    val replacement = HazeBlurStyle {}
    val expected = resolveHazeBlurStyle(inherited, replacement)

    assertThat(resolveHazeBlurStyle(inherited, HazeBlurStyle(noiseFactor = -0.1f)))
      .isEqualTo(expected)
    assertThat(
      resolveHazeBlurStyle(
        inherited,
        HazeBlurStyle(colorEffect = null, noiseFactor = -0.1f),
      ),
    ).isEqualTo(expected)
  }

  @Test
  fun deprecatedPluralFactory_emptyEffectsClearInheritedEffects() {
    val inherited = HazeBlurStyle {
      colorEffects(listOf(HazeColorEffect.tint(Color.Red)))
    }
    val legacy = HazeBlurStyle(colorEffects = emptyList())
    val replacement = HazeBlurStyle {
      colorEffects(emptyList())
    }

    assertThat(resolveHazeBlurStyle(inherited, legacy))
      .isEqualTo(resolveHazeBlurStyle(inherited, replacement))
    assertThat(resolveHazeBlurStyle(inherited, legacy).colorEffects).isEmpty()
  }

  @Test
  fun deprecatedPluralFactory_snapshotsCallerOwnedEffects() {
    val first = HazeColorEffect.tint(Color.Red)
    val second = HazeColorEffect.tint(Color.Blue)
    val input = mutableListOf(first)
    val legacy = HazeBlurStyle(colorEffects = input)
    val replacement = HazeBlurStyle {
      colorEffects(listOf(first))
    }

    input += second

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy).colorEffects)
      .containsExactly(first)
  }

  @Test
  fun deprecatedFactories_explicitNoiseUsesAccumulatorClamping() {
    val plural = HazeBlurStyle(noiseFactor = 2f)
    val singular = HazeBlurStyle(colorEffect = null, noiseFactor = 2f)
    val replacement = HazeBlurStyle {
      noiseFactor(2f)
    }
    val expected = resolveHazeBlurStyle(HazeBlurStyle, replacement)

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, plural)).isEqualTo(expected)
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, singular)).isEqualTo(expected)
    assertThat(expected.noiseFactor).isEqualTo(1f)
  }

  @Test
  fun deprecatedFactories_explicitNaNUsesAccumulatorValidation() {
    assertFailure {
      resolveHazeBlurStyle(HazeBlurStyle, HazeBlurStyle(noiseFactor = Float.NaN))
    }.isInstanceOf<IllegalArgumentException>()
    assertFailure {
      resolveHazeBlurStyle(
        HazeBlurStyle,
        HazeBlurStyle(colorEffect = null, noiseFactor = Float.NaN),
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun deprecatedDefaultsBuilder_matchesCanonicalDefaultsWithOverrides() {
    val tint = HazeColorEffect.tint(Color.Cyan)
    val legacy = HazeBlurDefaults.style(
      backgroundColor = Color.Black,
      tint = tint,
      blurRadius = 32.dp,
      noiseFactor = 0.6f,
    )
    val replacement = HazeBlurDefaults.style.then {
      backgroundColor(Color.Black)
      colorEffects(listOf(tint))
      blurRadius(32.dp)
      noiseFactor(0.6f)
    }

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
  }

  @Test
  fun deprecatedDefaultsBuilder_unspecifiedBackgroundColorOmitsOverride() {
    val legacy = HazeBlurDefaults.style(
      backgroundColor = Color.Unspecified,
    )
    val replacement = HazeBlurDefaults.style.then {}

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy).backgroundColor)
      .isEqualTo(Color.Transparent)
  }

  @Test
  fun deprecatedDefaultsBuilder_unspecifiedTintOmitsOverride() {
    val legacy = HazeBlurDefaults.style(
      backgroundColor = Color.Unspecified,
      tint = HazeColorEffect.Unspecified,
    )
    val replacement = HazeBlurDefaults.style.then {}

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy).colorEffects).isEmpty()
  }

  @Test
  fun deprecatedDefaultsBuilder_unspecifiedBlurRadiusOmitsOverride() {
    val legacy = HazeBlurDefaults.style(
      backgroundColor = Color.Unspecified,
      blurRadius = Dp.Unspecified,
    )
    val replacement = HazeBlurDefaults.style.then {}

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy).blurRadius)
      .isEqualTo(HazeBlurDefaults.blurRadius)
  }

  @Test
  fun deprecatedDefaultsBuilder_negativeNoiseOmitsOverride() {
    val legacy = HazeBlurDefaults.style(
      backgroundColor = Color.Unspecified,
      noiseFactor = -0.1f,
    )
    val replacement = HazeBlurDefaults.style.then {}

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy))
      .isEqualTo(resolveHazeBlurStyle(HazeBlurStyle, replacement))
    assertThat(resolveHazeBlurStyle(HazeBlurStyle, legacy).noiseFactor)
      .isEqualTo(HazeBlurDefaults.noiseFactor)
  }

  @Test
  fun deprecatedDefaultsBuilder_explicitNoiseUsesAccumulatorBehavior() {
    val clamped = HazeBlurDefaults.style(
      backgroundColor = Color.Unspecified,
      noiseFactor = 2f,
    )

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, clamped).noiseFactor).isEqualTo(1f)
    assertFailure {
      resolveHazeBlurStyle(
        HazeBlurStyle,
        HazeBlurDefaults.style(
          backgroundColor = Color.Unspecified,
          noiseFactor = Float.NaN,
        ),
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun styleChain_appliesWritesInOrder() {
    val style = HazeBlurStyle {
      blurRadius(8.dp)
      backgroundColor(Color.Red)
    }.then {
      blurRadius(24.dp)
    }

    val resolved = resolveHazeBlurStyle(
      localStyle = HazeBlurStyle,
      explicitStyle = style,
    )

    assertThat(resolved.blurRadius).isEqualTo(24.dp)
    assertThat(resolved.backgroundColor).isEqualTo(Color.Red)
  }

  @Test
  fun resolution_appliesDefaultsLocalAndExplicitStyle() {
    val local = HazeBlurStyle {
      blurRadius(12.dp)
      noiseFactor(0.3f)
    }
    val explicit = HazeBlurStyle {
      blurRadius(28.dp)
    }

    val resolved = resolveHazeBlurStyle(local, explicit)

    assertThat(resolved.blurRadius).isEqualTo(28.dp)
    assertThat(resolved.noiseFactor).isEqualTo(0.3f)
    assertThat(resolved.backgroundColor).isEqualTo(Color.Transparent)
    assertThat(resolved.alpha).isEqualTo(1f)
    assertThat(resolved.blurredEdgeTreatment).isEqualTo(HazeBlurDefaults.blurredEdgeTreatment)
  }

  @Test
  fun evaluation_startsFromFreshAccumulator() {
    val style = HazeBlurStyle {
      blurRadius(36.dp)
    }

    val first = resolveHazeBlurStyle(HazeBlurStyle, style)
    val second = resolveHazeBlurStyle(HazeBlurStyle, HazeBlurStyle)

    assertThat(first.blurRadius).isEqualTo(36.dp)
    assertThat(second.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius)
  }

  @Test
  fun propertyRemoval_revealsLowerTier() {
    val local = HazeBlurStyle {
      blurRadius(16.dp)
      noiseFactor(0.4f)
    }
    val withOverride = HazeBlurStyle {
      blurRadius(32.dp)
      noiseFactor(0.8f)
    }
    val withoutRadius = HazeBlurStyle {
      noiseFactor(0.8f)
    }

    assertThat(resolveHazeBlurStyle(local, withOverride).blurRadius).isEqualTo(32.dp)
    assertThat(resolveHazeBlurStyle(local, withoutRadius).blurRadius).isEqualTo(16.dp)
  }

  @Test
  fun emptyColorEffects_clearsInheritedEffects() {
    val inherited = HazeColorEffect.tint(Color.Red)
    val local = HazeBlurStyle {
      colorEffects(listOf(inherited))
    }
    val explicit = HazeBlurStyle {
      colorEffects(emptyList())
    }

    assertThat(resolveHazeBlurStyle(local, explicit).colorEffects).isEmpty()
  }

  @Test
  fun colorEffects_snapshotsCallerOwnedList() {
    val first = HazeColorEffect.tint(Color.Red)
    val second = HazeColorEffect.tint(Color.Blue)
    val input = mutableListOf(first)
    val style = HazeBlurStyle {
      colorEffects(input)
    }

    input += second

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, style).colorEffects)
      .containsExactly(first)
  }

  @Test
  fun noiseFactor_clampsAtAccumulatorBoundary() {
    val style = HazeBlurStyle {
      noiseFactor(2f)
    }

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, style).noiseFactor).isEqualTo(1f)
  }

  @Test
  fun noiseFactor_rejectsNaN() {
    assertFailure {
      resolveHazeBlurStyle(
        HazeBlurStyle,
        HazeBlurStyle {
          noiseFactor(Float.NaN)
        },
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun blurRadius_rejectsNegativeValues() {
    assertFailure {
      resolveHazeBlurStyle(
        HazeBlurStyle,
        HazeBlurStyle {
          blurRadius((-1).dp)
        },
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun everyStyleFunction_isCapturedInTheResolvedSnapshot() {
    val mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    val progressive = HazeProgressive.verticalGradient()
    val effect = HazeColorEffect.tint(Color.Magenta)
    val fallback = HazeColorEffect.tint(Color.Gray)
    val style = HazeBlurStyle {
      blurEnabled(false)
      blurRadius(18.dp)
      noiseFactor(0.25f)
      backgroundColor(Color.Cyan)
      colorEffects(listOf(effect))
      fallbackColorEffect(fallback)
      alpha(0.6f)
      mask(mask)
      progressive(progressive)
      blurredEdgeTreatment(BlurredEdgeTreatment.Unbounded)
    }

    val resolved = resolveHazeBlurStyle(HazeBlurStyle, style)

    assertThat(resolved.blurEnabled).isFalse()
    assertThat(resolved.blurRadius).isEqualTo(18.dp)
    assertThat(resolved.noiseFactor).isEqualTo(0.25f)
    assertThat(resolved.backgroundColor).isEqualTo(Color.Cyan)
    assertThat(resolved.colorEffects).containsExactly(effect)
    assertThat(resolved.fallbackColorEffect).isEqualTo(fallback)
    assertThat(resolved.alpha).isEqualTo(0.6f)
    assertThat(resolved.mask).isEqualTo(mask)
    assertThat(resolved.progressive).isEqualTo(progressive)
    assertThat(resolved.blurredEdgeTreatment).isEqualTo(BlurredEdgeTreatment.Unbounded)
  }

  @Test
  fun alpha_clampsAtAccumulatorBoundary() {
    val style = HazeBlurStyle {
      alpha(-1f)
    }

    assertThat(resolveHazeBlurStyle(HazeBlurStyle, style).alpha).isEqualTo(0f)
  }

  @Test
  fun alpha_rejectsNaN() {
    assertFailure {
      resolveHazeBlurStyle(
        HazeBlurStyle,
        HazeBlurStyle {
          alpha(Float.NaN)
        },
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun backgroundColor_rejectsUnspecifiedValues() {
    assertFailure {
      resolveHazeBlurStyle(
        HazeBlurStyle,
        HazeBlurStyle {
          backgroundColor(Color.Unspecified)
        },
      )
    }.isInstanceOf<IllegalArgumentException>()
  }
}
