// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chrisbanes.haze.sample.Samples

private const val FORCE_BLUR_EXTRA = "dev.chrisbanes.haze.sample.android.FORCE_BLUR"

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

    setContent {
      Samples(
        appTitle = title.toString(),
        forceBlur = intent.getBooleanExtra(FORCE_BLUR_EXTRA, false),
      )
    }
  }
}
