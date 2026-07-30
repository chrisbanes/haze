// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.hazeSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeBlurModifierTest {

  @Test
  fun contentInput_resolvesDefaultsLocalAndExplicitStyles() = runComposeUiTest {
    val localStyle = HazeBlurStyle {
      blurEnabled(false)
      fallbackColorEffect(HazeColorEffect.tint(Color.Red))
    }
    val explicitStyle = mutableStateOf(
      HazeBlurStyle {
        fallbackColorEffect(HazeColorEffect.tint(Color.Blue))
      },
    )

    setContent {
      CompositionLocalProvider(LocalHazeBlurStyle provides localStyle) {
        Box(
          Modifier
            .size(100.dp)
            .testTag("effect")
            .hazeBlur(
              input = HazeInput.Content,
              style = explicitStyle.value,
            )
            .background(Color.White),
        )
      }
    }

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)

    explicitStyle.value = HazeBlurStyle
    waitForIdle()

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun sourceInput_drawsTheSelectedSourceWithFallbackStyle() = runComposeUiTest {
    val state = HazeState()
    val style = HazeBlurStyle {
      blurEnabled(false)
      fallbackColorEffect(HazeColorEffect.tint(Color.Blue))
    }

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state)
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testTag("effect")
            .hazeBlur(
              input = HazeInput.Sources(state),
              style = style,
              sampling = HazeSampling.FullResolution,
              expandLayerBounds = false,
            ),
        )
      }
    }

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
  }

  @Test
  fun sharedFactory_createsIsolatedRuntimesAndStyleReplacementReusesRuntime() {
    val sharedStyle = HazeBlurStyle {
      blurRadius(12.dp)
    }
    val first = HazeBlurFactory.createVisualEffect(
      style = sharedStyle,
      sampling = HazeSampling.Default,
    ) as BlurHazeEffectFactoryVisualEffect
    val second = HazeBlurFactory.createVisualEffect(
      style = sharedStyle,
      sampling = HazeSampling.Default,
    ) as BlurHazeEffectFactoryVisualEffect

    assertThat(first.effect).isNotSameInstanceAs(second.effect)

    val firstRuntime = first.effect
    first.updateStyle(
      style = HazeBlurStyle {
        blurRadius(24.dp)
      },
      sampling = HazeSampling.FullResolution,
    )

    assertThat(first.effect).isSameInstanceAs(firstRuntime)
    assertThat(first.effect.blurRadius).isEqualTo(24.dp)
    assertThat(second.effect.blurRadius).isEqualTo(12.dp)
  }

  @Test
  fun detachingOneSharedFactoryRuntime_releasesOnlyThatRuntime() {
    val style = HazeBlurStyle
    val first = HazeBlurFactory.createVisualEffect(
      style = style,
      sampling = HazeSampling.Default,
    ) as BlurHazeEffectFactoryVisualEffect
    val second = HazeBlurFactory.createVisualEffect(
      style = style,
      sampling = HazeSampling.Default,
    ) as BlurHazeEffectFactoryVisualEffect
    val firstDelegate = ModifierTrackingDelegate()
    val secondDelegate = ModifierTrackingDelegate()
    first.effect.delegate = firstDelegate
    second.effect.delegate = secondDelegate

    first.attach(TestVisualEffectContext)
    second.attach(TestVisualEffectContext)
    first.detach(TestVisualEffectContext)

    assertThat(firstDelegate.attachCount).isEqualTo(1)
    assertThat(firstDelegate.detachCount).isEqualTo(1)
    assertThat(secondDelegate.attachCount).isEqualTo(1)
    assertThat(secondDelegate.detachCount).isEqualTo(0)

    second.detach(TestVisualEffectContext)
    assertThat(secondDelegate.detachCount).isEqualTo(1)
  }
}

private class ModifierTrackingDelegate : BlurVisualEffect.Delegate {
  var attachCount = 0
    private set
  var detachCount = 0
    private set

  override fun attach() {
    attachCount++
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit

  override fun detach() {
    detachCount++
  }
}

@OptIn(ExperimentalHazeApi::class)
private data object TestVisualEffectContext : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = Size.Zero
  override val layerSize: Size = Size.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun positionOf(area: HazeArea): Offset = area.coordinates.localPosition

  override fun boundsOf(area: HazeArea): Rect? {
    val position = area.coordinates.localPosition
    return if (position.isSpecified && area.size.isSpecified) Rect(position, area.size) else null
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in modifier tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in modifier tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in modifier tests")
  override fun invalidateDraw() = Unit
}

@Suppress("unused")
// HazeEffectInputTest behaviorally verifies these structural policies at the shared core seam.
// This compile inventory verifies that the typed Blur entry point forwards every policy shape.
private fun everyTypedBlurPolicyCompiles(
  state: HazeState,
  style: HazeBlurStyle,
): Modifier = Modifier
  .hazeBlur(
    input = HazeInput.Sources(
      state = state,
      retention = HazeSourceRetention.KeepLastFrame,
    ),
    style = style,
    sampling = HazeSampling.Default,
    expandLayerBounds = true,
  )
  .hazeBlur(
    input = HazeInput.Sources(
      state = state,
      retention = HazeSourceRetention.ClearWhenUnavailable,
    ),
    sampling = HazeSampling.Adaptive,
  )
  .hazeBlur(
    input = HazeInput.Content,
    sampling = HazeSampling.Fixed(0.6f),
  )
