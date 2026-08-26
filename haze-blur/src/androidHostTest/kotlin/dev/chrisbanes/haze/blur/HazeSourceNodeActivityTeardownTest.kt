// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isTrue
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.HazeRoborazziDefaults
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.experimental.LazyApplication

@Config(
  manifest = Config.NONE,
  application = Application::class,
  sdk = [Config.NEWEST_SDK],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LazyApplication(LazyApplication.LazyLoad.ON)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Suppress("DEPRECATION") // This regression specifically covers Compose UI Test v1 teardown.
class HazeSourceNodeActivityTeardownTest(
  private val qualifiers: String,
) {

  private val contentSlotDisposed = AtomicBoolean(false)
  private val snapshotApplyAfterContentSlotDispose = AtomicBoolean(false)
  private var snapshotApplyObserver: ObserverHandle? = null

  init {
    RuntimeEnvironment.setQualifiers(qualifiers)
  }

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeRule,
    captureRoot = composeRule.onRoot(),
    options = RoborazziRule.Options(
      outputDirectoryPath = "screenshots/android",
      roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
    ),
  )

  @Before
  fun observeTeardownSnapshotApplies() {
    snapshotApplyObserver = Snapshot.registerApplyObserver { _, _ ->
      if (contentSlotDisposed.get()) {
        snapshotApplyAfterContentSlotDispose.set(true)
      }
    }
  }

  @After
  fun cleanup() {
    var closeFailure: Throwable? = null
    try {
      composeRule.activityRule.scenario.close()
    } catch (failure: Throwable) {
      closeFailure = failure
      throw failure
    } finally {
      println(
        "Haze teardown probe: contentSlotDisposed=${contentSlotDisposed.get()}, " +
          "snapshotApplyAfterContentSlotDispose=${snapshotApplyAfterContentSlotDispose.get()}",
      )
      snapshotApplyObserver?.dispose()
      snapshotApplyObserver = null
      if (closeFailure == null) {
        assertThat(contentSlotDisposed.get()).isTrue()
        assertThat(snapshotApplyAfterContentSlotDispose.get()).isTrue()
      }
    }
  }

  @Test
  fun activityScenarioClose_withScaffoldSlots_doesNotAccessDetachedNode() {
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        MaterialTheme {
          Surface {
            val hazeState = remember { HazeState() }
            Scaffold(
              topBar = {
                Box(
                  Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .hazeBlur(
                      input = HazeInput.Sources(hazeState),
                      style = HazeBlurStyle { blurRadius(20.dp) },
                    ),
                )
              },
            ) { padding ->
              DisposableEffect(Unit) {
                onDispose { contentSlotDisposed.set(true) }
              }
              Box(
                Modifier
                  .padding(padding)
                  .fillMaxSize()
                  .hazeSource(hazeState),
              )
            }
          }
        }
      }
    }
    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage()
  }

  private companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    fun deviceQualifiers(): List<Array<String>> = listOf(
      arrayOf(RobolectricDeviceQualifiers.Pixel5),
      arrayOf(RobolectricDeviceQualifiers.Pixel7),
      arrayOf(RobolectricDeviceQualifiers.PixelFold),
    )
  }
}
