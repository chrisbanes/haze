// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import assertk.assertThat
import assertk.assertions.isTrue
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Unsupported collections return: the Android test engine reports JUnit assumptions as failures.
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  @Test
  fun generate() = baselineProfileRule.collect(
    packageName = TARGET_PACKAGE,
  ) {
    startActivityAndWait { intent ->
      intent.putExtra(FORCE_BLUR_EXTRA, true)
      intent.selectBenchmarkSample("images-list", "blur")
    }
    device.waitForImagesList()
    device.repeatedScrolls("lazy_column")

    startActivityAndWait { intent ->
      intent.putExtra(FORCE_BLUR_EXTRA, true)
      intent.selectBenchmarkSample("scaffold", "blur")
    }
    device.waitForScaffoldWithEquivalentStyleChurn()
    device.repeatedScrolls("lazy_grid")

    startActivityAndWait { intent ->
      intent.putExtra(FORCE_BLUR_EXTRA, true)
      intent.selectBenchmarkSample("credit-card", "blur")
    }
    device.waitForCreditCard()
    device.repeatedDrags("credit_card_2")

    startActivityAndWait { intent ->
      intent.selectBenchmarkSample("glass-product", "glass")
    }
    device.waitForGlassProduct()
    device.advanceGlassProduct()

    startActivityAndWait { intent ->
      intent.selectBenchmarkSample("glass-playground", "glass")
    }
    device.waitForGlassPlayground()
  }

  @Test
  fun generateBlurModes() = withoutUiAutomatorIdleWait {
    baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
      for (mode in listOf("quality", "balanced", "performance")) {
        val scenarioId = "source_update_$mode"
        startActivityAndWait { intent ->
          intent.putExtra(FORCE_BLUR_EXTRA, true)
          intent.selectBenchmarkSample("blur-profiling", "blur", scenarioId)
        }
        device.waitForBlurProfilingScenario(scenarioId)
        device.collectProfilingScenario("blur", scenarioId)
      }
    }
  }

  @Test
  fun generateGlassModes() {
    if (Build.VERSION.SDK_INT < 33) return
    withoutUiAutomatorIdleWait {
      baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
        for (mode in listOf("quality", "balanced", "performance")) {
          val scenarioId = "source_update_$mode"
          startActivityAndWait { intent ->
            intent.selectBenchmarkSample("glass-profiling", "glass", scenarioId)
          }
          device.waitForGlassProfilingScenario(scenarioId)
          device.collectProfilingScenario("glass", scenarioId)
        }
      }
    }
  }

  @Test
  fun generateGlassContainer() {
    if (Build.VERSION.SDK_INT < 33) return
    baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
      startActivityAndWait { intent ->
        intent.selectBenchmarkSample("glass-container", "glass")
      }
      device.waitForObject(By.res("glass_container_scroll"))
        .apply { setGestureMarginPercentage(0.1f) }
        .scrollUntil(Direction.DOWN, Until.findObject(By.res("glass_container_toggle")))
        .click()
      device.waitForObject(By.res("glass_container_extra_member"))
      device.waitForIdle()
      device.waitForObject(By.res("glass_container_toggle")).click()
      assertThat(device.wait(Until.gone(By.res("glass_container_extra_member")), 5.seconds)).isTrue()
    }
  }

  @Test
  fun generateNativeBackdrop() {
    if (!isBackdropSdkSupported()) return
    withoutUiAutomatorIdleWait {
      baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
        val scenarioId = "backdrop_source_update_quality"
        startActivityAndWait { intent ->
          intent.putExtra(FORCE_BLUR_EXTRA, true)
          intent.selectBenchmarkSample("blur-profiling", "blur", scenarioId)
        }
        device.waitForBlurProfilingScenario(scenarioId)
        device.collectProfilingScenario("blur", scenarioId)

        startActivityAndWait { intent ->
          intent.selectBenchmarkSample("glass-profiling", "glass", scenarioId)
        }
        device.waitForGlassProfilingScenario(scenarioId)
        device.collectProfilingScenario("glass", scenarioId)
      }
    }
  }
}

private fun UiDevice.collectProfilingScenario(effect: String, scenarioId: String) {
  val start = By.res("${effect}_profiling_start")
  repeat(3) {
    waitForObject(start).click()
    if (wait(Until.gone(start), 1.seconds)) {
      waitForObject(By.res("${effect}_profiling_phase_complete"), 10.seconds)
      return
    }
  }
  error("$effect profiling scenario $scenarioId never started")
}
