// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class HazeBlurStyleTest {

  @Test
  fun defaultStyle_isShared() {
    assertThat(HazeBlurDefaults.style).isSameInstanceAs(HazeBlurDefaults.style)
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
