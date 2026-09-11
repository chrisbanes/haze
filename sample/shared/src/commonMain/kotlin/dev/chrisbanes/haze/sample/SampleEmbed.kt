// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle

public enum class SampleEmbedTheme {
  System,
  Light,
  Dark,
}

public sealed interface SampleLaunchRequest {
  public data object Normal : SampleLaunchRequest

  @Poko
  public class Selected(
    val sample: Sample,
    val effect: SampleEffect,
    val theme: SampleEmbedTheme,
    val embedded: Boolean = true,
  ) : SampleLaunchRequest

  @Poko
  public class Invalid(val message: String) : SampleLaunchRequest
}

public fun resolveSampleLaunch(
  query: Map<String, String>,
  samples: List<Sample>,
): SampleLaunchRequest {
  val embedded = when (query["embed"]) {
    "true" -> true
    null, "false" -> false
    else -> return SampleLaunchRequest.Invalid("embed must be true or false")
  }
  if (!embedded && "sample" !in query && "effect" !in query) return SampleLaunchRequest.Normal

  val route = query["sample"] ?: return SampleLaunchRequest.Invalid("sample is required")
  val effect = when (query["effect"]) {
    "blur" -> SampleEffect.Blur
    "glass" -> SampleEffect.Glass
    null -> return SampleLaunchRequest.Invalid("effect is required")
    else -> return SampleLaunchRequest.Invalid("effect is invalid")
  }
  val theme = when (query["theme"]) {
    null, "system" -> SampleEmbedTheme.System
    "light" -> SampleEmbedTheme.Light
    "dark" -> SampleEmbedTheme.Dark
    else -> return SampleLaunchRequest.Invalid("theme is invalid")
  }
  val sample = samples.firstOrNull { it.route == route }
    ?: return SampleLaunchRequest.Invalid("sample is invalid")
  if (effect !in sample.effects) return SampleLaunchRequest.Invalid("effect is unsupported for sample")

  return SampleLaunchRequest.Selected(sample, effect, theme, embedded)
}

internal val LocalSampleNavigationEnabled = staticCompositionLocalOf { true }

@Composable
public fun EmbeddedSample(
  request: SampleLaunchRequest.Selected,
  modifier: Modifier = Modifier,
) {
  val useDarkColors = when (request.theme) {
    SampleEmbedTheme.System -> isSystemInDarkTheme()
    SampleEmbedTheme.Light -> false
    SampleEmbedTheme.Dark -> true
  }
  val navController = rememberNavController()
  val localBlurStyle = remember { HazeBlurStyle }

  SamplesTheme(useDarkColors = useDarkColors) {
    CompositionLocalProvider(
      LocalHazeBlurStyle provides localBlurStyle,
      LocalSampleNavigationEnabled provides false,
    ) {
      Box(
        modifier = modifier
          .fillMaxSize()
          .testTag("embedded_sample"),
      ) {
        key(request.sample.route, request.effect) {
          request.sample.content(navController, request.effect)
        }
      }
    }
  }
}
