// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class AncestorSourceAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_siblingEffect_rendersSource() = captureSourceLayout(sourceContainsEffect = false)

  @Test
  fun blur_descendantEffect_matchesSibling() = captureSourceLayout(sourceContainsEffect = true)

  private fun captureSourceLayout(sourceContainsEffect: Boolean) = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        AncestorSourceSample(sourceContainsEffect = sourceContainsEffect)
      }
    }

    waitForIdle()
    captureRoot()
  }
}

@Composable
private fun AncestorSourceSample(sourceContainsEffect: Boolean) {
  val hazeState = remember { HazeState() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    if (sourceContainsEffect) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(hazeState),
      ) {
        SourcePattern(Modifier.fillMaxSize())
        TestBottomBar(
          hazeState = hazeState,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    } else {
      SourcePattern(
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(hazeState),
      )
      TestBottomBar(
        hazeState = hazeState,
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

@Composable
private fun SourcePattern(modifier: Modifier = Modifier) {
  Row(modifier = modifier) {
    listOf(
      Color(0xFFE53935),
      Color(0xFF1E88E5),
      Color(0xFFFDD835),
      Color(0xFF43A047),
      Color(0xFF8E24AA),
      Color(0xFFFF7043),
    ).forEach { color ->
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .background(color),
      )
    }
  }
}

@Composable
private fun TestBottomBar(
  hazeState: HazeState,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(220.dp)
      .hazeEffect(hazeState) {
        blurEffect {
          blurRadius = 32.dp
          colorEffects = listOf(
            HazeColorEffect.tint(Color.White.copy(alpha = 0.18f)),
          )
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "Navigation",
      color = Color.White,
      style = MaterialTheme.typography.headlineMedium,
    )
  }
}
