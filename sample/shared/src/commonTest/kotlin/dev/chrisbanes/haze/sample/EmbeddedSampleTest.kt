// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EmbeddedSampleTest : ContextTest() {
  @Test
  fun embeddedSample_rendersOnlyTheSelectedContentWithTheRequestedEffectAndTheme() = runComposeUiTest {
    var selectedEffect: SampleEffect? = null
    var background: Color? = null
    val probe = Sample(
      route = "probe",
      title = "Probe",
      effects = listOf(SampleEffect.Glass),
    ) { _, effect ->
      selectedEffect = effect
      background = MaterialTheme.colorScheme.background
      androidx.compose.material3.Text("Probe", modifier = Modifier.testTag("probe"))
    }

    setContent {
      EmbeddedSample(SampleLaunchRequest.Selected(probe, SampleEffect.Glass, SampleEmbedTheme.Dark))
    }

    onNodeWithTag("embedded_sample").assertIsDisplayed()
    onNodeWithTag("probe").assertIsDisplayed()
    onAllNodesWithTag("sample_effect_list").assertCountEquals(0)
    onAllNodesWithTag("sample_list").assertCountEquals(0)
    runOnIdle {
      assertThat(selectedEffect).isEqualTo(SampleEffect.Glass)
      assertThat(background).isEqualTo(darkColorScheme().background)
    }
  }

  @Test
  fun embeddedMode_hidesDedicatedExitControlsAtNarrowAndWideSizes() = runComposeUiTest {
    var width by mutableStateOf(320.dp)
    setContent {
      androidx.compose.foundation.layout.Box(Modifier.size(width = width, height = 560.dp)) {
        EmbeddedSample(
          SampleLaunchRequest.Selected(Sample.Scaffold, SampleEffect.Glass, SampleEmbedTheme.Light),
        )
      }
    }

    onAllNodesWithTag("back").assertCountEquals(0)
    onAllNodesWithTag("glass_scaffold_back").assertCountEquals(0)
    runOnIdle { width = 960.dp }
    waitForIdle()
    onAllNodesWithTag("back").assertCountEquals(0)
    onAllNodesWithTag("glass_scaffold_back").assertCountEquals(0)
  }

  @Test
  fun embeddedMode_hidesCreditCardExitButton() = runComposeUiTest {
    setContent {
      EmbeddedSample(
        SampleLaunchRequest.Selected(Sample.CreditCard, SampleEffect.Glass, SampleEmbedTheme.System),
      )
    }

    onAllNodesWithTag("credit_card_back").assertCountEquals(0)
    onNodeWithTag("credit_card_2").assertIsDisplayed()
  }

  @Test
  fun embeddedMode_hidesImagesListExitControl() = assertEmbeddedSampleHidesBack(Sample.ImageList)

  @Test
  fun embeddedMode_hidesListOverImageExitControl() = assertEmbeddedSampleHidesBack(Sample.ListOverImage)

  @Test
  fun embeddedMode_hidesStickyHeadersExitControl() = assertEmbeddedSampleHidesBack(Sample.ListWithStickyHeaders)

  @Test
  fun embeddedMode_hidesBottomSheetExitControl() = assertEmbeddedSampleHidesBack(Sample.BottomSheet)

  @Test
  fun embeddedMode_hidesContentBlurringExitControl() = assertEmbeddedSampleHidesBack(Sample.ContentBlurring)

  @Test
  fun embeddedMode_hidesCustomVisualEffectExitControl() =
    assertEmbeddedSampleHidesBack(Sample.CustomVisualEffect, SampleEffect.Blur)

  @Test
  fun embeddedMode_hidesMaterialsExitControl() = assertEmbeddedSampleHidesBack(Sample.Materials)

  @Test
  fun embeddedMode_hidesLayerTransformationsExitControl() = assertEmbeddedSampleHidesBack(Sample.LayerTransformations)

  private fun assertEmbeddedSampleHidesBack(
    sample: Sample,
    effect: SampleEffect = SampleEffect.Glass,
  ) = runComposeUiTest {
    setContent {
      EmbeddedSample(SampleLaunchRequest.Selected(sample, effect, SampleEmbedTheme.System))
    }

    onAllNodesWithContentDescription("Back").assertCountEquals(0)
  }
}
