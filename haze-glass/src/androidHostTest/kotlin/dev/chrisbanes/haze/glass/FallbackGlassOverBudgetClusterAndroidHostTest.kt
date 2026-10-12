// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * A fallback cluster wider than the layer budget must still draw its on-screen part in bounded
 * layers, so its overlap matches a cluster within budget, and must not cut on-screen glass.
 */
@Config(sdk = [31])
class FallbackGlassOverBudgetClusterAndroidHostTest : ScreenshotTest() {
  private val layers = mutableListOf<Rect>()

  @After
  fun clearHook() {
    glassLayerBoundsForTest = null
  }

  @Test
  fun overBudgetCluster_overlapMatchesWithinBudgetCluster() = runScreenshotTest {
    val scene = clusterScene()
    val narrow = captureRootPixels()
    scene.assertOneFallbackSurface()
    scene.width.value = 2000.dp
    waitForIdle()
    scene.assertOneFallbackSurface()
    val wide = captureRootPixels()

    // Member 0's span, all inside member 1.
    val d = scene.density
    var differing = 0
    for (y in px(100.dp, d) until px(220.dp, d)) {
      for (x in px(120.dp, d) until px(160.dp, d)) {
        val a = narrow[x, y]
        val b = wide[x, y]
        val diff = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
        if (diff > 2f / 255f) differing++
      }
    }
    assertThat(differing, "overlap pixels differing by more than 2 levels").isEqualTo(0)
  }

  @Test
  fun overBudgetCluster_requestsLayersBoundedByWindow() = runScreenshotTest {
    val scene = clusterScene()
    scene.width.value = 2000.dp
    waitForIdle()
    glassLayerBoundsForTest = { layers += it }
    val rootWidth = captureRootPixels().width
    glassLayerBoundsForTest = null
    scene.assertOneFallbackSurface()

    // The cluster origin sits a spacing above and left of its members, so the union starts at
    // (40, 40) dp and ends 160 dp down in surface space; the window cuts it at the root width.
    val d = scene.density
    val expected = Rect(floor(40 * d), floor(40 * d), rootWidth.toFloat(), ceil(160 * d))
    // Background colour, input and tint, at least once each.
    assertThat(layers).prop(List<Rect>::size).isGreaterThanOrEqualTo(3)
    assertThat(layers).each { it.isEqualTo(expected) }
  }

  @Test
  fun standaloneOverBudgetMember_requestsInputLayerBoundedByWindow() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    var density = 0f
    setContent {
      density = LocalDensity.current.density
      Root(state) {
        Box(Modifier.offset(120.dp, 100.dp).wideMember(2000.dp).hazeGlass(effect, state, style(RoundedCornerShape(60.dp))))
      }
    }
    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    glassLayerBoundsForTest = { layers += it }
    val rootWidth = captureRootPixels().width
    glassLayerBoundsForTest = null

