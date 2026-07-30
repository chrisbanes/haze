// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeEffectTrimMemoryLifecycleTest : ContextTest() {

  @Test
  fun typedContent_lifecycleEventsEmitModerateOnlyForEachStopTransition() = runComposeUiTest {
    if (!supportsLifecycleTrimMemoryCallbacks) return@runComposeUiTest

    val lifecycleOwner = RecordingLifecycleOwner()
    val factory = RecordingTrimMemoryFactory()

    setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        Spacer(
          Modifier
            .size(10.dp)
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = Unit,
            ),
        )
      }
    }
    waitForIdle()

    runOnIdle {
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_CREATE)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
    }

    assertThat(factory.renderer.trimLevels).containsExactly(
      TrimMemoryLevel.MODERATE,
      TrimMemoryLevel.MODERATE,
    )
  }

  @Test
  fun lifecycleOwnerReplacement_rebindsFromCompositionAndDetachDisposesObserver() =
    runComposeUiTest {
      if (!supportsLifecycleTrimMemoryCallbacks) return@runComposeUiTest

      val firstOwner = RecordingLifecycleOwner()
      val secondOwner = RecordingLifecycleOwner()
      val lifecycleOwner = mutableStateOf(firstOwner)
      val showEffect = mutableStateOf(true)
      val factory = RecordingTrimMemoryFactory()

      setContent {
        CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner.value) {
          if (showEffect.value) {
            Spacer(
              Modifier
                .size(10.dp)
                .hazeEffect(
                  factory = factory,
                  input = HazeInput.Content,
                  style = Unit,
                ),
            )
          }
        }
      }
      waitForIdle()

      runOnIdle {
        firstOwner.handleEvent(Lifecycle.Event.ON_CREATE)
        firstOwner.handleEvent(Lifecycle.Event.ON_START)
      }

      lifecycleOwner.value = secondOwner
      waitForIdle()

      runOnIdle {
        firstOwner.handleEvent(Lifecycle.Event.ON_STOP)
      }
      assertThat(factory.renderer.trimLevels).isEmpty()

      runOnIdle {
        secondOwner.handleEvent(Lifecycle.Event.ON_CREATE)
        secondOwner.handleEvent(Lifecycle.Event.ON_START)
        secondOwner.handleEvent(Lifecycle.Event.ON_STOP)
      }
      assertThat(factory.renderer.trimLevels).containsExactly(TrimMemoryLevel.MODERATE)

      showEffect.value = false
      waitForIdle()

      runOnIdle {
        secondOwner.handleEvent(Lifecycle.Event.ON_START)
        secondOwner.handleEvent(Lifecycle.Event.ON_STOP)
      }
      assertThat(factory.renderer.trimLevels).containsExactly(TrimMemoryLevel.MODERATE)
    }

  @Test
  fun lifecycleStop_reachesTypedEffectsForSourcesAndContent() = runComposeUiTest {
    if (!supportsLifecycleTrimMemoryCallbacks) return@runComposeUiTest

    val lifecycleOwner = RecordingLifecycleOwner()
    val hazeState = HazeState()
    val typedSourcesFactory = RecordingTrimMemoryFactory()
    val typedContentFactory = RecordingTrimMemoryFactory()

    setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        Spacer(Modifier.size(10.dp).hazeSource(hazeState))
        Spacer(
          Modifier
            .size(10.dp)
            .hazeEffect(
              factory = typedSourcesFactory,
              input = HazeInput.Sources(hazeState),
              style = Unit,
            ),
        )
        Spacer(
          Modifier
            .size(10.dp)
            .hazeEffect(
              factory = typedContentFactory,
              input = HazeInput.Content,
              style = Unit,
            ),
        )
      }
    }
    waitForIdle()

    runOnIdle {
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_CREATE)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
      lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
    }

    val expected = listOf(TrimMemoryLevel.MODERATE)
    assertThat(typedSourcesFactory.renderer.trimLevels).containsExactly(*expected.toTypedArray())
    assertThat(typedContentFactory.renderer.trimLevels).containsExactly(*expected.toTypedArray())
  }
}

private class RecordingLifecycleOwner : LifecycleOwner {
  private val registry = LifecycleRegistry(this)

  override val lifecycle: Lifecycle
    get() = registry

  fun handleEvent(event: Lifecycle.Event) {
    registry.handleLifecycleEvent(event)
  }
}

private class RecordingTrimMemoryFactory : HazeEffectFactory<Unit> {
  val renderer = RecordingTrimMemoryRenderer()

  override fun createRenderer(): HazeEffectRenderer<Unit> = renderer
}

private class RecordingTrimMemoryRenderer : HazeEffectRenderer<Unit> {
  val trimLevels = mutableListOf<TrimMemoryLevel>()

  override fun HazeEffectDrawScope.draw(style: Unit) = Unit

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: Unit): Rect = modifierBounds

  override fun onTrimMemory(level: TrimMemoryLevel) {
    trimLevels += level
  }
}
