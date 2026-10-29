// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class HazeRootPositionStrategyAndroidTest : ScreenshotTest() {
  @Test
  fun siblingRootsInOneWindow_useScreenCoordinates() {
    val fixture = installStrategyFixture()
    composeTestRule.runOnIdle {
      val node = checkNotNull(fixture.effect)
      assertThat(node.windowId).isNotNull()
      fixture.state.areas.forEach { assertThat(it.windowId).isEqualTo(node.windowId) }
      assertThat(fixture.state.areas.first().rootCoordinates)
        .isNotSameInstanceAs(fixture.state.areas.last().rootCoordinates)
      assertThat(node.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Screen)
    }
  }

  @Test
  fun unselectedSiblingSource_keepsLocalCoordinates() {
    val fixture = installStrategyFixture(selectExternal = false)
    composeTestRule.runOnIdle {
      assertThat(fixture.state.areas.size).isEqualTo(2)
      val node = checkNotNull(fixture.effect)
      assertThat(node.areas.map { it.key }).isEqualTo(listOf("local"))
      assertThat(node.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Local)
    }
  }

  @Test
  fun siblingSourceJoiningAndLeaving_refreshesGeometryWithoutMovingEffect() {
    val fixture = installStrategyFixture(externalVisible = false)
    lateinit var initialTransform: List<Float>
    lateinit var initialLayerBounds: Rect
    composeTestRule.runOnIdle {
      val node = checkNotNull(fixture.effect)
      assertThat(node.resolvedPositionStrategy).isEqualTo(HazePositionStrategy.Local)
      initialTransform = node.sourceTransformInEffect(fixture.localArea()).values.toList()
      initialLayerBounds = Rect(Offset.Zero - node.layerOffset, node.layerSize)
      fixture.externalVisible = true
    }
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertGeometry(fixture, HazePositionStrategy.Screen, initialTransform, initialLayerBounds)
      fixture.externalVisible = false
    }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      assertGeometry(fixture, HazePositionStrategy.Local, initialTransform, initialLayerBounds)
    }
  }

  @Test
  fun equalTransformAcrossRoots_notifiesCoordinateObservers() {
    var first: LayoutCoordinates? = null
    var second: LayoutCoordinates? = null
    installRoots(
      first = { Box(Modifier.fillMaxSize().onGloballyPositioned { first = it }) },
      second = { Box(Modifier.fillMaxSize().onGloballyPositioned { second = it }) },
    )

    composeTestRule.runOnIdle {
      val area = HazeArea()
      val firstCoordinates = checkNotNull(first)
      val secondCoordinates = checkNotNull(second)
      area.updateLayoutCoordinates(firstCoordinates)
      val version = area.coordinateVersion
      val root = area.rootCoordinates
      val firstTransform = Matrix().also { firstCoordinates.transformToScreenOrRoot(it) }
      val secondTransform = Matrix().also { secondCoordinates.transformToScreenOrRoot(it) }
      assertThat(firstTransform.values.toList()).isEqualTo(secondTransform.values.toList())

      area.updateLayoutCoordinates(firstCoordinates)
      assertThat(area.coordinateVersion).isEqualTo(version)
      area.updateLayoutCoordinates(secondCoordinates)
      assertThat(area.rootCoordinates).isNotSameInstanceAs(root)
      assertThat(area.coordinateVersion, "root-only changes notify geometry observers").isEqualTo(version + 1)
      area.clearLayoutCoordinates()
      assertThat(area.layoutCoordinates).isNull()
      assertThat(area.rootCoordinates).isNull()
      area.updateLayoutCoordinates(secondCoordinates)
      assertThat(area.rootCoordinates).isSameInstanceAs(secondCoordinates.findRootCoordinates())
      assertThat(area.coordinateVersion).isEqualTo(version + 2)
    }
  }

  private fun installRoots(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    composeTestRule.runOnUiThread {
      val activity = composeTestRule.activity
      val scene = FrameLayout(activity)
      // Distinguish screen and root origins so stale geometry cannot satisfy the round trip.
      scene.setPadding(13, 29, 0, 0)
      scene.addView(ComposeView(activity).apply { setContent(content = first) }, FrameLayout.LayoutParams(-1, -1))
      scene.addView(ComposeView(activity).apply { setContent(content = second) }, FrameLayout.LayoutParams(-1, -1))
      activity.setContentView(scene)
    }
    composeTestRule.waitForIdle()
  }

  private fun installStrategyFixture(selectExternal: Boolean = true, externalVisible: Boolean = true): RootFixture {
    val fixture = RootFixture(externalVisible)
    val input = HazeInput.Sources(fixture.state, HazeSourceSelection.All.where { selectExternal || it.key == "local" })
    installRoots(
      first = {
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().hazeSource(fixture.state, key = "local"))
          Box(
            Modifier.offset(65.dp, 120.dp).size(200.dp, 120.dp)
              .onGloballyPositioned { fixture.effectCoordinates = it }
              .hazeEffect(ROOT_EFFECT_FACTORY, input, Unit)
              .then(RootEffectCaptureElement { fixture.effect = it }),
          )
        }
      },
      second = {
        if (fixture.externalVisible) Box(Modifier.fillMaxSize().hazeSource(fixture.state, key = "external"))
      },
    )
    return fixture
  }

  private fun assertGeometry(
    fixture: RootFixture,
    strategy: HazePositionStrategy,
    initialTransform: List<Float>,
    initialLayerBounds: Rect,
  ) {
    val node = checkNotNull(fixture.effect)
    val coordinates = checkNotNull(fixture.effectCoordinates)
    val root = coordinates.findRootCoordinates()
    assertThat(node.resolvedPositionStrategy).isEqualTo(strategy)
    val expectedPosition = if (strategy == HazePositionStrategy.Screen) coordinates.positionOnScreen() else coordinates.positionInRoot()
    val expectedRootPosition = if (strategy == HazePositionStrategy.Screen) root.positionOnScreen() else Offset.Zero
    assertThat(node.position).isEqualTo(expectedPosition)
    assertThat(node.rootBounds.topLeft).isEqualTo(expectedRootPosition)
    assertThat(node.rootBounds.width).isEqualTo(root.size.width.toFloat())
    assertThat(node.rootBounds.height).isEqualTo(root.size.height.toFloat())
    assertThat(node.sourceTransformInEffect(fixture.localArea()).values.toList()).isEqualTo(initialTransform)
    assertThat(Rect(Offset.Zero - node.layerOffset, node.layerSize)).isEqualTo(initialLayerBounds)
  }
}

private class RootFixture(externalVisible: Boolean) {
  val state = HazeState()
  var externalVisible by mutableStateOf(externalVisible)
  var effect: HazeEffectNode? = null
  var effectCoordinates: LayoutCoordinates? = null

  fun localArea(): HazeArea = state.areas.single { it.key == "local" }
}

private val ROOT_EFFECT_FACTORY = HazeEffectFactory<Unit> {
  object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
  }
}

private data class RootEffectCaptureElement(
  val onEffect: (HazeEffectNode) -> Unit,
) : ModifierNodeElement<RootEffectCaptureNode>() {
  override fun create() = RootEffectCaptureNode(onEffect)
  override fun update(node: RootEffectCaptureNode) {
    node.onEffect = onEffect
  }
  override fun InspectorInfo.inspectableProperties() {
    name = "captureEffect"
  }
}

private class RootEffectCaptureNode(var onEffect: (HazeEffectNode) -> Unit) : Modifier.Node() {
  override fun onAttach() {
    (findNearestAncestor(HazeTraversableNodeKeys.Effect) as? HazeEffectNode)?.let(onEffect)
  }
}
