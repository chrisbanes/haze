// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassMergePrototypeSampleTest : ContextTest() {
  @Test
  fun mergedMode_showsPillsAndControls() = runComposeUiTest {
    setContent {
      GlassMergePrototypeSample(navController = rememberNavController())
    }

    onNodeWithTag("merge_pill_a").assertIsDisplayed()
    onNodeWithTag("merge_pill_b").assertIsDisplayed()
    onNodeWithTag("merge_spacing").assertIsDisplayed()
    onNodeWithTag("merge_split_toggle").assertIsDisplayed()
    onNodeWithTag("merge_container").assertExists()
  }

  @Test
  fun independentMode_replacesTheContainerWithTwoSurfaces() = runComposeUiTest {
    setContent {
      GlassMergePrototypeSample(navController = rememberNavController())
    }

    onNodeWithTag("merge_mode_independent").performClick()

    onNodeWithTag("merge_independent_surface_a").assertExists()
    onNodeWithTag("merge_independent_surface_b").assertExists()
    onNodeWithTag("merge_container").assertDoesNotExist()
  }
}
