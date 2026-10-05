// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import java.awt.Frame
import java.awt.HeadlessException
import java.awt.Window
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class, ExperimentalComposeUiApi::class)
class HazeMultiWindowTest : ContextTest() {

  @Test
  fun crossWindowEffect_capturesLocalAwtWindowAndPromotesToScreenStrategy() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    var capturedEffectNode: HazeEffectNode? = null

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(
            Modifier
              .size(100.dp)
              .hazeSource(hazeState),
          )
        }

        CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
          Spacer(
            Modifier
              .size(100.dp)
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              )
              .captureEffectNode { capturedEffectNode = it },
          )
        }
      }

      waitForIdle()

      assertThat(hazeState.areas).hasSize(1)
      assertThat(hazeState.areas.single().windowId).isSameInstanceAs(sourceWindow)
      assertThat(capturedEffectNode).isNotNull()
      assertThat(capturedEffectNode?.windowId).isSameInstanceAs(effectWindow)
      assertThat(capturedEffectNode?.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Screen)
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }

  @Test
  fun sameWindowEffect_staysInLocalStrategy() = runComposeUiTest {
    val hazeState = HazeState()
    val window = createTestWindow("SingleWindow")
    var capturedEffectNode: HazeEffectNode? = null

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides window) {
          Box(
            Modifier
              .size(100.dp)
              .hazeSource(hazeState),
          )

          Spacer(
            Modifier
              .size(100.dp)
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              )
              .captureEffectNode { capturedEffectNode = it },
          )
        }
      }

      waitForIdle()

      assertThat(hazeState.areas).hasSize(1)
      assertThat(hazeState.areas.single().windowId).isSameInstanceAs(window)
      assertThat(capturedEffectNode).isNotNull()
      assertThat(capturedEffectNode?.windowId).isSameInstanceAs(window)
      assertThat(capturedEffectNode?.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Local)
    } finally {
      (window as? Frame)?.dispose()
    }
  }
}

private fun createTestWindow(title: String): Window {
  return try {
    Frame(title)
  } catch (_: HeadlessException) {
    // In headless CI environments (e.g. Linux GitHub Actions runners without an X11/Wayland display),
    // Frame/Window constructors throw HeadlessException. Allocate a headless-safe Window instance
    // to verify reference identity and strategy resolution without requiring native display peers.
    val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply {
      isAccessible = true
    }
    val unsafe = unsafeField.get(null)
    val allocateMethod = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
    allocateMethod.invoke(unsafe, Window::class.java) as Window
  }
}

private object TestEffectFactory : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
  }
}

private fun Modifier.captureEffectNode(
  onEffectNode: (HazeEffectNode) -> Unit,
): Modifier = this then CaptureEffectNodeElement(onEffectNode)

private data class CaptureEffectNodeElement(
  val onEffectNode: (HazeEffectNode) -> Unit,
) : ModifierNodeElement<CaptureEffectNode>() {

  override fun create(): CaptureEffectNode = CaptureEffectNode(onEffectNode)

  override fun update(node: CaptureEffectNode) {
    node.onEffectNode = onEffectNode
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "captureEffectNode"
  }
}

private class CaptureEffectNode(
  var onEffectNode: (HazeEffectNode) -> Unit,
) : Modifier.Node() {

  override fun onAttach() {
    (findNearestAncestor(HazeTraversableNodeKeys.Effect) as? HazeEffectNode)?.let(onEffectNode)
  }
}
