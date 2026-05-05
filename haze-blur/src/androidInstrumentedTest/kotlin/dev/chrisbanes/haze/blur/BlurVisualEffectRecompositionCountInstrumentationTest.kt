// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.RecompositionCounter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlurVisualEffectRecompositionCountInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val RECOMPOSITION_THRESHOLD = 1
  }

  @Test
  fun blurRadiusChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurRadius = mutableStateOf(10.dp)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.blurRadius = blurRadius.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    blurRadius.value = 20.dp
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blurRadius change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun colorEffectsChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.colorEffects = colorEffects.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after colorEffects change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun styleChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val style = mutableStateOf(
      HazeBlurStyle(
        colorEffects = emptyList(),
        blurRadius = 10.dp,
      ),
    )

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.style = style.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    style.value = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = 20.dp,
    )
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after style change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.blurEnabled = blurEnabled.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    blurEnabled.value = false
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blurEnabled toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun progressiveChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val progressive = mutableStateOf<HazeProgressive?>(null)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.progressive = progressive.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    progressive.value = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0f,
    )
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after progressive change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun noiseFactorChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val noiseFactor = mutableStateOf(0f)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.noiseFactor = noiseFactor.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    noiseFactor.value = 0.5f
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after noiseFactor change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun backgroundColorChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val backgroundColor = mutableStateOf(Color.Unspecified)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.backgroundColor = backgroundColor.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    backgroundColor.value = Color.Black
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after backgroundColor change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun alphaChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val alpha = mutableStateOf(1f)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.alpha = alpha.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    alpha.value = 0.5f
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after alpha change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun maskChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val mask = mutableStateOf<Brush?>(null)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.mask = mask.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    mask.value = Brush.verticalGradient(
      colors = listOf(Color.Transparent, Color.Black),
    )
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after mask change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun fallbackTintChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val fallbackTint = mutableStateOf<HazeColorEffect>(HazeColorEffect.Unspecified)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.fallbackTint = fallbackTint.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    fallbackTint.value = HazeColorEffect.tint(Color.Gray)
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after fallbackTint change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurredEdgeTreatmentChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurredEdgeTreatment = mutableStateOf(HazeBlurDefaults.blurredEdgeTreatment)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.blurredEdgeTreatment = blurredEdgeTreatment.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    blurredEdgeTreatment.value = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blurredEdgeTreatment change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun sourceAttachRemoveChurn_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showPrimarySource = mutableStateOf(true)
    val showSecondarySource = mutableStateOf(false)
    val blurRadius = mutableStateOf(10.dp)

    composeTestRule.setContent {
      if (showPrimarySource.value) {
        BlurTestGradientBox(Modifier.hazeSource(hazeState).size(60.dp))
      }
      if (showSecondarySource.value) {
        BlurTestGradientBox(Modifier.hazeSource(hazeState).size(80.dp))
      }

      RecompositionCounter(effectCounter) {
        BlurTestGradientBox(
          Modifier
            .hazeEffect(hazeState) {
              blurEffect {
                this.blurRadius = blurRadius.value
              }
            }
            .size(100.dp),
        )
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    // Coupling source attach/remove with a blur mutation catches source node + effect bookkeeping churn.
    showSecondarySource.value = true
    blurRadius.value = 18.dp
    composeTestRule.waitForIdle()

    showPrimarySource.value = false
    blurRadius.value = 12.dp
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after source attach/remove churn")
      .isLessThanOrEqualTo(3)
  }

  @Test
  fun sourceAndStyleMutationTogether_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)
    val style = mutableStateOf(
      HazeBlurStyle(
        blurRadius = 10.dp,
        colorEffects = emptyList(),
      ),
    )

    composeTestRule.setContent {
      if (showSource.value) {
        BlurTestGradientBox(Modifier.hazeSource(hazeState).size(100.dp))
      }

      RecompositionCounter(effectCounter) {
        BlurTestGradientBox(
          Modifier
            .hazeEffect(hazeState) {
              blurEffect {
                this.style = style.value
              }
            }
            .size(100.dp),
        )
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    composeTestRule.runOnIdle {
      showSource.value = false
      style.value = HazeBlurStyle(
        blurRadius = 22.dp,
        colorEffects = listOf(HazeColorEffect.tint(Color.Cyan.copy(alpha = 0.3f))),
      )
    }
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after source and style mutation")
      .isLessThanOrEqualTo(2)
  }

  @Test
  fun rapidCombinedPropertyChanges_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurRadius = mutableStateOf(10.dp)
    val alpha = mutableStateOf(1f)
    val noiseFactor = mutableStateOf(0f)

    composeTestRule.setBlurEffectContent(hazeState, effectCounter) {
      this.blurRadius = blurRadius.value
      this.alpha = alpha.value
      this.noiseFactor = noiseFactor.value
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    repeat(5) { index ->
      composeTestRule.runOnIdle {
        val toggled = index % 2 == 0
        blurRadius.value = if (toggled) 22.dp else 10.dp
        alpha.value = if (toggled) 0.55f else 1f
        noiseFactor.value = if (toggled) 0.4f else 0f
      }
      composeTestRule.waitForIdle()
    }

    // Rapid multi-property churn can coalesce differently on device; allow a small buffer.
    assertThat(effectCounter.intValue, "effect recompositions after rapid combined property changes")
      .isLessThanOrEqualTo(7)
  }

  private fun ComposeContentTestRule.setBlurEffectContent(
    hazeState: HazeState,
    effectCounter: MutableIntState,
    configure: BlurVisualEffect.() -> Unit,
  ) {
    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          BlurTestGradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect(configure)
              }
              .size(100.dp),
          )
        }
      }
    }
  }
}
