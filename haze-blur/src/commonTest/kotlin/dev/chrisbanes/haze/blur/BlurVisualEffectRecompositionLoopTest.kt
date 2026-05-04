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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalTestApi::class)
class BlurVisualEffectRecompositionLoopTest : ContextTest() {

  companion object {
    private const val IDLE_TIMEOUT_MS = 1000L
  }

  private suspend fun ComposeUiTest.awaitIdleWithTimeout(description: String) {
    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected $description. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }

  @Test
  fun blurRadiusMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)

    setContent {
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
    waitForIdle()

    blurRadius.value = 20.dp

    awaitIdleWithTimeout("after blurRadius mutation")
  }

  @Test
  fun colorEffectsMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    setContent {
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
    waitForIdle()

    colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))

    awaitIdleWithTimeout("after colorEffects mutation")
  }

  @Test
  fun styleMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val style = mutableStateOf(
      HazeBlurStyle(
        colorEffects = emptyList(),
        blurRadius = 10.dp,
      ),
    )

    setContent {
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
    waitForIdle()

    style.value = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = 20.dp,
    )

    awaitIdleWithTimeout("after style mutation")
  }

  @Test
  fun blurEnabledToggle_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurEnabled = mutableStateOf(true)

    setContent {
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
    waitForIdle()

    blurEnabled.value = false

    awaitIdleWithTimeout("after blurEnabled toggle")
  }

  @Test
  fun progressiveMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val progressive = mutableStateOf<HazeProgressive?>(null)

    setContent {
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
    waitForIdle()

    progressive.value = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0f,
    )

    awaitIdleWithTimeout("after progressive mutation")
  }

  @Test
  fun noiseFactorMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val noiseFactor = mutableStateOf(0f)

    setContent {
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
    waitForIdle()

    noiseFactor.value = 0.5f

    awaitIdleWithTimeout("after noiseFactor mutation")
  }

  @Test
  fun backgroundColorMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val backgroundColor = mutableStateOf(Color.Unspecified)

    setContent {
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
    waitForIdle()

    backgroundColor.value = Color.Black

    awaitIdleWithTimeout("after backgroundColor mutation")
  }

  @Test
  fun alphaMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val alpha = mutableStateOf(1f)

    setContent {
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
    waitForIdle()

    alpha.value = 0.5f

    awaitIdleWithTimeout("after alpha mutation")
  }

  @Test
  fun maskMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val mask = mutableStateOf<Brush?>(null)

    setContent {
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
    waitForIdle()

    mask.value = Brush.verticalGradient(
      colors = listOf(Color.Transparent, Color.Black),
    )

    awaitIdleWithTimeout("after mask mutation")
  }

  @Test
  fun fallbackTintMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val fallbackTint = mutableStateOf<HazeColorEffect>(HazeColorEffect.Unspecified)

    setContent {
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
    waitForIdle()

    fallbackTint.value = HazeColorEffect.tint(Color.Gray)

    awaitIdleWithTimeout("after fallbackTint mutation")
  }

  @Test
  fun blurredEdgeTreatmentMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurredEdgeTreatment = mutableStateOf(HazeBlurDefaults.blurredEdgeTreatment)

    setContent {
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
    waitForIdle()

    blurredEdgeTreatment.value = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded

    awaitIdleWithTimeout("after blurredEdgeTreatment mutation")
  }

  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    setContent {
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
    waitForIdle()

    repeat(5) { index ->
      if (index % 2 == 0) {
        blurRadius.value = 20.dp
        colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))
      } else {
        blurRadius.value = 10.dp
        colorEffects.value = listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f)))
      }
      awaitIdleWithTimeout("on alternating mutation #$index")
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
