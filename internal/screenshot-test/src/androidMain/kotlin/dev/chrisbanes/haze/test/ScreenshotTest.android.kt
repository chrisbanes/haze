// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.test

import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoboComponent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeEffectNode
import dev.chrisbanes.haze.glass.GlassRuntimeEffect
import dev.chrisbanes.haze.glass.RuntimeShaderGlassDelegate
import dev.chrisbanes.haze.notifyInputPresentationListeners
import kotlinx.coroutines.CancellationException
import org.junit.Rule
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 32, 35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual abstract class ScreenshotTest : ContextTest() {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(
      outputDirectoryPath = "screenshots/android",
      roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
    ),
  )
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
actual fun ScreenshotTest.runScreenshotTest(
  size: Size,
  block: ScreenshotUiTest.() -> Unit,
) {
  provideRoborazziContext().setRuleOverrideRoborazziOptions(HazeRoborazziDefaults.roborazziOptions)
  createScreenshotUiTest(composeTestRule).block()
}

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
private fun createScreenshotUiTest(rule: AndroidComposeTestRule<*, *>) =
  object : ScreenshotUiTest {
    override val supportsRuntimeBlur: Boolean = Build.VERSION.SDK_INT >= 31

    override fun setContent(content: @Composable () -> Unit) {
      rule.setContent {
        // Keep Glass snapshots independent from the host OS appearance. Nested providers still win.
        val configuration = Configuration(LocalConfiguration.current).apply {
          uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
      }
      rule.waitForIdle()
      awaitGlassInputs()
    }

    override fun captureRoot(
      nameSuffix: String?,
      unmatchedPixelThreshold: Float?,
      artifactPath: String?,
    ) {
      awaitGlassInputs()
      val output = artifactPath?.substringAfterLast('/') ?: when {
        nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.webp"
        else -> "${roboOutputName()}_$nameSuffix.webp"
      }
      val options = unmatchedPixelThreshold
        ?.let(HazeRoborazziDefaults::roborazziOptions)
        ?: HazeRoborazziDefaults.roborazziOptions
      val context = provideRoborazziContext()
      val previousOutputDirectory = context.outputDirectory
      artifactPath?.substringBeforeLast('/', missingDelimiterValue = "")?.takeIf(String::isNotEmpty)
        ?.let { provideRoborazziContext().setRuleOverrideOutputDirectory(it) }
      try {
        rule.onRoot().captureRoboImage(output, options)
      } finally {
        if (artifactPath != null) context.setRuleOverrideOutputDirectory(previousOutputDirectory)
      }
    }

    override fun captureRootPixels(): PixelMap {
      awaitGlassInputs()
      val bitmap = RoboComponent.Compose(
        node = rule.onRoot().fetchSemanticsNode("Failed to capture root pixels"),
        roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
      ).image
      return requireNotNull(bitmap).asImageBitmap().toPixelMap()
    }

    override fun onNodeWithTag(testTag: String) = rule.onNodeWithTag(testTag)

    override fun waitForIdle() {
      rule.waitForIdle()
      awaitGlassInputs()
    }

    private fun drawRoot() {
      requireNotNull(
        RoboComponent.Compose(
          node = rule.onRoot().fetchSemanticsNode("Failed to draw root for capture readiness"),
          roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
        ).image,
      ).recycle()
    }

    private var hostCaptureFailure: Exception? = null
    private val hostGlassInputCapture: suspend (GraphicsLayer) -> ImageBitmap = { layer ->
      try {
        layer.captureHostGlassInputSnapshot()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        hostCaptureFailure = failure
        throw failure
      }
    }

    private fun awaitGlassInputs() {
      rule.waitUntil(timeoutMillis = 60_000) {
        val root = rule.onRoot().fetchSemanticsNode("Failed to inspect capture readiness").layoutNode
        val newlyAdapted = mutableListOf<HazeEffectNode>()
        val before = rule.runOnIdle {
          if (Build.VERSION.SDK_INT >= 34) {
            root.installHostGlassInputCapture(hostGlassInputCapture, newlyAdapted)
          }
          root.glassInputReadiness()
        }
        // The final draw can enqueue downstream invalidation; dispatch it before comparing inputs.
        drawRoot()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
        drawRoot()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
        rule.runOnIdle {
          hostCaptureFailure?.let { throw AssertionError("Android host Glass input capture failed", it) }
          // setContent can capture before the adapter is installed. After a completed host draw,
          // invalidate those inputs through the existing source event so even static consumers
          // display adapted captures. Callback identity makes this a single installation event.
          newlyAdapted.flatMap { it.areas }.distinct().forEach { area ->
            area.notifyInputPresentationListeners()
          }
          val after = root.glassInputReadiness()
          if (!rule.mainClock.autoAdvance && before == after) {
            // A root capture can record a changed source while the test clock is paused. Request
            // its visible copied inputs through the existing event without advancing animation time.
            root.staleVisibleGlassInputAreas(hostGlassInputCapture).distinct().forEach { area ->
              area.notifyInputPresentationListeners()
            }
          }
          newlyAdapted.isEmpty() && !root.hasPendingGlassInput() && before == after
        }
      }
    }

    override fun swipeUpOnRoot() {
      rule.onRoot().performTouchInput {
        swipeUp()
      }
    }
  }

private fun LayoutNode.hasPendingGlassInput(): Boolean {
  var node: Modifier.Node? = nodes.head
  while (node != null) {
    val effect = (node as? HazeEffectNode)?.sourceBackedGlassEffect()
    val delegate = effect?.delegate as? RuntimeShaderGlassDelegate
    if (delegate != null) {
      val displayedOwners = if (delegate.displayedImmutableInput != null) 1 else 0
      if (delegate.immutableInputOwnerCount != displayedOwners) return true
      val snapshot = node.inputSnapshot()
      // Transparent Glass intentionally skips preparation and retains its displayed input.
      if (effect.alpha != 0f && snapshot != null && snapshot != delegate.lastSuccessfulSourceSnapshot?.inputSnapshot) return true
    }
    node = node.child
  }
  return children.any { it.hasPendingGlassInput() }
}

@RequiresApi(34)
private fun LayoutNode.installHostGlassInputCapture(
  capture: suspend (GraphicsLayer) -> ImageBitmap,
  installed: MutableList<HazeEffectNode>,
) {
  var node: Modifier.Node? = nodes.head
  while (node != null) {
    val effect = (node as? HazeEffectNode)?.sourceBackedGlassEffect()
    val delegate = effect?.delegate as? RuntimeShaderGlassDelegate
    if (delegate != null && delegate.captureImmutableInput !== capture) {
      delegate.captureImmutableInput = capture
      installed += node
    }
    node = node.child
  }
  children.forEach { it.installHostGlassInputCapture(capture, installed) }
}

private fun LayoutNode.glassInputReadiness(): List<Any?> = buildList {
  var node: Modifier.Node? = nodes.head
  while (node != null) {
    val effectNode = node as? HazeEffectNode
    val effect = effectNode?.sourceBackedGlassEffect()
    val delegate = effect?.delegate as? RuntimeShaderGlassDelegate
    if (delegate != null) {
      add(
        listOf(
          effectNode,
          effectNode.inputSnapshot(),
          delegate.lastSuccessfulSourceSnapshot?.inputSnapshot,
          delegate.immutableInputCaptureCount,
          delegate.displayedImmutableInput,
          delegate.immutableInputOwnerCount,
        ),
      )
    }
    node = node.child
  }
  children.forEach { addAll(it.glassInputReadiness()) }
}

private fun LayoutNode.staleVisibleGlassInputAreas(
  capture: suspend (GraphicsLayer) -> ImageBitmap,
): List<HazeArea> = buildList {
  var node: Modifier.Node? = nodes.head
  while (node != null) {
    val effectNode = node as? HazeEffectNode
    val effect = effectNode?.sourceBackedGlassEffect()
    val delegate = effect?.delegate as? RuntimeShaderGlassDelegate
    if (delegate != null && effect.alpha != 0f && delegate.captureImmutableInput === capture) {
      val snapshot = effectNode.inputSnapshot()
      if (delegate.displayedImmutableInput != null && delegate.immutableInputOwnerCount == 1 &&
        snapshot != null && snapshot != delegate.lastSuccessfulSourceSnapshot?.inputSnapshot
      ) {
        addAll(effectNode.areas)
      }
    }
    node = node.child
  }
  children.forEach { addAll(it.staleVisibleGlassInputAreas(capture)) }
}

private fun HazeEffectNode.sourceBackedGlassEffect(): GlassRuntimeEffect? =
  if (isSourceBackedInput()) typedEffectRenderer as? GlassRuntimeEffect else null
