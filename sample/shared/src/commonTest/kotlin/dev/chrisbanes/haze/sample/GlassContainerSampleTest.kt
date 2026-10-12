// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassContainerSampleTest : ContextTest() {
  @Test
  fun embeddedMode_hidesBackButton() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalSampleNavigationEnabled provides false) {
        GlassContainerSample(navController = rememberNavController())
      }
    }

    onAllNodesWithContentDescription("Back").assertCountEquals(0)
  }

  @Test
  fun normalMode_showsBackButton() = runComposeUiTest {
    setContent {
      GlassContainerSample(navController = rememberNavController())
    }

    onAllNodesWithContentDescription("Back").assertCountEquals(1)
  }

  @Test
  fun extraMemberToggle_addsAndRemovesMember() = runComposeUiTest {
    setContent {
      GlassContainerSample(navController = rememberNavController())
    }

    onAllNodesWithTag("glass_container_extra_member").assertCountEquals(0)

    onNodeWithTag("glass_container_toggle").performScrollTo().performClick()
    waitForIdle()
    onNodeWithTag("glass_container_extra_member").assertExists()

    onNodeWithTag("glass_container_toggle").performScrollTo().performClick()
    waitForIdle()
    onAllNodesWithTag("glass_container_extra_member").assertCountEquals(0)
  }
}