    // The member starts 120 dp into the root, so the window cuts its own bounds that much short.
    val expected = Rect(0f, 0f, rootWidth - floor(120 * density), ceil(120 * density))
    assertThat(layers).isNotEmpty()
    assertThat(layers).each { it.isEqualTo(expected) }
  }

  @Test
  fun clusterOverflowingLayerAncestor_keepsOnScreenGlass() = runScreenshotTest {
    val state = HazeState()
    val effects = List(2) { GlassRuntimeEffect() }
    var density = 0f
    setContent {
      density = LocalDensity.current.density
      Root(state) {
        // A non-clipping layer narrower than the cluster: its recording canvas clip is not the screen.
        Box(Modifier.size(150.dp, 400.dp).graphicsLayer()) {
          Box(Modifier.fillMaxSize().hazeGlassContainer(40.dp)) {
            Box(Modifier.offset(40.dp, 100.dp).size(120.dp).hazeGlass(effects[0], state, style(RoundedCornerShape(50))))
            Box(Modifier.offset(120.dp, 100.dp).size(200.dp, 120.dp).hazeGlass(effects[1], state, style(RoundedCornerShape(60.dp))))
          }
        }
      }
    }
    val pixels = captureRootPixels()
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)

    // Inside member 1, outside the layer ancestor, on screen.
    assertThat(pixels[px(250.dp, density), px(160.dp, density)].red, "red").isLessThan(0.9f)
  }

  @Test
  fun standaloneMemberMovedOnScreenByLayer_drawsInputInRevealedPart() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val shift = mutableStateOf(0f)
    var density = 0f
    setContent {
      density = LocalDensity.current.density
      Box(Modifier.fillMaxSize().testTag("root").background(Color.White)) {
        // The source moves with the member, so only the member's window position changes.
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shift.value }) {
          Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(1000.dp, 400.dp).hazeSource(state).background(Color.Red))
          // Opaque white, so the red input is the only red inside the member.
          Box(
            Modifier.offset(250.dp, 100.dp).size(300.dp, 120.dp)
              .hazeGlass(effect, state, GlassStyle { backgroundColor(Color.White) }),
          )
        }
      }
    }
    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    // 250 dp into the member: past the window's right edge before the move.
    val x = px(300.dp, density)
    val y = px(160.dp, density)
    captureRootPixels()
    shift.value = -200 * density
    waitForIdle()
    val moved = captureRootPixels()[x, y]
    assertThat(moved.red - moved.green, "red over green").isGreaterThan(0.3f)
  }

  @Test
  fun standaloneContentMemberMovedOnScreenByLayer_drawsInputInRevealedPart() = runScreenshotTest {
    val effect = GlassRuntimeEffect()
    val shift = mutableStateOf(0f)
    var density = 0f
    setContent {
      density = LocalDensity.current.density
      Box(Modifier.fillMaxSize().testTag("root").background(Color.White)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shift.value }) {
          // Opaque white over red content, so red inside the member comes only from the input.
          Box(
            Modifier.offset(250.dp, 100.dp).size(300.dp, 120.dp)
              .hazeGlass(
                factory = HazeEffectFactory { effect },
                input = HazeInput.Content,
                style = GlassStyle { backgroundColor(Color.White) },
                performanceMode = null,
                expandLayerBounds = true,
                interactionSource = null,
              ),
          ) {
            Box(Modifier.fillMaxSize().background(Color.Red))
          }
        }
      }
    }
    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    val x = px(300.dp, density)
    val y = px(160.dp, density)
    captureRootPixels()
    shift.value = -200 * density
    waitForIdle()
    val moved = captureRootPixels()[x, y]
    assertThat(moved.red - moved.green, "red over green").isGreaterThan(0.3f)
  }

  private class ClusterScene(val effects: List<GlassRuntimeEffect>) {
    val width = mutableStateOf(200.dp)
    var density = 0f

    fun assertOneFallbackSurface() {
      val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
      assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
      owner.renderer.runtimeSurfaces.forEach { assertThat(it.delegate).isInstanceOf<FallbackGlassDelegate>() }
    }
  }

  private fun ScreenshotUiTest.clusterScene(): ClusterScene {
    val state = HazeState()
    val scene = ClusterScene(List(2) { GlassRuntimeEffect() })
    setContent {
      scene.density = LocalDensity.current.density
      Root(state) {
        Box(Modifier.fillMaxSize().hazeGlassContainer(40.dp)) {
          Box(Modifier.offset(40.dp, 100.dp).size(120.dp).hazeGlass(scene.effects[0], state, style(RoundedCornerShape(50))))
          Box(Modifier.offset(120.dp, 100.dp).wideMember(scene.width.value).hazeGlass(scene.effects[1], state, style(RoundedCornerShape(60.dp))))
        }
      }
    }
    return scene
  }

  @Composable
  private fun Root(state: HazeState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("root").background(Color.White)) {
      Box(Modifier.fillMaxSize().hazeSource(state).background(Color(0xFFFF2020)))
      content()
    }
  }

  private fun Modifier.wideMember(width: Dp): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width, 120.dp)

  private fun Modifier.hazeGlass(effect: GlassRuntimeEffect, state: HazeState, style: GlassStyle): Modifier = hazeGlass(
    factory = HazeEffectFactory { effect },
    input = HazeInput.Sources(state),
    style = style,
    performanceMode = null,
    expandLayerBounds = true,
    interactionSource = null,
  )

  // Default edge and highlight brushes scale with the surface, so they are off.
  private fun style(shape: RoundedCornerShape) = GlassStyle {
    shape(shape)
    backgroundColor(Color(0x8020C040))
    tint(Color(0x802060E0))
    specularIntensity(0f)
    edgeSoftness(0.dp)
    edgeShadow(Color.Transparent)
    ambientResponse(0f)
  }

  private fun px(value: Dp, density: Float): Int = (value.value * density).toInt()
}
