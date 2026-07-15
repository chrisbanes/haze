// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Provides a Haze source per scene. List before decorators whose content uses `hazeEffect`, so
 * effects are siblings above the source.
 */
@Composable
public fun <T : Any> rememberHazeSourceSceneDecoratorStrategy(
  state: HazeState,
): SceneDecoratorStrategy<T> {
  val currentState = rememberUpdatedState(state)
  return remember {
    SceneDecoratorStrategy { scene -> HazeSourceScene(scene, currentState) }
  }
}

private data class HazeSourceScene<T : Any>(
  private val scene: Scene<T>,
  private val state: State<HazeState>,
) : Scene<T> by scene {
  override val key: Any = scene::class to scene.key

  override val content: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize().hazeSource(state.value)) {
      scene.content()
    }
  }
}
