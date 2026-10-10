// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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

  @Test
  fun pillGap_isPositiveApartAndNegativeWhenOverlapping() {
    // Pills are 140 dp wide.
    assertThat(pillGap(DpOffset(0.dp, 0.dp), DpOffset(156.dp, 0.dp))).isEqualTo(16.dp)
    assertThat(pillGap(DpOffset(0.dp, 0.dp), DpOffset(100.dp, 0.dp))).isEqualTo((-40).dp)
  }

  @Test
  fun nextJoined_joinsBelowSpacingAndBreaksOnlyPastTwiceSpacing() {
    assertThat(nextJoined(joined = false, gap = 39.dp, spacing = 40.dp)).isTrue()
    assertThat(nextJoined(joined = false, gap = 60.dp, spacing = 40.dp)).isFalse()
    // Surface tension: the same 60 dp gap keeps an existing bridge.
    assertThat(nextJoined(joined = true, gap = 60.dp, spacing = 40.dp)).isTrue()
    assertThat(nextJoined(joined = true, gap = 81.dp, spacing = 40.dp)).isFalse()
  }

  @Test
  fun dropletSpacing_keepsTheNeckUntilItClosesAtTheBreakDistance() {
    assertThat(dropletSpacing(gap = 60.dp, spacing = 40.dp, join = 0f)).isEqualTo(40.dp)
    // Joined: Glass bridges while the gap is below its spacing, so the neck survives at 60 dp...
    assertThat(dropletSpacing(gap = 60.dp, spacing = 40.dp, join = 1f)).isEqualTo(70.dp)
    // ...and closes exactly at the 80 dp break distance.
    assertThat(dropletSpacing(gap = 80.dp, spacing = 40.dp, join = 1f)).isEqualTo(80.dp)
  }
}
