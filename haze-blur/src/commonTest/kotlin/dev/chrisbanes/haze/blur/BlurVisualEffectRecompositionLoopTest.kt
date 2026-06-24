// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BlurVisualEffectRecompositionLoopTest : ContextTest() {

  @Test
  fun blurRadiusMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)

    setBlurEffectContent(hazeState) {
      this.blurRadius = blurRadius.value
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

    setBlurEffectContent(hazeState) {
      this.colorEffects = colorEffects.value
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

    setBlurEffectContent(hazeState) {
      this.style = style.value
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

    setBlurEffectContent(hazeState) {
      this.blurEnabled = blurEnabled.value
    }
    waitForIdle()

    blurEnabled.value = false

    awaitIdleWithTimeout("after blurEnabled toggle")
  }

  @Test
  fun progressiveMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val progressive = mutableStateOf<HazeProgressive?>(null)

    setBlurEffectContent(hazeState) {
      this.progressive = progressive.value
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

    setBlurEffectContent(hazeState) {
      this.noiseFactor = noiseFactor.value
    }
    waitForIdle()

    noiseFactor.value = 0.5f

    awaitIdleWithTimeout("after noiseFactor mutation")
  }

  @Test
  fun backgroundColorMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val backgroundColor = mutableStateOf(Color.Unspecified)

    setBlurEffectContent(hazeState) {
      this.backgroundColor = backgroundColor.value
    }
    waitForIdle()

    backgroundColor.value = Color.Black

    awaitIdleWithTimeout("after backgroundColor mutation")
  }

  @Test
  fun alphaMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val alpha = mutableStateOf(1f)

    setBlurEffectContent(hazeState) {
      this.alpha = alpha.value
    }
    waitForIdle()

    alpha.value = 0.5f

    awaitIdleWithTimeout("after alpha mutation")
  }

  @Test
  fun maskMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val mask = mutableStateOf<Brush?>(null)

    setBlurEffectContent(hazeState) {
      this.mask = mask.value
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

    setBlurEffectContent(hazeState) {
      this.fallbackTint = fallbackTint.value
    }
    waitForIdle()

    fallbackTint.value = HazeColorEffect.tint(Color.Gray)

    awaitIdleWithTimeout("after fallbackTint mutation")
  }

  @Test
  fun blurredEdgeTreatmentMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurredEdgeTreatment = mutableStateOf(HazeBlurDefaults.blurredEdgeTreatment)

    setBlurEffectContent(hazeState) {
      this.blurredEdgeTreatment = blurredEdgeTreatment.value
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

    setBlurEffectContent(hazeState) {
      this.blurRadius = blurRadius.value
      this.colorEffects = colorEffects.value
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

  @Test
  fun sourceAttachRemoveChurn_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showPrimarySource = mutableStateOf(true)
    val showSecondarySource = mutableStateOf(false)
    val blurRadius = mutableStateOf(10.dp)

    setContent {
      if (showPrimarySource.value) {
        BlurTestGradientBox(Modifier.hazeSource(hazeState).size(60.dp))
      }
      if (showSecondarySource.value) {
        BlurTestGradientBox(Modifier.hazeSource(hazeState).size(80.dp))
      }

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
    waitForIdle()

    // Attach and remove different sources while mutating effect to stress source bookkeeping.
    showSecondarySource.value = true
    blurRadius.value = 18.dp
    awaitIdleWithTimeout("after attaching secondary source and mutating blur")

    showPrimarySource.value = false
    blurRadius.value = 12.dp
    awaitIdleWithTimeout("after removing primary source and mutating blur")
  }

  @Test
  fun sourceAndStyleMutationTogether_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val style = mutableStateOf(
      HazeBlurStyle(
        blurRadius = 10.dp,
        colorEffects = emptyList(),
      ),
    )

    setContent {
      if (showSource.value) {
        BlurTestGradientBox(Modifier.hazeSource(hazeState).size(100.dp))
      }

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
    waitForIdle()

    runOnIdle {
      showSource.value = false
      style.value = HazeBlurStyle(
        blurRadius = 22.dp,
        colorEffects = listOf(HazeColorEffect.tint(Color.Cyan.copy(alpha = 0.3f))),
      )
    }
    awaitIdleWithTimeout("after combined source and style mutation")
  }

  @Test
  fun rapidCombinedPropertyChanges_doNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)
    val alpha = mutableStateOf(1f)
    val noiseFactor = mutableStateOf(0f)

    setBlurEffectContent(hazeState) {
      this.blurRadius = blurRadius.value
      this.alpha = alpha.value
      this.noiseFactor = noiseFactor.value
    }
    waitForIdle()

    repeat(5) { index ->
      runOnIdle {
        val toggled = index % 2 == 0
        blurRadius.value = if (toggled) 22.dp else 10.dp
        alpha.value = if (toggled) 0.55f else 1f
        noiseFactor.value = if (toggled) 0.4f else 0f
      }
      awaitIdleWithTimeout("after rapid combined mutation #$index")
    }
  }

  private fun ComposeUiTest.setBlurEffectContent(
    hazeState: HazeState,
    configure: BlurVisualEffect.() -> Unit,
  ) {
    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
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
