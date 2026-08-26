// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.junit.After
import org.junit.Rule

@Suppress("DEPRECATION") // This regression specifically covers Compose UI Test v1 teardown.
class HazeSourceNodeActivityTeardownTest : ContextTest() {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @After
  fun cleanup() {
    composeRule.activityRule.scenario.close()
  }

  @Test
  fun activityScenarioClose_withScaffoldSlots_doesNotAccessDetachedNode() {
    val hazeState = HazeState()
    composeRule.setContent {
      Scaffold(
        topBar = {
          Box(
            Modifier
              .fillMaxWidth()
              .height(56.dp)
              .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle { blurRadius(20.dp) },
              ),
          )
        },
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        )
      }
    }
    composeRule.waitForIdle()
  }
}
