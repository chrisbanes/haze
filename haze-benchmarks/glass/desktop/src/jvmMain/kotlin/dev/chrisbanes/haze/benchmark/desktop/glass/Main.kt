// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop.glass

import dev.chrisbanes.haze.benchmark.desktop.runDesktopBenchmarkSuite
import kotlin.system.exitProcess

public fun main(args: Array<String>) {
  exitProcess(runDesktopBenchmarkSuite(args, suiteId = "glass", scenarioFactories = emptyList()))
}
