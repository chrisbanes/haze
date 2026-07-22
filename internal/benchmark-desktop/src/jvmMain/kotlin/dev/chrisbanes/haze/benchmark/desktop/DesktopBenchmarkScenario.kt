// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import androidx.compose.runtime.Composable

public data class NormalizedPoint(val x: Float, val y: Float) {
  init {
    require(x.isFinite() && x in 0f..1f)
    require(y.isFinite() && y in 0f..1f)
  }
}

public enum class DesktopInputEventType { Move, Press, Drag, Release, Exit }

public data class DesktopInputEvent(
  val offsetNanos: Long,
  val type: DesktopInputEventType,
  val position: NormalizedPoint?,
)

public interface DesktopBenchmarkScenario {
  public val id: String
  public val protocolVersion: Int
  public val events: List<DesktopInputEvent>

  @Composable
  public fun Content()

  public suspend fun reset()
}

public fun validateScenario(scenario: DesktopBenchmarkScenario) {
  require(scenario.id.matches(Regex("[a-z][a-z0-9_]{0,63}")))
  require(scenario.protocolVersion > 0)
  require(scenario.events.isNotEmpty())
  require(scenario.events.zipWithNext().all { (a, b) -> a.offsetNanos <= b.offsetNanos })
  scenario.events.forEach { event ->
    require(event.offsetNanos >= 0)
    require((event.type == DesktopInputEventType.Exit) == (event.position == null))
  }
}
