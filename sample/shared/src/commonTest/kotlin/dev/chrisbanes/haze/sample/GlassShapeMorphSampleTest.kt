// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassShapeMorphSampleTest : ContextTest() {
  @Test
  fun embeddedMode_hidesBackButton() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalSampleNavigationEnabled provides false) {
        GlassShapeMorphSample(navController = rememberNavController())
      }
    }

    onAllNodesWithContentDescription("Back").assertCountEquals(0)
  }

  @Test
  fun normalMode_showsBackButton() = runComposeUiTest {
    setContent {
      GlassShapeMorphSample(navController = rememberNavController())
    }

    onAllNodesWithContentDescription("Back").assertCountEquals(1)
  }
}
