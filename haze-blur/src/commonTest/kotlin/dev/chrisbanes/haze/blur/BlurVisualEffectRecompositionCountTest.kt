// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.test.RecompositionCounter
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BlurVisualEffectRecompositionCountTest : ContextTest() {

  companion object {
    private const val RECOMPOSITION_THRESHOLD = 1
  }

  @Test
  fun blurRadiusChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurRadius = mutableStateOf(10.dp)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
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
    }
    waitForIdle()

    effectCounter.intValue = 0

    blurRadius.value = 20.dp
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur radius change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun colorEffectsChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.colorEffects = colorEffects.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after colorEffects change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun styleChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val style = mutableStateOf(
      HazeBlurStyle(
        colorEffects = emptyList(),
        blurRadius = 10.dp,
      ),
    )

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
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
    }
    waitForIdle()

    effectCounter.intValue = 0

    style.value = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = 20.dp,
    )
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after style change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.blurEnabled = blurEnabled.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    blurEnabled.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blurEnabled toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun progressiveChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val progressive = mutableStateOf<HazeProgressive?>(null)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.progressive = progressive.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    progressive.value = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0f,
    )
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after progressive change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun noiseFactorChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val noiseFactor = mutableStateOf(0f)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.noiseFactor = noiseFactor.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    noiseFactor.value = 0.5f
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after noiseFactor change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun backgroundColorChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val backgroundColor = mutableStateOf(Color.Unspecified)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.backgroundColor = backgroundColor.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    backgroundColor.value = Color.Black
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after backgroundColor change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun alphaChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val alpha = mutableStateOf(1f)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.alpha = alpha.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    alpha.value = 0.5f
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after alpha change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun maskChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val mask = mutableStateOf<Brush?>(null)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.mask = mask.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    mask.value = Brush.verticalGradient(
      colors = listOf(Color.Transparent, Color.Black),
    )
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after mask change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun fallbackTintChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val fallbackTint = mutableStateOf<HazeColorEffect>(HazeColorEffect.Unspecified)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.fallbackTint = fallbackTint.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    fallbackTint.value = HazeColorEffect.tint(Color.Gray)
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after fallbackTint change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurredEdgeTreatmentChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurredEdgeTreatment = mutableStateOf(HazeBlurDefaults.blurredEdgeTreatment)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                blurEffect {
                  this.blurredEdgeTreatment = blurredEdgeTreatment.value
                }
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    blurredEdgeTreatment.value = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blurredEdgeTreatment change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
}

@Composable
private fun GradientBox(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(
      Brush.linearGradient(
        colors = listOf(
          Color(0xFF6B73FF),
          Color(0xFF9B59B6),
          Color(0xFFE74C3C),
          Color(0xFFF39C12),
        ),
      ),
    ),
  )
}
