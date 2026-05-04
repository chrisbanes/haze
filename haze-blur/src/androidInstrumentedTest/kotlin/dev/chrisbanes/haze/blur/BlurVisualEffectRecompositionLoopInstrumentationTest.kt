// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlurVisualEffectRecompositionLoopInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun blurRadiusMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    blurRadius.value = 20.dp
    composeTestRule.waitForIdle()
  }

  @Test
  fun colorEffectsMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))
    composeTestRule.waitForIdle()
  }

  @Test
  fun styleMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val style = mutableStateOf(
      HazeBlurStyle(
        colorEffects = emptyList(),
        blurRadius = 10.dp,
      ),
    )

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    style.value = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = 20.dp,
    )
    composeTestRule.waitForIdle()
  }

  @Test
  fun blurEnabledToggle_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurEnabled = mutableStateOf(true)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    blurEnabled.value = false
    composeTestRule.waitForIdle()
  }

  @Test
  fun progressiveMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val progressive = mutableStateOf<HazeProgressive?>(null)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    progressive.value = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0f,
    )
    composeTestRule.waitForIdle()
  }

  @Test
  fun noiseFactorMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val noiseFactor = mutableStateOf(0f)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    noiseFactor.value = 0.5f
    composeTestRule.waitForIdle()
  }

  @Test
  fun backgroundColorMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val backgroundColor = mutableStateOf(Color.Unspecified)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    backgroundColor.value = Color.Black
    composeTestRule.waitForIdle()
  }

  @Test
  fun alphaMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val alpha = mutableStateOf(1f)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    alpha.value = 0.5f
    composeTestRule.waitForIdle()
  }

  @Test
  fun maskMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val mask = mutableStateOf<Brush?>(null)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    mask.value = Brush.verticalGradient(
      colors = listOf(Color.Transparent, Color.Black),
    )
    composeTestRule.waitForIdle()
  }

  @Test
  fun fallbackTintMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val fallbackTint = mutableStateOf<HazeColorEffect>(HazeColorEffect.Unspecified)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    fallbackTint.value = HazeColorEffect.tint(Color.Gray)
    composeTestRule.waitForIdle()
  }

  @Test
  fun blurredEdgeTreatmentMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurredEdgeTreatment = mutableStateOf(HazeBlurDefaults.blurredEdgeTreatment)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
    composeTestRule.waitForIdle()

    blurredEdgeTreatment.value = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
    composeTestRule.waitForIdle()
  }

  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        GradientBox(
          Modifier
            .hazeEffect(hazeState) {
              blurEffect {
                this.blurRadius = blurRadius.value
                this.colorEffects = colorEffects.value
              }
            }
            .size(100.dp),
        )
      }
    }
    composeTestRule.waitForIdle()

    repeat(5) { index ->
      if (index % 2 == 0) {
        blurRadius.value = 20.dp
        colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))
      } else {
        blurRadius.value = 10.dp
        colorEffects.value = listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f)))
      }
      composeTestRule.waitForIdle()
    }
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
