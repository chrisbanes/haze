// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chrisbanes.haze.sample.SampleLaunchRequest
import dev.chrisbanes.haze.sample.Samples
import dev.chrisbanes.haze.sample.resolveSampleLaunch

private const val FORCE_BLUR_EXTRA = "dev.chrisbanes.haze.sample.android.FORCE_BLUR"
private const val BENCHMARK_SAMPLE_ROUTE_EXTRA =
  "dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE"
private const val BENCHMARK_SAMPLE_EFFECT_EXTRA =
  "dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_EFFECT"
private const val BENCHMARK_SCENARIO_EXTRA =
  "dev.chrisbanes.haze.sample.android.BENCHMARK_SCENARIO"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    intent.getStringExtra("dev.chrisbanes.haze.sample.android.BENCHMARK_CPU_AFFINITY")?.let { mask ->
      require(mask.matches(Regex("[0-9a-fA-F]+")))
      // Apply before rendering starts; new threads inherit their creator's affinity.
      // This opt-in profiling control lasts until the sample process is stopped.
      val command = ProcessBuilder("/system/bin/taskset", "-ap", mask, Process.myPid().toString())
        .redirectErrorStream(true)
        .start()
      val output = command.inputStream.bufferedReader().use { it.readText() }
      check(command.waitFor() == 0) { "Could not set benchmark CPU affinity: $output" }
    }
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    val launchRequest = intent.toBenchmarkLaunchRequest()

    setContent {
      Samples(
        appTitle = title.toString(),
        forceBlur = launchRequest.forceBlur,
        initialSelection = launchRequest.initialSelection,
        initialProfilingScenarioId = launchRequest.initialProfilingScenarioId,
      )
    }
  }
}

internal class BenchmarkLaunchRequest(
  val initialSelection: SampleLaunchRequest.Selected?,
  val forceBlur: Boolean,
  val initialProfilingScenarioId: String?,
)

internal fun Intent.toBenchmarkLaunchRequest(): BenchmarkLaunchRequest {
  val initialSelection = getStringExtra(BENCHMARK_SAMPLE_ROUTE_EXTRA)?.let { route ->
    val effect = requireNotNull(getStringExtra(BENCHMARK_SAMPLE_EFFECT_EXTRA)) {
      "$BENCHMARK_SAMPLE_EFFECT_EXTRA is required with $BENCHMARK_SAMPLE_ROUTE_EXTRA"
    }
    when (
      val request = resolveSampleLaunch(
        query = mapOf("sample" to route, "effect" to effect),
        samples = Samples,
      )
    ) {
      is SampleLaunchRequest.Selected -> request
      is SampleLaunchRequest.Invalid -> error(request.message)
      SampleLaunchRequest.Normal -> error("Benchmark sample selection was not resolved")
    }
  }

  return BenchmarkLaunchRequest(
    initialSelection = initialSelection,
    forceBlur = getBooleanExtra(FORCE_BLUR_EXTRA, false),
    initialProfilingScenarioId = getStringExtra(BENCHMARK_SCENARIO_EXTRA),
  )
}
