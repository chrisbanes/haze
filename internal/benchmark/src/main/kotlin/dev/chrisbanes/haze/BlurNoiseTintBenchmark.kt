// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlurNoiseTintBenchmark {
  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test fun stable1() = measure("stable", 1)

  @Test fun stable3() = measure("stable", 3)

  @Test fun tint1() = measure("tint", 1)

  @Test fun tint3() = measure("tint", 3)

  @Test fun radius1() = measure("radius", 1)

  @Test fun radius3() = measure("radius", 3)

  @Test fun noise1() = measure("noise", 1)

  @Test fun noise3() = measure("noise", 3)

  @Test fun cold1() = measure("stable", 1, cold = true)

  @Test fun cold3() = measure("stable", 3, cold = true)

  @OptIn(ExperimentalMetricApi::class)
  private fun measure(property: String, nodes: Int, cold: Boolean = false) {
    requireGlassBenchmarkDevice()
    verifyFrozenApks()
    val scenario = "noise_tint_${property}_$nodes"
    withoutUiAutomatorIdleWait {
      benchmarkRule.measureRepeated(
        packageName = "dev.chrisbanes.haze.sample.android",
        metrics = listOf(
          FrameTimingMetric(),
          TraceSectionMetric("HazeBlur.combinedNoiseTint", TraceSectionMetric.Mode.Count, "combinedNoiseTint"),
          TraceSectionMetric("HazeRuntimeShader.construct", TraceSectionMetric.Mode.Sum, "runtimeShaderConstruct"),
        ),
        startupMode = if (cold) StartupMode.COLD else StartupMode.WARM,
        iterations = 8,
        setupBlock = {
          if (!cold) {
            startActivityAndWait { it.selectBenchmarkSample("blur-profiling", "blur", scenario) }
            device.waitForBlurProfilingScenario(scenario)
          }
        },
      ) {
        if (cold) {
          startActivityAndWait { it.selectBenchmarkSample("blur-profiling", "blur", scenario) }
          device.waitForBlurProfilingScenario(scenario)
        } else {
          device.runBlurProfilingScenario(scenario)
          check(device.waitForObjectOrNull(By.res("blur_profiling_phase_complete")) != null) {
            "Noise/tint workload did not complete: " + device.findObjects(By.pkg(GLASS_TARGET_PACKAGE)).map {
              "${it.resourceName}:${it.text}:${it.visibleBounds}"
            }
          }
        }
      }
    }
  }

  private fun verifyFrozenApks() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val arguments = InstrumentationRegistry.getArguments()
    val dryRun = arguments.getString("androidx.benchmark.dryRunMode.enable").toBoolean()
    val device = UiDevice.getInstance(instrumentation)
    for ((role, packageName) in listOf("target" to GLASS_TARGET_PACKAGE, "benchmark" to instrumentation.context.packageName)) {
      val expected = arguments.getString("haze.${role}Sha256")
      if (dryRun && expected == null) continue
      require(expected != null && expected.matches(Regex("[a-f0-9]{64}"))) {
        "Measured noise/tint runs require haze.${role}Sha256 for the frozen APK"
      }
      val path = device.executeShellCommand("pm path $packageName").trim().removePrefix("package:")
      check(path.matches(Regex("/data/app/[-~A-Za-z0-9_=./+]+\\.apk"))) { "Expected a single installed APK for $role" }
      val actual = device.executeShellCommand("sha256sum $path").substringBefore(' ')
      check(actual == expected) { "$role installed APK differs from the frozen build: $actual" }
    }
  }
}
