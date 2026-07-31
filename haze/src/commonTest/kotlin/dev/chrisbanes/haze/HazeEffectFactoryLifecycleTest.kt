// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeEffectFactoryLifecycleTest : ContextTest() {

  @Test
  fun sharedFactory_createsOneRendererPerNode() = runComposeUiTest {
    val factory = RecordingFactory()

    setContent {
      Box {
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = RecordingStyle("first"),
            ),
        )
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = RecordingStyle("second"),
            ),
        )
      }
    }
    waitForIdle()

    assertThat(factory.renderers.size).isEqualTo(2)
    assertThat(factory.renderers[0]).isNotSameInstanceAs(factory.renderers[1])
  }

  @Test
  fun styleReplacement_reusesRenderer() = runComposeUiTest {
    val factory = RecordingFactory()
    val style = mutableStateOf(RecordingStyle("first"))

    setContent {
      Spacer(
        Modifier
          .size(10.dp)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = style.value,
          ),
      )
    }
    waitForIdle()

    val renderer = factory.renderers.single()

    style.value = RecordingStyle("second")
    waitForIdle()

    assertThat(factory.renderers.single()).isSameInstanceAs(renderer)
  }

  @Test
  fun factoryReplacementAndDetach_disposeEachRendererExactlyOnce() = runComposeUiTest {
    val firstFactory = RecordingFactory()
    val secondFactory = RecordingFactory()
    val factory = mutableStateOf<HazeEffectFactory<RecordingStyle>>(firstFactory)
    val showEffect = mutableStateOf(true)

    setContent {
      if (showEffect.value) {
        Spacer(
          Modifier
            .size(10.dp)
            .hazeEffect(
              factory = factory.value,
              input = HazeInput.Content,
              style = RecordingStyle("style"),
            ),
        )
      }
    }
    waitForIdle()

    factory.value = secondFactory
    waitForIdle()

    val firstRenderer = firstFactory.renderers.single()
    val secondRenderer = secondFactory.renderers.single()
    assertThat(firstRenderer.disposeCalls).isEqualTo(1)
    assertThat(secondRenderer.disposeCalls).isEqualTo(0)

    showEffect.value = false
    waitForIdle()

    assertThat(firstRenderer.disposeCalls).isEqualTo(1)
    assertThat(secondRenderer.disposeCalls).isEqualTo(1)
  }

  @Test
  fun equalButDistinctFactoryReplacement_usesFactoryIdentity() = runComposeUiTest {
    val firstFactory = EqualRecordingFactory()
    val secondFactory = EqualRecordingFactory()
    val factory = mutableStateOf<HazeEffectFactory<RecordingStyle>>(
      firstFactory,
      referentialEqualityPolicy(),
    )

    setContent {
      Spacer(
        Modifier
          .size(10.dp)
          .hazeEffect(
            factory = factory.value,
            input = HazeInput.Content,
            style = RecordingStyle("style"),
          ),
      )
    }
    waitForIdle()

    factory.value = secondFactory
    waitForIdle()

    assertThat(firstFactory.renderers.single().disposeCalls).isEqualTo(1)
    assertThat(secondFactory.renderers.single().disposeCalls).isEqualTo(0)
  }
}

@Poko
private class RecordingStyle(val value: String)

private class RecordingFactory : HazeEffectFactory<RecordingStyle> {
  val renderers = mutableListOf<RecordingRenderer>()

  override fun createRenderer(): HazeEffectRenderer<RecordingStyle> {
    return RecordingRenderer().also(renderers::add)
  }
}

private class EqualRecordingFactory : HazeEffectFactory<RecordingStyle> {
  val renderers = mutableListOf<RecordingRenderer>()

  override fun createRenderer(): HazeEffectRenderer<RecordingStyle> {
    return RecordingRenderer().also(renderers::add)
  }

  override fun equals(other: Any?): Boolean = other is EqualRecordingFactory

  override fun hashCode(): Int = 0
}

private class RecordingRenderer : HazeEffectRenderer<RecordingStyle> {
  val trimLevels = mutableListOf<TrimMemoryLevel>()
  var disposeCalls = 0

  override fun HazeEffectDrawScope.draw(style: RecordingStyle) = Unit

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: RecordingStyle): Rect {
    return modifierBounds
  }

  override fun dispose() {
    disposeCalls++
  }

  override fun onTrimMemory(level: TrimMemoryLevel) {
    trimLevels += level
  }
}

@Suppress("unused")
private fun <Style> typedPairingCompiles(
  factory: HazeEffectFactory<Style>,
  style: Style,
): Modifier = Modifier.hazeEffect(
  factory = factory,
  input = HazeInput.Content,
  style = style,
)
