// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BlurVisualEffectRecompositionLoopInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun ComposeContentTestRule.setBlurEffectContent(
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

  @Test
  fun blurRadiusMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.blurRadius = blurRadius.value
    }
    composeTestRule.waitForIdle()

    blurRadius.value = 20.dp
    composeTestRule.awaitIdleWithTimeout("after blurRadius mutation")
  }

  @Test
  fun colorEffectsMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    composeTestRule.setBlurEffectContent(hazeState) {
      this.colorEffects = colorEffects.value
    }
    composeTestRule.waitForIdle()

    colorEffects.value = listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.5f)))
    composeTestRule.awaitIdleWithTimeout("after colorEffects mutation")
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

    composeTestRule.setBlurEffectContent(hazeState) {
      this.style = style.value
    }
    composeTestRule.waitForIdle()

    style.value = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = 20.dp,
    )
    composeTestRule.awaitIdleWithTimeout("after style mutation")
  }

  @Test
  fun blurEnabledToggle_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurEnabled = mutableStateOf(true)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.blurEnabled = blurEnabled.value
    }
    composeTestRule.waitForIdle()

    blurEnabled.value = false
    composeTestRule.awaitIdleWithTimeout("after blurEnabled toggle")
  }

  @Test
  fun progressiveMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val progressive = mutableStateOf<HazeProgressive?>(null)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.progressive = progressive.value
    }
    composeTestRule.waitForIdle()

    progressive.value = HazeProgressive.verticalGradient(
      startIntensity = 1f,
      endIntensity = 0f,
    )
    composeTestRule.awaitIdleWithTimeout("after progressive mutation")
  }

  @Test
  fun noiseFactorMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val noiseFactor = mutableStateOf(0f)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.noiseFactor = noiseFactor.value
    }
    composeTestRule.waitForIdle()

    noiseFactor.value = 0.5f
    composeTestRule.awaitIdleWithTimeout("after noiseFactor mutation")
  }

  @Test
  fun backgroundColorMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val backgroundColor = mutableStateOf(Color.Unspecified)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.backgroundColor = backgroundColor.value
    }
    composeTestRule.waitForIdle()

    backgroundColor.value = Color.Black
    composeTestRule.awaitIdleWithTimeout("after backgroundColor mutation")
  }

  @Test
  fun alphaMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val alpha = mutableStateOf(1f)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.alpha = alpha.value
    }
    composeTestRule.waitForIdle()

    alpha.value = 0.5f
    composeTestRule.awaitIdleWithTimeout("after alpha mutation")
  }

  @Test
  fun maskMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val mask = mutableStateOf<Brush?>(null)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.mask = mask.value
    }
    composeTestRule.waitForIdle()

    mask.value = Brush.verticalGradient(
      colors = listOf(Color.Transparent, Color.Black),
    )
    composeTestRule.awaitIdleWithTimeout("after mask mutation")
  }

  @Test
  fun fallbackTintMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val fallbackTint = mutableStateOf<HazeColorEffect>(HazeColorEffect.Unspecified)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.fallbackTint = fallbackTint.value
    }
    composeTestRule.waitForIdle()

    fallbackTint.value = HazeColorEffect.tint(Color.Gray)
    composeTestRule.awaitIdleWithTimeout("after fallbackTint mutation")
  }

  @Test
  fun blurredEdgeTreatmentMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurredEdgeTreatment = mutableStateOf(HazeBlurDefaults.blurredEdgeTreatment)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.blurredEdgeTreatment = blurredEdgeTreatment.value
    }
    composeTestRule.waitForIdle()

    blurredEdgeTreatment.value = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
    composeTestRule.awaitIdleWithTimeout("after blurredEdgeTreatment mutation")
  }

  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)
    val colorEffects = mutableStateOf(
      listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
    )

    composeTestRule.setBlurEffectContent(hazeState) {
      this.blurRadius = blurRadius.value
      this.colorEffects = colorEffects.value
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
      composeTestRule.awaitIdleWithTimeout("on alternating mutation #$index")
    }
  }

  @Test
  fun sourceAttachRemoveChurn_doesNotInfiniteLoop() {
    val hazeState = HazeState()
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
    composeTestRule.waitForIdle()

    showSecondarySource.value = true
    blurRadius.value = 18.dp
    composeTestRule.awaitIdleWithTimeout("after attaching secondary source and mutating blur")

    showPrimarySource.value = false
    blurRadius.value = 12.dp
    composeTestRule.awaitIdleWithTimeout("after removing primary source and mutating blur")
  }

  @Test
  fun sourceAndStyleMutationTogether_doesNotInfiniteLoop() {
    val hazeState = HazeState()
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
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      showSource.value = false
      style.value = HazeBlurStyle(
        blurRadius = 22.dp,
        colorEffects = listOf(HazeColorEffect.tint(Color.Cyan.copy(alpha = 0.3f))),
      )
    }
    composeTestRule.awaitIdleWithTimeout("after combined source and style mutation")
  }

  @Test
  fun rapidCombinedPropertyChanges_doNotInfiniteLoop() {
    val hazeState = HazeState()
    val blurRadius = mutableStateOf(10.dp)
    val alpha = mutableStateOf(1f)
    val noiseFactor = mutableStateOf(0f)

    composeTestRule.setBlurEffectContent(hazeState) {
      this.blurRadius = blurRadius.value
      this.alpha = alpha.value
      this.noiseFactor = noiseFactor.value
    }
    composeTestRule.waitForIdle()

    repeat(5) { index ->
      composeTestRule.runOnIdle {
        val toggled = index % 2 == 0
        blurRadius.value = if (toggled) 22.dp else 10.dp
        alpha.value = if (toggled) 0.55f else 1f
        noiseFactor.value = if (toggled) 0.4f else 0f
      }
      composeTestRule.awaitIdleWithTimeout("after rapid combined mutation #$index")
    }
  }
}
